/**
 * Tests for POST /merge-segments endpoint
 *
 * Validates: Requirements 8.2, 8.3
 */

const PizZip = require('pizzip');

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Create a minimal valid .docx buffer with given text content.
 */
function createTestDocx(content) {
  const docXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:wpc="http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas"
            xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"
            xmlns:o="urn:schemas-microsoft-com:office:office"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"
            xmlns:v="urn:schemas-microsoft-com:vml"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:w10="urn:schemas-microsoft-com:office:word"
            xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml"
            xmlns:wpg="http://schemas.microsoft.com/office/word/2010/wordprocessingGroup"
            xmlns:wpi="http://schemas.microsoft.com/office/word/2010/wordprocessingInk"
            xmlns:wne="http://schemas.microsoft.com/office/word/2006/wordml"
            xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"
            mc:Ignorable="w14 wp14">
  <w:body>
    <w:p><w:r><w:t>${content}</w:t></w:r></w:p>
    <w:sectPr><w:pgSz w:w="12240" w:h="15840"/></w:sectPr>
  </w:body>
</w:document>`;

  const contentTypesXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>`;

  const relsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>`;

  const wordRelsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
</Relationships>`;

  const zip = new PizZip();
  zip.file('[Content_Types].xml', contentTypesXml);
  zip.file('_rels/.rels', relsXml);
  zip.file('word/_rels/document.xml.rels', wordRelsXml);
  zip.file('word/document.xml', docXml);

  return zip.generate({ type: 'nodebuffer' });
}

/**
 * Create a .docx with multiple paragraphs.
 */
function createMultiParagraphDocx(paragraphs) {
  const pXml = paragraphs.map(p => `<w:p><w:r><w:t>${p}</w:t></w:r></w:p>`).join('');
  const docXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            mc:Ignorable="w14 wp14">
  <w:body>
    ${pXml}
    <w:sectPr><w:pgSz w:w="12240" w:h="15840"/></w:sectPr>
  </w:body>
</w:document>`;

  const contentTypesXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>`;

  const relsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>`;

  const wordRelsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
</Relationships>`;

  const zip = new PizZip();
  zip.file('[Content_Types].xml', contentTypesXml);
  zip.file('_rels/.rels', relsXml);
  zip.file('word/_rels/document.xml.rels', wordRelsXml);
  zip.file('word/document.xml', docXml);

  return zip.generate({ type: 'nodebuffer' });
}

// ── Mock MinIO ───────────────────────────────────────────────────────────────

jest.mock('../minio-client', () => ({
  minioClient: {
    listBuckets: jest.fn().mockResolvedValue([{ name: 'docgen' }]),
  },
  BUCKET_NAME: 'docgen',
  ensureBucket: jest.fn().mockResolvedValue(undefined),
  getFileBuffer: jest.fn().mockRejectedValue(new Error('Not used in merge')),
  putFileBuffer: jest.fn().mockResolvedValue(undefined),
}));

const app = require('../../server');
const http = require('http');

let server;
let baseUrl;

beforeAll((done) => {
  server = app.listen(0, () => {
    const addr = server.address();
    baseUrl = `http://127.0.0.1:${addr.port}`;
    done();
  });
});

afterAll((done) => {
  if (server) server.close(done);
  else done();
});

/**
 * HTTP request helper.
 */
function request(method, path, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, baseUrl);
    const bodyStr = body ? JSON.stringify(body) : null;
    const options = {
      method,
      hostname: url.hostname,
      port: url.port,
      path: url.pathname,
      headers: {
        'Content-Type': 'application/json',
        ...(bodyStr ? { 'Content-Length': Buffer.byteLength(bodyStr) } : {}),
      },
    };

    const req = http.request(options, (res) => {
      const chunks = [];
      res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => {
        const rawBody = Buffer.concat(chunks);
        let parsed;
        try {
          parsed = JSON.parse(rawBody.toString());
        } catch {
          parsed = rawBody;
        }
        resolve({ status: res.statusCode, headers: res.headers, body: parsed, rawBody });
      });
    });

    req.on('error', reject);
    if (bodyStr) req.write(bodyStr);
    req.end();
  });
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('POST /merge-segments', () => {
  describe('input validation', () => {
    it('should return 400 when segments is missing', async () => {
      const res = await request('POST', '/merge-segments', {});
      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('MISSING_SEGMENTS');
    });

    it('should return 400 when segments is empty array', async () => {
      const res = await request('POST', '/merge-segments', { segments: [] });
      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('MISSING_SEGMENTS');
    });

    it('should return 400 when segment buffer is missing', async () => {
      const res = await request('POST', '/merge-segments', {
        segments: [{ pageBreakBefore: false }],
      });
      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('INVALID_SEGMENT');
    });

    it('should return 400 when segment buffer is not a string', async () => {
      const res = await request('POST', '/merge-segments', {
        segments: [{ buffer: 123, pageBreakBefore: false }],
      });
      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('INVALID_SEGMENT');
    });
  });

  describe('single segment merge', () => {
    it('should return the single segment as-is', async () => {
      const docx = createTestDocx('Single Segment Content');
      const res = await request('POST', '/merge-segments', {
        segments: [{ buffer: docx.toString('base64'), pageBreakBefore: false }],
      });

      expect(res.status).toBe(200);
      expect(res.headers['content-type']).toContain(
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
      );

      // Parse the output and verify content
      const outputZip = new PizZip(res.rawBody);
      const docXml = outputZip.file('word/document.xml').asText();
      expect(docXml).toContain('Single Segment Content');
    });
  });

  describe('multi-segment merge', () => {
    it('should merge two segments preserving content order', async () => {
      const docx1 = createTestDocx('First Segment');
      const docx2 = createTestDocx('Second Segment');

      const res = await request('POST', '/merge-segments', {
        segments: [
          { buffer: docx1.toString('base64'), pageBreakBefore: false },
          { buffer: docx2.toString('base64'), pageBreakBefore: false },
        ],
      });

      expect(res.status).toBe(200);
      const outputZip = new PizZip(res.rawBody);
      const docXml = outputZip.file('word/document.xml').asText();
      expect(docXml).toContain('First Segment');
      expect(docXml).toContain('Second Segment');

      // Verify order: First should appear before Second
      const firstIdx = docXml.indexOf('First Segment');
      const secondIdx = docXml.indexOf('Second Segment');
      expect(firstIdx).toBeLessThan(secondIdx);
    });

    it('should merge three segments in correct order', async () => {
      const docx1 = createTestDocx('Alpha');
      const docx2 = createTestDocx('Beta');
      const docx3 = createTestDocx('Gamma');

      const res = await request('POST', '/merge-segments', {
        segments: [
          { buffer: docx1.toString('base64'), pageBreakBefore: false },
          { buffer: docx2.toString('base64'), pageBreakBefore: false },
          { buffer: docx3.toString('base64'), pageBreakBefore: false },
        ],
      });

      expect(res.status).toBe(200);
      const outputZip = new PizZip(res.rawBody);
      const docXml = outputZip.file('word/document.xml').asText();

      const alphaIdx = docXml.indexOf('Alpha');
      const betaIdx = docXml.indexOf('Beta');
      const gammaIdx = docXml.indexOf('Gamma');
      expect(alphaIdx).toBeLessThan(betaIdx);
      expect(betaIdx).toBeLessThan(gammaIdx);
    });

    it('should merge segments with multiple paragraphs each', async () => {
      const docx1 = createMultiParagraphDocx(['Para1A', 'Para1B']);
      const docx2 = createMultiParagraphDocx(['Para2A', 'Para2B']);

      const res = await request('POST', '/merge-segments', {
        segments: [
          { buffer: docx1.toString('base64'), pageBreakBefore: false },
          { buffer: docx2.toString('base64'), pageBreakBefore: false },
        ],
      });

      expect(res.status).toBe(200);
      const outputZip = new PizZip(res.rawBody);
      const docXml = outputZip.file('word/document.xml').asText();
      expect(docXml).toContain('Para1A');
      expect(docXml).toContain('Para1B');
      expect(docXml).toContain('Para2A');
      expect(docXml).toContain('Para2B');

      // Verify order
      expect(docXml.indexOf('Para1B')).toBeLessThan(docXml.indexOf('Para2A'));
    });
  });

  describe('page break insertion', () => {
    it('should insert page break before segment when pageBreakBefore is true', async () => {
      const docx1 = createTestDocx('Page One');
      const docx2 = createTestDocx('Page Two');

      const res = await request('POST', '/merge-segments', {
        segments: [
          { buffer: docx1.toString('base64'), pageBreakBefore: false },
          { buffer: docx2.toString('base64'), pageBreakBefore: true },
        ],
      });

      expect(res.status).toBe(200);
      const outputZip = new PizZip(res.rawBody);
      const docXml = outputZip.file('word/document.xml').asText();

      // Should contain a page break element
      expect(docXml).toContain('<w:br w:type="page"/>');

      // Page break should be between the two segments
      const pageOneIdx = docXml.indexOf('Page One');
      const breakIdx = docXml.indexOf('<w:br w:type="page"/>');
      const pageTwoIdx = docXml.indexOf('Page Two');
      expect(pageOneIdx).toBeLessThan(breakIdx);
      expect(breakIdx).toBeLessThan(pageTwoIdx);
    });

    it('should not insert page break when pageBreakBefore is false', async () => {
      const docx1 = createTestDocx('No Break One');
      const docx2 = createTestDocx('No Break Two');

      const res = await request('POST', '/merge-segments', {
        segments: [
          { buffer: docx1.toString('base64'), pageBreakBefore: false },
          { buffer: docx2.toString('base64'), pageBreakBefore: false },
        ],
      });

      expect(res.status).toBe(200);
      const outputZip = new PizZip(res.rawBody);
      const docXml = outputZip.file('word/document.xml').asText();
      expect(docXml).not.toContain('<w:br w:type="page"/>');
    });

    it('should ignore pageBreakBefore on the first segment', async () => {
      const docx1 = createTestDocx('First With Break Flag');

      const res = await request('POST', '/merge-segments', {
        segments: [
          { buffer: docx1.toString('base64'), pageBreakBefore: true },
        ],
      });

      expect(res.status).toBe(200);
      const outputZip = new PizZip(res.rawBody);
      const docXml = outputZip.file('word/document.xml').asText();
      // No page break should be inserted for the first (and only) segment
      expect(docXml).not.toContain('<w:br w:type="page"/>');
    });

    it('should handle mixed page break settings across multiple segments', async () => {
      const docx1 = createTestDocx('Seg1');
      const docx2 = createTestDocx('Seg2');
      const docx3 = createTestDocx('Seg3');
      const docx4 = createTestDocx('Seg4');

      const res = await request('POST', '/merge-segments', {
        segments: [
          { buffer: docx1.toString('base64'), pageBreakBefore: false },
          { buffer: docx2.toString('base64'), pageBreakBefore: true },
          { buffer: docx3.toString('base64'), pageBreakBefore: false },
          { buffer: docx4.toString('base64'), pageBreakBefore: true },
        ],
      });

      expect(res.status).toBe(200);
      const outputZip = new PizZip(res.rawBody);
      const docXml = outputZip.file('word/document.xml').asText();

      // Count page breaks — should be exactly 2 (before Seg2 and Seg4)
      const breakCount = (docXml.match(/<w:br w:type="page"\/>/g) || []).length;
      expect(breakCount).toBe(2);
    });
  });

  describe('output format', () => {
    it('should return a valid .docx file', async () => {
      const docx1 = createTestDocx('Valid Output');
      const res = await request('POST', '/merge-segments', {
        segments: [{ buffer: docx1.toString('base64'), pageBreakBefore: false }],
      });

      expect(res.status).toBe(200);

      // Should be parseable as a zip/docx
      const outputZip = new PizZip(res.rawBody);
      expect(outputZip.file('word/document.xml')).toBeTruthy();
      expect(outputZip.file('[Content_Types].xml')).toBeTruthy();
    });

    it('should preserve section properties from the last segment', async () => {
      const docx1 = createTestDocx('First');
      const docx2 = createTestDocx('Last');

      const res = await request('POST', '/merge-segments', {
        segments: [
          { buffer: docx1.toString('base64'), pageBreakBefore: false },
          { buffer: docx2.toString('base64'), pageBreakBefore: false },
        ],
      });

      expect(res.status).toBe(200);
      const outputZip = new PizZip(res.rawBody);
      const docXml = outputZip.file('word/document.xml').asText();
      // Should contain sectPr (section properties)
      expect(docXml).toContain('<w:sectPr>');
    });
  });

  describe('error handling', () => {
    it('should return 500 for invalid base64 content', async () => {
      const res = await request('POST', '/merge-segments', {
        segments: [{ buffer: 'not-valid-base64-docx!!!', pageBreakBefore: false }],
      });

      expect(res.status).toBe(500);
      expect(res.body.error).toBeDefined();
    });
  });
});
