"use client";

import { useEffect } from "react";
import { refreshModelList } from "@/lib/ai/model-list";
import { aiConfigOf, getAiSettings } from "@/lib/ai/settings";

/** When the app opens, the model list of the chosen provider is refreshed (at most once a day, silently). */
export function AiModelRefresh() {
  useEffect(() => {
    refreshModelList(aiConfigOf(getAiSettings())).catch(() => {
      // Offline or a bad key: the cached list stays; Ajustes → IA shows why.
    });
  }, []);
  return null;
}
