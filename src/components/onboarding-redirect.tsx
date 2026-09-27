"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { markOnboardingDone, needsOnboarding, onboardingDone } from "@/lib/onboarding";

/**
 * Sends a new user from Comidas to /bienvenida once (profile incomplete, steps
 * not done in this browser). A complete profile marks the steps as done.
 */
export function OnboardingRedirect() {
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (pathname !== "/" || onboardingDone()) return;
    let alive = true;
    api
      .session()
      .then((session) => {
        if (!alive) return;
        if (needsOnboarding(onboardingDone(), session.calorieProfile)) router.replace("/bienvenida");
        else markOnboardingDone();
      })
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [pathname, router]);

  return null;
}
