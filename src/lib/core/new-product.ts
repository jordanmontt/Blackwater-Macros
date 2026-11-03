import { extractJson } from "./ai-schema";
import { normalizeText, parseServingGrams, type FoodLang, type FoodProduct, type Per100g } from "./foods";
import { round1, round2 } from "./nutrition";

/**
 * «Añadir este producto»: a product Open Food Facts doesn't have (or has without
 * nutrition facts), copied by the user from its label, kept on the device and
 * sent to Open Food Facts so the next person who scans it finds it.
 */

/** What the label's numbers are for: 100 g, 100 ml or one serving. */
export type LabelBasis = "100g" | "100ml" | "serving";

/** The «Nuevo producto» form: the label as printed. Null = left empty. */
export interface NewProduct {
  code: string;
  name: string;
  brand: string | null;
  basis: LabelBasis;
  /** «200 ml», «30 g». Required for `serving`, optional otherwise (it adds «1 ración»). */
  servingSize: string | null;
  calories: number | null;
  protein: number | null;
  carbs: number | null;
  fat: number | null;
}

export type NewProductProblem = "name" | "calories" | "protein" | "serving" | "tooHigh";

/** How Open Food Facts stores a product's values: per 100 g (or ml) or per serving. */
export type OffDataPer = "100g" | "serving";

/**
 * «Corregir valores»: a product Open Food Facts has with values that don't
 * match its label. `base` is what Open Food Facts has, in the basis it stores
 * (`per`), to start the form from and to show what changed; `lang` is the
 * product's main language there (null when it doesn't say).
 */
export interface OffCorrection {
  per: OffDataPer;
  base: NewProduct;
  lang: string | null;
}

/**
 * Languages offered for «Idioma del envase», most common first; any other
 * two-letter code (from Open Food Facts or the AI) is accepted too.
 */
export const PACKAGE_LANGS = ["es", "en", "fr", "de", "it", "pt", "nl", "ca", "pl"] as const;

/** A two-letter language code (ISO 639-1, «es»), or null: what Open Food Facts keys names and photos by. */
export function packageLang(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const code = value.trim().toLowerCase();
  return /^[a-z]{2}$/.test(code) ? code : null;
}

/** The shared account products are sent with unless the user signs in with theirs (RELEASING.md). */
export const OFF_APP_USER_ID = "blackwater-macros-app";
/** Public on purpose, like other open-source apps' (it only says «sent by Blackwater Macros»). */
export const OFF_APP_PASSWORD = "QQz3yfGiR9I8bM";
export const OFF_APP_NAME = "Blackwater Macros";

/** Grams (or ml) in the serving the label describes, or null. */
export function servingAmount(product: Pick<NewProduct, "servingSize">): number | null {
  return parseServingGrams(product.servingSize);
}

/** The label's values per 100 g (or ml), one decimal, as the app stores foods. */
export function newProductPer100g(product: NewProduct): Per100g {
  const serving = servingAmount(product);
  const factor = product.basis === "serving" ? (serving ? 100 / serving : 0) : 1;
  const scale = (value: number | null) => round1(Math.max(value ?? 0, 0) * factor);
  return {
    calories: scale(product.calories),
    protein: scale(product.protein),
    carbs: scale(product.carbs),
    fat: scale(product.fat),
  };
}

/**
 * What still stops the form from saving, in screen order. Over 100 g of
 * nutrients or 900 kcal per 100 g is impossible: a misplaced digit or the
 * wrong basis. `keepPer`: a correction, sent in the basis Open Food Facts
 * stores, which needs the serving size to convert.
 */
export function newProductProblems(product: NewProduct, keepPer: OffDataPer | null = null): NewProductProblem[] {
  const problems: NewProductProblem[] = [];
  if (product.name.trim() === "") problems.push("name");
  if (
    (product.basis === "serving" && servingAmount(product) === null) ||
    (keepPer !== null && newProductInBasis(product, keepPer) === null)
  ) {
    problems.push("serving");
  }
  if (product.calories === null || !Number.isFinite(product.calories) || product.calories < 0) problems.push("calories");
  if (product.protein === null || !Number.isFinite(product.protein) || product.protein < 0) problems.push("protein");
  if (!problems.includes("serving")) {
    const per100 = newProductPer100g(product);
    if (per100.calories > 900 || per100.protein + per100.carbs + per100.fat > 100.5) problems.push("tooHigh");
  }
  return problems;
}

/** The product as the rest of the app uses it (portion, search, recents). */
export function newProductToFoodProduct(product: NewProduct): FoodProduct {
  return {
    code: product.code,
    name: product.name.trim(),
    brand: product.brand?.trim() || null,
    per100g: newProductPer100g(product),
    servingGrams: servingAmount(product),
    incomplete: product.carbs === null || product.fat === null,
  };
}

/**
 * The label's values in the basis Open Food Facts stores (`per`): a correction
 * keeps it, so the nutrients the app doesn't send (sugars, salt…) keep their
 * meaning. Null when converting needs a serving size that isn't there.
 */
export function newProductInBasis(product: NewProduct, per: OffDataPer): NewProduct | null {
  const isServing = product.basis === "serving";
  if (isServing === (per === "serving")) return product;
  const serving = servingAmount(product);
  if (serving === null || serving <= 0) return null;
  const factor = isServing ? 100 / serving : serving / 100;
  const scale = (value: number | null) => (value === null ? null : round2(value * factor));
  return {
    ...product,
    basis: per === "serving" ? "serving" : "100g",
    calories: scale(product.calories),
    protein: scale(product.protein),
    carbs: scale(product.carbs),
    fat: scale(product.fat),
  };
}

/** The label's four values; null = not given. */
export type LabelValues = Pick<NewProduct, "calories" | "protein" | "carbs" | "fat">;

/**
 * What Open Food Facts has, in the basis and serving the form shows now (to
 * write «Open Food Facts: 64» next to a changed value); null when a serving
 * size is missing to convert.
 */
export function correctionBaseline(correction: OffCorrection, form: NewProduct): LabelValues | null {
  const { base } = correction;
  const baseServing = servingAmount(base);
  const formServing = servingAmount(form);
  const to100 = base.basis === "serving" ? (baseServing ? 100 / baseServing : null) : 1;
  const from100 = form.basis === "serving" ? (formServing ? formServing / 100 : null) : 1;
  if (to100 === null || from100 === null) return null;
  const scale = (value: number | null) => (value === null ? null : round1(value * to100 * from100));
  return {
    calories: scale(base.calories),
    protein: scale(base.protein),
    carbs: scale(base.carbs),
    fat: scale(base.fat),
  };
}

const NUTRIENTS = ["calories", "protein", "carbs", "fat"] as const;

/** The values the form changes from what Open Food Facts has (an empty field changes nothing). */
export function correctionChanges(correction: OffCorrection, form: NewProduct): (keyof LabelValues)[] {
  const baseline = correctionBaseline(correction, form);
  return NUTRIENTS.filter((key) => {
    const value = form[key];
    if (value === null) return false;
    const before = baseline?.[key] ?? null;
    if (baseline === null || before === null) return true;
    return Math.abs(value - before) > (key === "calories" ? 0.5 : 0.05);
  });
}

/**
 * Who sends it and from which app install (`app_uuid`: a random id, so
 * moderators can block one install). `lang`: the language of the package (of a
 * new product) or the product's main language on Open Food Facts, never simply
 * the app's: names and label photos are kept per language there.
 */
export interface OffWriteContext {
  lang: string;
  userId: string;
  password: string;
  appVersion: string;
  appUuid: string;
  /** Open Food Facts already had the product: keep its name and brand. */
  existing: boolean;
  /** A correction: send the values in the basis Open Food Facts stores. */
  keepPer: OffDataPer | null;
}

function credentials(context: OffWriteContext): [string, string][] {
  return [
    ["user_id", context.userId],
    ["password", context.password],
    ["app_name", OFF_APP_NAME],
    ["app_version", context.appVersion],
    ["app_uuid", context.appUuid],
  ];
}

/**
 * The form fields of `POST /cgi/product_jqm2.pl`: the label as printed (Open
 * Food Facts converts a serving to 100 g itself), or for a correction in the
 * basis it stores. Brands are added, never replaced; a product Open Food Facts
 * already had keeps its name.
 */
export function offProductFields(label: NewProduct, context: OffWriteContext): [string, string][] {
  const product = context.keepPer ? (newProductInBasis(label, context.keepPer) ?? label) : label;
  const fields: [string, string][] = [["code", product.code], ...credentials(context), ["lc", context.lang]];
  if (!context.existing) {
    // A new product's main language is the package's (an existing one keeps its own).
    fields.push(["lang", context.lang], [`product_name_${context.lang}`, product.name.trim()]);
  }
  const brand = product.brand?.trim();
  if (brand && !context.existing) fields.push(["add_brands", brand]);
  fields.push(["nutrition_data_per", product.basis]);
  const serving = product.servingSize?.trim();
  if (serving) fields.push(["serving_size", serving]);
  const nutrients: [string, number | null, string][] = [
    ["energy-kcal", product.calories, "kcal"],
    ["proteins", product.protein, "g"],
    ["carbohydrates", product.carbs, "g"],
    ["fat", product.fat, "g"],
  ];
  for (const [id, value, unit] of nutrients) {
    if (value === null) continue;
    fields.push([`nutriment_${id}`, String(value)], [`nutriment_${id}_unit`, unit]);
  }
  fields.push(["comment", `${OFF_APP_NAME} ${context.appVersion}`]);
  return fields;
}

/** The photo's field: the nutrition label in the package's language («nutrition_es»). */
export function offLabelImageField(lang: string): string {
  return `nutrition_${lang}`;
}

/** The text fields of `POST /cgi/product_image_upload.pl`; the JPEG goes as `imgupload_<field>`. */
export function offImageFields(code: string, context: OffWriteContext): [string, string][] {
  return [["code", code], ...credentials(context), ["imagefield", offLabelImageField(context.lang)]];
}

/** What to do after an answer: done, try later, the account was refused, or give up. */
export type OffWriteOutcome = "ok" | "retry" | "account" | "rejected";

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function toText(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}

function byHttpStatus(status: number): OffWriteOutcome | null {
  if (status === 401 || status === 403) return "account";
  if (status === 408 || status === 429 || status >= 500) return "retry";
  if (status < 200 || status >= 300) return "rejected";
  return null;
}

/** Answer of `product_jqm2.pl`: `{"status":1,"status_verbose":"fields saved"}`. */
export function offFieldsOutcome(httpStatus: number, json: unknown): OffWriteOutcome {
  const byStatus = byHttpStatus(httpStatus);
  if (byStatus) return byStatus;
  const response = asRecord(json);
  if (!response) return "retry";
  if (response.status === 1 || response.status === "1") return "ok";
  const verbose = toText(response.status_verbose) ?? "";
  return /credentials|password|user/i.test(verbose) ? "account" : "rejected";
}

/** Answer of `product_image_upload.pl`; a photo it already has counts as sent. */
export function offImageOutcome(httpStatus: number, json: unknown): OffWriteOutcome {
  const byStatus = byHttpStatus(httpStatus);
  if (byStatus) return byStatus;
  const response = asRecord(json);
  if (!response) return "retry";
  if (response.status === "status ok") return "ok";
  const error = `${toText(response.error) ?? ""} ${toText(response.debug) ?? ""}`;
  if (/already/i.test(error)) return "ok";
  return /credentials|password|user/i.test(error) ? "account" : "rejected";
}

/** What Open Food Facts knows of a product: name (in `lang` if it has it), brand and its main language. */
export interface OffKnown {
  name: string | null;
  brand: string | null;
  lang: string | null;
}

/** Name, brand and main language of a product Open Food Facts knows without nutrition, to start the form from. */
export function offKnownProduct(json: unknown, lang: FoodLang): OffKnown {
  const product = asRecord(asRecord(json)?.product);
  if (!product) return { name: null, brand: null, lang: null };
  const brands = Array.isArray(product.brands) ? toText(product.brands[0]) : toText(product.brands);
  return {
    name: toText(product[`product_name_${lang}`]) ?? toText(product.product_name) ?? toText(product.generic_name),
    brand: brands ? brands.split(",")[0].trim() : null,
    lang: packageLang(product.lang) ?? packageLang(product.lc),
  };
}

function nutrient(nutriments: Record<string, unknown>, id: string, suffix: string): number | null {
  const value = nutriments[`${id}${suffix}`];
  const number = typeof value === "number" ? value : typeof value === "string" ? Number(value) : NaN;
  return Number.isFinite(number) && number >= 0 ? number : null;
}

/**
 * A product Open Food Facts has, as «Corregir valores» starts from it: its name
 * and brand, and its values in the basis it stores them. Null when it has no
 * such product.
 */
export function parseOffCorrection(json: unknown, code: string, lang: FoodLang): OffCorrection | null {
  const product = asRecord(asRecord(json)?.product);
  if (!product) return null;
  const known = offKnownProduct(json, lang);
  const per: OffDataPer = product.nutrition_data_per === "serving" ? "serving" : "100g";
  const suffix = per === "serving" ? "_serving" : "_100g";
  const nutriments = asRecord(product.nutriments) ?? {};
  const kj = nutrient(nutriments, "energy-kj", suffix) ?? nutrient(nutriments, "energy", suffix);
  const calories = nutrient(nutriments, "energy-kcal", suffix) ?? (kj === null ? null : kj / 4.184);
  const round = (value: number | null) => (value === null ? null : round1(value));
  return {
    per,
    lang: known.lang,
    base: {
      code,
      name: known.name ?? "",
      brand: known.brand,
      basis: per === "serving" ? "serving" : "100g",
      servingSize: toText(product.serving_size),
      calories: round(calories),
      protein: round(nutrient(nutriments, "proteins", suffix)),
      carbs: round(nutrient(nutriments, "carbohydrates", suffix)),
      fat: round(nutrient(nutriments, "fat", suffix)),
    },
  };
}

/** The user's own products matching a search: every word starts a word of the name or brand. */
export function searchOwnProducts(products: FoodProduct[], query: string, limit = 5): FoodProduct[] {
  const words = normalizeText(query).split(/[^a-z0-9]+/).filter(Boolean);
  if (words.length === 0) return [];
  return products
    .filter((product) => {
      const haystack = normalizeText(`${product.name} ${product.brand ?? ""}`).split(/[^a-z0-9]+/);
      return words.every((word) => haystack.some((part) => part.startsWith(word)));
    })
    .slice(0, limit);
}

/** What the AI read from a photo of the label (null = not printed or unreadable). */
export interface LabelReading {
  name: string | null;
  /** The language the label is written in («es»). */
  language: string | null;
  brand: string | null;
  basis: LabelBasis;
  servingSize: string | null;
  calories: number | null;
  protein: number | null;
  carbs: number | null;
  fat: number | null;
}

export const LABEL_READING_SHAPE =
  '{"name": string | null, "brand": string | null, "language": string | null, "basis": "100g" | "100ml" | "serving", "servingSize": string | null, ' +
  '"calories": number | null, "protein": number | null, "carbs": number | null, "fat": number | null}';

/** System prompt for reading a nutrition label. `language` in English («Spanish»), only for names it must describe. */
export function buildLabelReadingSystemPrompt(language: string): string {
  return [
    "You read the nutrition label of a packaged food for Blackwater Macros, a calorie and macro tracker.",
    `Reply with only one JSON object with this shape: ${LABEL_READING_SHAPE}`,
    "Copy the numbers exactly as printed. Never estimate or invent a value: use null for anything not printed or not readable.",
    'Use the column per 100 g or per 100 ml when the label has one ("100g" or "100ml"); otherwise the column per serving ("serving") with servingSize as printed, for example "200 ml" or "30 g".',
    "calories is in kcal. If only kJ is printed, divide by 4.184.",
    "carbs is total carbohydrates (not only sugars), fat is total fat.",
    `name and brand as printed on the package when visible (do not translate them); if only a generic name shows, write it in ${language}.`,
    'language is the two-letter ISO 639-1 code of the language the label is written in ("es" for Spanish), or null if you can\'t tell.',
  ].join("\n");
}

export const LABEL_READING_USER_TEXT = "A photo of the nutrition label of a packaged food.";

function toNumber(value: unknown): number | null {
  if (typeof value === "number") return Number.isFinite(value) && value >= 0 ? value : null;
  if (typeof value === "string") {
    const match = /\d+(?:[.,]\d+)?/.exec(value);
    return match ? Number(match[0].replace(",", ".")) : null;
  }
  return null;
}

function toBasis(value: unknown, servingSize: string | null): LabelBasis {
  const text = normalizeText(typeof value === "string" ? value : "");
  if (/serv|porc|portion|rac/.test(text)) return "serving";
  if (/ml/.test(text)) return "100ml";
  if (text === "" && servingSize) return "serving";
  return "100g";
}

/** The model's answer → the form's values; null when it read neither kcal nor protein. */
export function parseLabelReading(text: string): LabelReading | null {
  const record = asRecord(extractJson(text));
  if (!record) return null;
  const servingSize = toText(record.servingSize) ?? toText(record.serving_size);
  const reading: LabelReading = {
    name: toText(record.name),
    language: packageLang(record.language ?? record.lang),
    brand: toText(record.brand),
    basis: toBasis(record.basis, servingSize),
    servingSize,
    calories: toNumber(record.calories ?? record.kcal),
    protein: toNumber(record.protein ?? record.proteins),
    carbs: toNumber(record.carbs ?? record.carbohydrates),
    fat: toNumber(record.fat),
  };
  return reading.calories === null && reading.protein === null ? null : reading;
}
