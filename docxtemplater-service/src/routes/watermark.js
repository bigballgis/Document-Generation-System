const express = require('express');
const { applyTextWatermark, applyImageWatermark } = require('../utils/watermark');

const router = express.Router();

/**
 * POST /watermark
 * Body (Java WatermarkService contract):
 * {
 *   document: string,   // base64-encoded .docx
 *   type: 'text' | 'image',
 *   // text:
 *   text, fontSize?, color?, opacity?, rotation?
 *   // image (inline base64 only; data URIs allowed):
 *   imageSource or imageBase64, opacity?, position?  // position is accepted for API compatibility; layout is always centered in util
 * }
 *
 * Remote HTTP(S) image URLs are rejected (SSRF-safe contract with backend validation).
 */
router.post('/', async (req, res) => {
  try {
    const body = req.body || {};
    const { document, type } = body;

    if (!document || typeof document !== 'string') {
      return res.status(400).json({
        error: { code: 'MISSING_DOCUMENT', message: 'document (base64 .docx) is required' },
      });
    }

    if (type !== 'text' && type !== 'image') {
      return res.status(400).json({
        error: { code: 'INVALID_WATERMARK_TYPE', message: 'type must be "text" or "image"' },
      });
    }

    const docBuffer = Buffer.from(document.trim(), 'base64');

    if (!docBuffer.length) {
      return res.status(400).json({
        error: { code: 'EMPTY_DOCUMENT', message: 'decoded document is empty' },
      });
    }

    if (type === 'text') {
      const { text, fontSize, color, opacity, rotation } = body;
      if (!text || typeof text !== 'string' || !text.trim()) {
        return res.status(400).json({
          error: { code: 'MISSING_TEXT', message: 'text is required for text watermark' },
        });
      }
      const out = await applyTextWatermark(docBuffer, {
        text: text.trim(),
        fontSize,
        color,
        opacity,
        rotation,
      });
      res.set('Content-Type', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
      res.set('Content-Disposition', 'attachment; filename="watermarked.docx"');
      return res.send(out);
    }

    const rawImage = extractInlineImageBase64(body.imageBase64 || body.imageSource);
    if (rawImage == null) {
      return res.status(400).json({
        error: {
          code: 'MISSING_IMAGE',
          message: 'imageBase64 or imageSource with inline base64 (or data: URI) is required',
        },
      });
    }

    const out = await applyImageWatermark(docBuffer, {
      imageBase64: rawImage,
      opacity: typeof body.opacity === 'number' ? body.opacity : undefined,
    });
    res.set('Content-Type', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
    res.set('Content-Disposition', 'attachment; filename="watermarked.docx"');
    return res.send(out);
  } catch (err) {
    if (err && err.code === 'WATERMARK_IMAGE_URL_REJECTED') {
      return res.status(400).json({
        error: { code: err.code, message: err.message },
      });
    }
    console.error('Watermark error:', err);
    return res.status(500).json({
      error: { code: 'WATERMARK_FAILED', message: err.message || 'Watermark failed' },
    });
  }
});

/**
 * @param {string|undefined} value
 * @returns {string|null} raw base64 payload without data URI prefix
 */
function extractInlineImageBase64(value) {
  if (value == null || typeof value !== 'string') {
    return null;
  }
  const t = value.trim();
  if (!t) {
    return null;
  }
  if (/^https?:\/\//i.test(t)) {
    const e = new Error('Remote image URLs are not supported for watermark');
    e.code = 'WATERMARK_IMAGE_URL_REJECTED';
    throw e;
  }
  const m = /^data:image\/[^;]+;base64,/i.exec(t);
  if (m) {
    return t.slice(m[0].length);
  }
  return t;
}

module.exports = router;
