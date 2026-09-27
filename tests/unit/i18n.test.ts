import { describe, expect, it } from "vitest";
import { t as es } from "@/i18n/es";
import { en } from "@/i18n/en";
import { fr } from "@/i18n/fr";
import { it as itDict } from "@/i18n/it";
import { de } from "@/i18n/de";
import { languageFromAcceptLanguage, resolveLanguage } from "@/i18n/languages";

/**
 * Requisitos de los idiomas de la web (como los strings de Android):
 *  - cada idioma tiene las mismas claves y listas del mismo largo que el español,
 *  - y los mismos marcadores «{n}», para que ningún número desaparezca de una frase,
 *  - la cookie manda; sin ella, el primer idioma soportado del navegador; si no, español.
 */

type Entry = { path: string; value: string };

function flatten(value: unknown, path = ""): Entry[] {
  if (typeof value === "string") return [{ path, value }];
  if (Array.isArray(value)) return value.flatMap((item, i) => flatten(item, `${path}[${i}]`));
  return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
    flatten(child, path ? `${path}.${key}` : key),
  );
}

const placeholders = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((match) => match[1]).sort();

const reference = flatten(es);

describe.each([
  ["en", en],
  ["fr", fr],
  ["it", itDict],
  ["de", de],
])("diccionario %s", (_, dictionary) => {
  const entries = flatten(dictionary);

  it("tiene las mismas claves y listas que el español", () => {
    expect(entries.map((entry) => entry.path)).toEqual(reference.map((entry) => entry.path));
  });

  it("usa los mismos marcadores en cada texto", () => {
    const byPath = new Map(entries.map((entry) => [entry.path, entry.value]));
    for (const { path, value } of reference) {
      expect([path, placeholders(byPath.get(path) ?? "")]).toEqual([path, placeholders(value)]);
    }
  });

  it("no deja textos vacíos", () => {
    expect(entries.filter((entry) => entry.value.trim() === "").map((entry) => entry.path)).toEqual([]);
  });
});

describe("elegir idioma", () => {
  it("la cookie manda sobre el navegador", () => {
    expect(resolveLanguage("de", "fr-CH,fr;q=0.9")).toBe("de");
  });

  it("«Sistema» sigue al primer idioma soportado del navegador", () => {
    expect(resolveLanguage("system", "pt-BR,pt;q=0.9,it;q=0.8,en;q=0.7")).toBe("it");
    expect(languageFromAcceptLanguage("en;q=0.5,fr-CH")).toBe("fr");
  });

  it("sin nada soportado, español", () => {
    expect(resolveLanguage(undefined, "ja-JP")).toBe("es");
    expect(resolveLanguage(null, null)).toBe("es");
  });
});
