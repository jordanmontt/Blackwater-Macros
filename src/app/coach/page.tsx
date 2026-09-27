"use client";

import { Fragment, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ArrowUpIcon, MessageSquarePlusIcon, SparklesIcon, SquareIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { resetCoach, sendCoachMessage, stopCoach, useCoachChat, type ChatMessage } from "@/lib/ai/coach-chat";
import { isCoachReady, useAiSettings } from "@/lib/ai/settings";
import { BROWSER_MODEL, checkBrowserModel, useBrowserModel } from "@/lib/ai/browser-model";
import { cn } from "@/lib/utils";
import { formatTemplate, t } from "@/i18n";

/**
 * Coach (§4.3 of docs/AI-PLAN.md): a chat about food, the diet and progress.
 * The conversation lives in memory only; each question carries a fresh
 * summary of the user's data when «El coach puede ver mis datos» is on.
 */
export default function CoachPage() {
  const settings = useAiSettings();
  const chat = useCoachChat();
  const [draft, setDraft] = useState("");
  const endRef = useRef<HTMLDivElement>(null);
  const browserModel = useBrowserModel();
  const ready = isCoachReady(settings, browserModel.status === "ready");
  const onBrowser = settings.coachEngine === "browser";

  useEffect(() => {
    if (onBrowser) void checkBrowserModel();
  }, [onBrowser]);
  const lastText = chat.messages.at(-1)?.text;

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: "end" });
  }, [chat.messages.length, lastText]);

  function send(text: string) {
    if (text.trim() === "" || chat.streaming) return;
    setDraft("");
    void sendCoachMessage(text);
  }

  return (
    <main className="mx-auto flex min-h-[calc(100dvh-5.5rem)] w-full max-w-2xl flex-col gap-4 px-4 pt-4 md:min-h-[calc(100dvh-5rem)] md:pt-6">
      <header className="flex items-center justify-between gap-2">
        <h1 className="text-lg font-semibold">{t.coach.title}</h1>
        {chat.messages.length > 0 ? (
          <Button variant="outline" size="sm" onClick={resetCoach}>
            <MessageSquarePlusIcon /> {t.coach.newChat}
          </Button>
        ) : null}
      </header>

      {!ready ? (
        <Card>
          <CardContent className="space-y-3 pt-4 text-sm">
            <p>{t.coach.notConfigured}</p>
            <Button nativeButton={false} render={<Link href="/ajustes" />}>
              <SparklesIcon /> {t.coach.configure}
            </Button>
          </CardContent>
        </Card>
      ) : (
        <>
          <div className="flex-1">
          {chat.messages.length === 0 ? (
            <section className="space-y-3">
              <p className="text-sm text-muted-foreground">{t.coach.intro}</p>
              <div className="flex flex-wrap gap-2">
                {t.coach.examples.map((example) => (
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
              send(draft);
            }}
          >
            <div className="flex items-end gap-2">
              <Textarea
                aria-label={t.coach.placeholder}
                placeholder={t.coach.placeholder}
                rows={1}
                className="max-h-40 min-h-10 resize-none"
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
                    event.preventDefault();
                    send(draft);
                  }
                }}
              />
              {chat.streaming ? (
                <Button type="button" size="icon" variant="outline" aria-label={t.coach.stop} onClick={stopCoach}>
                  <SquareIcon />
                </Button>
              ) : (
                <Button type="submit" size="icon" aria-label={t.coach.send} disabled={draft.trim() === ""}>
                  <ArrowUpIcon />
                </Button>
              )}
            </div>
            <p className="text-[11px] leading-snug text-muted-foreground">
              {t.coach.disclaimer} {onBrowser
                ? formatTemplate(t.coach.engineBrowser, { model: BROWSER_MODEL.name })
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
  return (
    <li className={cn("flex", mine ? "justify-end" : "justify-start")}>
      <div
        className={cn(
          "max-w-[85%] rounded-2xl px-3.5 py-2 text-sm",
          mine ? "bg-primary text-primary-foreground" : "border bg-card",
        )}
      >
        {message.error ? (
          <>
            <p className="text-destructive">{t.ai.errors[message.error]}</p>
            {message.errorDetail ? <p className="mt-0.5 text-xs text-muted-foreground">{message.errorDetail}</p> : null}
          </>
        ) : message.text === "" && pending ? (
          <p className="text-muted-foreground">{t.coach.thinking}</p>
        ) : mine ? (
          <p className="whitespace-pre-wrap">{message.text}</p>
        ) : (
          <SimpleMarkdown text={message.text} />
        )}
      </div>
    </li>
  );
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
