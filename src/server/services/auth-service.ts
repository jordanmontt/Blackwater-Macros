import {
  generateSessionToken,
  isExpired,
  sessionExpiryFromNow,
  shouldRenew,
  type IssuedSession,
} from "../auth/session";
import type { SessionsRepository } from "../repositories/sessions-repo";
import type { UsersRepository } from "../repositories/users-repo";
import type { UserRow } from "../db/schema";
import { hashPassword, verifyPassword } from "../auth/password";

export interface AuthServiceDeps {
  users: UsersRepository;
  sessions: SessionsRepository;
}

export class InvalidCredentialsError extends Error {
  constructor() {
    super("Usuario o contraseña incorrectos");
    this.name = "InvalidCredentialsError";
  }
}

export class UsernameExistsError extends Error {
  constructor() {
    super("El usuario ya existe");
    this.name = "UsernameExistsError";
  }
}

/**
 * Creates a new account. The username is normalized to lowercase and must not
 * already exist; otherwise the backend rejects it explicitly (not relying only
 * on the database unique constraint). No session is started: account creation
 * is a management operation, not a login.
 */
export async function register(
  deps: AuthServiceDeps,
  username: string,
  password: string,
): Promise<UserRow> {
  const normalized = username.trim().toLowerCase();
  if (await deps.users.findByUsername(normalized)) {
    throw new UsernameExistsError();
  }
  const passwordHash = await hashPassword(password);
  try {
    return await deps.users.create({ username: normalized, passwordHash });
  } catch (error) {
    // Posible carrera: otro registro con el mismo nombre entró primero.
    if ((error as { code?: string }).code === "23505") throw new UsernameExistsError();
    throw error;
  }
}

/**
 * Authenticates a user and starts a new session. Expired sessions of the user
 * are cleaned up opportunistically on every successful login.
 */
export async function login(
  deps: AuthServiceDeps,
  username: string,
  password: string,
): Promise<IssuedSession> {
  const user = await deps.users.findByUsername(username);
  if (!user) {
    // Burn comparable time so response latency does not reveal valid usernames.
    await verifyPassword(password, "scrypt$16384$8$1$c2FsdA==$aGFzaA==");
    throw new InvalidCredentialsError();
  }
  const valid = await verifyPassword(password, user.passwordHash);
  if (!valid) throw new InvalidCredentialsError();

  const issued: IssuedSession = {
    token: generateSessionToken(),
    expiresAt: sessionExpiryFromNow(),
  };
  await deps.sessions.create({ ...issued, userId: user.id });
  await deps.sessions.deleteExpiredBefore(new Date());
  return issued;
}

export interface ResolvedSession {
  userId: string;
  /** Set when this request renewed the session: the new end (the web refreshes its cookie). */
  renewedUntil: Date | null;
}

/**
 * Checks a session token and, past the middle of its 90 days, renews it (sliding
 * expiry): a session in use never runs out, so a phone syncing in the background is
 * never logged out by surprise. Null if the token is unknown or expired.
 */
export async function resolveSessionDetails(
  deps: AuthServiceDeps,
  token: string | undefined,
  now: Date = new Date(),
): Promise<ResolvedSession | null> {
  if (!token) return null;
  const record = await deps.sessions.findByToken(token);
  if (!record || isExpired(record.expiresAt, now)) return null;
  if (!shouldRenew(record.expiresAt, now)) return { userId: record.userId, renewedUntil: null };
  const renewedUntil = sessionExpiryFromNow(now);
  await deps.sessions.extend(token, renewedUntil);
  return { userId: record.userId, renewedUntil };
}

/** Resolves the user id behind a session token (renewing it when due), or null if invalid/expired. */
export async function resolveSession(
  deps: AuthServiceDeps,
  token: string | undefined,
  now: Date = new Date(),
): Promise<string | null> {
  return (await resolveSessionDetails(deps, token, now))?.userId ?? null;
}

export async function logout(deps: AuthServiceDeps, token: string | undefined): Promise<void> {
  if (token) await deps.sessions.deleteByToken(token);
}
