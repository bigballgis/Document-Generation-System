const express = require('express');
const router = express.Router();
const { execFile, exec } = require('child_process');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { getFileBuffer, putFileBuffer } = require('../minio-client');
const { badRequest } = require('../utils/http-errors');

const LIBREOFFICE_BIN = process.env.LIBREOFFICE_BIN || 'libreoffice';
const PYTHON_BIN = process.env.PYTHON_BIN || 'python3';
const UNO_SCRIPT = path.resolve(__dirname, '../../scripts/convert_with_fields.py');

let _unoAvailable = null;
async function isUnoAvailable() {
  if (_unoAvailable !== null) return _unoAvailable;
  return new Promise((resolve) => {
    exec(`${PYTHON_BIN} -c "import uno; print('ok')"`, { timeout: 5000 }, (err, stdout) => {
      _unoAvailable = !err && stdout.trim() === 'ok';
      if (_unoAvailable) {
        console.log('[convert-pdf] UNO converter available — will use for field refresh');
      } else {
        console.warn('[convert-pdf] python3-uno not available — falling back to CLI conversion');
      }
      resolve(_unoAvailable);
    });
  });
}

function convertViaUno(inputPath, outputPath, timeout = 120000) {
  return new Promise((resolve, reject) => {
    execFile(
      PYTHON_BIN,
      [UNO_SCRIPT, inputPath, outputPath],
      { timeout, env: { ...process.env, HOME: os.tmpdir() } },
      (error, stdout, stderr) => {
        if (error) {
          console.error('[convert-pdf] UNO conversion stderr:', stderr);
          reject(new Error(`UNO conversion failed: ${error.message}`));
        } else {
          if (stderr) {
            console.log('[convert-pdf] UNO:', stderr.trim());
          }
          resolve();
        }
      }
    );
  });
}

function convertViaCli(inputPath, tmpDir, timeout = 120000) {
  return new Promise((resolve, reject) => {
    const userInstall = path.join(tmpDir, 'user_profile');
    fs.mkdirSync(userInstall, { recursive: true });

    const args = [
      '--headless',
      '--norestore',
      '--nologo',
      `--env:UserInstallation=file://${userInstall.replace(/\\/g, '/')}`,
      '--convert-to',
      'pdf:writer_pdf_Export:{"UpdateDocMode":{"type":"long","value":"3"}}',
      '--outdir', tmpDir,
      inputPath,
    ];

    execFile(LIBREOFFICE_BIN, args, { timeout }, (error, stdout, stderr) => {
      if (error) {
        reject(new Error(`LibreOffice CLI conversion failed: ${error.message}. stderr: ${stderr}`));
        return;
      }

      const baseName = path.basename(inputPath, path.extname(inputPath)) + '.pdf';
      const expectedPath = path.join(tmpDir, baseName);

      if (fs.existsSync(expectedPath)) {
        resolve(expectedPath);
        return;
      }

      const pdfFiles = fs.readdirSync(tmpDir).filter(f => f.endsWith('.pdf'));
      if (pdfFiles.length > 0) {
        resolve(path.join(tmpDir, pdfFiles[0]));
      } else {
        reject(new Error('PDF output file not found after CLI conversion'));
      }
    });
  });
}

async function convertToPdf(docxBuffer, options = {}) {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'docgen-pdf-'));
  const inputPath = path.join(tmpDir, 'input.docx');
  const outputPath = path.join(tmpDir, 'input.pdf');

  try {
    fs.writeFileSync(inputPath, docxBuffer);

    const unoOk = await isUnoAvailable();

    if (unoOk) {
      try {
        await convertViaUno(inputPath, outputPath);
      } catch (unoErr) {
        console.warn('[convert-pdf] UNO conversion failed, falling back to CLI:', unoErr.message);
        const cliOutputPath = await convertViaCli(inputPath, tmpDir);
        if (cliOutputPath !== outputPath && fs.existsSync(cliOutputPath)) {
          fs.renameSync(cliOutputPath, outputPath);
        }
      }
    } else {
      const cliOutputPath = await convertViaCli(inputPath, tmpDir);
      if (cliOutputPath !== outputPath && fs.existsSync(cliOutputPath)) {
        fs.renameSync(cliOutputPath, outputPath);
      }
    }

    if (!fs.existsSync(outputPath)) {
      const pdfFiles = fs.readdirSync(tmpDir).filter(f => f.endsWith('.pdf'));
      if (pdfFiles.length > 0) {
        fs.renameSync(path.join(tmpDir, pdfFiles[0]), outputPath);
      } else {
        throw new Error('PDF output file not found after conversion');
      }
    }

    let pdfBuffer = fs.readFileSync(outputPath);

    if (options.password || options.noPrint || options.noCopy || options.noEdit) {
      pdfBuffer = await applyPdfSecurity(pdfBuffer, options, tmpDir);
    }

    return pdfBuffer;
  } finally {
    cleanupDir(tmpDir);
  }
}

async function applyPdfSecurity(pdfBuffer, options, tmpDir) {
  const securedPath = path.join(tmpDir, 'secured.pdf');
  const inputPdfPath = path.join(tmpDir, 'input_sec.pdf');
  fs.writeFileSync(inputPdfPath, pdfBuffer);

  const qpdfArgs = ['--encrypt'];
  const userPassword = options.password || '';
  const ownerPassword = options.ownerPassword || 'owner_' + Date.now();
  qpdfArgs.push(userPassword, ownerPassword, '256', '--');

  if (options.noPrint) qpdfArgs.push('--print=none');
  if (options.noCopy) qpdfArgs.push('--extract=n');
  if (options.noEdit) qpdfArgs.push('--modify=none');

  qpdfArgs.push(inputPdfPath, securedPath);

  try {
    await new Promise((resolve, reject) => {
      execFile('qpdf', qpdfArgs, { timeout: 30000 }, (error, stdout, stderr) => {
        if (error) reject(new Error(`qpdf failed: ${error.message}`));
        else resolve(stdout);
      });
    });
    if (fs.existsSync(securedPath)) {
      return fs.readFileSync(securedPath);
    }
  } catch {
    console.warn('[convert-pdf] qpdf not available, returning unprotected PDF');
  }
  return pdfBuffer;
}

function cleanupDir(dirPath) {
  try {
    const entries = fs.readdirSync(dirPath, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(dirPath, entry.name);
      if (entry.isDirectory()) {
        cleanupDir(fullPath);
      } else {
        fs.unlinkSync(fullPath);
      }
    }
    fs.rmdirSync(dirPath);
  } catch {
  }
}

router.post('/', async (req, res) => {
  try {
    const { inputPath, inputBuffer, outputPath, files, password, noPrint, noCopy, noEdit } = req.body;
    const securityOptions = { password, noPrint, noCopy, noEdit };

    if (files && Array.isArray(files) && files.length > 0) {
      const results = [];
      for (const file of files) {
        try {
          const docxBuffer = await getFileBuffer(file.inputPath);
          const pdfBuffer = await convertToPdf(docxBuffer, securityOptions);

          if (file.outputPath) {
            await putFileBuffer(file.outputPath, pdfBuffer, 'application/pdf');
            results.push({ inputPath: file.inputPath, outputPath: file.outputPath, size: pdfBuffer.length, success: true });
          } else {
            results.push({ inputPath: file.inputPath, size: pdfBuffer.length, success: true, base64: pdfBuffer.toString('base64') });
          }
        } catch (err) {
          results.push({ inputPath: file.inputPath, success: false, error: err.message });
        }
      }
      return res.json({ success: true, results });
    }

    let docxBuffer;
    if (inputPath) {
      docxBuffer = await getFileBuffer(inputPath);
    } else if (inputBuffer) {
      docxBuffer = Buffer.from(inputBuffer, 'base64');
    } else {
      return badRequest(res, 'MISSING_INPUT', 'inputPath, inputBuffer, or files array is required');
    }

    const pdfBuffer = await convertToPdf(docxBuffer, securityOptions);

    if (outputPath) {
      await putFileBuffer(outputPath, pdfBuffer, 'application/pdf');
      return res.json({ success: true, outputPath, size: pdfBuffer.length });
    }

    res.set('Content-Type', 'application/pdf');
    res.set('Content-Disposition', 'attachment; filename="converted.pdf"');
    res.send(pdfBuffer);
  } catch (err) {
    console.error('[convert-pdf] Error:', err);
    res.status(500).json({
      error: { code: 'PDF_CONVERSION_FAILED', message: err.message },
    });
  }
});

module.exports = router;
module.exports.convertToPdf = convertToPdf;
