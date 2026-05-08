/**
 * Normalize @Tag values to ASCII kebab-case for JUnit 5 / jqwik compatibility.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { readdirSync, statSync } from "node:fs";
import { join } from "node:path";

function normalizeTagInner(inner) {
  let s = inner.trim().toLowerCase();
  s = s.replace(/feature:\s*/gi, "feature-");
  s = s.replace(/property\s*(\d+)\s*&\s*(\d+)/gi, "property-$1-$2");
  s = s.replace(/property\s*(\d+)\s*:\s*/gi, "property-$1-");
  s = s.replace(/,/g, "-");
  s = s.replace(/[^a-z0-9_-]+/g, "-");
  s = s.replace(/-+/g, "-").replace(/^-|-$/g, "");
  return s;
}

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, out);
    else if (name.endsWith(".java")) out.push(p);
  }
  return out;
}

const root = join(import.meta.dirname, "..", "src", "test");
const files = walk(root);
let count = 0;
for (const file of files) {
  const text = readFileSync(file, "utf8");
  const newText = text.replace(/@Tag\("([^"]*)"\)/g, (_, inner) => {
    return `@Tag("${normalizeTagInner(inner)}")`;
  });
  if (newText !== text) {
    writeFileSync(file, newText, "utf8");
    count++;
  }
}
console.log(`Updated ${count} files`);
