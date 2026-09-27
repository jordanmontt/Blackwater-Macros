"use client";

import {
  parseGenericIndex,
  parseOffProduct,
  type FoodLang,
  type FoodProduct,
  type GenericFood,
  type Per100g,
} from "@/lib/core/foods";

/** A food the user picked from search or a barcode, before choosing the portion. */
export interface FoodChoice {
  key: string;
  name: string;
  brand: string | null;
  per100g: Per100g;
  servingGrams: number | null;
  source: "generic" | "off";
  /** Some macros were missing in the source (shown as 0). */
  incomplete: boolean;
}

const OFF_PRODUCT_URL = "https://world.openfoodfacts.org/api/v2/product/";
const OFF_FIELDS =
  "code,product_name,product_name_es,product_name_en,product_name_fr,product_name_de,product_name_it,generic_name,brands,serving_size,serving_quantity,nutriments";

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
 * Looks a barcode up in Open Food Facts (browser request; the product API
 * allows it). Null when the product is unknown; throws when offline.
 */
export async function lookupBarcode(code: string, lang: FoodLang = "es"): Promise<FoodProduct | null> {
  const url = `${OFF_PRODUCT_URL}${encodeURIComponent(code)}.json?fields=${OFF_FIELDS}`;
  const response = await fetch(url, { signal: AbortSignal.timeout(8000) });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Open Food Facts ${response.status}`);
  return parseOffProduct(await response.json(), lang);
}

export function genericChoice(food: GenericFood, name: string): FoodChoice {
  return {
    key: food.id,
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
