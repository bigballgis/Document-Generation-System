const express = require('express');
const router = express.Router();
const Docxtemplater = require('docxtemplater');
const PizZip = require('pizzip');
const { parser, createImageModule } = require('../docx-templater-config');
const { getFileBuffer, putFileBuffer } = require('../minio-client');
const { generateBarcode, generateQRCode } = require('../utils/barcode');
const { applyTextWatermark, applyImageWatermark } = require('../utils/watermark');
const { rewriteLegacyIfTagsInZip } = require('../utils/legacy-if-tags');
const { badRequest } = require('../utils/http-errors');

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

router.post('/', async (req, res) => {
  try {
    const { templatePath, data, outputPath, watermark, barcodes } = req.body;

    if (!templatePath) {
      return badRequest(res, 'MISSING_TEMPLATE_PATH', 'templatePath is required');
    }
    if (!data || typeof data !== 'object') {
      return badRequest(res, 'MISSING_DATA', 'data object is required');
    }

    const templateBuffer = await getFileBuffer(templatePath);
    const zip = rewriteLegacyIfTagsInZip(new PizZip(templateBuffer));

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

    injectAggregationProperties(renderData);

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

    if (watermark) {
      if (watermark.type === 'text') {
        outputBuffer = await applyTextWatermark(outputBuffer, watermark);
      } else if (watermark.type === 'image') {
        outputBuffer = await applyImageWatermark(outputBuffer, watermark);
      }
    }

    if (outputPath) {
      await putFileBuffer(outputPath, outputBuffer, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
      return res.json({
        success: true,
        outputPath,
        size: outputBuffer.length,
      });
    }

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
