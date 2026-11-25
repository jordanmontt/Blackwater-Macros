"use client";

import { PageHeader } from "@/components/page-header";
import { Fragment, useEffect, useRef, useState, useSyncExternalStore } from "react";
import Link from "next/link";
import { ArrowUpIcon, CopyIcon, ImagePlusIcon, MessageCircleIcon, MessageSquarePlusIcon, SparklesIcon, SquareIcon, XIcon } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { resetCoach, sendCoachMessage, stopCoach, useCoachChat, type ChatMessage } from "@/lib/ai/coach-chat";
import { isCoachReady, useAiSettings } from "@/lib/ai/settings";
import { checkBrowserModel, useBrowserModel } from "@/lib/ai/browser-model";
import { cn } from "@/lib/utils";
import { downscalePhoto, MAX_PHOTOS } from "@/lib/ai/images";
import type { AiImage } from "@/lib/core/ai-providers";
import { formatTemplate, t } from "@/i18n";
import { aiSettingsHref } from "@/lib/ai-settings-origin";
import { foodDetailsLines, foodExampleQuestion } from "@/lib/ai/food-questions";
import { api } from "@/lib/api";
import { addDaysToKey, todayKey } from "@/lib/core/dates";
import type { FoodDetails } from "@/lib/core/food-questions";

/**
 * Coach: a chat about food, the diet and progress.
 * The conversation lives in memory only; each question carries a fresh
 * summary of the user's data when «El coach puede ver mis datos» is on.
 */
/** A question tapped before AI was set up: it waits in the box when the coach is ready (never sent by itself). */
const PENDING_QUESTION_KEY = "bw:coach-pending-question";

function savePendingQuestion(question: string) {
  try {
    sessionStorage.setItem(PENDING_QUESTION_KEY, question);
  } catch {
    // Storage blocked: the question is simply not kept.
  }
}

function readPendingQuestion(): string | null {
  try {
    return sessionStorage.getItem(PENDING_QUESTION_KEY);
  } catch {
    return null;
  }
}

function clearPendingQuestion() {
  try {
    sessionStorage.removeItem(PENDING_QUESTION_KEY);
  } catch {
    // Storage blocked: nothing was kept.
  }
}

/** Only this page writes the pending question, and it re-renders itself when it does. */
const subscribeNever = () => () => {};

/**
 * An example question about one of the user's own foods («¿Cuántas veces por
 * semana como «Avena»?»), from the last 4 weeks; null while loading or when the
 * coach may not read the food log.
 */
function useFoodExample(enabled: boolean): string | null {
  const [example, setExample] = useState<string | null>(null);
  useEffect(() => {
    if (!enabled) return;
    let alive = true;
    const today = todayKey();
    api.listMeals(addDaysToKey(today, -27), today).then(
      (meals) => {
        if (alive) setExample(foodExampleQuestion({ meals, today }));
      },
      // No example then: the usual ones stay.
      () => undefined,
    );
    return () => {
      alive = false;
    };
  }, [enabled]);
  return enabled ? example : null;
}

export default function CoachPage() {
  const settings = useAiSettings();
  const chat = useCoachChat();
  // null until the user types: then a question waiting from before the setup no longer shows.
  const [draft, setDraft] = useState<string | null>(null);
  // Photos for the next question: in memory only, dropped once sent or removed.
  const [photos, setPhotos] = useState<AiImage[]>([]);
  const photoInput = useRef<HTMLInputElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const browserModel = useBrowserModel();
  const ready = isCoachReady(settings, browserModel.status === "ready");
  const onBrowser = settings.coachEngine === "browser";
  // Once the coach is ready, a question tapped before setup waits in the box until edited or sent.
  const pending = useSyncExternalStore(subscribeNever, readPendingQuestion, () => null);
  const text = draft ?? (ready ? (pending ?? "") : "");
  const foodExample = useFoodExample(settings.coachSeesData && settings.coachSeesFood);
  const examples = foodExample === null ? t.coach.examples : [foodExample, ...t.coach.examples];

  useEffect(() => {
    if (onBrowser) void checkBrowserModel();
  }, [onBrowser]);
  const lastText = chat.messages.at(-1)?.text;

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: "end" });
  }, [chat.messages.length, lastText]);

  function send(text: string) {
    if ((text.trim() === "" && photos.length === 0) || chat.streaming) return;
    setDraft("");
    clearPendingQuestion();
    setPhotos([]);
    void sendCoachMessage(text, photos);
  }

  function pickPhotos() {
    // The browser model reads text only (WebLLM); photos need a cloud provider.
    if (onBrowser) toast.info(t.coach.photosBrowser);
    else photoInput.current?.click();
  }

  async function addPhotos(files: FileList | null) {
    const added: AiImage[] = [];
    for (const file of Array.from(files ?? []).slice(0, MAX_PHOTOS - photos.length)) {
      try {
        added.push(await downscalePhoto(file));
      } catch {
        toast.error(t.photo.photoError);
      }
    }
    setPhotos((current) => [...current, ...added].slice(0, MAX_PHOTOS));
  }

  return (
    <main className="mx-auto flex min-h-[calc(100dvh-5.5rem)] w-full max-w-2xl flex-col gap-4 px-4 pt-4 md:min-h-[calc(100dvh-5rem)] md:pt-6">
      <PageHeader
        title={t.coach.title}
        end={
          chat.messages.length > 0 ? (
            <Button variant="outline" size="sm" onClick={resetCoach}>
              <MessageSquarePlusIcon /> {t.coach.newChat}
            </Button>
          ) : null
        }
      />

      {!ready ? (
        <section className="flex flex-col items-center gap-3 px-2 pt-6 text-center">
          <span className="flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
            <MessageCircleIcon className="size-6" />
          </span>
          <h2 className="text-base font-semibold">{t.coach.emptyTitle}</h2>
          <p className="max-w-sm text-sm text-muted-foreground">{t.coach.emptyBody}</p>
          <ul className="flex flex-col items-center gap-2" aria-label={t.coach.examplesNotReady}>
            {examples.slice(0, 3).map((example) => (
              <li key={example}>
                <Button
                  variant="outline"
                  size="sm"
                  className="h-auto rounded-full py-1.5 whitespace-normal"
                  nativeButton={false}
                  render={<Link href={aiSettingsHref("coach")} onClick={() => savePendingQuestion(example)} />}
                >
                  {example}
                </Button>
              </li>
            ))}
          </ul>
          {/* One sentence and the button: it used to say «configure the AI» three times. What a
              question chip does is the list's name for screen readers. */}
          <p className="max-w-sm text-xs text-muted-foreground">{t.coach.notConfigured}</p>
          <Button nativeButton={false} render={<Link href={aiSettingsHref("coach")} />}>
            <SparklesIcon /> {t.coach.configure}
          </Button>
        </section>
      ) : (
        <>
          <div className="flex-1">
          {chat.messages.length === 0 ? (
            <section className="space-y-3">
              <p className="text-sm text-muted-foreground">{t.coach.intro}</p>
              <div className="flex flex-wrap gap-2">
                {examples.map((example) => (
                  <Button
                    key={example}
                    variant="outline"
                    size="sm"
                    className="h-auto rounded-full py-1.5 whitespace-normal text-left"
                    onClick={() => send(example)}
                  >
                    {example}
                  </Button>
                ))}
              </div>
              <p className="text-xs text-muted-foreground">{t.coach.memoryOnly}</p>
            </section>
          ) : (
            <ol aria-label={t.coach.title} className="space-y-3">
              {chat.messages.map((message, index) => (
                <MessageBubble
                  key={message.id}
                  message={message}
                  pending={chat.streaming && index === chat.messages.length - 1}
                />
              ))}
            </ol>
          )}
          </div>

          <form
            className="sticky bottom-[calc(4.5rem+env(safe-area-inset-bottom))] z-10 -mx-4 space-y-1.5 border-t bg-background/95 px-4 pt-2 pb-2 backdrop-blur md:bottom-0"
            onSubmit={(event) => {
              event.preventDefault();
              send(text);
            }}
          >
            {photos.length > 0 ? (
              <div className="space-y-1">
                <ul className="flex gap-2 overflow-x-auto pt-1.5">
                  {photos.map((photo, index) => (
                    <li key={index} className="relative shrink-0">
                      {/* eslint-disable-next-line @next/next/no-img-element -- in-memory data URL, never uploaded to us */}
                      <img
                        src={`data:${photo.mimeType};base64,${photo.data}`}
                        alt={formatTemplate(t.photo.photoAlt, { n: index + 1 })}
                        className="size-14 rounded-lg border object-cover"
                      />
                      <button
                        type="button"
                        aria-label={formatTemplate(t.photo.removePhoto, { n: index + 1 })}
                        onClick={() => setPhotos((current) => current.filter((_, i) => i !== index))}
                        className="absolute -top-1.5 -right-1.5 flex size-5 items-center justify-center rounded-full border bg-background shadow-sm"
                      >
                        <XIcon className="size-3" />
                      </button>
                    </li>
                  ))}
                </ul>
                <p className="text-[11px] leading-snug text-muted-foreground">
                  {formatTemplate(t.coach.photosKept, { provider: t.ai.providerShort[settings.provider] })}
                </p>
              </div>
            ) : null}
            <div className="flex items-end gap-2">
              <Button
                type="button"
                size="icon"
                variant="outline"
                aria-label={t.coach.addPhoto}
                disabled={chat.streaming || photos.length >= MAX_PHOTOS}
                onClick={pickPhotos}
              >
                <ImagePlusIcon />
              </Button>
              <input
                ref={photoInput}
                type="file"
                accept="image/*"
                multiple
                hidden
                data-testid="coach-photo-input"
                onChange={(event) => {
                  void addPhotos(event.target.files);
                  event.target.value = "";
                }}
              />
              <Textarea
                aria-label={t.coach.placeholder}
                placeholder={t.coach.placeholder}
                rows={1}
                className="max-h-40 min-h-10 resize-none"
                value={text}
                onChange={(event) => {
                  clearPendingQuestion();
                  setDraft(event.target.value);
                }}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
                    event.preventDefault();
                    send(text);
                  }
                }}
              />
              {chat.streaming ? (
                <Button type="button" size="icon" variant="outline" aria-label={t.coach.stop} onClick={stopCoach}>
                  <SquareIcon />
                </Button>
              ) : (
                <Button type="submit" size="icon" aria-label={t.coach.send} disabled={text.trim() === "" && photos.length === 0}>
                  <ArrowUpIcon />
                </Button>
              )}
            </div>
            <p className="text-[11px] leading-snug text-muted-foreground">
              {t.coach.disclaimer} {onBrowser
                ? `${formatTemplate(t.coach.engineBrowser, { model: browserModel.status === "ready" ? browserModel.model.name : "" })}. ${t.coach.browserHint}`
                : formatTemplate(t.coach.engine, { provider: t.ai.providerShort[settings.provider] })}
              {settings.coachSeesData ? "" : ` · ${t.coach.noData}`}
            </p>
          </form>
          <div ref={endRef} />
        </>
      )}
    </main>
  );
}

function MessageBubble({ message, pending }: { message: ChatMessage; pending: boolean }) {
  const mine = message.role === "user";
  // Copies the whole message, as in messaging apps: the text as shown (the coach's without the
  // ** of bold); while it is still arriving, what has arrived so far. Nothing for photos alone.
  const copyable = mine ? message.text.trim() : plainText(message.text);
  return (
    <li className={cn("group flex items-end gap-1", mine ? "flex-row-reverse" : "")}>
      <div
        className={cn(
          "max-w-[85%] rounded-2xl px-3.5 py-2 text-sm",
          mine ? "bg-primary text-primary-foreground" : "border bg-card",
        )}
      >
        {message.error ? (
          <>
            {message.text.trim() !== "" ? (
              <div className="mb-1.5">
                <SimpleMarkdown text={message.text} />
              </div>
            ) : null}
            <p className="text-destructive">{t.ai.errors[message.error]}</p>
            {message.errorDetail ? <p className="mt-0.5 text-xs text-muted-foreground">{message.errorDetail}</p> : null}
          </>
        ) : message.text === "" && pending ? (
          <p className="text-muted-foreground">{t.coach.thinking}</p>
        ) : mine ? (
          <>
            {message.images?.length ? (
              <div className="-mx-1 mb-1 flex flex-wrap justify-end gap-1">
                {message.images.map((image, index) => (
                  // eslint-disable-next-line @next/next/no-img-element -- in-memory data URL
                  <img
                    key={index}
                    src={`data:${image.mimeType};base64,${image.data}`}
                    alt={formatTemplate(t.coach.photoSent, { n: index + 1 })}
                    className="size-24 rounded-lg object-cover"
                  />
                ))}
              </div>
            ) : null}
            {message.text ? <p className="whitespace-pre-wrap">{message.text}</p> : null}
          </>
        ) : (
          <>
            <SimpleMarkdown text={message.text} />
            {message.foodDetails && !pending ? <FoodDetailsPanel details={message.foodDetails} /> : null}
          </>
        )}
      </div>
      {copyable ? (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={t.coach.copy}
          title={t.coach.copy}
          // Shown on hover with a mouse; always there on a touch screen.
          className="text-muted-foreground opacity-0 group-hover:opacity-100 focus-visible:opacity-100 pointer-coarse:opacity-100"
          onClick={() =>
            navigator.clipboard.writeText(copyable).then(
              () => toast.success(t.coach.copied),
              () => toast.error(t.coach.copyFailed),
            )
          }
        >
          <CopyIcon />
        </Button>
      ) : null}
    </li>
  );
}

/**
 * «Cómo se calculó» under an answer about the food log: what the app counted,
 * written by the app (never by the model), with a link to the methodology.
 */
function FoodDetailsPanel({ details }: { details: FoodDetails }) {
  return (
    <details className="mt-2 border-t pt-1.5 text-xs text-muted-foreground">
      <summary className="cursor-pointer py-1 font-medium">{t.coach.food.howTitle}</summary>
      <ul className="mt-1 space-y-1">
        {foodDetailsLines(details).map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
      <Link href="/metodologia#food-questions" className="mt-1.5 inline-flex min-h-9 items-center text-primary hover:underline">
        {t.coach.food.methodology}
      </Link>
    </details>
  );
}

/** The text of a message as it reads on screen: no # headings, no ** around bold. */
function plainText(text: string): string {
  return text
    .trim()
    .replace(/^#{1,6}\s+/gm, "")
    .replace(/\*\*([^*]+)\*\*/g, "$1");
}

/** Enough Markdown for chat answers: paragraphs, «- » / «1. » lists and **bold**. */
function SimpleMarkdown({ text }: { text: string }) {
  const blocks = text.trim().split(/\n{2,}/);
  return (
    <div className="space-y-2">
      {blocks.map((block, index) => {
        const lines = block.split("\n");
        const bullets = lines.every((line) => /^\s*([-*•]|\d+[.)])\s+/.test(line));
        if (bullets) {
          const ordered = /^\s*\d/.test(lines[0]);
          const List = ordered ? "ol" : "ul";
          return (
            <List key={index} className={cn("space-y-0.5 pl-5", ordered ? "list-decimal" : "list-disc")}>
              {lines.map((line, i) => (
                <li key={i}>
                  <Inline text={line.replace(/^\s*([-*•]|\d+[.)])\s+/, "")} />
                </li>
              ))}
            </List>
          );
        }
        return (
          <p key={index} className="whitespace-pre-wrap">
            <Inline text={block.replace(/^#{1,6}\s+/gm, "")} />
          </p>
        );
      })}
    </div>
  );
}

function Inline({ text }: { text: string }) {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return (
    <>
      {parts.map((part, index) =>
        part.startsWith("**") && part.endsWith("**") && part.length > 4 ? (
          <strong key={index}>{part.slice(2, -2)}</strong>
        ) : (
          <Fragment key={index}>{part}</Fragment>
        ),
      )}
    </>
  );
}
