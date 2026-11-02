"use client";

import { useEffect, useState } from "react";
import { CheckIcon, Loader2Icon, PencilIcon } from "lucide-react";
import { formatNumber } from "@/i18n/format";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { scalePer100g } from "@/lib/core/foods";
import type { FoodChoice } from "@/lib/foods/foods-client";
import { ownProduct, useOwnProducts, type OwnProduct } from "@/lib/foods/own-products";
import { normalizeDecimal } from "@/lib/utils";
import { formatTemplate, t } from "@/i18n";

/**
 * How much of the chosen food: grams (or ml), with 100 g and one-serving
 * shortcuts. A product the user added (or corrected) says so, can be edited
 * (`onEdit`) and shows whether Open Food Facts has it yet. Any other Open Food
 * Facts product offers «Corregir valores» (`onCorrect`, false when Open Food
 * Facts couldn't be reached).
 */
export function PortionPicker({
  choice,
  onAdd,
  onEdit,
  onCorrect,
}: {
  choice: FoodChoice;
  onAdd: (grams: number) => void;
  onEdit?: (own: OwnProduct) => void;
  onCorrect?: () => Promise<boolean>;
}) {
  useOwnProducts();
  const own = ownProduct(choice.code);
  const [correcting, setCorrecting] = useState<"idle" | "busy" | "failed">("idle");
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
        {own ? (
          <div className="mt-2 space-y-1">
            <div className="flex items-center gap-2">
              <Badge variant="secondary">{own.correction ? t.newProduct.yourCorrection : t.newProduct.yourProduct}</Badge>
              {onEdit ? (
                <Button variant="ghost" size="sm" className="ml-auto" onClick={() => onEdit(own)}>
                  <PencilIcon /> {t.newProduct.edit}
                </Button>
              ) : null}
            </div>
            <OwnProductStatus own={own} />
          </div>
        ) : onCorrect && choice.source === "off" && choice.code ? (
          <div className="mt-1">
            <Button
              variant="link"
              size="sm"
              className="h-auto px-0 text-xs text-muted-foreground"
              disabled={correcting === "busy"}
              onClick={async () => {
                setCorrecting("busy");
                setCorrecting((await onCorrect()) ? "idle" : "failed");
              }}
            >
              {correcting === "busy" ? <Loader2Icon className="animate-spin" /> : null}
              {t.newProduct.correctLink}
            </Button>
            {correcting === "failed" ? (
              <p className="text-xs text-destructive" role="alert">
                {t.newProduct.correctFailed}
              </p>
            ) : null}
          </div>
        ) : null}
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

function useOnline(): boolean {
  const [online, setOnline] = useState(() => (typeof navigator === "undefined" ? true : navigator.onLine));
  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);
  return online;
}

/** Whether Open Food Facts has the product yet, in one line. */
function OwnProductStatus({ own }: { own: OwnProduct }) {
  const online = useOnline();
  if (own.state === "sent") {
    return (
      <p className="flex items-center gap-1 text-xs text-green-600 dark:text-green-400" role="status">
        <CheckIcon className="size-3.5" /> {own.correction ? t.newProduct.status.sentCorrection : t.newProduct.status.sent}
      </p>
    );
  }
  const text =
    own.state === "pending"
      ? online
        ? t.newProduct.status.sending
        : t.newProduct.status.pending
      : own.state === "account"
        ? t.newProduct.status.account
        : `${t.newProduct.status.rejected}${own.detail ? ` (${own.detail})` : ""}`;
  return (
    <p className={own.state === "pending" ? "text-xs text-muted-foreground" : "text-xs text-destructive"} role="status">
      {text}
    </p>
  );
}
