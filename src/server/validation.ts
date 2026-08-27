import { z } from "zod";
import { isValidDateKey } from "@/lib/dates";

export const ingredientInputSchema = z.object({
  name: z.string().trim().min(1, "El nombre del ingrediente es obligatorio").max(200),
  quantity: z.string().trim().min(1).max(200).optional(),
  calories: z.number().min(0).max(100_000).optional(),
  protein: z.number().min(0).max(10_000).optional(),
  carbs: z.number().min(0).max(10_000).optional(),
  fat: z.number().min(0).max(10_000).optional(),
});

export const mealInputSchema = z.object({
  logDate: z
    .string()
    .refine(isValidDateKey, "La fecha debe tener formato YYYY-MM-DD"),
  title: z.string().trim().min(1, "El título es obligatorio").max(120),
  notes: z.string().trim().max(2_000).nullish(),
  entryMode: z.enum(["per_ingredient", "total_only"]),
  ingredients: z.array(ingredientInputSchema).max(100),
  totalCalories: z.number().min(0).max(100_000).nullish(),
  totalProtein: z.number().min(0).max(10_000).nullish(),
  totalCarbs: z.number().min(0).max(10_000).nullish(),
  totalFat: z.number().min(0).max(10_000).nullish(),
});

export type MealInput = z.infer<typeof mealInputSchema>;

export const templateInputSchema = z.object({
  name: z.string().trim().min(1, "El nombre de la plantilla es obligatorio").max(120),
  title: z.string().trim().min(1, "El título es obligatorio").max(120),
  notes: z.string().trim().max(2_000).nullish(),
  ingredients: z.array(ingredientInputSchema).max(100),
});

export type TemplateInput = z.infer<typeof templateInputSchema>;

export const weightInputSchema = z.object({
  measuredAt: z.string().refine((value) => !Number.isNaN(new Date(value).getTime()), {
    message: "Fecha y hora no válidas",
  }),
  weightKg: z.number().min(20, "Peso fuera de rango").max(400, "Peso fuera de rango"),
  bodyFatPct: z.number().min(3, "Grasa corporal fuera de rango").max(60, "Grasa corporal fuera de rango").nullish(),
  note: z.string().trim().max(500).nullish(),
});

export type WeightInput = z.infer<typeof weightInputSchema>;

export const calorieProfileInputSchema = z.object({
  gender: z.enum(["male", "female"]).nullable(),
  birthYear: z
    .number()
    .min(1920, "El año de nacimiento debe ser entre 1920 y 2010")
    .max(2010, "El año de nacimiento debe ser entre 1920 y 2010")
    .nullable(),
  heightCm: z
    .number()
    .min(100, "La altura debe estar entre 100 y 250 cm")
    .max(250, "La altura debe estar entre 100 y 250 cm")
    .nullable(),
  gymDaysPerWeek: z
    .number()
    .min(0, "Los días de gimnasio deben ser entre 0 y 7")
    .max(7, "Los días de gimnasio deben ser entre 0 y 7")
    .nullable(),
  gymSessionMinutes: z
    .number()
    .min(0, "La duración debe ser entre 0 y 300 minutos")
    .max(300, "La duración debe ser entre 0 y 300 minutos")
    .nullable(),
  walkingMinutesPerDay: z
    .number()
    .min(0, "El tiempo de caminata debe ser entre 0 y 480 minutos")
    .max(480, "El tiempo de caminata debe ser entre 0 y 480 minutos")
    .nullable(),
  calorieGoal: z.enum(["cut", "maintain", "surplus"]).nullable(),
});

export type CalorieProfileInput = z.infer<typeof calorieProfileInputSchema>;

export const loginInputSchema = z.object({
  username: z.string().trim().min(1).max(80),
  password: z.string().min(1).max(200),
});
