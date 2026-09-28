import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { LANGUAGES } from "@/i18n/languages";

/**
 * `public/models/local-models.json` is the list of phone models the Android
 * app offers (it reads it each time it opens). Editing it changes every phone,
 * so this checks each entry the way the app will (`parseLocalModelCatalog`):
 * a wrong entry would be silently skipped there.
 */

interface Entry {
  id: string;
  name: string;
  url: string;
  fileName: string;
  sizeBytes: number;
  sha256: string;
  recommendedPhoneGb: number;
  vision: boolean;
  notes: Record<string, string>;
  hidden?: boolean;
  minAppVersionCode?: number;
}

const catalog = JSON.parse(readFileSync("public/models/local-models.json", "utf8")) as { models: Entry[] };

describe("catálogo de modelos del teléfono", () => {
  it("cada modelo pasa las comprobaciones de la app", () => {
    expect(catalog.models.length).toBeGreaterThan(0);
    for (const model of catalog.models) {
      expect(model.id, model.id).toMatch(/^[a-z0-9][a-z0-9._-]{0,63}$/);
      expect(model.name.trim(), model.id).not.toBe("");
      expect(model.fileName, model.id).toMatch(/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}\.litertlm$/);
      expect(model.url.startsWith("https://huggingface.co/"), model.id).toBe(true);
      expect(model.url.endsWith(`/${model.fileName}`), model.id).toBe(true);
      expect(model.sizeBytes, model.id).toBeGreaterThan(0);
      expect(model.sha256, model.id).toMatch(/^[0-9a-f]{64}$/);
      expect(model.recommendedPhoneGb, model.id).toBeGreaterThanOrEqual(1);
      expect(typeof model.vision, model.id).toBe("boolean");
    }
  });

  it("sin ids repetidos y con la nota en los cinco idiomas", () => {
    const ids = catalog.models.map((model) => model.id);
    expect(new Set(ids).size).toBe(ids.length);
    for (const model of catalog.models) {
      expect(Object.keys(model.notes).sort(), model.id).toEqual([...LANGUAGES].sort());
    }
  });
});
