"use client";

import { useSyncExternalStore } from "react";
import type { FoodLang } from "@/lib/core/foods";
import {
  OFF_APP_PASSWORD,
  OFF_APP_USER_ID,
  newProductToFoodProduct,
  offFieldsOutcome,
  offImageFields,
  offImageOutcome,
  offLabelImageField,
  offProductFields,
  type NewProduct,
  type OffCorrection,
  type OffWriteContext,
  type OffWriteOutcome,
} from "@/lib/core/new-product";
import type { AiImage } from "@/lib/core/ai-providers";
import { localJsonStore } from "@/lib/local-json-store";
import type { FoodChoice } from "@/lib/foods/foods-client";

/**
 * Products the user added from a label («Añadir este producto»), kept in this
 * browser and sent from it straight to Open Food Facts (never through our
 * server). A record stays «pending» until Open Food Facts has both the values
 * and the label photo; the photo is dropped once it is there.
 */

/** Open Food Facts; `NEXT_PUBLIC_OFF_SERVER=https://world.openfoodfacts.net` tries everything on their test server. */
export const OFF_SERVER = process.env.NEXT_PUBLIC_OFF_SERVER || "https://world.openfoodfacts.org";

export type OwnProductState = "pending" | "sent" | "account" | "rejected";

export interface OwnProduct {
  product: NewProduct;
  lang: FoodLang;
  /** Open Food Facts already had it: only the values (and photo) are sent, never the name or brand. */
  existing: boolean;
  /** «Corregir valores»: what Open Food Facts had, and the basis to send in (missing in older records). */
  correction?: OffCorrection | null;
  /** The label photo (JPEG, base64) until Open Food Facts has it. */
  photo: string | null;
  /** The values (new or edited) still have to reach Open Food Facts. */
  fieldsPending: boolean;
  state: OwnProductState;
  /** Open Food Facts' own words when it refused. */
  detail: string;
  updatedAt: number;
}

const MAX_PRODUCTS = 50;

function parseProducts(raw: string | null): OwnProduct[] {
  if (!raw) return [];
  try {
    const list = JSON.parse(raw) as OwnProduct[];
    return Array.isArray(list) ? list.filter((item) => typeof item?.product?.code === "string") : [];
  } catch {
    return [];
  }
}

const products = localJsonStore("bw:own-products", parseProducts);

export function ownProducts(): OwnProduct[] {
  return products.get();
}

export function useOwnProducts(): OwnProduct[] {
  return useSyncExternalStore(products.subscribe, products.get, () => EMPTY);
}
const EMPTY: OwnProduct[] = [];

export function ownProduct(code: string | null | undefined): OwnProduct | null {
  return code ? (products.get().find((item) => item.product.code === code) ?? null) : null;
}

/** Newest first; past the limit the oldest products already sent are forgotten. */
function store(next: OwnProduct[]): void {
  const list = [...next];
  for (let i = list.length - 1; i >= 0 && list.length > MAX_PRODUCTS; i--) {
    if (list[i].state === "sent") list.splice(i, 1);
  }
  products.set(list);
}

function update(code: string, change: (item: OwnProduct) => OwnProduct): void {
  store(products.get().map((item) => (item.product.code === code ? change(item) : item)));
}

/** The choice for the portion screen and the search («Tus productos»). */
export function ownChoice(item: OwnProduct): FoodChoice {
  const food = newProductToFoodProduct(item.product);
  return {
    key: `off:${food.code}`,
    code: food.code,
    name: food.name,
    brand: food.brand,
    per100g: food.per100g,
    servingGrams: food.servingGrams,
    source: "off",
    incomplete: food.incomplete,
  };
}

/**
 * Saves (or replaces, after «Editar») a product and starts sending it.
 * `photo`: a new label photo, if any; `correction`: values Open Food Facts had
 * that the user corrected.
 */
export function saveOwnProduct(input: {
  product: NewProduct;
  lang: FoodLang;
  existing: boolean;
  correction?: OffCorrection | null;
  photo: AiImage | null;
}): OwnProduct {
  const previous = ownProduct(input.product.code);
  const correction = previous?.correction ?? input.correction ?? null;
  const item: OwnProduct = {
    product: input.product,
    lang: input.lang,
    existing: previous?.existing ?? (input.existing || correction !== null),
    correction,
    photo: input.photo?.data ?? previous?.photo ?? null,
    fieldsPending: true,
    state: "pending",
    detail: "",
    updatedAt: Date.now(),
  };
  store([item, ...products.get().filter((other) => other.product.code !== item.product.code)]);
  void sendOwnProducts();
  return item;
}

// --- Open Food Facts account ---------------------------------------------------

export interface OffAccount {
  userId: string;
  password: string;
}

function parseAccount(raw: string | null): OffAccount | null {
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as Partial<OffAccount> | null;
    return value && typeof value.userId === "string" && typeof value.password === "string" && value.userId !== ""
      ? { userId: value.userId, password: value.password }
      : null;
  } catch {
    return null;
  }
}

/** The user's own Open Food Facts account (Ajustes → Open Food Facts), in this browser only. */
const account = localJsonStore("bw:off-account", parseAccount);

export function useOffAccount(): OffAccount | null {
  return useSyncExternalStore(account.subscribe, account.get, () => null);
}

/** Saves (or with null, forgets) the user's account; products it refused are tried again. */
export function setOffAccount(next: OffAccount | null): void {
  account.set(next);
  store(products.get().map((item) => (item.state === "account" ? { ...item, state: "pending" } : item)));
  void sendOwnProducts();
}

export type AccountCheck = "ok" | "wrong" | "offline";

/** Asks Open Food Facts whether the username and password are right (`/cgi/auth.pl`). */
export async function checkOffAccount(userId: string, password: string): Promise<AccountCheck> {
  try {
    const response = await fetch(`${OFF_SERVER}/cgi/auth.pl`, {
      method: "POST",
      body: new URLSearchParams({ user_id: userId, password }),
      signal: AbortSignal.timeout(15000),
    });
    if (response.ok) return "ok";
    return response.status === 401 || response.status === 403 ? "wrong" : "offline";
  } catch {
    return "offline";
  }
}

/** A random id for this browser, sent as `app_uuid` (no personal data: it only tells installs apart). */
function installId(): string {
  const key = "bw:off-install-id";
  try {
    const existing = localStorage.getItem(key);
    if (existing) return existing;
    const created = crypto.randomUUID();
    localStorage.setItem(key, created);
    return created;
  } catch {
    return "unknown";
  }
}

// --- Sending ---------------------------------------------------------------------

function base64ToBlob(data: string): Blob {
  const binary = atob(data);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return new Blob([bytes], { type: "image/jpeg" });
}

async function post(path: string, body: BodyInit, outcome: (status: number, json: unknown) => OffWriteOutcome) {
  const response = await fetch(`${OFF_SERVER}${path}`, { method: "POST", body, signal: AbortSignal.timeout(30000) });
  const json: unknown = await response.json().catch(() => null);
  const detail =
    json && typeof json === "object"
      ? String((json as Record<string, unknown>).status_verbose ?? (json as Record<string, unknown>).error ?? "")
      : "";
  return { outcome: outcome(response.status, json), detail };
}

let running: Promise<void> | null = null;

/**
 * Sends every pending product (values, then the photo), oldest first. Stops at
 * the first failure that may work later (offline, Open Food Facts busy): the
 * next try is when the app opens, the browser is back online, or a product is saved.
 */
export function sendOwnProducts(): Promise<void> {
  running ??= sendAll().finally(() => {
    running = null;
  });
  return running;
}

async function sendAll(): Promise<void> {
  const own = account.get();
  const userId = own?.userId ?? OFF_APP_USER_ID;
  const password = own?.password ?? OFF_APP_PASSWORD;
  // Without the app account's password (a build before RELEASING.md's step) everything waits.
  if (password === "") return;
  const appUuid = installId();
  const pending = products
    .get()
    .filter((item) => item.state === "pending")
    .reverse();
  for (const queued of pending) {
    const item = ownProduct(queued.product.code);
    if (!item || item.state !== "pending") continue;
    const context: OffWriteContext = {
      lang: item.lang,
      userId,
      password,
      appVersion: "web",
      appUuid,
      existing: item.existing,
      keepPer: item.correction?.per ?? null,
    };
    try {
      if (item.fieldsPending) {
        const sent = { ...item.product };
        const { outcome, detail } = await post(
          "/cgi/product_jqm2.pl",
          new URLSearchParams(offProductFields(sent, context)),
          offFieldsOutcome,
        );
        if (outcome === "retry") return;
        if (outcome !== "ok") {
          update(item.product.code, (current) => ({ ...current, state: outcome, detail }));
          continue;
        }
        // An edit made while this was on its way is sent next time.
        update(item.product.code, (current) =>
          JSON.stringify(current.product) === JSON.stringify(sent) ? { ...current, fieldsPending: false } : current,
        );
      }
      const photo = ownProduct(item.product.code)?.photo;
      if (photo) {
        const form = new FormData();
        for (const [key, value] of offImageFields(item.product.code, context)) form.append(key, value);
        form.append(`imgupload_${offLabelImageField(item.lang)}`, base64ToBlob(photo), "label.jpg");
        const { outcome } = await post("/cgi/product_image_upload.pl", form, offImageOutcome);
        if (outcome === "retry") return;
        if (outcome === "account") {
          update(item.product.code, (current) => ({ ...current, state: "account" }));
          continue;
        }
        // Sent, or refused for good (too small…): either way the photo leaves this browser.
        update(item.product.code, (current) => (current.photo === photo ? { ...current, photo: null } : current));
      }
      update(item.product.code, (current) =>
        current.state === "pending" && !current.fieldsPending && !current.photo ? { ...current, state: "sent" } : current,
      );
    } catch {
      // Offline or Open Food Facts unreachable: try again later.
      return;
    }
  }
}
