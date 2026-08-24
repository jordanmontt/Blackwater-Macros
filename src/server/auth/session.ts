import { randomBytes } from "node:crypto";

export const SESSION_COOKIE_NAME = "bw_session";
/** Users stay logged in for 90 days; each login refreshes the window. */
export const SESSION_TTL_DAYS = 90;
export const SESSION_TTL_MS = SESSION_TTL_DAYS * 24 * 60 * 60 * 1000;

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

export const sessionCookieOptions = {
  httpOnly: true,
  sameSite: "lax",
  secure: true,
  path: "/",
} as const;
