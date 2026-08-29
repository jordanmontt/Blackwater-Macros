"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { AUTH_EXPIRED_EVENT } from "@/lib/api";

/**
 * Escucha el evento que emite el cliente API cuando una respuesta 401 expira
 * la sesión y navega a la pantalla de acceso vía el router de Next.js.
 */
export function AuthRedirect() {
  const router = useRouter();

  useEffect(() => {
    const onUnauthorized = () => router.push("/login");
    window.addEventListener(AUTH_EXPIRED_EVENT, onUnauthorized);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onUnauthorized);
  }, [router]);

  return null;
}