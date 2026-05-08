---
inclusion: auto
name: writing-steering
description: How to author steering files under .kiro/steering/
---

# Writing steering docs

## Front matter

Must start on line 1 with no leading blank lines.

| Mode | When | Required fields |
|------|------|-----------------|
| `auto` | Domain-specific (preferred) | `name` + `description` |
| `fileMatch` | File-type triggers | `fileMatchPattern` |
| `always` | Global-only docs (≤3 files) | none |
| `manual` | Rare / on-demand | none |

Prefer `auto` to limit noise. `description` = one English sentence on when to apply the doc.

## Anti-hallucination

1. Verify before writing — confirm patterns exist via codebase search
2. Anchor with `#[[file:path]]` — never rely on memory alone
3. Auditable claims — every statement must be provable in-repo or removed
4. Unknowns — mark `<!-- TODO: verify -->` instead of guessing
5. Conflicts — call out contradictions with code; do not hide them

## Token budget

Ask per line: "Would removing this make an AI fail?" If not, delete.

- Omit universal best practices; keep project-only rules
- Tables beat prose; bullets beat paragraphs
- Prefer `#[[file:]]` over long inline snippets
- One concrete example beats ten abstract rules

| Metric | Limit |
|--------|-------|
| Single file | ≤ 100 lines |
| `always` file count | ≤ 3 |
| `always` total lines | ≤ 300 |

## Template

```markdown
---
inclusion: auto
name: {kebab-case}
description: {One English sentence}
---
# {Topic}
## Rules
- Actionable bullets
## Examples (optional)
- #[[file:relative/path]]
```

One file = one domain. File names are kebab-case.

## References

- Files: `#[[file:relative/path]]`
- fileMatch: `fileMatchPattern: ["**/*.ts"]` (array) or `"**/*.test.*"` (string)

## Checklist

1. Front matter valid; inclusion mode justified
2. Single responsibility; no overlap with other steering docs
3. Facts verified; no fabricated APIs
4. Every `#[[file:]]` path exists
5. ≤ 100 lines
