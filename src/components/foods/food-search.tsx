"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { SearchIcon, SparklesIcon } from "lucide-react";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api";
import { formatNumberEs } from "@/lib/core/dates";
import { searchGenericFoods, type FoodProduct, type GenericFood } from "@/lib/core/foods";
import { isDemoMode } from "@/lib/demo-store";
import {
  genericChoice,
  loadGenericFoods,
  productChoice,
  recentFoods,
  type FoodChoice,
} from "@/lib/foods/foods-client";
import { formatTemplate, t } from "@/i18n";

const ONLINE_MIN_CHARS = 3;
const ONLINE_DEBOUNCE_MS = 450;

/** Online results per query for this page load (Open Food Facts limits searches per minute). */
const onlineCache = new Map<string, FoodProduct[]>();

/**
 * Search: generic foods first (offline, instant, Spanish names), then Open
 * Food Facts products (online, debounced). Recent picks when the box is empty.
 */
export function FoodSearch({
  onPick,
  onEstimateQuery,
}: {
  onPick: (choice: FoodChoice) => void;
  /** «Estimar “…” con IA»: the query as a text estimate (e.g. «3 plátanos»). */
  onEstimateQuery?: (query: string) => void;
}) {
  const [query, setQuery] = useState("");
  const [generic, setGeneric] = useState<GenericFood[] | null>(null);
  const [online, setOnline] = useState<{ query: string; products: FoodProduct[] } | null>(null);
  const [onlineError, setOnlineError] = useState(false);
  const [recents] = useState(recentFoods);
  const latest = useRef("");

  useEffect(() => {
    let alive = true;
    loadGenericFoods()
      .then((foods) => alive && setGeneric(foods))
      .catch(() => alive && setGeneric([]));
    return () => {
      alive = false;
    };
  }, []);

  const trimmed = query.trim();
  const genericMatches = useMemo(
    () => (generic && trimmed ? searchGenericFoods(generic, trimmed, "es", 8) : []),
    [generic, trimmed],
  );

  useEffect(() => {
    latest.current = trimmed;
    if (trimmed.length < ONLINE_MIN_CHARS || isDemoMode()) return;
    if (onlineCache.has(trimmed.toLowerCase())) return;
    const timer = setTimeout(() => {
      api
        .searchFoods(trimmed, "es")
        .then((products) => {
          onlineCache.set(trimmed.toLowerCase(), products);
          if (latest.current === trimmed) {
            setOnlineError(false);
            setOnline({ query: trimmed, products });
          }
        })
        .catch(() => {
          if (latest.current === trimmed) setOnlineError(true);
        });
    }, ONLINE_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [trimmed]);

  const onlineProducts =
    online && online.query === trimmed ? online.products : (onlineCache.get(trimmed.toLowerCase()) ?? null);
  const waitingOnline = trimmed.length >= ONLINE_MIN_CHARS && !isDemoMode() && !onlineError && onlineProducts === null;

  return (
    <div className="space-y-3">
      <div className="relative">
        <SearchIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          autoFocus
          type="search"
          aria-label={t.addFood.search}
          placeholder={t.addFood.searchPlaceholder}
          className="pl-9"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>

      {!trimmed ? (
        recents.length > 0 ? (
          <ResultGroup title={t.addFood.recent}>
            {recents.map((choice) => (
              <ResultRow key={choice.key} choice={choice} onPick={onPick} />
            ))}
          </ResultGroup>
        ) : null
      ) : (
        <>
          {genericMatches.length > 0 ? (
            <ResultGroup title={t.addFood.generic}>
              {genericMatches.map((match) => (
                <ResultRow key={match.food.id} choice={genericChoice(match.food, match.name)} onPick={onPick} />
              ))}
            </ResultGroup>
          ) : null}
          {onlineProducts && onlineProducts.length > 0 ? (
            <ResultGroup title={t.addFood.products}>
              {onlineProducts.map((product) => (
                <ResultRow key={`${product.code}-${product.name}`} choice={productChoice(product)} onPick={onPick} />
              ))}
            </ResultGroup>
          ) : null}
          {waitingOnline ? <p className="text-xs text-muted-foreground">{t.addFood.searching}</p> : null}
          {onlineError ? <p className="text-xs text-muted-foreground">{t.addFood.onlineError}</p> : null}
          {isDemoMode() ? <p className="text-xs text-muted-foreground">{t.addFood.demoNoOnline}</p> : null}
          {generic !== null && genericMatches.length === 0 && onlineProducts?.length === 0 ? (
            <p className="py-4 text-center text-sm text-muted-foreground">
              {formatTemplate(t.addFood.noResults, { q: trimmed })}
            </p>
          ) : null}
          {onEstimateQuery && trimmed.length >= 2 ? (
            <button
              type="button"
              onClick={() => onEstimateQuery(trimmed)}
              className="flex w-full items-center gap-2 rounded-xl border border-dashed px-3 py-2.5 text-left text-sm transition-colors hover:bg-accent"
            >
              <SparklesIcon className="size-4 shrink-0 text-primary" />
              {formatTemplate(t.photo.estimateQuery, { q: trimmed })}
            </button>
          ) : null}
        </>
      )}
      <p className="text-[11px] leading-snug text-muted-foreground">{t.addFood.credits}</p>
    </div>
  );
}

function ResultGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section aria-label={title}>
      <h3 className="mb-1.5 text-xs font-medium text-muted-foreground">{title}</h3>
      <ul className="divide-y overflow-hidden rounded-xl border">{children}</ul>
    </section>
  );
}

function ResultRow({ choice, onPick }: { choice: FoodChoice; onPick: (choice: FoodChoice) => void }) {
  return (
    <li>
      <button
        type="button"
        onClick={() => onPick(choice)}
        className="flex w-full flex-col items-start px-3 py-2 text-left transition-colors hover:bg-accent"
      >
        <span className="text-sm font-medium">
          {choice.name}
          {choice.brand ? <span className="font-normal text-muted-foreground"> · {choice.brand}</span> : null}
        </span>
        <span className="text-xs text-muted-foreground tabular-nums">
          {formatTemplate(t.addFood.per100, {
            kcal: formatNumberEs(choice.per100g.calories),
            p: formatNumberEs(choice.per100g.protein, 1),
          })}
        </span>
      </button>
    </li>
  );
}
