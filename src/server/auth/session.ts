import { randomBytes } from "node:crypto";

export const SESSION_COOKIE_NAME = "bw_session";
/**
 * A session lasts 90 days **since it was last used** (sliding): a phone that syncs or a
 * browser that is open keeps its session for good; one unused for 90 days must log in again.
 */
export const SESSION_TTL_DAYS = 90;
export const SESSION_TTL_MS = SESSION_TTL_DAYS * 24 * 60 * 60 * 1000;
/** Renewed once less than half the window is left: one database write every ~45 days, not per request. */
export const SESSION_RENEW_BELOW_MS = SESSION_TTL_MS / 2;

export interface IssuedSession {
  token: string;
  expiresAt: Date;
}

export function generateSessionToken(): string {
  return randomBytes(32).toString("base64url");
}

export function sessionExpiryFromNow(now: Date = new Date()): Date {
  return new Date(now.getTime() + SESSION_TTL_MS);
}

export function isExpired(expiresAt: Date, now: Date = new Date()): boolean {
  return expiresAt.getTime() <= now.getTime();
}

/** A valid session past the middle of its window gets a new 90 days. */
export function shouldRenew(expiresAt: Date, now: Date = new Date()): boolean {
  return expiresAt.getTime() - now.getTime() < SESSION_RENEW_BELOW_MS;
}

export const sessionCookieOptions = {
  httpOnly: true,
  sameSite: "lax",
  secure: true,
  path: "/",
} as const;
