import type { Metadata } from "next";
import { cookies, headers } from "next/headers";
import { dictionaryFor } from "@/i18n";
import { LANGUAGE_COOKIE, resolveLanguage } from "@/i18n/languages";

/**
 * The page itself is a client component, so it renders in the user's
 * language like every other page (the server always renders Spanish); only
 * the browser tab title is decided here, from the same cookie as the layout.
 */
export async function generateMetadata(): Promise<Metadata> {
  const language = resolveLanguage((await cookies()).get(LANGUAGE_COOKIE)?.value, (await headers()).get("accept-language"));
  const dictionary = dictionaryFor(language);
  return { title: dictionary.metodologia.title };
}

export default function MetodologiaLayout({ children }: { children: React.ReactNode }) {
  return children;
}
