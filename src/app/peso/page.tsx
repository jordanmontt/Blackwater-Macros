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
  CardContent,
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
import { movingAverageByDays } from "@/lib/stats";
import { round1 } from "@/lib/nutrition";
import { formatTemplate } from "@/i18n";
import type { WeightDTO } from "@/lib/types";
import { t } from "@/i18n";
import {
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

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
  const currentBodyFat = weights?.at(-1)?.bodyFatPct ?? null;

  const hasBodyFat = useMemo(
    () => (weights ?? []).some((w) => w.bodyFatPct !== null),
    [weights],
  );

  const bodyFatSeries = useMemo(() => {
    return (weights ?? [])
      .filter((w) => w.bodyFatPct !== null)
      .map((w) => ({ date: w.measuredAt.slice(0, 10), value: w.bodyFatPct! }));
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
        <CardFooter className="flex items-center justify-between text-xs text-muted-foreground">
          <span>{weights ? t_entries(weights.length) : t.common.loading}</span>
          {currentBodyFat !== null && (
            <span>
              {t.peso.currentBodyFat}: {formatNumberEs(currentBodyFat, 1)}{t.peso.bodyFatUnit}
            </span>
          )}
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
                    {entry.bodyFatPct !== null && (
                      <span className="text-muted-foreground">
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

      {hasBodyFat && bodyFatSeries.length > 1 ? (
        <Card className="mt-5">
          <CardHeader className="pb-0">
            <CardTitle className="text-base">{t.peso.bodyFatChartTitle}</CardTitle>
          </CardHeader>
          <CardContent className="h-56 px-2 sm:h-64">
            <BodyFatChart data={bodyFatSeries} />
          </CardContent>
        </Card>
      ) : null}

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

function BodyFatChart({ data }: { data: { date: string; value: number }[] }) {
  const movingAverage = useMemo(() => movingAverageByDays(data, 7), [data]);
  const rows = data.map((point, i) => ({
    date: point.date,
    value: point.value,
    tendencia: movingAverage[i] === null ? null : round1(movingAverage[i] as number),
  }));

  return (
    <ResponsiveContainer width="100%" height="100%">
      <ComposedChart data={rows} margin={{ top: 16, right: 12, bottom: 0, left: -18 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
        <XAxis
          dataKey="date"
          tickFormatter={(value: string) => formatDateKeyLong(value).slice(0, 5)}
          tick={{ fontSize: 10 }}
          interval="preserveStartEnd"
          minTickGap={28}
          tickLine={false}
          axisLine={false}
        />
        <YAxis
          domain={["auto", "auto"]}
          tick={{ fontSize: 10 }}
          tickLine={false}
          axisLine={false}
          width={44}
          tickFormatter={(value: number) => `${String(Math.round(value * 10) / 10)}%`}
        />
        <Tooltip
          contentStyle={{
            background: "var(--popover)",
            border: "1px solid var(--border)",
            borderRadius: 10,
            fontSize: 12,
            color: "var(--popover-foreground)",
          }}
          formatter={(value: unknown) =>
            value === null || value === undefined
              ? "—"
              : `${formatNumberEs(Number(value), 1)}%`
          }
          labelFormatter={(value) => formatDateKeyLong(String(value))}
        />
        <Line
          type="monotone"
          dataKey="value"
          name={t.peso.bodyFatChartTitle}
          stroke="var(--chart-2)"
          strokeWidth={1.5}
          dot={{ r: 2 }}
        />
        <Line
          type="monotone"
          dataKey="tendencia"
          name={t.stats.trendLine}
          stroke="currentColor"
          className="text-muted-foreground"
          strokeWidth={2.5}
          dot={false}
          connectNulls
        />
      </ComposedChart>
    </ResponsiveContainer>
  );
}
