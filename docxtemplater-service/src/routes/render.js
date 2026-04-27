const express = require('express');
const router = express.Router();
const Docxtemplater = require('docxtemplater');
const PizZip = require('pizzip');
const { parser, createImageModule } = require('../docx-templater-config');
const { getFileBuffer, putFileBuffer } = require('../minio-client');
const { generateBarcode, generateQRCode } = require('../utils/barcode');
const { applyTextWatermark, applyImageWatermark } = require('../utils/watermark');
const { rewriteLegacyIfTagsInZip } = require('../utils/legacy-if-tags');

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
    const zip = rewriteLegacyIfTagsInZip(new PizZip(templateBuffer));

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

    function buildDoc(modules) {
      return new Docxtemplater(zip, {
        modules,
        paragraphLoop: true,
        linebreaks: true,
        parser,
      });
    }

    function isMalformedImageTagError(err) {
      const props = err && err.properties;
      const id = props && props.id;
      if (id === 'raw_tag_outerxml_invalid' || id === 'no_xml_tag_found_at_left') return true;
      if (id !== 'multi_error') return false;
      const errors = Array.isArray(props.errors) ? props.errors : [];
      for (const e of errors) {
        const eid = e && e.properties && e.properties.id;
        const rootId = e && e.properties && e.properties.rootError && e.properties.rootError.properties
          ? e.properties.rootError.properties.id
          : undefined;
        if (eid === 'raw_tag_outerxml_invalid' || eid === 'no_xml_tag_found_at_left') return true;
        if (rootId === 'raw_tag_outerxml_invalid' || rootId === 'no_xml_tag_found_at_left') return true;
      }
      return false;
    }

    // Render document with data (supports conditions, loops, nested loops, tables)
    injectAggregationProperties(renderData);

    // Prefer image module, but fall back when templates contain malformed image placeholders
    // (e.g. raw tag not in paragraph). This keeps demos usable while allowing gradual cleanup.
    let doc;
    try {
      doc = buildDoc([createImageModule()]);
      doc.render(renderData);
    } catch (err) {
      if (!isMalformedImageTagError(err)) {
        throw err;
      }
      console.warn('Render retry without image module due to malformed image tag:', err.message);
      doc = buildDoc([]);
      doc.render(renderData);
    }

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
