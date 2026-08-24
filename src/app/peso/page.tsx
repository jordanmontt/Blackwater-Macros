"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { PencilIcon, PlusIcon, Trash2Icon } from "lucide-react";
import { toast } from "sonner";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ThemeToggle } from "@/components/theme-toggle";
import { api, ApiError } from "@/lib/api";
import { formatDateKeyLong, formatNumberEs, formatTimestamp, nowDateTimeLocalValue, parseLocalDateTime } from "@/lib/dates";
import { normalizeDecimal, toDecimalInput } from "@/lib/utils";
import { formatTemplate } from "@/i18n";
import type { WeightDTO } from "@/lib/types";
import { t } from "@/i18n";

interface WeightFormState {
  id: string | null;
  weight: string;
  datetime: string;
  note: string;
}

function freshForm(): WeightFormState {
  return { id: null, weight: "", datetime: nowDateTimeLocalValue(), note: "" };
}

export default function PesoPage() {
  const [weights, setWeights] = useState<WeightDTO[] | null>(null);
  const [form, setForm] = useState<WeightFormState>(freshForm);
  const [formOpen, setFormOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [deleting, setDeleting] = useState<WeightDTO | null>(null);

  const refresh = useCallback(async () => {
    try {
      setWeights(await api.listWeights());
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) toast.error(t.common.errorGeneric);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    api
      .listWeights()
      .then((data) => {
        if (!cancelled) setWeights(data);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  const currentWeight = weights?.at(-1)?.weightKg ?? null;

  const groupedByDay = useMemo(() => {
    const groups = new Map<string, WeightDTO[]>();
    for (const entry of weights ?? []) {
      const day = entry.measuredAt.slice(0, 10);
      const bucket = groups.get(day) ?? [];
      bucket.push(entry);
      groups.set(day, bucket);
    }
    return [...groups.entries()].sort(([a], [b]) => b.localeCompare(a));
  }, [weights]);

  function openCreate() {
    setForm(freshForm());
    setFormOpen(true);
  }

  function openEdit(entry: WeightDTO) {
    setForm({
      id: entry.id,
      weight: toDecimalInput(entry.weightKg),
      datetime: toDateTimeLocal(entry.measuredAt),
      note: entry.note ?? "",
    });
    setFormOpen(true);
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const weightKg = Number(form.weight.trim().replace(",", "."));
    const measuredAt = parseLocalDateTime(form.datetime);
    if (!measuredAt || !Number.isFinite(weightKg) || weightKg <= 0) {
      toast.error(t.common.errorGeneric);
      return;
    }
    setPending(true);
    try {
      const payload = {
        measuredAt: measuredAt.toISOString(),
        weightKg,
        note: form.note.trim() || null,
      };
      if (form.id) {
        await api.updateWeight(form.id, payload);
      } else {
        await api.createWeight(payload);
      }
      setFormOpen(false);
      await refresh();
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : t.common.errorGeneric);
    } finally {
      setPending(false);
    }
  }

  async function handleDeleteConfirmed() {
    if (!deleting) return;
    try {
      await api.deleteWeight(deleting.id);
      setDeleting(null);
      await refresh();
    } catch {
      toast.error(t.common.errorGeneric);
    }
  }

  return (
    <main className="mx-auto w-full max-w-2xl px-4 pt-4 md:pt-6">
      <header className="flex items-center justify-between gap-2">
        <h1 className="text-lg font-semibold">{t.peso.title}</h1>
        <div className="flex items-center gap-1">
          <ThemeToggle />
          <Button size="sm" onClick={openCreate}>
            <PlusIcon /> {t.peso.addTitle}
          </Button>
        </div>
      </header>

      <Card className="mt-4">
        <CardHeader className="pb-3">
          <CardDescription>{t.stats.currentWeight}</CardDescription>
          <CardTitle className="text-4xl tabular-nums">
            {currentWeight !== null ? `${formatNumberEs(currentWeight, 1)} kg` : "—"}
          </CardTitle>
        </CardHeader>
        <CardFooter className="text-xs text-muted-foreground">
          {weights ? t_entries(weights.length) : t.common.loading}
        </CardFooter>
      </Card>

      {weights !== null && weights.length === 0 ? (
        <button
          type="button"
          onClick={openCreate}
          className="mt-5 w-full rounded-xl border border-dashed py-10 text-center text-sm text-muted-foreground transition-colors hover:bg-accent"
        >
          {t.peso.emptyList}
        </button>
      ) : null}

      <section className="mt-5 space-y-4">
        {groupedByDay.map(([day, entries]) => (
          <div key={day}>
            <h2 className="text-sm font-medium text-muted-foreground">
              {formatDateKeyLong(day)}
            </h2>
            <ul className="mt-1.5 divide-y overflow-hidden rounded-xl border">
              {[...entries].reverse().map((entry) => (
                <li key={entry.id} className="flex items-center gap-3 bg-card px-3 py-2.5">
                  <span className="w-12 shrink-0 text-sm text-muted-foreground tabular-nums">
                    {formatTimestamp(entry.measuredAt)}
                  </span>
                  <span className="font-medium tabular-nums">
                    {formatNumberEs(entry.weightKg, 1)} kg
                  </span>
                  {entry.note ? (
                    <span className="min-w-0 flex-1 truncate text-xs text-muted-foreground">
                      {entry.note}
                    </span>
                  ) : (
                    <span className="flex-1" />
                  )}
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={t.peso.edit}
                    onClick={() => openEdit(entry)}
                  >
                    <PencilIcon className="text-muted-foreground" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={t.peso.delete}
                    onClick={() => setDeleting(entry)}
                  >
                    <Trash2Icon className="text-muted-foreground" />
                  </Button>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </section>

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{form.id ? t.peso.edit : t.peso.addTitle}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="weight-input">{t.peso.weightLabel}</Label>
                <Input
                  id="weight-input"
                  required
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
              <Label htmlFor="weight-note">{t.peso.noteLabel}</Label>
              <Textarea
                id="weight-note"
                rows={2}
                value={form.note}
                onChange={(event) => setForm({ ...form, note: event.target.value })}
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="ghost" onClick={() => setFormOpen(false)}>
                {t.peso.cancel}
              </Button>
              <Button type="submit" disabled={pending}>
                {pending ? t.common.loading : t.peso.save}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog
        open={deleting !== null}
        onOpenChange={(open) => {
          if (!open) setDeleting(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t.peso.deleteConfirmTitle}</AlertDialogTitle>
            <AlertDialogDescription>{t.peso.deleteConfirmBody}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t.peso.cancel}</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => void handleDeleteConfirmed()}>
              {t.peso.delete}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </main>
  );
}

function toDateTimeLocal(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}

function t_entries(count: number) {
  return formatTemplate(t.peso.entriesCount, { n: count });
}
