import { randomBytes, scrypt as scryptCallback, timingSafeEqual } from "node:crypto";

const KEY_LENGTH = 64;
// OpenSSL exige maxmem > 128·N·r·p; dejamos margen holgado en ambas llamadas.
const COST_PARAMS = { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 };

function scrypt(password: string | Buffer, salt: string | Buffer, keylen: number): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    scryptCallback(password, salt, keylen, COST_PARAMS, (error, derivedKey) => {
      if (error) reject(error);
      else resolve(derivedKey);
    });
  });
}

/**
 * Hashes a password with scrypt and a random salt.
 * Stored format: `scrypt$<N>$<r>$<p>$<salt-b64>$<hash-b64>`
 */
export async function hashPassword(password: string): Promise<string> {
  const salt = randomBytes(16);
  const derived = await scrypt(password.normalize("NFKC"), salt, KEY_LENGTH);
  return [
    "scrypt",
    COST_PARAMS.N,
    COST_PARAMS.r,
    COST_PARAMS.p,
    salt.toString("base64"),
    derived.toString("base64"),
  ].join("$");
}

/**
 * Checks a plaintext password against a stored hash in constant time.
 * Returns false for malformed hashes instead of throwing.
 */
export async function verifyPassword(password: string, storedHash: string): Promise<boolean> {
  const parts = storedHash.split("$");
  if (parts.length !== 6 || parts[0] !== "scrypt") return false;
  const [, nRaw, rRaw, pRaw, saltB64, hashB64] = parts;
  const N = Number(nRaw);
  const r = Number(rRaw);
  const p = Number(pRaw);
  if (!Number.isFinite(N) || !Number.isFinite(r) || !Number.isFinite(p)) return false;
  let salt: Buffer;
  let expected: Buffer;
  try {
    salt = Buffer.from(saltB64, "base64");
    expected = Buffer.from(hashB64, "base64");
  } catch {
    return false;
  }
  if (salt.length === 0 || expected.length === 0) return false;
  try {
    // Recalcula con los parámetros guardados (máximo de memoria holgado).
    const costParams = { ...COST_PARAMS, N, r, p };
    const derived = await new Promise<Buffer>((resolve, reject) => {
      scryptCallback(
        password.normalize("NFKC"),
        salt,
        expected.length,
        { ...costParams, maxmem: 64 * 1024 * 1024 },
        (error, derivedKey) => {
          if (error) reject(error);
          else resolve(derivedKey);
        },
      );
    });
    return timingSafeEqual(derived, expected);
  } catch {
    return false;
  }
}
