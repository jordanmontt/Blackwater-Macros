import { sql } from "drizzle-orm";
import {
  date,
  doublePrecision,
  index,
  integer,
  jsonb,
  pgEnum,
  pgTable,
  text,
  timestamp,
  uuid,
} from "drizzle-orm/pg-core";

export const entryModeEnum = pgEnum("entry_mode", ["per_ingredient", "total_only"]);
export const genderEnum = pgEnum("gender", ["male", "female"]);
export const calorieGoalEnum = pgEnum("calorie_goal", ["cut", "maintain", "surplus"]);

export const users = pgTable("users", {
  id: uuid("id").primaryKey().default(sql`gen_random_uuid()`),
  username: text("username").notNull().unique(),
  passwordHash: text("password_hash").notNull(),
  gender: genderEnum("gender"),
  birthYear: integer("birth_year"),
  heightCm: doublePrecision("height_cm"),
  gymDaysPerWeek: integer("gym_days_per_week"),
  gymSessionMinutes: integer("gym_session_minutes"),
  walkingMinutesPerDay: integer("walking_minutes_per_day"),
  calorieGoal: calorieGoalEnum("calorie_goal"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

export const sessions = pgTable(
  "sessions",
  {
    token: text("token").primaryKey(),
    userId: uuid("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index("sessions_user_id_idx").on(table.userId)],
);

/**
 * A single ingredient of a meal. Stored as JSONB inside `meals.ingredients`.
 * Nutrition values are optional per ingredient; calories/protein may be added
 * for any ingredient. `carbs`/`fat` are reserved for future macro support and
 * are not collected by the UI yet.
 */
export type MealIngredient = {
  name: string;
  quantity?: string;
  calories?: number;
  protein?: number;
  carbs?: number;
  fat?: number;
};

export const meals = pgTable(
  "meals",
  {
    id: uuid("id").primaryKey().default(sql`gen_random_uuid()`),
    userId: uuid("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    logDate: date("log_date").notNull(),
    title: text("title").notNull(),
    notes: text("notes"),
    entryMode: entryModeEnum("entry_mode").notNull().default("per_ingredient"),
    ingredients: jsonb("ingredients").$type<MealIngredient[]>().notNull().default([]),
    totalCalories: doublePrecision("total_calories"),
    totalProtein: doublePrecision("total_protein"),
    totalCarbs: doublePrecision("total_carbs"),
    totalFat: doublePrecision("total_fat"),
    resolvedCalories: doublePrecision("resolved_calories").notNull().default(0),
    resolvedProtein: doublePrecision("resolved_protein").notNull().default(0),
    resolvedCarbs: doublePrecision("resolved_carbs").notNull().default(0),
    resolvedFat: doublePrecision("resolved_fat").notNull().default(0),
    sortOrder: integer("sort_order").notNull().default(0),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index("meals_user_date_idx").on(table.userId, table.logDate)],
);

export const mealTemplates = pgTable(
  "meal_templates",
  {
    id: uuid("id").primaryKey().default(sql`gen_random_uuid()`),
    userId: uuid("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    name: text("name").notNull(),
    title: text("title").notNull(),
    notes: text("notes"),
    entryMode: entryModeEnum("entry_mode").notNull().default("per_ingredient"),
    ingredients: jsonb("ingredients").$type<MealIngredient[]>().notNull().default([]),
    totalCalories: doublePrecision("total_calories"),
    totalProtein: doublePrecision("total_protein"),
    totalCarbs: doublePrecision("total_carbs"),
    totalFat: doublePrecision("total_fat"),
    resolvedCalories: doublePrecision("resolved_calories").notNull().default(0),
    resolvedProtein: doublePrecision("resolved_protein").notNull().default(0),
    resolvedCarbs: doublePrecision("resolved_carbs").notNull().default(0),
    resolvedFat: doublePrecision("resolved_fat").notNull().default(0),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index("meal_templates_user_idx").on(table.userId)],
);

export const weights = pgTable(
  "weights",
  {
    id: uuid("id").primaryKey().default(sql`gen_random_uuid()`),
    userId: uuid("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    measuredAt: timestamp("measured_at", { withTimezone: true }).notNull(),
    weightKg: doublePrecision("weight_kg").notNull(),
    bodyFatPct: doublePrecision("body_fat_pct"),
    note: text("note"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index("weights_user_measured_idx").on(table.userId, table.measuredAt)],
);

export type UserRow = typeof users.$inferSelect;
export type MealRow = typeof meals.$inferSelect;
export type MealTemplateRow = typeof mealTemplates.$inferSelect;
export type WeightRow = typeof weights.$inferSelect;
