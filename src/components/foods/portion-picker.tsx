"use client";

import { useState } from "react";
import { formatNumber } from "@/i18n/format";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { scalePer100g } from "@/lib/core/foods";
import type { FoodChoice } from "@/lib/foods/foods-client";
import { normalizeDecimal } from "@/lib/utils";
import { formatTemplate, t } from "@/i18n";

/** How much of the chosen food: grams (or ml), with 100 g and one-serving shortcuts. */
export function PortionPicker({ choice, onAdd }: { choice: FoodChoice; onAdd: (grams: number) => void }) {
  const [text, setText] = useState(() => String(choice.servingGrams ?? 100).replace(".", ","));
  const grams = Number(text.replace(",", "."));
  const valid = Number.isFinite(grams) && grams > 0 && grams <= 5000;
  const totals = scalePer100g(choice.per100g, valid ? grams : 0);

  return (
    <div className="space-y-4">
      <div>
        <p className="font-medium">
          {choice.name}
          {choice.brand ? <span className="font-normal text-muted-foreground"> · {choice.brand}</span> : null}
        </p>
        <p className="text-xs text-muted-foreground tabular-nums">
          {formatTemplate(t.addFood.per100, {
            kcal: formatNumber(choice.per100g.calories),
            p: formatNumber(choice.per100g.protein, 1),
          })}
        </p>
        {choice.incomplete ? <p className="mt-1 text-xs text-muted-foreground">{t.addFood.incomplete}</p> : null}
      </div>

      <div className="space-y-2">
        <Label htmlFor="portion-grams">{t.addFood.gramsLabel}</Label>
        <Input
          id="portion-grams"
          inputMode="decimal"
          autoFocus
          value={text}
          onChange={(event) => setText(normalizeDecimal(event.target.value))}
        />
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" size="sm" className="rounded-full" onClick={() => setText("100")}>
            100 g
          </Button>
          {choice.servingGrams ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="rounded-full"
              onClick={() => setText(String(choice.servingGrams).replace(".", ","))}
            >
              {formatTemplate(t.addFood.serving, { g: formatNumber(choice.servingGrams, 1) })}
            </Button>
          ) : null}
        </div>
      </div>

      <dl className="grid grid-cols-4 gap-2 rounded-xl border p-3 text-center" data-testid="portion-totals">
        {[
          [t.hoy.calories, `${formatNumber(totals.calories)}`, "kcal"],
          [t.hoy.protein, formatNumber(totals.protein, 1), "g"],
          [t.hoy.carbs, formatNumber(totals.carbs, 1), "g"],
          [t.hoy.fat, formatNumber(totals.fat, 1), "g"],
        ].map(([label, value, unit]) => (
          <div key={label}>
            <dt className="truncate text-[11px] text-muted-foreground">{label}</dt>
            <dd className="text-sm font-semibold tabular-nums">
              {value} <span className="text-xs font-normal text-muted-foreground">{unit}</span>
            </dd>
          </div>
        ))}
      </dl>

      <Button className="w-full" disabled={!valid} onClick={() => onAdd(grams)}>
        {t.addFood.addItem}
      </Button>
    </div>
  );
}
