import { describe, expect, it } from "vitest";
import {
  hashPassword,
  verifyPassword,
} from "@/server/auth/password";
import {
  generateSessionToken,
  isExpired,
  sessionExpiryFromNow,
} from "@/server/auth/session";
import { toCsv } from "@/lib/csv";

describe("almacenamiento de contraseñas", () => {
  it("guarda un hash con sal, nunca la contraseña en claro", async () => {
    const hash = await hashPassword("mi-contraseña-secreta");
    expect(hash).not.toContain("mi-contraseña-secreta");
    expect(hash).toMatch(/^scrypt\$/);
    expect(hash.split("$")[4]).not.toBe(hash.split("$")[5]);
  });

  it("verifica la contraseña correcta y rechaza la incorrecta", async () => {
    const hash = await hashPassword("correct horse battery staple");
    await expect(verifyPassword("correct horse battery staple", hash)).resolves.toBe(true);
    await expect(verifyPassword("incorrect horse", hash)).resolves.toBe(false);
  });

  it("produce hashes distintos para la misma contraseña (sal aleatoria)", async () => {
    const a = await hashPassword("misma");
    const b = await hashPassword("misma");
    expect(a).not.toBe(b);
    await expect(verifyPassword("misma", a)).resolves.toBe(true);
    await expect(verifyPassword("misma", b)).resolves.toBe(true);
  });

  it("no lanza con hashes malformados: simplemente los rechaza", async () => {
    await expect(verifyPassword("x", "")).resolves.toBe(false);
    await expect(verifyPassword("x", "bcrypt$abc")).resolves.toBe(false);
    await expect(verifyPassword("x", "scrypt$16384$8$1$!!!$???")).resolves.toBe(false);
  });
});

describe("sesiones persistentes", () => {
  it("los tokens son aleatorios y no reutilizables entre sesiones", () => {
    expect(generateSessionToken()).not.toBe(generateSessionToken());
    expect(generateSessionToken()).toMatch(/^[A-Za-z0-9_-]{40,}$/); // base64url de 32 bytes
  });

  it("la sesión dura 90 días desde el momento del inicio", () => {
    const now = new Date("2026-08-23T10:00:00Z");
    const expires = sessionExpiryFromNow(now);
    expect(expires.getTime() - now.getTime()).toBe(90 * 24 * 60 * 60 * 1000);
  });

  it("una sesión caducada ya no cuenta como válida", () => {
    const now = new Date("2026-08-23T10:00:00Z");
    expect(isExpired(sessionExpiryFromNow(now), now)).toBe(false);
    expect(isExpired(new Date(now.getTime() - 1), now)).toBe(true);
  });
});

describe("exportación CSV", () => {
  it("escapa comas, comillas y saltos de línea en las notas", () => {
    const csv = toCsv([
      ["fecha", "nota"],
      ["2026-03-01", 'comida "especial", con salto\nde línea'],
    ]);
    expect(csv).toContain('"comida ""especial"", con salto\n');
    expect(csv.startsWith("\uFEFF")).toBe(true); // BOM para Excel
  });

  it("convierte números y valores nulos a celdas vacías", () => {
    const csv = toCsv([
      [1.5, null, undefined],
    ]);
    expect(csv).toContain("1.5,,");
  });
});
