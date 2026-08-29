import type {
  CalorieProfile,
  IngredientInput,
  MealDTO,
  MealTemplateDTO,
  StatsRange,
  StatsSummary,
  WeightDTO,
} from "./types";
import { demoApi } from "./demo-api";
import { isDemoMode } from "./demo-store";

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
    return request<{ ok: true }>("/api/auth/logout", { method: "POST" });
  },

  session: () => {
    if (isDemoMode()) return demoApi.session();
    return request<{
      username: string;
      calorieProfile: CalorieProfile;
    }>("/api/auth/session", {
      method: "GET",
    }).catch(() => ({
      username: "",
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
    return data.meal;
  },

  updateMeal: async (id: string, payload: MealPayload) => {
    if (isDemoMode()) return demoApi.updateMeal(id, payload);
    const data = await request<{ meal: MealDTO }>(`/api/meals/${id}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    return data.meal;
  },

  deleteMeal: (id: string) => {
    if (isDemoMode()) return demoApi.deleteMeal(id);
    return request<{ ok: true }>(`/api/meals/${id}`, { method: "DELETE" });
  },

  reorderMeals: (orderedIds: string[]) => {
    if (isDemoMode()) return demoApi.reorderMeals(orderedIds);
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
    return data.template;
  },

  updateTemplate: async (id: string, payload: TemplatePayload) => {
    if (isDemoMode()) return demoApi.updateTemplate(id, payload);
    const data = await request<{ template: MealTemplateDTO }>(`/api/templates/${id}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    return data.template;
  },

  deleteTemplate: (id: string) => {
    if (isDemoMode()) return demoApi.deleteTemplate(id);
    return request<{ ok: true }>(`/api/templates/${id}`, { method: "DELETE" });
  },

  listWeights: async () => {
    if (isDemoMode()) return demoApi.listWeights();
    const data = await request<{ weights: WeightDTO[] }>("/api/weights");
    return data.weights;
  },

  createWeight: async (payload: WeightPayload) => {
    if (isDemoMode()) return demoApi.createWeight(payload);
    const data = await request<{ weight: WeightDTO }>("/api/weights", jsonBody(payload));
    return data.weight;
  },

  updateWeight: async (id: string, payload: WeightPayload) => {
    if (isDemoMode()) return demoApi.updateWeight(id, payload);
    const data = await request<{ weight: WeightDTO }>(`/api/weights/${id}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    return data.weight;
  },

  deleteWeight: (id: string) => {
    if (isDemoMode()) return demoApi.deleteWeight(id);
    return request<{ ok: true }>(`/api/weights/${id}`, { method: "DELETE" });
  },

  stats: async (range: StatsRange, today: string) => {
    if (isDemoMode()) return demoApi.stats(range, today);
    const query = new URLSearchParams({ range, today });
    return request<StatsSummary>(`/api/stats?${query}`);
  },

  updateSettings: async (payload: CalorieProfile) => {
    if (isDemoMode()) return demoApi.updateSettings(payload);
    return request<{
      calorieProfile: CalorieProfile;
    }>("/api/settings", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },
};
