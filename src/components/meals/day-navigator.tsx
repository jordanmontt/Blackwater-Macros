"use client";

import { useMemo } from "react";
import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { addDaysToKey, formatDateKeyLong, todayKey } from "@/lib/core/dates";
import { t } from "@/i18n";

interface DayNavigatorProps {
  value: string;
  onChange: (key: string) => void;
}

export function DayNavigator({ value, onChange }: DayNavigatorProps) {
  const today = useMemo(() => todayKey(), []);
  const isToday = value === today;

  return (
    <div className="flex items-center gap-1">
      <Button
        variant="ghost"
        size="icon"
        aria-label="Día anterior"
        onClick={() => onChange(addDaysToKey(value, -1))}
      >
        <ChevronLeftIcon />
      </Button>
      {/* Double-click (double-tap on phones) the date to jump back to today. */}
      <div
        className="flex min-w-0 cursor-default touch-manipulation select-none flex-col items-center rounded-md px-1"
        data-testid="day-navigator-date"
        onDoubleClick={() => {
          if (!isToday) onChange(today);
        }}
      >
        <span className="truncate text-sm font-medium">{formatDateKeyLong(value)}</span>
        {isToday ? (
          <span className="text-xs text-primary">{t.common.today}</span>
        ) : null}
      </div>
      <Button
        variant="ghost"
        size="icon"
        aria-label="Día siguiente"
        onClick={() => onChange(addDaysToKey(value, 1))}
      >
        <ChevronRightIcon />
      </Button>
    </div>
  );
}
