import type { WeightDTO } from "@/lib/types";
import { round2 } from "@/lib/nutrition";
import type { WeightsRepository, NewWeightData } from "../repositories/weights-repo";
import type { WeightInput } from "../validation";

export interface WeightsServiceDeps {
  weights: WeightsRepository;
}

function toDomainData(input: WeightInput): NewWeightData {
  return {
    measuredAt: new Date(input.measuredAt),
    weightKg: round2(input.weightKg),
    note: input.note ?? null,
  };
}

export function toWeightDto(row: WeightRowLike): WeightDTO {
  return {
    id: row.id,
    measuredAt: row.measuredAt.toISOString(),
    weightKg: row.weightKg,
    note: row.note,
  };
}

interface WeightRowLike {
  id: string;
  measuredAt: Date;
  weightKg: number;
  note: string | null;
}

export async function createWeight(deps: WeightsServiceDeps, userId: string, input: WeightInput) {
  const row = await deps.weights.create(userId, toDomainData(input));
  return toWeightDto(row);
}

export async function updateWeight(
  deps: WeightsServiceDeps,
  userId: string,
  id: string,
  input: WeightInput,
) {
  const row = await deps.weights.update(userId, id, toDomainData(input));
  return row ? toWeightDto(row) : null;
}

export async function deleteWeight(deps: WeightsServiceDeps, userId: string, id: string) {
  return deps.weights.delete(userId, id);
}

export async function listWeights(deps: WeightsServiceDeps, userId: string) {
  const rows = await deps.weights.listForUser(userId);
  return rows.map(toWeightDto);
}
