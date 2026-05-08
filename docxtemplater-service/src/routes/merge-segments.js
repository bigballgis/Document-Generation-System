const express = require('express');
const router = express.Router();
const PizZip = require('pizzip');
const { badRequest } = require('../utils/http-errors');

function extractBodyContent(zip) {
  const docXml = zip.file('word/document.xml').asText();
  const bodyStart = docXml.indexOf('<w:body>');
  const bodyEnd = docXml.indexOf('</w:body>');
  if (bodyStart === -1 || bodyEnd === -1) {
    throw new Error('Invalid .docx: missing <w:body> element');
  }

  let inner = docXml.substring(bodyStart + '<w:body>'.length, bodyEnd);

  const sectPrMatch = inner.match(/<w:sectPr[\s\S]*<\/w:sectPr>\s*$/);
  if (sectPrMatch) {
    inner = inner.substring(0, inner.length - sectPrMatch[0].length);
  }

  return inner.trim();
}

function extractSectionProperties(zip) {
  const docXml = zip.file('word/document.xml').asText();
  const bodyStart = docXml.indexOf('<w:body>');
  const bodyEnd = docXml.indexOf('</w:body>');
  if (bodyStart === -1 || bodyEnd === -1) return '';

  const bodyContent = docXml.substring(bodyStart + '<w:body>'.length, bodyEnd);
  const sectPrMatch = bodyContent.match(/<w:sectPr[\s\S]*<\/w:sectPr>\s*$/);
  return sectPrMatch ? sectPrMatch[0] : '';
}

function pageBreakXml() {
  return '<w:p><w:r><w:br w:type="page"/></w:r></w:p>';
}

router.post('/', async (req, res) => {
  try {
    const { segments } = req.body;

    if (!Array.isArray(segments) || segments.length === 0) {
      return badRequest(res, 'MISSING_SEGMENTS', 'segments array is required and must not be empty');
    }

    for (let i = 0; i < segments.length; i++) {
      if (!segments[i].buffer || typeof segments[i].buffer !== 'string') {
        return badRequest(
          res,
          'INVALID_SEGMENT',
          `segments[${i}].buffer is required and must be a base64 string`,
        );
      }
    }

    const segmentZips = segments.map((seg, i) => {
      try {
        const buf = Buffer.from(seg.buffer, 'base64');
        return new PizZip(buf);
      } catch (err) {
        throw Object.assign(new Error(`Failed to parse segment[${i}] as .docx: ${err.message}`), { segmentIndex: i });
      }
    });

    const baseZip = segmentZips[0];
    const bodyParts = [];

    for (let i = 0; i < segmentZips.length; i++) {
      const content = extractBodyContent(segmentZips[i]);
      if (!content) continue;

      if (i > 0 && segments[i].pageBreakBefore) {
        bodyParts.push(pageBreakXml());
      }

      bodyParts.push(content);
    }

    const lastZip = segmentZips[segmentZips.length - 1];
    const sectionProps = extractSectionProperties(lastZip);

    const originalDocXml = baseZip.file('word/document.xml').asText();
    const docStart = originalDocXml.substring(0, originalDocXml.indexOf('<w:body>') + '<w:body>'.length);
    const docEnd = '</w:body>' + originalDocXml.substring(originalDocXml.indexOf('</w:body>') + '</w:body>'.length);

    const mergedDocXml = docStart + bodyParts.join('') + sectionProps + docEnd;

    baseZip.file('word/document.xml', mergedDocXml);

    const outputBuffer = baseZip.generate({ type: 'nodebuffer' });

    res.set('Content-Type', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
    res.set('Content-Disposition', 'attachment; filename="merged.docx"');
    res.send(outputBuffer);
  } catch (err) {
    console.error('Merge segments error:', err);
    const code = err.segmentIndex !== undefined ? 'SEGMENT_PARSE_ERROR' : 'MERGE_FAILED';
    res.status(500).json({
      error: {
        code,
        message: err.message,
        segmentIndex: err.segmentIndex,
      },
    });
  }
});

module.exports = router;
