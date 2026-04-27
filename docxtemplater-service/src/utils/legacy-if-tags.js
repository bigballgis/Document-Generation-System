/**
 * Normalize legacy section/condition syntax used in some .docx templates.
 *
 * We have seen templates that use:
 * - {#if <expr>} ... {/if}
 * - {#if !flag} ... {/if}
 * - {#items | where:'x'} ... {/items}   (closing tag omits filters)
 *
 * Docxtemplater requires open/close tags to match, and the expression parser used by
 * this service expects expressions (not prefixed with "if ").
 *
 * This helper rewrites in-memory XML (no file mutation) to:
 * - strip leading "if " from section expressions
 * - close sections with the exact same expression that opened them
 *
 * This makes placeholder discovery and rendering more tolerant for demo templates,
 * while still keeping expressions intact (filters, negations, etc.).
 */
function rewriteLegacyIfTags(xml) {
  if (typeof xml !== 'string' || xml.length === 0) return xml;

  // Fast check to avoid extra work for most docs.
  if (!xml.includes('{#') && !xml.includes('{/')) return xml;

  const tokenRe = /\{#([^}]+)\}|\{\/([^}]+)\}/g;
  const out = [];
  const stack = [];
  let cursor = 0;

  /**
   * Best-effort "base name" for a section expression, used to match closing tags
   * that omit filters (ex: "fees | groupBy:'category'" closed by "fees").
   */
  function baseName(expr) {
    const s = String(expr || '').trim();
    // Strip leading "if " (legacy)
    const normalized = s.startsWith('if ') ? s.slice(3).trim() : s;
    // Base name is the identifier before space or pipe.
    const m = normalized.match(/^([A-Za-z0-9_.]+)/);
    return m ? m[1] : normalized;
  }

  for (const m of xml.matchAll(tokenRe)) {
    const idx = m.index;
    if (idx < cursor) continue;
    out.push(xml.slice(cursor, idx));

    const openExprRaw = m[1];
    const closeExprRaw = m[2];

    if (openExprRaw != null) {
      let expr = String(openExprRaw).trim();
      if (expr.startsWith('if ')) {
        expr = expr.slice(3).trim();
      }
      stack.push({ expr, base: baseName(expr) });
      out.push(`{#${expr}}`);
      cursor = idx + m[0].length;
      continue;
    }

    if (closeExprRaw != null) {
      const close = String(closeExprRaw).trim();

      // Legacy {/if} closes the last {#if ...} (or {#...} after normalization)
      if (close === 'if') {
        const top = stack.pop();
        out.push(top ? `{/${top.expr}}` : m[0]);
        cursor = idx + m[0].length;
        continue;
      }

      // If the template closes with a base identifier, upgrade it to the full opening expression.
      if (stack.length > 0) {
        // Prefer matching the closest open section with the same base.
        let matchIdx = -1;
        for (let i = stack.length - 1; i >= 0; i--) {
          if (stack[i].base === close) {
            matchIdx = i;
            break;
          }
        }
        if (matchIdx !== -1) {
          const top = stack[matchIdx];
          // Pop everything from the top of stack down to the match to keep nesting consistent.
          stack.splice(matchIdx);
          out.push(`{/${top.expr}}`);
          cursor = idx + m[0].length;
          continue;
        }
      }

      out.push(m[0]);
      cursor = idx + m[0].length;
    }
  }

  out.push(xml.slice(cursor));
  return out.join('');
}

/**
 * Apply legacy tag rewrite to all XML files inside a PizZip.
 * We only touch XML files because placeholder tags live there.
 */
function rewriteLegacyIfTagsInZip(zip) {
  const files = zip && zip.files ? Object.keys(zip.files) : [];
  for (const name of files) {
    if (!name.endsWith('.xml')) continue;
    const f = zip.files[name];
    if (!f || typeof f.asText !== 'function') continue;
    const original = f.asText();
    const rewritten = rewriteLegacyIfTags(original);
    if (rewritten !== original) {
      zip.file(name, rewritten);
    }
  }
  return zip;
}

module.exports = { rewriteLegacyIfTags, rewriteLegacyIfTagsInZip };

