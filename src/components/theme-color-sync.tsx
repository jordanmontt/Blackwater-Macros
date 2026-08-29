"use client";

import { useEffect } from "react";
import { useTheme } from "next-themes";

const THEME_COLOR_LIGHT = "#f5f4ef";
const THEME_COLOR_DARK = "#151810";

/**
 * Keeps the page's single <meta name="theme-color"> in sync with the active
 * theme so the Android status bar (including installed PWAs) matches the app
 * UI, not just the system preference.
 */
export function ThemeColorSync() {
  const { resolvedTheme } = useTheme();

  useEffect(() => {
    const meta = document.querySelector('meta[name="theme-color"]');
    if (!meta) return;
    meta.setAttribute("content", resolvedTheme === "dark" ? THEME_COLOR_DARK : THEME_COLOR_LIGHT);
  }, [resolvedTheme]);

  return null;
}