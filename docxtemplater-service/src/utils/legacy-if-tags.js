function rewriteLegacyIfTags(xml) {
  if (typeof xml !== 'string' || xml.length === 0) return xml;

  if (!xml.includes('{#') && !xml.includes('{/')) return xml;

  const tokenRe = /\{#([^}]+)\}|\{\/([^}]+)\}/g;
  const out = [];
  const stack = [];
  let cursor = 0;

  function baseName(expr) {
    const s = String(expr || '').trim();
    const normalized = s.startsWith('if ') ? s.slice(3).trim() : s;
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

      if (close === 'if') {
        const top = stack.pop();
        out.push(top ? `{/${top.expr}}` : m[0]);
        cursor = idx + m[0].length;
        continue;
      }

      if (stack.length > 0) {
        let matchIdx = -1;
        for (let i = stack.length - 1; i >= 0; i--) {
          if (stack[i].base === close) {
            matchIdx = i;
            break;
          }
        }
        if (matchIdx !== -1) {
          const top = stack[matchIdx];
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

