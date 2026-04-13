/**
 * Integration tests for Docxtemplater Service
 *
 * Tests the HTTP endpoints: /health, /evaluate, /render, /convert-pdf
 *
 * **Validates: Requirements 6, 11, 21, 51**
 */

const express = require('express');
const Docxtemplater = require('docxtemplater');
const PizZip = require('pizzip');
const { execFile } = require('child_process');

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Create a minimal valid .docx buffer with given template content.
 * Uses docxtemplater to produce a real docx from a blank template.
 */
function createTestDocx(content) {
  // Minimal OOXML document.xml content
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
 * Check if LibreOffice is available on this system.
 */
function isLibreOfficeAvailable() {
  return new Promise((resolve) => {
    const bin = process.env.LIBREOFFICE_BIN || 'libreoffice';
    execFile(bin, ['--version'], { timeout: 5000 }, (error) => {
      resolve(!error);
    });
  });
}

// ── Mock MinIO before requiring app ──────────────────────────────────────────

// Store for mock files
const mockFileStore = new Map();

jest.mock('../minio-client', () => ({
  minioClient: {
    listBuckets: jest.fn().mockResolvedValue([{ name: 'docgen' }]),
  },
  BUCKET_NAME: 'docgen',
  ensureBucket: jest.fn().mockResolvedValue(undefined),
  getFileBuffer: jest.fn().mockImplementation(async (filePath) => {
    const buf = mockFileStore.get(filePath);
    if (!buf) throw new Error(`File not found: ${filePath}`);
    return buf;
  }),
  putFileBuffer: jest.fn().mockImplementation(async (filePath, buffer) => {
    mockFileStore.set(filePath, buffer);
  }),
}));

// Now require the app after mocks are set up
const app = require('../../server');

// Simple supertest-like helper using built-in http
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

beforeEach(() => {
  mockFileStore.clear();
});

/**
 * Helper to make HTTP requests to the test server.
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

describe('GET /health', () => {
  it('should return health status with service name', async () => {
    const res = await request('GET', '/health');
    expect(res.status).toBe(200);
    expect(res.body.service).toBe('docxtemplater-service');
    expect(res.body.status).toBeDefined();
    expect(res.body.checks).toBeDefined();
    expect(res.body.checks.minio).toBeDefined();
  });

  it('should report minio UP when listBuckets succeeds', async () => {
    const { minioClient } = require('../minio-client');
    minioClient.listBuckets.mockResolvedValueOnce([{ name: 'docgen' }]);

    const res = await request('GET', '/health');
    expect(res.status).toBe(200);
    expect(res.body.checks.minio).toBe('UP');
    expect(res.body.status).toBe('UP');
  });

  it('should report DEGRADED when minio is down', async () => {
    const { minioClient } = require('../minio-client');
    minioClient.listBuckets.mockRejectedValueOnce(new Error('Connection refused'));

    const res = await request('GET', '/health');
    expect(res.status).toBe(200);
    expect(res.body.checks.minio).toBe('DOWN');
    expect(res.body.status).toBe('DEGRADED');
  });
});


describe('POST /evaluate', () => {
  describe('valid JavaScript expressions', () => {
    it('should evaluate simple arithmetic', async () => {
      const res = await request('POST', '/evaluate', {
        expression: '1 + 2 + 3',
        context: {},
      });
      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.result).toBe(6);
    });

    it('should evaluate expressions with data context', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'data.price * data.quantity',
        context: { price: 10.5, quantity: 3 },
      });
      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.result).toBe(31.5);
    });

    it('should evaluate string operations', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'data.firstName + " " + data.lastName',
        context: { firstName: 'John', lastName: 'Doe' },
      });
      expect(res.status).toBe(200);
      expect(res.body.result).toBe('John Doe');
    });

    it('should evaluate nested object access', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'data.user.address.city',
        context: { user: { address: { city: 'Beijing' } } },
      });
      expect(res.status).toBe(200);
      expect(res.body.result).toBe('Beijing');
    });

    it('should evaluate array operations', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'data.items.length',
        context: { items: [1, 2, 3, 4, 5] },
      });
      expect(res.status).toBe(200);
      expect(res.body.result).toBe(5);
    });

    it('should evaluate boolean logic', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'data.age >= 18 && data.active',
        context: { age: 25, active: true },
      });
      expect(res.status).toBe(200);
      expect(res.body.result).toBe(true);
    });

    it('should evaluate ternary expressions', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'data.score >= 60 ? "Pass" : "Fail"',
        context: { score: 75 },
      });
      expect(res.status).toBe(200);
      expect(res.body.result).toBe('Pass');
    });
  });

  describe('security sandbox - forbidden module access', () => {
    it('should reject require("fs")', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'require("fs").readFileSync("/etc/passwd")',
        context: {},
      });
      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('SANDBOX_SECURITY_VIOLATION');
    });

    it('should reject require("http")', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'require("http").get("http://evil.com")',
        context: {},
      });
      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('SANDBOX_SECURITY_VIOLATION');
    });

    it('should reject require("child_process")', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'require("child_process").exec("rm -rf /")',
        context: {},
      });
      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('SANDBOX_SECURITY_VIOLATION');
    });

    it('should reject require("net")', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'require("net").connect(80, "evil.com")',
        context: {},
      });
      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('SANDBOX_SECURITY_VIOLATION');
    });

    it('should reject process access', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'process.env.SECRET_KEY',
        context: {},
      });
      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('SANDBOX_SECURITY_VIOLATION');
    });

    it('should reject eval()', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'eval("1+1")',
        context: {},
      });
      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('SANDBOX_SECURITY_VIOLATION');
    });

    it('should reject Function constructor', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'new Function("return 1")()',
        context: {},
      });
      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('SANDBOX_SECURITY_VIOLATION');
    });

    it('should reject require("https")', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'require("https").get("https://evil.com")',
        context: {},
      });
      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('SANDBOX_SECURITY_VIOLATION');
    });
  });

  describe('expression timeout handling', () => {
    it('should timeout on infinite loops with short timeout', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'while(true){}',
        context: {},
        timeout: 200,
      });
      // Should return 408 (timeout) or 500 (execution error)
      expect([408, 500]).toContain(res.status);
    });
  });

  describe('Excel formula evaluation via Formula.js', () => {
    it('should evaluate SUM formula', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'SUM(1, 2, 3, 4, 5)',
        type: 'excel',
        context: {},
      });
      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.result).toBe(15);
    });

    it('should evaluate AVERAGE formula', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'AVERAGE(10, 20, 30)',
        type: 'excel',
        context: {},
      });
      expect(res.status).toBe(200);
      expect(res.body.result).toBe(20);
    });

    it('should evaluate IF formula', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'IF(TRUE, "yes", "no")',
        type: 'excel',
        context: {},
      });
      expect(res.status).toBe(200);
      expect(res.body.result).toBe('yes');
    });

    it('should evaluate CONCATENATE formula', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'CONCATENATE("Hello", " ", "World")',
        type: 'excel',
        context: {},
      });
      expect(res.status).toBe(200);
      expect(res.body.result).toBe('Hello World');
    });

    it('should evaluate MAX and MIN formulas', async () => {
      const resMax = await request('POST', '/evaluate', {
        expression: 'MAX(5, 3, 8, 1)',
        type: 'excel',
        context: {},
      });
      expect(resMax.status).toBe(200);
      expect(resMax.body.result).toBe(8);

      const resMin = await request('POST', '/evaluate', {
        expression: 'MIN(5, 3, 8, 1)',
        type: 'excel',
        context: {},
      });
      expect(resMin.status).toBe(200);
      expect(resMin.body.result).toBe(1);
    });

    it('should return error for unsupported formula', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 'NONEXISTENT(1, 2)',
        type: 'excel',
        context: {},
      });
      expect(res.status).toBe(500);
      expect(res.body.error).toBeDefined();
    });
  });

  describe('input validation', () => {
    it('should return 400 when expression is missing', async () => {
      const res = await request('POST', '/evaluate', {
        context: {},
      });
      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('MISSING_EXPRESSION');
    });

    it('should return 400 when expression is not a string', async () => {
      const res = await request('POST', '/evaluate', {
        expression: 123,
        context: {},
      });
      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('MISSING_EXPRESSION');
    });
  });
});


describe('POST /render', () => {
  describe('simple variable substitution', () => {
    it('should render a template with simple variables', async () => {
      // Create a test template with {name} variable
      const templateBuffer = createTestDocx('{name}');
      mockFileStore.set('templates/test-simple.docx', templateBuffer);

      const res = await request('POST', '/render', {
        templatePath: 'templates/test-simple.docx',
        data: { name: 'Alice' },
        outputPath: 'output/test-simple-rendered.docx',
      });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.outputPath).toBe('output/test-simple-rendered.docx');
      expect(res.body.size).toBeGreaterThan(0);

      // Verify the output was stored
      const outputBuffer = mockFileStore.get('output/test-simple-rendered.docx');
      expect(outputBuffer).toBeDefined();

      // Verify the rendered content contains the substituted value
      const zip = new PizZip(outputBuffer);
      const docXml = zip.file('word/document.xml').asText();
      expect(docXml).toContain('Alice');
      expect(docXml).not.toContain('{name}');
    });

    it('should render multiple variables', async () => {
      const templateBuffer = createTestDocx('{firstName} {lastName} - {company}');
      mockFileStore.set('templates/test-multi.docx', templateBuffer);

      const res = await request('POST', '/render', {
        templatePath: 'templates/test-multi.docx',
        data: { firstName: 'Bob', lastName: 'Smith', company: 'Acme Corp' },
        outputPath: 'output/test-multi-rendered.docx',
      });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);

      const outputBuffer = mockFileStore.get('output/test-multi-rendered.docx');
      const zip = new PizZip(outputBuffer);
      const docXml = zip.file('word/document.xml').asText();
      expect(docXml).toContain('Bob');
      expect(docXml).toContain('Smith');
      expect(docXml).toContain('Acme Corp');
    });
  });

  describe('conditional rendering', () => {
    it('should render content when condition is true', async () => {
      const templateBuffer = createTestDocx('{#showGreeting}Hello {name}!{/showGreeting}');
      mockFileStore.set('templates/test-cond.docx', templateBuffer);

      const res = await request('POST', '/render', {
        templatePath: 'templates/test-cond.docx',
        data: { showGreeting: true, name: 'Charlie' },
        outputPath: 'output/test-cond-rendered.docx',
      });

      expect(res.status).toBe(200);
      const outputBuffer = mockFileStore.get('output/test-cond-rendered.docx');
      const zip = new PizZip(outputBuffer);
      const docXml = zip.file('word/document.xml').asText();
      expect(docXml).toContain('Hello Charlie!');
    });

    it('should hide content when condition is false', async () => {
      const templateBuffer = createTestDocx('{#showGreeting}Hello {name}!{/showGreeting}');
      mockFileStore.set('templates/test-cond-false.docx', templateBuffer);

      const res = await request('POST', '/render', {
        templatePath: 'templates/test-cond-false.docx',
        data: { showGreeting: false, name: 'Charlie' },
        outputPath: 'output/test-cond-false-rendered.docx',
      });

      expect(res.status).toBe(200);
      const outputBuffer = mockFileStore.get('output/test-cond-false-rendered.docx');
      const zip = new PizZip(outputBuffer);
      const docXml = zip.file('word/document.xml').asText();
      expect(docXml).not.toContain('Hello Charlie!');
    });
  });

  describe('loop rendering', () => {
    it('should render loop with array data', async () => {
      const templateBuffer = createTestDocx('{#items}{name} {/items}');
      mockFileStore.set('templates/test-loop.docx', templateBuffer);

      const res = await request('POST', '/render', {
        templatePath: 'templates/test-loop.docx',
        data: {
          items: [
            { name: 'Item1' },
            { name: 'Item2' },
            { name: 'Item3' },
          ],
        },
        outputPath: 'output/test-loop-rendered.docx',
      });

      expect(res.status).toBe(200);
      const outputBuffer = mockFileStore.get('output/test-loop-rendered.docx');
      const zip = new PizZip(outputBuffer);
      const docXml = zip.file('word/document.xml').asText();
      expect(docXml).toContain('Item1');
      expect(docXml).toContain('Item2');
      expect(docXml).toContain('Item3');
    });

    it('should render empty loop when array is empty', async () => {
      const templateBuffer = createTestDocx('Before{#items}{name}{/items}After');
      mockFileStore.set('templates/test-empty-loop.docx', templateBuffer);

      const res = await request('POST', '/render', {
        templatePath: 'templates/test-empty-loop.docx',
        data: { items: [] },
        outputPath: 'output/test-empty-loop-rendered.docx',
      });

      expect(res.status).toBe(200);
      const outputBuffer = mockFileStore.get('output/test-empty-loop-rendered.docx');
      const zip = new PizZip(outputBuffer);
      const docXml = zip.file('word/document.xml').asText();
      expect(docXml).toContain('Before');
      expect(docXml).toContain('After');
    });
  });

  describe('input validation', () => {
    it('should return 400 when templatePath is missing', async () => {
      const res = await request('POST', '/render', {
        data: { name: 'Test' },
      });
      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('MISSING_TEMPLATE_PATH');
    });

    it('should return 400 when data is missing', async () => {
      const res = await request('POST', '/render', {
        templatePath: 'templates/test.docx',
      });
      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('MISSING_DATA');
    });

    it('should return 500 when template file does not exist', async () => {
      const res = await request('POST', '/render', {
        templatePath: 'templates/nonexistent.docx',
        data: { name: 'Test' },
      });
      expect(res.status).toBe(500);
    });
  });

  describe('direct document return (no outputPath)', () => {
    it('should return the rendered document directly when no outputPath', async () => {
      const templateBuffer = createTestDocx('{greeting}');
      mockFileStore.set('templates/test-direct.docx', templateBuffer);

      const res = await request('POST', '/render', {
        templatePath: 'templates/test-direct.docx',
        data: { greeting: 'Hello World' },
      });

      expect(res.status).toBe(200);
      expect(res.headers['content-type']).toContain('application/vnd.openxmlformats-officedocument.wordprocessingml.document');
      // The response body should be a buffer (the docx file)
      expect(res.rawBody).toBeDefined();
      expect(res.rawBody.length).toBeGreaterThan(0);
    });
  });
});


describe('POST /convert-pdf', () => {
  let libreOfficeAvailable;

  beforeAll(async () => {
    libreOfficeAvailable = await isLibreOfficeAvailable();
    if (!libreOfficeAvailable) {
      console.warn('LibreOffice not available - PDF conversion tests will be skipped');
    }
  });

  it('should return 400 when no input is provided', async () => {
    const res = await request('POST', '/convert-pdf', {});
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('MISSING_INPUT');
  });

  it('should accept base64 input and convert to PDF (if LibreOffice available)', async () => {
    if (!libreOfficeAvailable) {
      console.log('Skipping: LibreOffice not installed');
      return;
    }

    const docxBuffer = createTestDocx('PDF Test Content');
    const base64Input = docxBuffer.toString('base64');

    const res = await request('POST', '/convert-pdf', {
      inputBuffer: base64Input,
      outputPath: 'output/test-converted.pdf',
    });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.outputPath).toBe('output/test-converted.pdf');
    expect(res.body.size).toBeGreaterThan(0);
  });

  it('should convert from MinIO path (if LibreOffice available)', async () => {
    if (!libreOfficeAvailable) {
      console.log('Skipping: LibreOffice not installed');
      return;
    }

    const docxBuffer = createTestDocx('MinIO PDF Test');
    mockFileStore.set('templates/test-pdf.docx', docxBuffer);

    const res = await request('POST', '/convert-pdf', {
      inputPath: 'templates/test-pdf.docx',
      outputPath: 'output/test-pdf-converted.pdf',
    });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
  });

  it('should handle batch conversion (if LibreOffice available)', async () => {
    if (!libreOfficeAvailable) {
      console.log('Skipping: LibreOffice not installed');
      return;
    }

    const docx1 = createTestDocx('Batch Doc 1');
    const docx2 = createTestDocx('Batch Doc 2');
    mockFileStore.set('templates/batch1.docx', docx1);
    mockFileStore.set('templates/batch2.docx', docx2);

    const res = await request('POST', '/convert-pdf', {
      files: [
        { inputPath: 'templates/batch1.docx', outputPath: 'output/batch1.pdf' },
        { inputPath: 'templates/batch2.docx', outputPath: 'output/batch2.pdf' },
      ],
    });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.results).toHaveLength(2);
    expect(res.body.results[0].success).toBe(true);
    expect(res.body.results[1].success).toBe(true);
  });

  it('should return 500 when LibreOffice is not available and inputPath is used', async () => {
    if (libreOfficeAvailable) {
      console.log('Skipping: LibreOffice IS available, cannot test failure path');
      return;
    }

    const docxBuffer = createTestDocx('Should fail');
    mockFileStore.set('templates/test-fail.docx', docxBuffer);

    const res = await request('POST', '/convert-pdf', {
      inputPath: 'templates/test-fail.docx',
    });

    // When LibreOffice is not available, conversion should fail
    expect(res.status).toBe(500);
    expect(res.body.error.code).toBe('PDF_CONVERSION_FAILED');
  });
});
