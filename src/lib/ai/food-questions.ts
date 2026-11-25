import { aiComplete } from "@/lib/ai/client";
import { browserCompleteJson } from "@/lib/ai/browser-model";
import { loadFoodLog } from "@/lib/ai/coach-data";
import { aiLanguage } from "@/lib/ai/estimate";
import { aiConfigOf, type AiSettings } from "@/lib/ai/settings";
import { todayKey } from "@/lib/core/dates";
import { MIN_WEIGHT_SPAN_DAYS, MIN_WEIGH_INS } from "@/lib/core/expenditure";
import {
  FOOD_QUESTION_RULES,
  answerFoodRequest,
  buildFoodRequestPrompt,
  foodQuestionMessage,
  mostLoggedFood,
  parseFoodRequest,
  type FoodAnswer,
  type FoodDetails,
  type FoodLog,
  type FoodRequest,
} from "@/lib/core/food-questions";
import { formatDateMedium } from "@/i18n/format";
import { formatTemplate, t } from "@/i18n";

/**
 * What the coach does with a question once the app has read it against the
 * food log: answer as usual (`normal`), let the model explain the app's numbers
 * (`numbers`), or answer itself when there is nothing to compute (`reply`).
 */
export type FoodStep =
  | { kind: "normal" }
  | { kind: "numbers"; request: FoodRequest; modelText: string; details: FoodDetails }
  | { kind: "reply"; request: FoodRequest | null; text: string; details: FoodDetails | null };

/**
 * Steps 1 and 2 of a food question: the model turns [question] into a request
 * (in the cloud or in this browser, as the coach), the app computes it from the
 * log. [previous] is the conversation's last request, for follow-ups.
 */
export async function readFoodQuestion(
  settings: AiSettings,
  question: string,
  previous: FoodRequest | null,
  signal?: AbortSignal,
): Promise<FoodStep> {
  const today = todayKey();
  const system = buildFoodRequestPrompt(today, aiLanguage(), previous);
  const [raw, log] = await Promise.all([
    settings.coachEngine === "browser"
      ? browserCompleteJson(system, question, FOOD_QUESTION_RULES.localRequestMaxTokens, signal)
      : aiComplete(
          aiConfigOf(settings),
          {
            system,
            messages: [{ role: "user", text: question }],
            json: true,
            stream: false,
            maxTokens: FOOD_QUESTION_RULES.requestMaxTokens,
          },
          signal,
        ),
    loadFoodLog(today),
  ]);
  const parsed = parseFoodRequest(raw);
  if (!parsed.ok) return { kind: "reply", request: null, text: unclearText(log), details: null };
  const request = parsed.request;
  if (request.analysis === "none") return { kind: "normal" };
  const answer = answerFoodRequest(log, request);
  if (answer === null) return { kind: "reply", request, text: unclearText(log), details: null };
  if (answer.kind === "result") {
    return { kind: "numbers", request, modelText: foodQuestionMessage(question, answer.text), details: answer.details };
  }
  return { kind: "reply", request, text: replyText(answer), details: answer.kind === "not_enough" ? answer.details : null };
}

function day(key: string): string {
  return formatDateMedium(new Date(`${key}T00:00:00`));
}

function quoted(text: string): string {
  return formatTemplate(t.coach.food.quoted, { text });
}

/** An example question with one of the user's own foods (or a general one with an empty log). */
export function foodExampleQuestion(log: Pick<FoodLog, "meals" | "today">): string {
  const food = mostLoggedFood(log.meals, log.today);
  return food === null ? t.coach.food.exampleGeneric : formatTemplate(t.coach.food.example, { food });
}

/** The question could not be read as a request: said plainly, with an example to copy. */
function unclearText(log: FoodLog): string {
  return formatTemplate(t.coach.food.unclear, { example: foodExampleQuestion(log) });
}

/** What the app says itself when there is nothing to compute. */
function replyText(answer: Exclude<FoodAnswer, { kind: "result" }>): string {
  const f = t.coach.food;
  if (answer.kind === "no_match") {
    const parts = [
      formatTemplate(f.noMatch, {
        foods: answer.foods.map(quoted).join(", "),
        from: day(answer.range.from),
        to: day(answer.range.to),
      }),
    ];
    if (answer.lastSeen !== null) parts.push(formatTemplate(f.lastSeen, { date: day(answer.lastSeen) }));
    if (answer.similar.length > 0) parts.push(formatTemplate(f.similar, { names: answer.similar.map(quoted).join(", ") }));
    return parts.join(" ");
  }
  const { groups, split } = answer.details;
  if (answer.reason === "side_days") {
    return formatTemplate(f.notEnoughSides, { min: FOOD_QUESTION_RULES.minSideDays, at: day(split!.at) });
  }
  if (answer.reason === "weigh_ins") {
    return formatTemplate(f.notEnoughWeighIns, { at: day(split!.at), n: MIN_WEIGH_INS, span: MIN_WEIGHT_SPAN_DAYS });
  }
  if (groups.length === 1) {
    const [group] = groups;
    return formatTemplate(f.notEnough, {
      min: FOOD_QUESTION_RULES.minLoggedDays,
      from: day(group.from),
      to: day(group.to),
      n: group.loggedDays,
    });
  }
  return formatTemplate(f.notEnoughGroups, {
    min: FOOD_QUESTION_RULES.minLoggedDays,
    groups: groups.map((group) => formatTemplate(f.groupCount, { label: f.labels[group.label], n: group.loggedDays })).join(", "),
  });
}

/** The lines of «Cómo se calculó», in the app's language: written by the app, never by the model. */
export function foodDetailsLines(details: FoodDetails): string[] {
  const f = t.coach.food;
  const lines = [f.analyses[details.analysis]];
  for (const group of details.groups) {
    lines.push(
      formatTemplate(f.group, {
        label: f.labels[group.label],
        from: day(group.from),
        to: day(group.to),
        logged: group.loggedDays,
        days: group.totalDays,
      }),
    );
  }
  if (details.split) lines.push(formatTemplate(f.split[details.split.reason], { at: day(details.split.at) }));
  if (details.counted.length > 0) {
    const names = details.counted.join(", ");
    lines.push(details.moreCounted > 0 ? formatTemplate(f.countedMore, { names, more: details.moreCounted }) : formatTemplate(f.counted, { names }));
  }
  if (details.withoutAmount > 0 && details.analysis === "food_stats") lines.push(formatTemplate(f.withoutAmount, { n: details.withoutAmount }));
  if (details.few) lines.push(f.few);
  if (details.analysis !== "day_log") lines.push(f.unlogged);
  if (details.analysis === "food_vs_weight") lines.push(f.association);
  return lines;
}
