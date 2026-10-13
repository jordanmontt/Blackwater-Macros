"use client";

import { useMemo } from "react";
import { formatDateKeyLong } from "@/i18n/format";
import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { addDaysToKey, todayKey } from "@/lib/core/dates";
import { t } from "@/i18n";

interface DayNavigatorProps {
  value: string;
  onChange: (key: string) => void;
}

/**
 * ‹ date › with the arrows at fixed places: the date takes the space between them and
 * the «Hoy» line is always reserved, so a longer date or another day never moves the
 * arrows (tapping back through many days keeps the thumb on the same spot).
 */
export function DayNavigator({ value, onChange }: DayNavigatorProps) {
  const today = useMemo(() => todayKey(), []);
  const isToday = value === today;

  return (
    <div className="flex w-full max-w-80 items-center gap-1">
      <Button
        variant="ghost"
        size="icon"
        aria-label={t.common.previousDay}
        onClick={() => onChange(addDaysToKey(value, -1))}
      >
        <ChevronLeftIcon />
      </Button>
      {/* Double-click (double-tap on phones) the date to jump back to today. */}
      <div
        className="flex min-w-0 flex-1 cursor-default touch-manipulation select-none flex-col items-center rounded-md px-1"
        data-testid="day-navigator-date"
        onDoubleClick={() => {
          if (!isToday) onChange(today);
        }}
      >
        <span className="truncate text-sm font-medium">{formatDateKeyLong(value)}</span>
        <span className={isToday ? "text-xs text-primary" : "invisible text-xs"} aria-hidden={!isToday}>
          {t.common.today}
        </span>
      </div>
      <Button
        variant="ghost"
        size="icon"
        aria-label={t.common.nextDay}
        onClick={() => onChange(addDaysToKey(value, 1))}
      >
        <ChevronRightIcon />
      </Button>
    </div>
  );
}
