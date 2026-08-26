import type { WeightDTO } from "@/lib/types";
import { round2 } from "@/lib/nutrition";
import type { WeightsRepository, NewWeightData } from "../repositories/weights-repo";
import type { WeightInput } from "../validation";

function toDomainData(input: WeightInput): NewWeightData {
  return {
    measuredAt: new Date(input.measuredAt),
    weightKg: round2(input.weightKg),
    bodyFatPct: input.bodyFatPct ?? null,
    note: input.note ?? null,
  };
}

export function toWeightDto(row: WeightRowLike): WeightDTO {
  return {
    id: row.id,
    measuredAt: row.measuredAt.toISOString(),
    weightKg: row.weightKg,
    bodyFatPct: row.bodyFatPct,
    note: row.note,
  };
}

interface WeightRowLike {
  id: string;
  measuredAt: Date;
  weightKg: number;
  bodyFatPct: number | null;
  note: string | null;
}

export async function createWeight(repo: WeightsRepository, userId: string, input: WeightInput) {
  const row = await repo.create(userId, toDomainData(input));
  return toWeightDto(row);
}

export async function updateWeight(
  repo: WeightsRepository,
  userId: string,
  id: string,
  input: WeightInput,
) {
  const row = await repo.update(userId, id, toDomainData(input));
  return row ? toWeightDto(row) : null;
}

export async function deleteWeight(repo: WeightsRepository, userId: string, id: string) {
  return repo.delete(userId, id);
}

export async function listWeights(repo: WeightsRepository, userId: string) {
  const rows = await repo.listForUser(userId);
  return rows.map(toWeightDto);
}
