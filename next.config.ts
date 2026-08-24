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
    ];
  },
};

export default nextConfig;
