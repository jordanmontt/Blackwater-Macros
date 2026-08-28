import { handle } from "hono/vercel";
import { createApp } from "./app";
import { prodDeps } from "./deps";

const app = createApp(prodDeps);
const handler = handle(app);

// Vercel invokes each HTTP method export; CORS middleware handles preflight.
export const GET = handler;
export const POST = handler;
export const PATCH = handler;
export const PUT = handler;
export const DELETE = handler;
export const OPTIONS = handler;