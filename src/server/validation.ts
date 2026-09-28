import { z } from "zod";
import { isValidDateKey } from "@/lib/core/dates";

// Messages are `ServerErrorCode`s (lib/server-errors.ts): each client translates them.

export const ingredientInputSchema = z.object({
  name: z.string().trim().min(1, "ingredient_name_required").max(200),
  quantity: z.string().trim().min(1).max(200).optional(),
  calories: z.number().min(0).max(100_000).optional(),
  protein: z.number().min(0).max(10_000).optional(),
  carbs: z.number().min(0).max(10_000).optional(),
  fat: z.number().min(0).max(10_000).optional(),
});

export const mealInputSchema = z.object({
  logDate: z
    .string()
    .refine(isValidDateKey, "invalid_date"),
  title: z.string().trim().min(1, "title_required").max(120),
  notes: z.string().trim().max(2_000).nullish(),
  entryMode: z.enum(["per_ingredient", "total_only"]),
  ingredients: z.array(ingredientInputSchema).max(100),
  totalCalories: z.number().min(0).max(100_000).nullish(),
  totalProtein: z.number().min(0).max(10_000).nullish(),
  totalCarbs: z.number().min(0).max(10_000).nullish(),
  totalFat: z.number().min(0).max(10_000).nullish(),
});

export type MealInput = z.infer<typeof mealInputSchema>;

/** `PUT /api/meals/:id` body: a meal plus its optional position within the day. */
export const mealUpsertSchema = mealInputSchema.extend({
  sortOrder: z.number().int().min(0).optional(),
});

/** Client-generated record ids (offline sync) must be UUIDs — the DB column type. */
export const recordIdSchema = z.string().uuid("invalid_id");

export const templateInputSchema = z.object({
  name: z.string().trim().min(1, "template_name_required").max(120),
  title: z.string().trim().min(1, "title_required").max(120),
  notes: z.string().trim().max(2_000).nullish(),
  entryMode: z.enum(["per_ingredient", "total_only"]),
  ingredients: z.array(ingredientInputSchema).max(100),
  totalCalories: z.number().min(0).max(100_000).nullish(),
  totalProtein: z.number().min(0).max(10_000).nullish(),
  totalCarbs: z.number().min(0).max(10_000).nullish(),
  totalFat: z.number().min(0).max(10_000).nullish(),
});

export type TemplateInput = z.infer<typeof templateInputSchema>;

export const weightInputSchema = z.object({
  measuredAt: z.string().refine((value) => !Number.isNaN(new Date(value).getTime()), {
    message: "invalid_datetime",
  }),
  weightKg: z.number().min(20, "weight_out_of_range").max(400, "weight_out_of_range"),
  bodyFatPct: z.number().min(3, "body_fat_out_of_range").max(60, "body_fat_out_of_range").nullish(),
  note: z.string().trim().max(500).nullish(),
});

export type WeightInput = z.infer<typeof weightInputSchema>;

export const calorieProfileInputSchema = z.object({
  gender: z.enum(["male", "female"]).nullable(),
  birthYear: z
    .number()
    .min(1920, "birth_year_out_of_range")
    .max(2010, "birth_year_out_of_range")
    .nullable(),
  heightCm: z
    .number()
    .min(100, "height_out_of_range")
    .max(250, "height_out_of_range")
    .nullable(),
  gymDaysPerWeek: z
    .number()
    .min(0, "gym_days_out_of_range")
    .max(7, "gym_days_out_of_range")
    .nullable(),
  gymSessionMinutes: z
    .number()
    .min(0, "gym_minutes_out_of_range")
    .max(300, "gym_minutes_out_of_range")
    .nullable(),
  walkingMinutesPerDay: z
    .number()
    .min(0, "walking_out_of_range")
    .max(480, "walking_out_of_range")
    .nullable(),
  calorieGoal: z.enum(["cut", "maintain", "surplus"]).nullable(),
});

export type CalorieProfileInput = z.infer<typeof calorieProfileInputSchema>;

export const loginInputSchema = z.object({
  username: z.string().trim().min(1).max(80),
  password: z.string().min(1).max(200),
});

export const registerInputSchema = z.object({
  username: z.string().trim().min(3, "username_too_short").max(80),
  password: z.string().min(8, "password_too_short").max(200),
});

export const adminUpdateUserSchema = z
  .object({
    username: z
      .string()
      .trim()
      .min(3, "username_too_short")
      .max(80)
      .optional(),
    password: z
      .string()
      .min(8, "password_too_short")
      .max(200)
      .optional(),
    isAdmin: z.boolean().optional(),
  })
  .superRefine((data, ctx) => {
    if (data.username === undefined && data.password === undefined && data.isAdmin === undefined) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "no_changes" });
    }
  });

export type AdminUpdateUserInput = z.infer<typeof adminUpdateUserSchema>;
