/* Blackwater Macros service worker (online-first).
 *
 * - Shell (/, /login, icons, manifest) precached on install so the app opens
 *   without a network request.
 * - Navigations are network-first: fresh HTML when online, last-viewed page
 *   (or the shell) when offline.
 * - /api/* is never cached: data is always live and auth protected.
 * - Only immutable, content-hashed /_next/static/* assets are cached.
 */
const VERSION = "v1";

const SHELL_CACHE = `bwm-shell-${VERSION}`;
const PAGES_CACHE = `bwm-pages-${VERSION}`;
const STATIC_CACHE = `bwm-static-${VERSION}`;

const SHELL_URLS = [
  "/",
  "/login",
  "/icon-192x192.png",
  "/icon-512x512.png",
  "/icon-maskable-512x512.png",
  "/manifest.webmanifest",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(SHELL_CACHE).then((cache) =>
      Promise.allSettled(SHELL_URLS.map((url) => cache.add(url))),
    ),
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key.startsWith("bwm-") && key !== SHELL_CACHE && key !== PAGES_CACHE && key !== STATIC_CACHE)
            .map((key) => caches.delete(key)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

async function cacheFirst(request, cacheName) {
  const cache = await caches.open(cacheName);
  const cached = await cache.match(request);
  if (cached) return cached;
  const response = await fetch(request);
  if (response.ok) cache.put(request, response.clone());
  return response;
}

async function networkFirstNavigation(request) {
  const url = new URL(request.url);
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(PAGES_CACHE);
      cache.put(request, response.clone());
    }
    return response;
  } catch {
    const cache = await caches.open(PAGES_CACHE);
    const cached = await cache.match(request);
    if (cached) return cached;
    const shell = await cache.match(url.origin + "/");
    if (shell) return shell;
    return new Response("Sin conexión", { status: 503, headers: { "Content-Type": "text/plain" } });
  }
}

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  // API data is never cached.
  if (url.pathname.startsWith("/api/")) return;

  // Content-hashed build assets: safe to serve from cache.
  if (url.pathname.startsWith("/_next/static/")) {
    event.respondWith(cacheFirst(request, STATIC_CACHE));
    return;
  }

  // Pages: network-first so deployed updates always win online.
  if (request.mode === "navigate") {
    event.respondWith(networkFirstNavigation(request));
    return;
  }

  // Everything else (RSC payloads, images, ...) stays live.
});