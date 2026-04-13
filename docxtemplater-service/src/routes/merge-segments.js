const express = require('express');
const router = express.Router();
const PizZip = require('pizzip');

/**
 * Extract the body content (paragraphs, tables, etc.) from a .docx document.xml.
 * Returns the inner XML of <w:body> excluding the final <w:sectPr> element.
 */
function extractBodyContent(zip) {
  const docXml = zip.file('word/document.xml').asText();
  const bodyStart = docXml.indexOf('<w:body>');
  const bodyEnd = docXml.indexOf('</w:body>');
  if (bodyStart === -1 || bodyEnd === -1) {
    throw new Error('Invalid .docx: missing <w:body> element');
  }

  let inner = docXml.substring(bodyStart + '<w:body>'.length, bodyEnd);

  // Remove trailing <w:sectPr ...>...</w:sectPr> (section properties belong to the last segment only)
  const sectPrMatch = inner.match(/<w:sectPr[\s\S]*<\/w:sectPr>\s*$/);
  if (sectPrMatch) {
    inner = inner.substring(0, inner.length - sectPrMatch[0].length);
  }

  return inner.trim();
}

/**
 * Extract the final <w:sectPr> from a document body.
 */
function extractSectionProperties(zip) {
  const docXml = zip.file('word/document.xml').asText();
  const bodyStart = docXml.indexOf('<w:body>');
  const bodyEnd = docXml.indexOf('</w:body>');
  if (bodyStart === -1 || bodyEnd === -1) return '';

  const bodyContent = docXml.substring(bodyStart + '<w:body>'.length, bodyEnd);
  const sectPrMatch = bodyContent.match(/<w:sectPr[\s\S]*<\/w:sectPr>\s*$/);
  return sectPrMatch ? sectPrMatch[0] : '';
}

/**
 * Generate a page break paragraph in Open XML.
 */
function pageBreakXml() {
  return '<w:p><w:r><w:br w:type="page"/></w:r></w:p>';
}

/**
 * POST /merge-segments
 * Body: {
 *   segments: [
 *     { buffer: "base64-encoded .docx", pageBreakBefore: boolean },
 *     ...
 *   ]
 * }
 *
 * Merges multiple .docx segment files into a single .docx document.
 * Uses the first segment as the base document (preserving styles, fonts, etc.)
 * and appends body content from subsequent segments.
 */
router.post('/', async (req, res) => {
  try {
    const { segments } = req.body;

    if (!Array.isArray(segments) || segments.length === 0) {
      return res.status(400).json({
        error: { code: 'MISSING_SEGMENTS', message: 'segments array is required and must not be empty' },
      });
    }

    // Validate all segments have buffer
    for (let i = 0; i < segments.length; i++) {
      if (!segments[i].buffer || typeof segments[i].buffer !== 'string') {
        return res.status(400).json({
          error: {
            code: 'INVALID_SEGMENT',
            message: `segments[${i}].buffer is required and must be a base64 string`,
          },
        });
      }
    }

    // Decode all segment buffers
    const segmentZips = segments.map((seg, i) => {
      try {
        const buf = Buffer.from(seg.buffer, 'base64');
        return new PizZip(buf);
      } catch (err) {
        throw Object.assign(new Error(`Failed to parse segment[${i}] as .docx: ${err.message}`), { segmentIndex: i });
      }
    });

    // Use the first segment as the base document (preserves styles, numbering, fonts, etc.)
    const baseZip = segmentZips[0];
    const bodyParts = [];

    // Extract body content from each segment
    for (let i = 0; i < segmentZips.length; i++) {
      const content = extractBodyContent(segmentZips[i]);
      if (!content) continue;

      // Insert page break before this segment if requested (skip for the first segment)
      if (i > 0 && segments[i].pageBreakBefore) {
        bodyParts.push(pageBreakXml());
      }

      bodyParts.push(content);
    }

    // Get section properties from the last segment (or first if only one)
    const lastZip = segmentZips[segmentZips.length - 1];
    const sectionProps = extractSectionProperties(lastZip);

    // Reconstruct the document.xml
    const originalDocXml = baseZip.file('word/document.xml').asText();
    const docStart = originalDocXml.substring(0, originalDocXml.indexOf('<w:body>') + '<w:body>'.length);
    const docEnd = '</w:body>' + originalDocXml.substring(originalDocXml.indexOf('</w:body>') + '</w:body>'.length);

    const mergedDocXml = docStart + bodyParts.join('') + sectionProps + docEnd;

    // Update the base zip with merged content
    baseZip.file('word/document.xml', mergedDocXml);

    // Generate the merged .docx buffer
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
