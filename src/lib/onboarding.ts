"use client";

import { isCalorieProfileComplete } from "@/lib/core/calories";
import type { CalorieProfile } from "@/lib/core/types";

/** Set once the first-launch steps were finished or skipped (this browser only). */
const DONE_KEY = "bw:onboarding-done";

export function onboardingDone(): boolean {
  try {
    return localStorage.getItem(DONE_KEY) === "1";
  } catch {
    return true;
  }
}

export function markOnboardingDone(): void {
  try {
    localStorage.setItem(DONE_KEY, "1");
  } catch {
    // Private mode: the steps may show again next time; they can be skipped.
  }
}

/** D12 on the web: after login, once, while the profile is still incomplete. */
export function needsOnboarding(done: boolean, profile: CalorieProfile): boolean {
  return !done && !isCalorieProfileComplete(profile);
}

/** Activity used when the user does not open «Actividad»: a light, common week. */
export const DEFAULT_ACTIVITY = { gymDaysPerWeek: 0, gymSessionMinutes: 60, walkingMinutesPerDay: 30 } as const;
