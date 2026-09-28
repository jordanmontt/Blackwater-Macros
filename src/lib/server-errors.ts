import { t as es } from "@/i18n/es";

/**
 * What went wrong on the server, as a stable code. The API answers
 * `{ error: "<Spanish text>", code }`: each client shows the code's text in
 * its own language (web `t.serverErrors`, Android `server_error_*`); the
 * Spanish text is for older clients and for reading logs.
 */
export type ServerErrorCode = keyof typeof es.serverErrors;

export function isServerErrorCode(value: unknown): value is ServerErrorCode {
  return typeof value === "string" && Object.hasOwn(es.serverErrors, value);
}

/** The reference (Spanish) text of a code. */
export function serverErrorText(code: ServerErrorCode): string {
  return es.serverErrors[code];
}
