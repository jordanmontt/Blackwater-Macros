import { describe, expect, it } from "vitest";
import {
  createWeight,
  deleteWeight,
  listWeights,
  updateWeight,
  toWeightDto,
} from "@/server/services/weights-service";
import type { WeightsRepository, NewWeightData } from "@/server/repositories/weights-repo";
import type { WeightRow } from "@/server/db/schema";
import type { WeightInput } from "@/server/validation";

function memoryWeights(): WeightsRepository {
  const rows = new Map<string, WeightRow>();
  let nextId = 1;

  return {
    async listForUser(userId) {
      return [...rows.values()]
        .filter((row) => row.userId === userId)
        .sort((a, b) => a.measuredAt.getTime() - b.measuredAt.getTime());
    },
    async getById(userId, id) {
      const found = rows.get(id);
      return found && found.userId === userId ? found : null;
    },
    async create(userId, data: NewWeightData) {
      const id = `wt-${nextId++}`;
      const now = new Date();
      const row: WeightRow = { id, userId, createdAt: now, updatedAt: now, ...data };
      rows.set(id, row);
      return row;
    },
    async update(userId, id, data: NewWeightData) {
      const existing = rows.get(id);
      if (!existing || existing.userId !== userId) return null;
      const updated: WeightRow = { ...existing, ...data, updatedAt: new Date() };
      rows.set(id, updated);
      return updated;
    },
    async delete(userId, id) {
      const existing = rows.get(id);
      if (!existing || existing.userId !== userId) return false;
      rows.delete(id);
      return true;
    },
    async upsert(userId, id, data: NewWeightData) {
      const existing = rows.get(id);
      if (existing && existing.userId !== userId) return null;
      const now = new Date();
      const row: WeightRow = existing
        ? { ...existing, ...data, updatedAt: now }
        : { id, userId, createdAt: now, updatedAt: now, ...data };
      rows.set(id, row);
      return row;
    },
  };
}

const weight80: WeightInput = {
  measuredAt: "2026-08-23T08:00",
  weightKg: 80.5,
  bodyFatPct: 18.2,
  note: "ayunas",
};

const weight90: WeightInput = {
  measuredAt: "2026-08-30T08:00",
  weightKg: 90,
  bodyFatPct: null,
  note: null,
};

describe("gestión de pesos", () => {
  it("crear un registro devuelve el DTO con los datos correctos", async () => {
    const repo = memoryWeights();
    const dto = await createWeight(repo, "user-1", weight80);

    expect(dto.id).toBe("wt-1");
    expect(dto.weightKg).toBe(80.5);
    expect(dto.bodyFatPct).toBe(18.2);
    expect(dto.note).toBe("ayunas");
    expect(dto.measuredAt).toContain("2026-08-23");
  });

  it("listar pesos solo muestra los del usuario", async () => {
    const repo = memoryWeights();
    await createWeight(repo, "user-1", weight80);
    await createWeight(repo, "user-2", weight90);

    const user1 = await listWeights(repo, "user-1");
    expect(user1).toHaveLength(1);
    expect(user1[0].weightKg).toBe(80.5);

    const user2 = await listWeights(repo, "user-2");
    expect(user2).toHaveLength(1);
    expect(user2[0].weightKg).toBe(90);
  });

  it("listar pesos ordena por fecha ascendente", async () => {
    const repo = memoryWeights();
    await createWeight(repo, "user-1", weight90);
    await createWeight(repo, "user-1", weight80);

    const all = await listWeights(repo, "user-1");
    expect(all[0].weightKg).toBe(80.5);
    expect(all[1].weightKg).toBe(90);
  });

  it("editar un registro actualiza los campos", async () => {
    const repo = memoryWeights();
    const created = await createWeight(repo, "user-1", weight80);

    const updated = await updateWeight(repo, "user-1", created.id, {
      ...weight80,
      weightKg: 81,
      bodyFatPct: 19,
      note: "post-comida",
    });

    expect(updated).not.toBeNull();
    expect(updated!.weightKg).toBe(81);
    expect(updated!.bodyFatPct).toBe(19);
    expect(updated!.note).toBe("post-comida");
  });

  it("editar registro ajeno devuelve null", async () => {
    const repo = memoryWeights();
    const created = await createWeight(repo, "user-1", weight80);

    const result = await updateWeight(repo, "user-2", created.id, weight90);
    expect(result).toBeNull();
  });

  it("borrar un registro funciona y desaparece de la lista", async () => {
    const repo = memoryWeights();
    const created = await createWeight(repo, "user-1", weight80);

    expect(await deleteWeight(repo, "user-1", created.id)).toBe(true);
    const remaining = await listWeights(repo, "user-1");
    expect(remaining).toEqual([]);
  });

  it("borrar registro ajeno devuelve false", async () => {
    const repo = memoryWeights();
    const created = await createWeight(repo, "user-1", weight80);

    expect(await deleteWeight(repo, "user-2", created.id)).toBe(false);
    const remaining = await listWeights(repo, "user-1");
    expect(remaining).toHaveLength(1);
  });

  it("peso sin grasa corporal ni nota se guarda correctamente", async () => {
    const repo = memoryWeights();
    const dto = await createWeight(repo, "user-1", weight90);

    expect(dto.bodyFatPct).toBeNull();
    expect(dto.note).toBeNull();
  });
});

describe("toWeightDto", () => {
  it("convierte un WeightRow a WeightDTO con fecha ISO", () => {
    const row = {
      id: "wt-1",
      userId: "user-1",
      measuredAt: new Date("2026-08-23T08:00:00.000Z"),
      weightKg: 80.5,
      bodyFatPct: 18.2,
      note: "ayunas",
      updatedAt: new Date("2026-08-23T08:00:00.000Z"),
    };
    const dto = toWeightDto(row);
    expect(dto.id).toBe("wt-1");
    expect(dto.measuredAt).toBe("2026-08-23T08:00:00.000Z");
    expect(dto.weightKg).toBe(80.5);
    expect(dto.bodyFatPct).toBe(18.2);
    expect(dto.note).toBe("ayunas");
    expect(dto.updatedAt).toBe("2026-08-23T08:00:00.000Z");
  });

  it("maneja bodyFatPct y note null", () => {
    const row = {
      id: "wt-2",
      userId: "user-1",
      measuredAt: new Date("2026-08-30T08:00:00.000Z"),
      weightKg: 90,
      bodyFatPct: null,
      note: null,
      updatedAt: new Date("2026-08-30T08:00:00.000Z"),
    };
    const dto = toWeightDto(row);
    expect(dto.bodyFatPct).toBeNull();
    expect(dto.note).toBeNull();
  });
});
