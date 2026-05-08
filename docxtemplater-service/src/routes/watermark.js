const express = require('express');
const { applyTextWatermark, applyImageWatermark } = require('../utils/watermark');
const { badRequest } = require('../utils/http-errors');

const router = express.Router();

router.post('/', async (req, res) => {
  try {
    const body = req.body || {};
    const { document, type } = body;

    if (!document || typeof document !== 'string') {
      return badRequest(res, 'MISSING_DOCUMENT', 'document (base64 .docx) is required');
    }

    if (type !== 'text' && type !== 'image') {
      return badRequest(res, 'INVALID_WATERMARK_TYPE', 'type must be "text" or "image"');
    }

    const docBuffer = Buffer.from(document.trim(), 'base64');

    if (!docBuffer.length) {
      return badRequest(res, 'EMPTY_DOCUMENT', 'decoded document is empty');
    }

    if (type === 'text') {
      const { text, fontSize, color, opacity, rotation } = body;
      if (!text || typeof text !== 'string' || !text.trim()) {
        return badRequest(res, 'MISSING_TEXT', 'text is required for text watermark');
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
      return badRequest(
        res,
        'MISSING_IMAGE',
        'imageBase64 or imageSource with inline base64 (or data: URI) is required',
      );
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
      return badRequest(res, err.code, err.message);
    }
    console.error('Watermark error:', err);
    return res.status(500).json({
      error: { code: 'WATERMARK_FAILED', message: err.message || 'Watermark failed' },
    });
  }
});

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
