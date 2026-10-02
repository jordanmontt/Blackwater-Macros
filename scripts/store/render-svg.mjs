// Renders the store SVGs (scripts/store/*.svg) to PNG. See docs/RELEASING.md «Store images»:
//   npm i --no-save @resvg/resvg-js && node scripts/store/render-svg.mjs <in.svg> <out.png>
import { Resvg } from "@resvg/resvg-js";
import fs from "node:fs";
import path from "node:path";

const [src, out] = process.argv.slice(2);
const dir = path.dirname(path.resolve(src));
// Inline local images (resvg does not load files from disk on its own).
const svg = fs.readFileSync(src, "utf8").replace(/xlink:href="([^"#:]+\.png)"/g, (_, file) =>
  `xlink:href="data:image/png;base64,${fs.readFileSync(path.join(dir, file)).toString("base64")}"`);
fs.writeFileSync(out, new Resvg(svg, { font: { loadSystemFonts: true } }).render().asPng());
