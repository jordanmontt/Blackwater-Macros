// Serves the barcode reader's WebAssembly from our own origin (no CDN):
// copies zxing-wasm's reader into public/wasm before dev and build.
import { copyFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const source = join(root, "node_modules", "zxing-wasm", "dist", "reader", "zxing_reader.wasm");
const target = join(root, "public", "wasm", "zxing_reader.wasm");
mkdirSync(dirname(target), { recursive: true });
copyFileSync(source, target);
