"use client";

import { useCallback, useEffect, useState } from "react";
import type { ExpenditureNoticeDismissal } from "./core/types";

const GAP_KEY = "bw-expenditure-notice-dismissed";
const WEIGH_IN_HINT_KEY = "bw-weigh-in-hint-dismissed";

function readGapDismissal(): ExpenditureNoticeDismissal | null {
  try {
    const raw = localStorage.getItem(GAP_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<ExpenditureNoticeDismissal>;
    return (parsed.verdict === "higher" || parsed.verdict === "lower") && typeof parsed.date === "string"
      ? { verdict: parsed.verdict, date: parsed.date }
      : null;
  } catch {
    return null;
  }
}

function readHintDismissed(): boolean {
  try {
    return localStorage.getItem(WEIGH_IN_HINT_KEY) === "1";
  } catch {
    return false;
  }
}

/**
 * «Not now» on the Today card's expenditure notices, kept in this browser only
 * (a per-device convenience, like the phone's preferences). `loaded` is false
 * until storage was read, so a dismissed notice never flashes on first paint.
 */
export function useExpenditureNotices(): {
  loaded: boolean;
  gapDismissal: ExpenditureNoticeDismissal | null;
  dismissGap: (dismissal: ExpenditureNoticeDismissal) => void;
  weighInHintDismissed: boolean;
  dismissWeighInHint: () => void;
} {
  const [loaded, setLoaded] = useState(false);
  const [gapDismissal, setGapDismissal] = useState<ExpenditureNoticeDismissal | null>(null);
  const [weighInHintDismissed, setWeighInHintDismissed] = useState(false);

  useEffect(() => {
    // Storage is read after mount: the server render has no localStorage.
    void Promise.resolve().then(() => {
      setGapDismissal(readGapDismissal());
      setWeighInHintDismissed(readHintDismissed());
      setLoaded(true);
    });
  }, []);

  const dismissGap = useCallback((dismissal: ExpenditureNoticeDismissal) => {
    setGapDismissal(dismissal);
    try {
      localStorage.setItem(GAP_KEY, JSON.stringify(dismissal));
    } catch {
      // Private mode or blocked storage: hidden for this visit only.
    }
  }, []);

  const dismissWeighInHint = useCallback(() => {
    setWeighInHintDismissed(true);
    try {
      localStorage.setItem(WEIGH_IN_HINT_KEY, "1");
    } catch {
      // Private mode or blocked storage: hidden for this visit only.
    }
  }, []);

  return { loaded, gapDismissal, dismissGap, weighInHintDismissed, dismissWeighInHint };
}
