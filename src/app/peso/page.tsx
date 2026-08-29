"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Logo } from "@/components/logo";
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
  CardContent,
  CardDescription,
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
import { WeightFatChart, type WeightFatRow } from "@/components/weight-fat-chart";
import { api, ApiError } from "@/lib/api";
import {
  addDaysToKey,
  formatDateKeyLong,
  formatNumberEs,
  nowDateTimeLocalValue,
  toDateTimeLocalValue,
  parseLocalDateTime,
} from "@/lib/dates";
import { normalizeDecimal, toDecimalInput } from "@/lib/utils";
import { movingAverageByDays } from "@/lib/stats";
import { round1 } from "@/lib/nutrition";
import type { WeightDTO } from "@/lib/types";
import { t } from "@/i18n";

interface WeightFormState {
  id: string | null;
  weight: string;
  bodyFat: string;
  datetime: string;
  note: string;
}

function freshForm(): WeightFormState {
  return { id: null, weight: "", bodyFat: "", datetime: nowDateTimeLocalValue(), note: "" };
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
  const currentBodyFat = useMemo(() => {
    if (!weights) return null;
    for (let i = weights.length - 1; i >= 0; i--) {
      const entry = weights[i];
      if (entry.bodyFatPct !== null) return entry.bodyFatPct;
    }
    return null;
  }, [weights]);

  const changeFat7d = useMemo(() => {
    if (!weights) return null;
    const fatEntries = weights.filter((entry) => entry.bodyFatPct !== null);
    const latest = fatEntries.at(-1);
    if (!latest) return null;
    const latestDay = latest.measuredAt.slice(0, 10);
    const cutoff = addDaysToKey(latestDay, -7);
    const prior = [...fatEntries].reverse().find((entry) => entry.measuredAt.slice(0, 10) <= cutoff);
    if (!prior) return null;
    return round1((latest.bodyFatPct as number) - (prior.bodyFatPct as number));
  }, [weights]);

  const changeWeight7d = useMemo(() => {
    if (!weights) return null;
    const latest = weights.at(-1);
    if (!latest) return null;
    const latestDay = latest.measuredAt.slice(0, 10);
    const cutoff = addDaysToKey(latestDay, -7);
    const prior = [...weights].reverse().find((entry) => entry.measuredAt.slice(0, 10) <= cutoff);
    if (!prior) return null;
    return round1(latest.weightKg - prior.weightKg);
  }, [weights]);

  const chartRows = useMemo<WeightFatRow[]>(() => {
    const points = (weights ?? []).map((entry) => ({
      date: entry.measuredAt.slice(0, 10),
      value: entry.weightKg,
    }));
    const trend = movingAverageByDays(points, 7);
    const fatByDay = new Map<string, number>();
    for (const entry of weights ?? []) {
      if (entry.bodyFatPct !== null) fatByDay.set(entry.measuredAt.slice(0, 10), entry.bodyFatPct);
    }
    return points.map((point, i) => ({
      date: point.date,
      weight: point.value,
      weightTrend: trend[i] === null ? null : round1(trend[i] as number),
      bodyFatPct: fatByDay.get(point.date) ?? null,
    }));
  }, [weights]);

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
      bodyFat: entry.bodyFatPct !== null ? toDecimalInput(entry.bodyFatPct) : "",
      datetime: toDateTimeLocalValue(new Date(entry.measuredAt)),
      note: entry.note ?? "",
    });
    setFormOpen(true);
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const weightKg = Number(form.weight.trim().replace(",", "."));
    const measuredAt = parseLocalDateTime(form.datetime);
    if (!measuredAt || !Number.isFinite(weightKg) || weightKg <= 0) {
      toast.error(t.peso.weightRequired);
      return;
    }
    const bodyFatStr = form.bodyFat.trim().replace(",", ".");
    const bodyFatPct =
      bodyFatStr !== "" && !Number.isNaN(Number(bodyFatStr)) ? Number(bodyFatStr) : null;
    setPending(true);
    try {
      const payload = {
        measuredAt: measuredAt.toISOString(),
        weightKg,
        bodyFatPct,
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
      <header className="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
        <div className="flex items-center justify-self-start gap-2">
          <Logo size="header" priority />
        </div>
        <h1 className="text-lg font-semibold text-center">{t.peso.title}</h1>
      </header>

      <Card className="mt-4">
        <CardHeader className="pb-2">
          <CardDescription>{t.peso.currentWeight}</CardDescription>
          <CardTitle className="text-4xl tabular-nums">
            {currentWeight !== null ? `${formatNumberEs(currentWeight, 1)} kg` : "—"}
          </CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-3 gap-2 px-4 pb-4">
          <div className="rounded-lg border px-3 py-2">
            <p className="line-clamp-2 min-h-[2lh] text-xs font-medium leading-snug text-muted-foreground">
              {t.peso.changeWeight7d}
            </p>
            <p className="text-lg font-semibold tabular-nums">
              {changeWeight7d === null
                ? "—"
                : `${changeWeight7d > 0 ? "+" : ""}${formatNumberEs(changeWeight7d, 1)} kg`}
            </p>
          </div>
          <div className="rounded-lg border px-3 py-2">
            <p className="line-clamp-2 min-h-[2lh] text-xs font-medium leading-snug text-muted-foreground">
              {t.peso.currentBodyFat}
            </p>
            <p className="text-lg font-semibold tabular-nums">
              {currentBodyFat !== null ? `${formatNumberEs(currentBodyFat, 1)}%` : "—"}
            </p>
          </div>
          <div className="rounded-lg border px-3 py-2">
            <p className="line-clamp-2 min-h-[2lh] text-xs font-medium leading-snug text-muted-foreground">
              {t.peso.changeFat7d}
            </p>
            <p className="text-lg font-semibold tabular-nums">
              {changeFat7d === null
                ? "—"
                : `${changeFat7d > 0 ? "+" : ""}${formatNumberEs(changeFat7d, 1)}%`}
            </p>
          </div>
        </CardContent>
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

      {weights !== null && weights.length > 1 ? (
        <Card className="mt-4">
          <CardHeader className="pb-0">
            <CardTitle className="text-base">{t.stats.weightFatChartTitle}</CardTitle>
          </CardHeader>
          <CardContent className="h-56 px-2 sm:h-64">
            <WeightFatChart data={chartRows} />
          </CardContent>
        </Card>
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
                  <span className="font-medium tabular-nums">
                    {formatNumberEs(entry.weightKg, 1)} kg
                    {entry.bodyFatPct !== null && (
                      <span className="hidden text-muted-foreground min-[400px]:inline">
                        {" "}· {formatNumberEs(entry.bodyFatPct, 1)}% grasa
                      </span>
                    )}
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

      <Button
        onClick={openCreate}
        aria-label={t.peso.addTitle}
        size="icon"
        className="fixed right-4 bottom-[calc(5rem+env(safe-area-inset-bottom))] z-50 size-14 rounded-full shadow-lg md:bottom-6"
      >
        <PlusIcon className="size-6" />
      </Button>
    </main>
  );
}