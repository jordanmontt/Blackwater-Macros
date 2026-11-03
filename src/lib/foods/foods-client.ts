"use client";

import {
  barcodeMiss,
  parseGenericIndex,
  parseOffProduct,
  type BarcodeMiss,
  type FoodLang,
  type FoodProduct,
  type GenericFood,
  type Per100g,
} from "@/lib/core/foods";
import { offKnownProduct, parseOffCorrection, type OffCorrection, type OffKnown } from "@/lib/core/new-product";
import { OFF_SERVER, ownChoice, ownProduct } from "@/lib/foods/own-products";

/** A food the user picked from search or a barcode, before choosing the portion. */
export interface FoodChoice {
  key: string;
  /** The barcode, for products (missing in recents saved before it existed). */
  code?: string | null;
  name: string;
  brand: string | null;
  per100g: Per100g;
  servingGrams: number | null;
  source: "generic" | "off";
  /** Some macros were missing in the source (shown as 0). */
  incomplete: boolean;
}

const OFF_PRODUCT_URL = `${OFF_SERVER}/api/v2/product/`;
const OFF_FIELDS =
  "code,product_name,product_name_es,product_name_en,product_name_fr,product_name_de,product_name_it,generic_name,brands,serving_size,serving_quantity,nutriments,lang";

let genericFoods: Promise<GenericFood[]> | null = null;

/** The bundled generic foods (loaded once, then from memory). */
export function loadGenericFoods(): Promise<GenericFood[]> {
  genericFoods ??= fetch("/foods/generic.json")
    .then((response) => {
      if (!response.ok) throw new Error(`generic foods ${response.status}`);
      return response.json();
    })
    .then(parseGenericIndex)
    .catch((error) => {
      genericFoods = null;
      throw error;
    });
  return genericFoods;
}

/**
 * A barcode lookup: the product (`own`: one the user added, from this
 * browser), or why there is none, with the name and brand Open Food Facts
 * already has when it knows the product without nutrition.
 */
export type BarcodeResult =
  | { choice: FoodChoice; own: boolean }
  | { miss: BarcodeMiss; known: OffKnown };

/**
 * Looks a barcode up: first the products added in this browser (Open Food Facts
 * may still answer «unknown» from its cache, or not have it yet), then Open Food
 * Facts (browser request; the product API allows it). Throws when offline.
 */
export async function lookupBarcode(code: string, lang: FoodLang = "es"): Promise<BarcodeResult> {
  const own = ownProduct(code);
  if (own) return { choice: ownChoice(own), own: true };
  const url = `${OFF_PRODUCT_URL}${encodeURIComponent(code)}.json?fields=${OFF_FIELDS}`;
  const response = await fetch(url, { signal: AbortSignal.timeout(8000) });
  if (response.status === 404) return { miss: barcodeMiss(code, null), known: { name: null, brand: null, lang: null } };
  if (!response.ok) throw new Error(`Open Food Facts ${response.status}`);
  const json: unknown = await response.json();
  const product = parseOffProduct(json, lang);
  return product
    ? { choice: productChoice(product), own: false }
    : { miss: barcodeMiss(code, json), known: offKnownProduct(json, lang) };
}

/** The nutrition label photo Open Food Facts shows for `lc`, only from its own image server. */
function offLabelImage(json: unknown): string | null {
  const product = (json as { product?: { image_nutrition_url?: unknown } } | null)?.product;
  const url = product?.image_nutrition_url;
  return typeof url === "string" && /^https:\/\/images\.openfoodfacts\.(org|net)\//.test(url) ? url : null;
}

async function fetchProduct(code: string, lang: FoodLang, fields: string): Promise<unknown | null> {
  const query = `fields=${fields}&lc=${lang}`;
  const response = await fetch(`${OFF_PRODUCT_URL}${encodeURIComponent(code)}.json?${query}`, {
    signal: AbortSignal.timeout(8000),
    cache: "no-store",
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Open Food Facts ${response.status}`);
  return response.json();
}

/**
 * What Open Food Facts has now for «Corregir valores» (fetched again, so the
 * form starts from its latest values and the basis it stores them in), with
 * its label photo. Null when it doesn't have the product; throws when offline.
 */
export async function fetchCorrection(
  code: string,
  lang: FoodLang,
): Promise<{ correction: OffCorrection; labelImage: string | null } | null> {
  const json = await fetchProduct(code, lang, `${OFF_FIELDS},nutrition_data_per,image_nutrition_url`);
  const correction = json === null ? null : parseOffCorrection(json, code, lang);
  return correction ? { correction, labelImage: offLabelImage(json) } : null;
}

/** The label photo Open Food Facts has for a product (to show it while editing); null offline or without one. */
export async function fetchLabelImage(code: string, lang: FoodLang): Promise<string | null> {
  try {
    return offLabelImage(await fetchProduct(code, lang, "image_nutrition_url"));
  } catch {
    return null;
  }
}

export function genericChoice(food: GenericFood, name: string): FoodChoice {
  return {
    key: food.id,
    code: null,
    name,
    brand: null,
    per100g: food.per100g,
    servingGrams: null,
    source: "generic",
    incomplete: false,
  };
}

export function productChoice(product: FoodProduct): FoodChoice {
  return {
    key: `off:${product.code ?? product.name}`,
    code: product.code,
    name: product.name,
    brand: product.brand,
    per100g: product.per100g,
    servingGrams: product.servingGrams,
    source: "off",
    incomplete: product.incomplete,
  };
}

const RECENT_KEY = "bw:recent-foods";
const RECENT_MAX = 20;

/** Foods picked recently on this device (shown when the search box is empty). */
export function recentFoods(): FoodChoice[] {
  try {
    const raw = localStorage.getItem(RECENT_KEY);
    const list = raw ? (JSON.parse(raw) as FoodChoice[]) : [];
    return Array.isArray(list) ? list.slice(0, RECENT_MAX) : [];
  } catch {
    return [];
  }
}

export function rememberFood(choice: FoodChoice): void {
  try {
    const next = [choice, ...recentFoods().filter((item) => item.key !== choice.key)].slice(0, RECENT_MAX);
    localStorage.setItem(RECENT_KEY, JSON.stringify(next));
  } catch {
    // Private mode or full storage: recents are a convenience only.
  }
}
