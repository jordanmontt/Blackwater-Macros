"use client";

import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { api, ApiError } from "@/lib/api";
import { nowDateTimeLocalValue, parseLocalDateTime, toDateTimeLocalValue } from "@/lib/core/dates";
import type { WeightDTO } from "@/lib/core/types";
import { normalizeDecimal, toDecimalInput } from "@/lib/utils";
import { t } from "@/i18n";

interface WeightFormState {
  weight: string;
  bodyFat: string;
  datetime: string;
  note: string;
}

function formFor(entry: WeightDTO | null): WeightFormState {
  if (!entry) return { weight: "", bodyFat: "", datetime: nowDateTimeLocalValue(), note: "" };
  return {
    weight: toDecimalInput(entry.weightKg),
    bodyFat: entry.bodyFatPct !== null ? toDecimalInput(entry.bodyFatPct) : "",
    datetime: toDateTimeLocalValue(new Date(entry.measuredAt)),
    note: entry.note ?? "",
  };
}

/**
 * Create or edit a weigh-in. `entry` null = new. The parent re-mounts it per
 * open (`key`) so the fields always start from `entry`.
 */
export function WeightFormDialog({
  open,
  entry,
  onOpenChange,
  onSaved,
}: {
  open: boolean;
  entry: WeightDTO | null;
  onOpenChange: (open: boolean) => void;
  onSaved: () => void | Promise<unknown>;
}) {
  const [form, setForm] = useState<WeightFormState>(() => formFor(entry));
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const weightKg = Number(form.weight.trim().replace(",", "."));
    const measuredAt = parseLocalDateTime(form.datetime);
    if (!measuredAt || !Number.isFinite(weightKg) || weightKg <= 0) {
      toast.error(t.peso.weightRequired);
      return;
    }
    const bodyFatStr = form.bodyFat.trim().replace(",", ".");
    const bodyFatPct = bodyFatStr !== "" && !Number.isNaN(Number(bodyFatStr)) ? Number(bodyFatStr) : null;
    setPending(true);
    try {
      const payload = { measuredAt: measuredAt.toISOString(), weightKg, bodyFatPct, note: form.note.trim() || null };
      if (entry) {
        await api.updateWeight(entry.id, payload);
      } else {
        await api.createWeight(payload);
      }
      onOpenChange(false);
      await onSaved();
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : t.common.errorGeneric);
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>{entry ? t.peso.edit : t.peso.addTitle}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="weight-input">{t.peso.weightLabel}</Label>
              <Input
                id="weight-input"
                inputMode="decimal"
                step="any"
                autoFocus
                value={form.weight}
                onChange={(event) => setForm({ ...form, weight: normalizeDecimal(event.target.value) })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="weight-datetime">{t.peso.datetimeLabel}</Label>
              <Input
                id="weight-datetime"
                type="datetime-local"
                value={form.datetime}
                onChange={(event) => setForm({ ...form, datetime: event.target.value })}
              />
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-6 px-2 text-xs"
                onClick={() => setForm({ ...form, datetime: nowDateTimeLocalValue() })}
              >
                {t.peso.nowButton}
              </Button>
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="bodyfat-input">{t.peso.bodyFatLabel}</Label>
            <Input
              id="bodyfat-input"
              inputMode="decimal"
              step="any"
              placeholder={t.peso.bodyFatPlaceholder}
              value={form.bodyFat}
              onChange={(event) => setForm({ ...form, bodyFat: normalizeDecimal(event.target.value) })}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="weight-note">{t.peso.noteLabel}</Label>
            <Textarea
              id="weight-note"
              rows={2}
              value={form.note}
              onChange={(event) => setForm({ ...form, note: event.target.value })}
            />
          </div>
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
