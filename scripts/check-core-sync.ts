import { existsSync, readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

/**
 * Verifies the Option A contract: every pure-algorithm spec has BOTH a TypeScript
 * test and a mirrored Kotlin (Android) test, and that the two sides of each pair
 * are changed together.
 *
 * Usage:
 *   npm run core:sync-check          # warn on drift (local)
 *   CI=true npm run core:sync-check  # hard-fail the PR on drift (CI)
 *
 * Behavior:
 *   - Missing Kotlin tests are INFO only while `android/` is not yet scaffolded.
 *   - Unpaired changes (only one side of a pair edited) always fail under CI.
 *   - A pair missing on either side is an ERROR once `android/` is scaffolded.
 *
 * See TECHNICAL.md §11.1 and android/test-sync/manifest.json.
 */

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, "..");
const manifestPath = resolve(repoRoot, "android/test-sync/manifest.json");
const failOnError = process.env.CI === "true" || process.argv.includes("--fail");
// Android is not scaffolded yet → missing Kotlin tests are expected, not a failure.
const androidScaffolded = existsSync(resolve(repoRoot, "android/settings.gradle.kts"));

interface SpecPair {
  source: string;
  ts: string;
  kt: string;
}

const errors: string[] = [];
// Warnings caused by actual drift → block the PR in CI.
const warnings: string[] = [];
// Informational notices (e.g. core not ported yet) → never block.
const infos: string[] = [];

function warn(msg: string) {
  warnings.push(msg);
  console.warn(`  ⚠ ${msg}`);
}

function info(msg: string) {
  infos.push(msg);
  console.warn(`  · ${msg}`);
}

function error(msg: string) {
  errors.push(msg);
  console.error(`  ✗ ${msg}`);
}

function gitChangedFiles(): Set<string> {
  try {
    const defaultBranch =
      execFileSync("git", ["symbolic-ref", "--short", "refs/remotes/origin/HEAD"], {
        cwd: repoRoot,
        stdio: ["ignore", "pipe", "ignore"],
      })
        .toString()
        .trim()
        .replace("origin/", "") || "main";

    const out = execFileSync(
      "git",
      ["diff", "--name-only", `origin/${defaultBranch}...HEAD`],
      { cwd: repoRoot, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] },
    );
    return new Set<string>(out.split("\n").filter(Boolean));
  } catch {
    return new Set<string>();
  }
}

function loadManifest(): { specs: SpecPair[] } {
  try {
    return JSON.parse(readFileSync(manifestPath, "utf8"));
  } catch {
    error(`No se pudo leer ${manifestPath}`);
    process.exit(1);
  }
}

const manifest = loadManifest();

console.log("\nCore sync check (Option A: TypeScript spec ⇄ Kotlin test)");

for (const pair of manifest.specs) {
  console.log(`\n  ${pair.source}`);
  const tsExists = existsSync(resolve(repoRoot, pair.ts));
  const ktExists = existsSync(resolve(repoRoot, pair.kt));

  if (!tsExists) {
    error(`Falta la prueba TypeScript: ${pair.ts}`);
    info(`Completa ambos lados del par (ver TECHNICAL.md §11.1).`);
    continue;
  }
  if (!ktExists && androidScaffolded) {
    error(`Falta la prueba Kotlin: ${pair.kt}`);
    info(`Completa ambos lados del par (ver TECHNICAL.md §11.1).`);
    continue;
  }
  if (!ktExists) {
    info(`Kotlin aún no portado (se implementará con la app Android).`);
  }
  console.log(`    ✓ TS    ${pair.ts}`);
  console.log(`    ✓ Kotlin ${pair.kt}`);
}

// Detect unpaired changes between the two sides of each pair → real drift.
const changed = gitChangedFiles();
if (changed.size > 0 && manifest.specs.length > 0) {
  console.log("\n  Comprobando que cada lado cambió con su espejo…");
  for (const pair of manifest.specs) {
    if (changed.has(pair.ts) && !changed.has(pair.kt)) {
      warn(
        `${pair.ts} cambió pero su espejo Kotlin (${pair.kt}) NO. Option A obliga a actualizar AMBAS suites.`,
      );
    }
    if (changed.has(pair.kt) && !changed.has(pair.ts)) {
      warn(
        `${pair.kt} cambió pero su espejo TypeScript (${pair.ts}) NO. Option A obliga a actualizar AMBAS suites.`,
      );
    }
  }
}

function finish() {
  if (errors.length === 0 && warnings.length === 0) {
    console.log("\n✔ Core sync check OK\n");
    process.exit(0);
  }

  for (const i of infos) console.log(`  · ${i}`);

  if (errors.length > 0) {
    console.error("\n✗ Core sync check FAILED:");
    for (const e of errors) console.error(`  ${e}`);
    if (failOnError) process.exit(1);
    console.warn("\n  (Resuelve los errores antes de continuar.)\n");
    process.exit(0);
  }

  if (warnings.length > 0) {
    console.warn("\n⚠ Cambios DESEQUILIBRADOS entre TS y Kotlin:");
    for (const w of warnings) console.warn(`  ${w}`);
    if (failOnError) {
      console.warn("\n  Option A lo bloquea en CI: actualiza el espejo del otro lado.\n");
      process.exit(1);
    }
    console.warn("\n  (Aviso local: actualiza el espejo del otro lado.)\n");
    process.exit(0);
  }
}

finish();