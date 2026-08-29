import type { MetadataRoute } from "next";
import { t } from "@/i18n";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: t.appName,
    short_name: t.appName,
    start_url: "/",
    display: "standalone",
    theme_color: "#f5f4ef",
    background_color: "#f5f4ef",
    icons: [
      {
        src: "/icon.png",
        sizes: "512x512",
        type: "image/png",
      },
    ],
  };
}