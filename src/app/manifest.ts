import type { MetadataRoute } from "next";
import { t } from "@/i18n";

export default function manifest(): MetadataRoute.Manifest {
  return {
    id: "/",
    name: t.appName,
    short_name: t.appName,
    description: "Registro de calorías, macros y peso.",
    lang: "es",
    start_url: "/",
    display: "standalone",
    theme_color: "#f5f4ef",
    background_color: "#f5f4ef",
    icons: [
      {
        src: "/icon-192x192.png",
        sizes: "192x192",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icon-512x512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icon-maskable-512x512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
    ],
  };
}