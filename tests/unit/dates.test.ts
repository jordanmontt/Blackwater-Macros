import { describe, expect, it } from "vitest";
import {
  addDaysToKey,
  daysBetweenKeys,
  isValidDateKey,
  nowDateTimeLocalValue,
  parseLocalDateTime,
  toDateKey,
  toDateTimeLocalValue,
  todayKey,
} from "@/lib/core/dates";

describe("claves de fecha (YYYY-MM-DD en hora local)", () => {
  it("convierte fechas locales a clave sin desplazamientos de zona horaria", () => {
    expect(toDateKey(new Date(2026, 2, 9))).toBe("2026-03-09");
    expect(toDateKey(new Date(2026, 11, 31))).toBe("2026-12-31");
  });

  it("valida claves reales y rechaza las imposibles", () => {
    expect(isValidDateKey("2026-02-28")).toBe(true);
    expect(isValidDateKey("2026-13-01")).toBe(false);
    expect(isValidDateKey("2026-02-30")).toBe(false);
    expect(isValidDateKey("09-03-2026")).toBe(false);
    expect(isValidDateKey("no es fecha")).toBe(false);
  });

  it("suma y resta días cruzando meses y años", () => {
    expect(addDaysToKey("2026-03-01", -1)).toBe("2026-02-28");
    expect(addDaysToKey("2026-12-31", 1)).toBe("2027-01-01");
    expect(addDaysToKey("2026-05-15", 0)).toBe("2026-05-15");
  });

  it("calcula la distancia en días entre dos claves", () => {
    expect(daysBetweenKeys("2026-03-01", "2026-03-08")).toBe(7);
    expect(daysBetweenKeys("2026-03-08", "2026-03-01")).toBe(-7);
  });

  it("'hoy' devuelve una clave válida para el día actual", () => {
    expect(isValidDateKey(todayKey())).toBe(true);
  });
});

describe("selector de fecha y hora (datetime-local)", () => {
  it("parsea el valor del input como hora local, no como UTC", () => {
    const parsed = parseLocalDateTime("2026-07-16T08:30");
    expect(parsed).not.toBeNull();
    expect(parsed?.getFullYear()).toBe(2026);
    expect(parsed?.getHours()).toBe(8); // si se leyera como UTC fallaría fuera de UTC
  });

  it("rechaza valores malformados", () => {
    expect(parseLocalDateTime("ayer por la mañana")).toBeNull();
    expect(parseLocalDateTime("2026-07-16")).toBeNull();
  });

  it("autocompleta con la hora actual al pulsar 'Ahora'", () => {
    const value = nowDateTimeLocalValue();
    expect(value).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    const parsed = parseLocalDateTime(value);
    expect(Math.abs(Date.now() - (parsed?.getTime() ?? 0))).toBeLessThan(60_000);
  });

  it("el viaje de ida y vuelta fecha → input → fecha mantiene la misma hora local", () => {
    const original = new Date(2026, 6, 16, 8, 45);
    const roundTrip = parseLocalDateTime(toDateTimeLocalValue(original));
    expect(roundTrip?.getTime()).toBe(original.getTime());
  });
});
