const express = require('express');
const Docxtemplater = require('docxtemplater');
const PizZip = require('pizzip');
const { getTags } = require('docxtemplater/js/get-tags');
const { parser, createImageModule } = require('../docx-templater-config');
const { getFileBuffer } = require('../minio-client');
const { rewriteLegacyIfTagsInZip } = require('../utils/legacy-if-tags');

const router = express.Router();

/**
 * Flatten Docxtemplater getTags() tree into dot-path placeholder names (leaf nodes only).
 * @param {Record<string, object>} node
 * @param {string} prefix
 * @returns {string[]}
 */
function collectLeafPathsFromTagTree(node, prefix = '') {
  if (!node || typeof node !== 'object') {
    return [];
  }
  const names = [];
  for (const [key, child] of Object.entries(node)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (!child || typeof child !== 'object') {
      continue;
    }
    const childKeys = Object.keys(child);
    if (childKeys.length === 0) {
      names.push(path);
    } else {
      names.push(...collectLeafPathsFromTagTree(child, path));
    }
  }
  return names;
}

function listVariablesFromCompiledDoc(doc) {
  doc.compile();
  const all = new Set();
  for (const fileName of Object.keys(doc.compiled)) {
    const xf = doc.compiled[fileName];
    if (!xf.postparsed || !Array.isArray(xf.postparsed)) {
      continue;
    }
    const tree = getTags(xf.postparsed);
    for (const p of collectLeafPathsFromTagTree(tree)) {
      all.add(p);
    }
  }
  return [...all].sort((a, b) => a.localeCompare(b));
}

function isNotFoundError(err) {
  if (!err) return false;
  if (err.code === 'NotFound' || err.code === 'NoSuchKey') return true;
  if (typeof err.message === 'string' && err.message.includes('File not found')) return true;
  return false;
}

/**
 * POST /scan-variables
 * Body: { templatePath: string }
 * Response: { variables: string[] }
 *
 * WS-02-T07: read-only placeholder discovery for composite coverage (same Docxtemplater stack as /render).
 */
router.post('/', async (req, res) => {
  try {
    const { templatePath } = req.body || {};

    if (!templatePath || typeof templatePath !== 'string' || !templatePath.trim()) {
      return res.status(400).json({
        error: { code: 'MISSING_TEMPLATE_PATH', message: 'templatePath is required' },
      });
    }

    let templateBuffer;
    try {
      templateBuffer = await getFileBuffer(templatePath);
    } catch (err) {
      if (isNotFoundError(err)) {
        return res.status(404).json({
          error: { code: 'TEMPLATE_NOT_FOUND', message: `Template object not found: ${templatePath}` },
        });
      }
      throw err;
    }

    const zip = rewriteLegacyIfTagsInZip(new PizZip(templateBuffer));
    // For variable discovery, images are not required and the image module can fail
    // on templates that place raw image tags outside of a paragraph. Avoid breaking
    // readiness/coverage when only image placeholders are malformed.
    const doc = new Docxtemplater(zip, {
      modules: [],
      paragraphLoop: true,
      linebreaks: true,
      parser,
    });

    let variables;
    try {
      variables = listVariablesFromCompiledDoc(doc);
    } catch (err) {
      const id = err && err.properties && err.properties.id;
      return res.status(422).json({
        error: {
          code: 'TEMPLATE_PARSE_ERROR',
          message: err.message || 'Failed to parse template placeholders',
          details: id ? { id } : undefined,
        },
      });
    }

    return res.json({ variables });
  } catch (err) {
    console.error('scan-variables error:', err);
    return res.status(500).json({
      error: { code: 'SCAN_FAILED', message: err.message || 'Variable scan failed' },
    });
  }
});

module.exports = router;
