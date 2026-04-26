const express = require('express');
const router = express.Router();
const Docxtemplater = require('docxtemplater');
const expressionParser = require('docxtemplater/expressions.js');
const PizZip = require('pizzip');
const ImageModule = require('docxtemplater-image-module-free');
const { getFileBuffer, putFileBuffer } = require('../minio-client');
const { generateBarcode, generateQRCode } = require('../utils/barcode');
const { applyTextWatermark, applyImageWatermark } = require('../utils/watermark');

/**
 * Angular parser with built-in filters for template expressions.
 * Enables: {user.name}, {price * 1.2}, {name | upper}, {items | sumBy:'price'}, etc.
 */
const parser = expressionParser.configure({
  filters: {
    // ── String filters ──
    upper(input) { return input ? String(input).toUpperCase() : input; },
    lower(input) { return input ? String(input).toLowerCase() : input; },
    trim(input) { return input ? String(input).trim() : input; },
    padStart(input, len, char) { return input ? String(input).padStart(len, char || ' ') : input; },
    padEnd(input, len, char) { return input ? String(input).padEnd(len, char || ' ') : input; },
    replace(input, search, replacement) { return input ? String(input).replaceAll(search, replacement || '') : input; },
    substr(input, start, length) { return input ? String(input).substring(start, length != null ? start + length : undefined) : input; },
    default(input, fallback) { return (input == null || input === '') ? fallback : input; },

    // ── Number filters ──
    toFixed(input, precision) { return input != null ? Number(input).toFixed(precision || 0) : input; },
    round(input, decimals) {
      if (input == null) return input;
      const f = Math.pow(10, decimals || 0);
      return Math.round(Number(input) * f) / f;
    },
    currency(input, symbol, decimals) {
      if (input == null) return input;
      const s = symbol || '¥';
      const d = decimals != null ? decimals : 2;
      return s + Number(input).toFixed(d);
    },
    percent(input, decimals) {
      if (input == null) return input;
      return (Number(input) * 100).toFixed(decimals != null ? decimals : 0) + '%';
    },
    abs(input) { return input != null ? Math.abs(Number(input)) : input; },

    // ── Date filters ──
    dateFormat(input, format) {
      if (!input) return input;
      const d = new Date(input);
      if (isNaN(d.getTime())) return input;
      const fmt = format || 'YYYY-MM-DD';
      const pad = (n) => String(n).padStart(2, '0');
      return fmt
        .replace('YYYY', d.getFullYear())
        .replace('MM', pad(d.getMonth() + 1))
        .replace('DD', pad(d.getDate()))
        .replace('HH', pad(d.getHours()))
        .replace('mm', pad(d.getMinutes()))
        .replace('ss', pad(d.getSeconds()));
    },

    // ── Array filters ──
    join(input, separator) { return Array.isArray(input) ? input.filter(v => v != null).join(separator || ', ') : input; },
    joinBy(input, field, separator) {
      if (!Array.isArray(input)) return input;
      return input.map(item => item && item[field]).filter(v => v != null).join(separator || ', ');
    },
    sumBy(input, field) {
      if (!Array.isArray(input)) return input;
      return input.reduce((sum, item) => sum + (Number(item && item[field]) || 0), 0);
    },
    avgBy(input, field) {
      if (!Array.isArray(input) || input.length === 0) return 0;
      const sum = input.reduce((s, item) => s + (Number(item && item[field]) || 0), 0);
      return sum / input.length;
    },
    minBy(input, field) {
      if (!Array.isArray(input) || input.length === 0) return null;
      return Math.min(...input.map(item => Number(item && item[field]) || 0));
    },
    maxBy(input, field) {
      if (!Array.isArray(input) || input.length === 0) return null;
      return Math.max(...input.map(item => Number(item && item[field]) || 0));
    },
    sortBy(input, ...fields) {
      if (!Array.isArray(input)) return input;
      return [...input].sort((a, b) => {
        for (const f of fields) {
          const va = a && a[f], vb = b && b[f];
          if (va < vb) return -1;
          if (va > vb) return 1;
        }
        return 0;
      });
    },
    where(input, query) {
      if (!Array.isArray(input)) return input;
      return input.filter(item => expressionParser.compile(query)(item));
    },
    first(input) { return Array.isArray(input) && input.length > 0 ? input[0] : null; },
    last(input) { return Array.isArray(input) && input.length > 0 ? input[input.length - 1] : null; },
    count(input) { return Array.isArray(input) ? input.length : 0; },
    reverse(input) { return Array.isArray(input) ? [...input].reverse() : input; },
    unique(input, field) {
      if (!Array.isArray(input)) return input;
      if (!field) return [...new Set(input)];
      const seen = new Set();
      return input.filter(item => {
        const v = item && item[field];
        if (seen.has(v)) return false;
        seen.add(v);
        return true;
      });
    },
    slice(input, start, end) { return Array.isArray(input) ? input.slice(start, end) : input; },
    groupBy(input, field) {
      if (!Array.isArray(input)) return input;
      const groups = {};
      for (const item of input) {
        const key = item && item[field];
        if (!groups[key]) groups[key] = [];
        groups[key].push(item);
      }
      return Object.entries(groups).map(([key, items]) => ({ key, items }));
    },
  },
});

function createImageModule() {
  return new ImageModule({
    centered: false,
    getImage(tagValue) {
      if (typeof tagValue === 'string' && tagValue.startsWith('data:')) {
        // Base64 image
        const base64Data = tagValue.split(',')[1] || tagValue;
        return Buffer.from(base64Data, 'base64');
      }
      if (Buffer.isBuffer(tagValue)) {
        return tagValue;
      }
      // For URL-based images, the caller should pre-resolve them to buffers
      return tagValue;
    },
    getSize(img) {
      // Default size; can be overridden via tag options
      return [150, 150];
    },
  });
}

/**
 * Preprocess render data to convert flat aggregation keys (e.g., "items.$sum_price")
 * into JavaScript array properties that Docxtemplater can resolve via nested path access.
 * Recursively processes nested array elements for nested aggregation properties.
 */
function injectAggregationProperties(data) {
  for (const key of Object.keys(data)) {
    const dotIdx = key.indexOf('.$');
    if (dotIdx > 0) {
      const arrayName = key.substring(0, dotIdx);
      const propName = key.substring(dotIdx + 1);
      const arrayVal = data[arrayName];
      if (Array.isArray(arrayVal)) {
        arrayVal[propName] = data[key];
      }
      delete data[key];
    }
  }
  // Recursively process nested array elements
  for (const val of Object.values(data)) {
    if (Array.isArray(val)) {
      for (const elem of val) {
        if (elem && typeof elem === 'object' && !Array.isArray(elem)) {
          injectAggregationProperties(elem);
        }
      }
    }
  }
}

/**
 * POST /render
 * Body: {
 *   templatePath: string,       // MinIO path to .docx template
 *   data: object,               // Data context for rendering
 *   outputPath?: string,        // MinIO path for output (optional)
 *   watermark?: { type: 'text'|'image', ... } (same fields as POST /watermark image/text payloads, in-process),
 *   barcodes?: { [key]: { type: 'barcode'|'qrcode', value: string, ... } }
 * }
 */
router.post('/', async (req, res) => {
  try {
    const { templatePath, data, outputPath, watermark, barcodes } = req.body;

    if (!templatePath) {
      return res.status(400).json({
        error: { code: 'MISSING_TEMPLATE_PATH', message: 'templatePath is required' },
      });
    }
    if (!data || typeof data !== 'object') {
      return res.status(400).json({
        error: { code: 'MISSING_DATA', message: 'data object is required' },
      });
    }

    // Fetch template from MinIO
    const templateBuffer = await getFileBuffer(templatePath);
    const zip = new PizZip(templateBuffer);

    // Prepare rendering data - resolve barcodes/QR codes to image buffers
    const renderData = { ...data };
    if (barcodes && typeof barcodes === 'object') {
      for (const [key, config] of Object.entries(barcodes)) {
        try {
          if (config.type === 'qrcode') {
            renderData[key] = await generateQRCode(config.value, config.options);
          } else {
            renderData[key] = await generateBarcode(config.value, config.format, config.options);
          }
        } catch (err) {
          console.warn(`Failed to generate barcode/qrcode for key "${key}":`, err.message);
        }
      }
    }

    // Configure Docxtemplater with angular parser
    const modules = [createImageModule()];
    const doc = new Docxtemplater(zip, {
      modules,
      paragraphLoop: true,
      linebreaks: true,
      parser,
    });

    // Render document with data (supports conditions, loops, nested loops, tables)
    injectAggregationProperties(renderData);
    doc.render(renderData);

    let outputBuffer = doc.getZip().generate({ type: 'nodebuffer' });

    // Apply watermark if configured
    if (watermark) {
      if (watermark.type === 'text') {
        outputBuffer = await applyTextWatermark(outputBuffer, watermark);
      } else if (watermark.type === 'image') {
        outputBuffer = await applyImageWatermark(outputBuffer, watermark);
      }
    }

    // Store result to MinIO if outputPath provided
    if (outputPath) {
      await putFileBuffer(outputPath, outputBuffer, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
      return res.json({
        success: true,
        outputPath,
        size: outputBuffer.length,
      });
    }

    // Return the document directly
    res.set('Content-Type', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
    res.set('Content-Disposition', 'attachment; filename="rendered.docx"');
    res.send(outputBuffer);
  } catch (err) {
    console.error('Render error:', err);
    const code = err.properties && err.properties.id ? 'TEMPLATE_RENDER_ERROR' : 'RENDER_FAILED';
    res.status(500).json({
      error: {
        code,
        message: err.message,
        details: err.properties || undefined,
      },
    });
  }
});

module.exports = router;
module.exports.injectAggregationProperties = injectAggregationProperties;
