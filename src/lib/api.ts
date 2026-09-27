import type {
  AdminUserDTO,
  CalorieProfile,
  IngredientInput,
  MealDTO,
  MealTemplateDTO,
  StatsRange,
  StatsSummary,
  WeightDTO,
} from "./core/types";
import type { FoodLang, FoodProduct } from "./core/foods";
import { demoApi } from "./demo-api";
import { isDemoMode } from "./demo-store";
import { clearCache, invalidate } from "./client-cache";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

/** Evento disparado cuando una respuesta 401 expira la sesión del cliente. */
export const AUTH_EXPIRED_EVENT = "app:unauthorized";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: { "Content-Type": "application/json", ...init?.headers },
    });
  } catch {
    throw new ApiError(0, "Error de red");
  }

  if (response.status === 401 && !path.startsWith("/api/auth/login")) {
    const REDIRECT_KEY = "_authRedirect";
    const lastRedirect = Number(sessionStorage.getItem(REDIRECT_KEY) ?? 0);
    if (Date.now() - lastRedirect > 3000) {
      sessionStorage.setItem(REDIRECT_KEY, String(Date.now()));
      window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT));
    }
    throw new ApiError(401, "No autenticado");
  }

  const body = response.headers.get("content-type")?.includes("application/json")
    ? await response.json().catch(() => null)
    : null;

  if (!response.ok) {
    const message =
      body && typeof body.error === "string" ? body.error : `Error ${response.status}`;
    throw new ApiError(response.status, message);
  }
  return body as T;
}

function jsonBody(payload: unknown): RequestInit {
  return { method: "POST", body: JSON.stringify(payload) };
}

export interface MealPayload {
  logDate: string;
  title: string;
  notes?: string | null;
  entryMode: "per_ingredient" | "total_only";
  ingredients: IngredientInput[];
  totalCalories?: number | null;
  totalProtein?: number | null;
  totalCarbs?: number | null;
  totalFat?: number | null;
}

export interface WeightPayload {
  measuredAt: string;
  weightKg: number;
  bodyFatPct?: number | null;
  note?: string | null;
}

export interface TemplatePayload {
  name: string;
  title: string;
  notes?: string | null;
  entryMode: "per_ingredient" | "total_only";
  ingredients: IngredientInput[];
  totalCalories?: number | null;
  totalProtein?: number | null;
  totalCarbs?: number | null;
  totalFat?: number | null;
}

export const api = {
  login: (username: string, password: string) =>
    request<{ ok: true }>("/api/auth/login", jsonBody({ username, password })),

  logout: () => {
    if (isDemoMode()) {
      // Demo sessions have no server session to destroy.
      return Promise.resolve({ ok: true as const });
    }
    return request<{ ok: true }>("/api/auth/logout", { method: "POST" }).then((result) => {
      clearCache();
      return result;
    });
  },

  session: () => {
    if (isDemoMode()) return demoApi.session();
    return request<{
      username: string;
      isAdmin: boolean;
      calorieProfile: CalorieProfile;
    }>("/api/auth/session", {
      method: "GET",
    }).catch(() => ({
      username: "",
      isAdmin: false,
      calorieProfile: {
        gender: null,
        birthYear: null,
        heightCm: null,
        gymDaysPerWeek: null,
        gymSessionMinutes: null,
        walkingMinutesPerDay: null,
        calorieGoal: null,
      } as CalorieProfile,
    }));
  },

  listMeals: async (from: string, to: string) => {
    if (isDemoMode()) return demoApi.listMeals(from, to);
    const query = new URLSearchParams({ from, to });
    const data = await request<{ meals: MealDTO[] }>(`/api/meals?${query}`);
    return data.meals;
  },

  createMeal: async (payload: MealPayload) => {
    if (isDemoMode()) return demoApi.createMeal(payload);
    const data = await request<{ meal: MealDTO }>("/api/meals", jsonBody(payload));
    invalidate("meals:");
    invalidate("stats:");
    return data.meal;
  },

  updateMeal: async (id: string, payload: MealPayload) => {
    if (isDemoMode()) return demoApi.updateMeal(id, payload);
    const data = await request<{ meal: MealDTO }>(`/api/meals/${id}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    invalidate("meals:");
    invalidate("stats:");
    return data.meal;
  },

  deleteMeal: (id: string) => {
    if (isDemoMode()) return demoApi.deleteMeal(id);
    return request<{ ok: true }>(`/api/meals/${id}`, { method: "DELETE" }).then((result) => {
      invalidate("meals:");
      invalidate("stats:");
      return result;
    });
  },

  reorderMeals: (orderedIds: string[]) => {
    if (isDemoMode()) return demoApi.reorderMeals(orderedIds);
    // No se invalida "meals:" aquí: la página ya escribió el orden optimista
    // en la caché y reordenar no cambia los datos, solo su orden. Invalidar
    // provocaría un parpadeo de carga tras cada arrastre.
    return request<{ ok: true }>("/api/meals/reorder", {
      method: "PATCH",
      body: JSON.stringify({ orderedIds }),
    });
  },

  listTemplates: async () => {
    if (isDemoMode()) return demoApi.listTemplates();
    const data = await request<{ templates: MealTemplateDTO[] }>("/api/templates");
    return data.templates;
  },

  createTemplate: async (payload: TemplatePayload) => {
    if (isDemoMode()) return demoApi.createTemplate(payload);
    const data = await request<{ template: MealTemplateDTO }>("/api/templates", jsonBody(payload));
    invalidate("templates");
    return data.template;
  },

  updateTemplate: async (id: string, payload: TemplatePayload) => {
    if (isDemoMode()) return demoApi.updateTemplate(id, payload);
    const data = await request<{ template: MealTemplateDTO }>(`/api/templates/${id}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    invalidate("templates");
    return data.template;
  },

  deleteTemplate: (id: string) => {
    if (isDemoMode()) return demoApi.deleteTemplate(id);
    return request<{ ok: true }>(`/api/templates/${id}`, { method: "DELETE" }).then((result) => {
      invalidate("templates");
      return result;
    });
  },

  listWeights: async () => {
    if (isDemoMode()) return demoApi.listWeights();
    const data = await request<{ weights: WeightDTO[] }>("/api/weights");
    return data.weights;
  },

  createWeight: async (payload: WeightPayload) => {
    if (isDemoMode()) return demoApi.createWeight(payload);
    const data = await request<{ weight: WeightDTO }>("/api/weights", jsonBody(payload));
    invalidate("weights");
    invalidate("stats:");
    return data.weight;
  },

  updateWeight: async (id: string, payload: WeightPayload) => {
    if (isDemoMode()) return demoApi.updateWeight(id, payload);
    const data = await request<{ weight: WeightDTO }>(`/api/weights/${id}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    invalidate("weights");
    invalidate("stats:");
    return data.weight;
  },

  deleteWeight: (id: string) => {
    if (isDemoMode()) return demoApi.deleteWeight(id);
    return request<{ ok: true }>(`/api/weights/${id}`, { method: "DELETE" }).then((result) => {
      invalidate("weights");
      invalidate("stats:");
      return result;
    });
  },

  /**
   * Open Food Facts text search through our pass-through route (the search
   * service does not allow browser requests). Not available in demo mode.
   */
  searchFoods: async (query: string, lang: FoodLang = "es"): Promise<FoodProduct[]> => {
    if (isDemoMode()) return [];
    const params = new URLSearchParams({ q: query, lang });
    const data = await request<{ products: FoodProduct[] }>(`/api/foods/search?${params}`);
    return data.products;
  },

  stats: async (range: StatsRange, today: string) => {
    if (isDemoMode()) return demoApi.stats(range, today);
    const query = new URLSearchParams({ range, today });
    return request<StatsSummary>(`/api/stats?${query}`);
  },

  updateSettings: async (payload: CalorieProfile) => {
    if (isDemoMode()) return demoApi.updateSettings(payload);
    const data = await request<{
      calorieProfile: CalorieProfile;
    }>("/api/settings", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
    invalidate("session");
    return data;
  },

  adminUsers: async () => {
    const data = await request<{ users: AdminUserDTO[] }>("/api/admin/users");
    return data.users;
  },

  adminCreateUser: async (payload: { username: string; password: string }) => {
    await request<{ ok: true }>("/api/admin/users", jsonBody(payload));
  },

  adminUpdateUser: async (
    id: string,
    payload: { username?: string; password?: string; isAdmin?: boolean },
  ) => {
    await request<{ user: AdminUserDTO }>(`/api/admin/users/${id}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
  },

  adminDeleteUser: async (id: string) => {
    await request<{ ok: true }>(`/api/admin/users/${id}`, { method: "DELETE" });
  },
};
