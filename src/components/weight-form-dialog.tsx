"use client";

import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { api, errorText } from "@/lib/api";
import { deviceTimeZone, nowDateTimeLocalValue, parseZonedDateTime, toZonedDateTimeValue } from "@/lib/core/dates";
import type { WeightDTO } from "@/lib/core/types";
import { normalizeDecimal, toDecimalInput } from "@/lib/utils";
import { t } from "@/i18n";

interface WeightFormState {
  weight: string;
  bodyFat: string;
  /** `YYYY-MM-DD`, from `<input type="date">`. */
  date: string;
  /** `HH:MM`, from `<input type="time">`. */
  time: string;
  note: string;
  fasted: boolean;
}

/** An edit shows the date and time where the weigh-in was taken (its saved zone), as the list does. */
function formFor(entry: WeightDTO | null, defaultFasted: boolean): WeightFormState {
  const [date, time] = (entry ? toZonedDateTimeValue(new Date(entry.measuredAt), entry.timeZone) : nowDateTimeLocalValue()).split("T");
  if (!entry) return { weight: "", bodyFat: "", date, time, note: "", fasted: defaultFasted };
  return {
    weight: toDecimalInput(entry.weightKg),
    bodyFat: entry.bodyFatPct !== null ? toDecimalInput(entry.bodyFatPct) : "",
    date,
    time,
    note: entry.note ?? "",
    fasted: entry.fasted,
  };
}

/** A typed decimal («77,4» or «77.4»), or null when it isn't one. */
function parseDecimal(text: string): number | null {
  const trimmed = text.trim().replace(",", ".");
  if (trimmed === "") return null;
  const value = Number(trimmed);
  return Number.isFinite(value) ? value : null;
}

/**
 * The server's limits (`weightInputSchema`): 20–400 kg, 3–60 % body fat (optional). Checked
 * here first so the message says the range, next to the fields (Android `validateWeight`).
 */
export function validateWeight(
  weightText: string,
  bodyFatText: string,
): { ok: true; weightKg: number; bodyFatPct: number | null } | { ok: false; error: string } {
  const weightKg = parseDecimal(weightText);
  if (weightKg === null || weightKg < 20 || weightKg > 400) return { ok: false, error: t.peso.weightRange };
  if (bodyFatText.trim() === "") return { ok: true, weightKg, bodyFatPct: null };
  const bodyFatPct = parseDecimal(bodyFatText);
  if (bodyFatPct === null || bodyFatPct < 3 || bodyFatPct > 60) return { ok: false, error: t.peso.bodyFatRange };
  return { ok: true, weightKg, bodyFatPct };
}

/**
 * Create or edit a weigh-in. `entry` null = new. The parent re-mounts it per
 * open (`key`) so the fields always start from `entry`. `defaultFasted` is the
 * latest weigh-in's «En ayunas», so it is only touched when the habit changes.
 * A new weigh-in saves the device's time zone; an edit keeps the stored one, and its date and
 * time are that zone's (a weigh-in made on waking in France reads 08:00 from Bolivia too).
 */
export function WeightFormDialog({
  open,
  entry,
  defaultFasted = true,
  onOpenChange,
  onSaved,
}: {
  open: boolean;
  entry: WeightDTO | null;
  defaultFasted?: boolean;
  onOpenChange: (open: boolean) => void;
  onSaved: () => void | Promise<unknown>;
}) {
  const [form, setForm] = useState<WeightFormState>(() => formFor(entry, defaultFasted));
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const result = validateWeight(form.weight, form.bodyFat);
    if (!result.ok) {
      setError(result.error);
      return;
    }
    // Read back in the zone the fields were shown in: the weigh-in's own, the device's for a new one.
    const measuredAt = parseZonedDateTime(`${form.date}T${form.time}`, entry ? entry.timeZone : null);
    if (!measuredAt) {
      setError(t.serverErrors.invalid_datetime);
      return;
    }
    setError(null);
    setPending(true);
    try {
      const payload = {
        measuredAt: measuredAt.toISOString(),
        weightKg: result.weightKg,
        bodyFatPct: result.bodyFatPct,
        note: form.note.trim() || null,
        fasted: form.fasted,
        timeZone: entry ? entry.timeZone : deviceTimeZone(),
      };
      if (entry) {
        await api.updateWeight(entry.id, payload);
      } else {
        await api.createWeight(payload);
      }
      onOpenChange(false);
      await onSaved();
    } catch (error) {
      toast.error(errorText(error));
    } finally {
      setPending(false);
    }
  }

  // Same fields, order and wording as Android's `WeightFormDialog.kt`: weight and body fat side
  // by side, then date and time, then «En ayunas» with its switch on the right, then the note.
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>{entry ? t.peso.editTitle : t.peso.addTitle}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} noValidate className="space-y-3">
          {/* Mixed times of day tilt the trend the measured expenditure is built on. */}
          {entry ? null : <p className="text-xs text-muted-foreground">{t.peso.timingHint}</p>}
          {/* Boxes aligned at the bottom: a label that wraps (large text) must not push its box down. */}
          <div className="grid grid-cols-2 items-end gap-2">
            <div className="space-y-1">
              <Label htmlFor="weight-input">{t.peso.weightLabel}</Label>
              <Input
                id="weight-input"
                inputMode="decimal"
                autoFocus
                aria-invalid={error === t.peso.weightRange || undefined}
                value={form.weight}
                onChange={(event) => setForm({ ...form, weight: normalizeDecimal(event.target.value) })}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="bodyfat-input">{t.peso.bodyFatLabel}</Label>
              <Input
                id="bodyfat-input"
                inputMode="decimal"
                placeholder={t.peso.bodyFatPlaceholder}
                aria-invalid={error === t.peso.bodyFatRange || undefined}
                value={form.bodyFat}
                onChange={(event) => setForm({ ...form, bodyFat: normalizeDecimal(event.target.value) })}
              />
            </div>
          </div>
          <div className="grid grid-cols-2 items-end gap-2">
            <div className="space-y-1">
              <Label htmlFor="weight-date">{t.peso.dateLabel}</Label>
              <Input
                id="weight-date"
                type="date"
                required
                value={form.date}
                onChange={(event) => setForm({ ...form, date: event.target.value })}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="weight-time">{t.peso.timeLabel}</Label>
              <Input
                id="weight-time"
                type="time"
                required
                value={form.time}
                onChange={(event) => setForm({ ...form, time: event.target.value })}
              />
            </div>
          </div>
          {/* Only weigh-ins taken alike are compared: after eating reads 0.5–1 kg higher. One line
              here; why, and what to do after eating, in /metodologia#fasted (Android the same). The
              link sits outside the switch, so the switch keeps its own name. */}
          <div className="rounded-lg border">
            <label className="flex cursor-pointer items-center gap-3 px-3 pt-3">
              <span className="flex-1 space-y-0.5">
                <span className="block text-sm">{t.peso.fastedLabel}</span>
                <span className="block text-xs text-muted-foreground">{t.peso.fastedHint}</span>
              </span>
              {/* A switch drawn the same in every browser (Android's Material switch): the real
                  checkbox stays for keyboard and screen readers, the track and thumb follow it. */}
              <span className="relative inline-flex shrink-0">
                <input
                  id="weight-fasted"
                  type="checkbox"
                  role="switch"
                  className="peer absolute inset-0 size-full cursor-pointer opacity-0"
                  checked={form.fasted}
                  onChange={(event) => setForm({ ...form, fasted: event.target.checked })}
                />
                <span
                  aria-hidden="true"
                  className="pointer-events-none h-6 w-11 rounded-full border-2 border-muted-foreground/60 bg-muted transition-colors peer-checked:border-primary peer-checked:bg-primary peer-focus-visible:ring-3 peer-focus-visible:ring-ring/50 after:absolute after:top-1 after:left-1 after:size-4 after:rounded-full after:bg-muted-foreground after:transition-transform peer-checked:after:translate-x-5 peer-checked:after:bg-primary-foreground"
                />
              </span>
            </label>
            <Link
              href="/metodologia#fasted"
              className="ml-1 inline-flex min-h-10 items-center px-2 text-xs font-medium text-primary hover:underline"
            >
              {t.peso.fastedWhy}
            </Link>
          </div>
          {/* The switch copies the last weigh-in; after noon that habit may not hold. A question, never a block. */}
          {form.fasted && Number(form.time.slice(0, 2)) >= 12 ? (
            <p className="text-xs text-muted-foreground" role="status">
              {t.peso.fastedAfterNoon}
            </p>
          ) : null}
          <div className="space-y-1">
            <Label htmlFor="weight-note">{t.peso.noteLabel}</Label>
            <Textarea
              id="weight-note"
              rows={2}
              value={form.note}
              onChange={(event) => setForm({ ...form, note: event.target.value })}
            />
          </div>
          {error ? (
            <p className="text-xs text-destructive" role="alert">
              {error}
            </p>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              {t.peso.cancel}
            </Button>
            <Button type="submit" disabled={pending}>
              {pending ? t.common.loading : t.peso.save}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
