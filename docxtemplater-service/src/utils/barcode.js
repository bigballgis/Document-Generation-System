const bwipjs = require('bwip-js');
const QRCode = require('qrcode');

/**
 * Generate a barcode image buffer.
 * @param {string} value - The barcode data
 * @param {string} format - Barcode format (e.g., 'code128', 'ean13', 'code39')
 * @param {object} options - Additional bwip-js options
 * @returns {Promise<Buffer>} PNG image buffer
 */
async function generateBarcode(value, format = 'code128', options = {}) {
  const pngBuffer = await bwipjs.toBuffer({
    bcid: format,
    text: String(value),
    scale: options.scale || 3,
    height: options.height || 10,
    includetext: options.includeText !== false,
    textxalign: 'center',
    ...options,
  });
  return pngBuffer;
}

/**
 * Generate a QR code image buffer.
 * @param {string} value - The QR code data
 * @param {object} options - QR code options
 * @returns {Promise<Buffer>} PNG image buffer
 */
async function generateQRCode(value, options = {}) {
  const pngBuffer = await QRCode.toBuffer(String(value), {
    type: 'png',
    width: options.width || 200,
    margin: options.margin || 2,
    errorCorrectionLevel: options.errorCorrectionLevel || 'M',
    ...options,
  });
  return pngBuffer;
}

module.exports = { generateBarcode, generateQRCode };
