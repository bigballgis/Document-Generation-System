const bwipjs = require('bwip-js');
const QRCode = require('qrcode');

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
