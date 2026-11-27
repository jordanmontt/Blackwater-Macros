import { describe, expect, it } from "vitest";
import {
  addDaysToKey,
  daysBetweenKeys,
  isValidDateKey,
  nowDateTimeLocalValue,
  parseLocalDateTime,
  parseZonedDateTime,
  toDateKey,
  toDateTimeLocalValue,
  todayKey,
  toZonedDateTimeValue,
  weekStartOf,
  weighInDay,
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

  it("el lunes de la semana de una fecha (las semanas empiezan en lunes)", () => {
    expect(weekStartOf("2026-10-30")).toBe("2026-10-26"); // viernes
    expect(weekStartOf("2026-10-26")).toBe("2026-10-26"); // lunes
    expect(weekStartOf("2026-11-01")).toBe("2026-10-26"); // domingo
    expect(weekStartOf("2026-01-01")).toBe("2025-12-29"); // cruza el año
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

describe("hora de un pesaje en su zona horaria guardada", () => {
  it("un pesaje se lee a la hora del lugar donde se tomó, no a la del dispositivo", () => {
    const wakingInFrance = new Date("2026-08-25T07:03:00Z");
    expect(toZonedDateTimeValue(wakingInFrance, "Europe/Paris")).toBe("2026-08-25T09:03");
    expect(toZonedDateTimeValue(wakingInFrance, "America/La_Paz")).toBe("2026-08-25T03:03");
    // The day is that place's day too.
    expect(toZonedDateTimeValue(new Date("2026-11-27T03:30:00Z"), "America/La_Paz")).toBe("2026-11-26T23:30");
  });

  it("la hora del formulario vuelve al mismo instante en esa zona", () => {
    expect(parseZonedDateTime("2026-08-25T09:03", "Europe/Paris")?.toISOString()).toBe("2026-08-25T07:03:00.000Z");
    expect(parseZonedDateTime("2026-11-26T23:30", "America/La_Paz")?.toISOString()).toBe("2026-11-27T03:30:00.000Z");
    const instant = new Date("2026-03-01T06:45:00Z");
    expect(parseZonedDateTime(toZonedDateTimeValue(instant, "Asia/Kolkata"), "Asia/Kolkata")?.getTime()).toBe(instant.getTime());
  });

  it("cambio de hora como Android (java.time): la hora repetida es la primera, la que no existe se corre", () => {
    // 25 Oct 2026, Paris: 03:00 → 02:00, so 02:30 happens twice (CEST, then CET).
    expect(parseZonedDateTime("2026-10-25T02:30", "Europe/Paris")?.toISOString()).toBe("2026-10-25T00:30:00.000Z");
    // 29 Mar 2026, Paris: 02:00 → 03:00, so 02:30 doesn't exist: it becomes 03:30 CEST.
    expect(parseZonedDateTime("2026-03-29T02:30", "Europe/Paris")?.toISOString()).toBe("2026-03-29T01:30:00.000Z");
  });

  it("sin zona (pesajes antiguos) o con una desconocida, la del dispositivo", () => {
    const original = new Date(2026, 6, 16, 8, 45);
    for (const zone of [null, "Marte/Olimpo"]) {
      expect(toZonedDateTimeValue(original, zone)).toBe(toDateTimeLocalValue(original));
      expect(parseZonedDateTime("2026-07-16T08:45", zone)?.getTime()).toBe(original.getTime());
    }
  });

  it("el día de un pesaje es el de su zona; sin zona, el del dispositivo", () => {
    expect(weighInDay(new Date("2026-11-27T03:30:00Z"), "America/La_Paz")).toBe("2026-11-26");
    expect(weighInDay(new Date("2026-11-26T23:30:00Z"), "Europe/Paris")).toBe("2026-11-27");
    const local = new Date(2026, 6, 16, 0, 30);
    expect(weighInDay(local, null)).toBe("2026-07-16");
  });

  it("rechaza valores malformados o imposibles", () => {
    expect(parseZonedDateTime("ayer por la mañana", "Europe/Paris")).toBeNull();
    expect(parseZonedDateTime("2026-02-30T08:00", "Europe/Paris")).toBeNull();
  });
});
