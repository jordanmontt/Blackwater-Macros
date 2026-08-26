import { db } from "./db/client";
import { createSessionsRepository } from "./repositories/sessions-repo";
import { createUsersRepository } from "./repositories/users-repo";
import { createMealsRepository } from "./repositories/meals-repo";
import { createMealTemplatesRepository } from "./repositories/templates-repo";
import { createWeightsRepository } from "./repositories/weights-repo";
import { createSettingsRepository } from "./repositories/settings-repo";

/** Composition root: real repository instances backed by the shared DB pool. */
export const repositories = {
  users: createUsersRepository(db),
  sessions: createSessionsRepository(db),
  meals: createMealsRepository(db),
  templates: createMealTemplatesRepository(db),
  weights: createWeightsRepository(db),
  settings: createSettingsRepository(db),
};

export const serviceDeps = {
  auth: { users: repositories.users, sessions: repositories.sessions },
  meals: { meals: repositories.meals },
  templates: { templates: repositories.templates },
  weights: { weights: repositories.weights },
  stats: { meals: repositories.meals, weights: repositories.weights },
  settings: { settings: repositories.settings },
};
