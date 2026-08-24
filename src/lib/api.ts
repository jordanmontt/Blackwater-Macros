import type {
  IngredientInput,
  MealDTO,
  MealTemplateDTO,
  StatsRange,
  StatsSummary,
  WeightDTO,
} from "./types";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

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
    window.location.href = "/login";
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
}

export interface WeightPayload {
  measuredAt: string;
  weightKg: number;
  note?: string | null;
}

export interface TemplatePayload {
  name: string;
  title: string;
  notes?: string | null;
  ingredients: IngredientInput[];
}

export const api = {
  login: (username: string, password: string) =>
    request<{ ok: true }>("/api/auth/login", jsonBody({ username, password })),

  logout: () => request<{ ok: true }>("/api/auth/logout", { method: "POST" }),

  session: () =>
    request<{ username: string }>("/api/auth/session", { method: "GET" }).catch(() => ({
      username: "",
    })),

  listMeals: async (from: string, to: string) => {
    const query = new URLSearchParams({ from, to });
    const data = await request<{ meals: MealDTO[] }>(`/api/meals?${query}`);
    return data.meals;
  },

  createMeal: async (payload: MealPayload) => {
    const data = await request<{ meal: MealDTO }>("/api/meals", jsonBody(payload));
    return data.meal;
  },

  updateMeal: async (id: string, payload: MealPayload) => {
    const data = await request<{ meal: MealDTO }>(`/api/meals/${id}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    return data.meal;
  },

  deleteMeal: (id: string) => request<{ ok: true }>(`/api/meals/${id}`, { method: "DELETE" }),

  listTemplates: async () => {
    const data = await request<{ templates: MealTemplateDTO[] }>("/api/templates");
    return data.templates;
  },

  createTemplate: async (payload: TemplatePayload) => {
    const data = await request<{ template: MealTemplateDTO }>("/api/templates", jsonBody(payload));
    return data.template;
  },

  deleteTemplate: (id: string) =>
    request<{ ok: true }>(`/api/templates/${id}`, { method: "DELETE" }),

  listWeights: async () => {
    const data = await request<{ weights: WeightDTO[] }>("/api/weights");
    return data.weights;
  },

  createWeight: async (payload: WeightPayload) => {
    const data = await request<{ weight: WeightDTO }>("/api/weights", jsonBody(payload));
    return data.weight;
  },

  updateWeight: async (id: string, payload: WeightPayload) => {
    const data = await request<{ weight: WeightDTO }>(`/api/weights/${id}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    return data.weight;
  },

  deleteWeight: (id: string) => request<{ ok: true }>(`/api/weights/${id}`, { method: "DELETE" }),

  stats: async (range: StatsRange, today: string) => {
    const query = new URLSearchParams({ range, today });
    return request<StatsSummary>(`/api/stats?${query}`);
  },
};
