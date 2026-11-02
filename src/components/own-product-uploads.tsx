"use client";

import { useEffect } from "react";
import { sendOwnProducts } from "@/lib/foods/own-products";

/**
 * Products added from a label that Open Food Facts doesn't have yet are sent
 * when the app opens and when the browser is back online (silently; the
 * portion screen shows where each one stands).
 */
export function OwnProductUploads() {
  useEffect(() => {
    void sendOwnProducts();
    const retry = () => void sendOwnProducts();
    window.addEventListener("online", retry);
    return () => window.removeEventListener("online", retry);
  }, []);
  return null;
}
