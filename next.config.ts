import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async headers() {
    return [
      {
        // Keeps browsers (and their back/forward cache) from serving a stale
        // login page; the proxy decides where /login belongs on every request.
        source: "/login",
        headers: [{ key: "Cache-Control", value: "no-store" }],
      },
      {
        // The service worker must never be cached so installed apps pick up
        // updates on the next visit.
        source: "/sw.js",
        headers: [
          { key: "Cache-Control", value: "no-cache, no-store, must-revalidate" },
          { key: "Content-Type", value: "application/javascript; charset=utf-8" },
        ],
      },
    ];
  },
};

export default nextConfig;
