"use client";

import { useMemo } from "react";
import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { addDaysToKey, formatDateKeyLong, todayKey } from "@/lib/dates";
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
      <div className="flex min-w-0 flex-col items-center px-1">
        <span className="truncate text-sm font-medium">{formatDateKeyLong(value)}</span>
        {!isToday ? (
          <button
            type="button"
            onClick={() => onChange(today)}
            className="text-xs text-muted-foreground underline-offset-2 hover:underline"
          >
            {t.common.today}
          </button>
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
