const PizZip = require('pizzip');

/**
 * Apply a text watermark to a .docx buffer by injecting a watermark shape into the document header.
 * Uses the Word XML watermark approach (VML shape in header).
 *
 * @param {Buffer} docxBuffer - The rendered .docx buffer
 * @param {object} config - Watermark configuration
 * @param {string} config.text - Watermark text
 * @param {number} [config.fontSize=48] - Font size
 * @param {string} [config.color='#C0C0C0'] - Hex color
 * @param {number} [config.opacity=0.5] - Opacity (0-1)
 * @param {number} [config.rotation=-45] - Rotation angle in degrees
 * @returns {Promise<Buffer>} Modified .docx buffer
 */
async function applyTextWatermark(docxBuffer, config) {
  const { text = 'WATERMARK', fontSize = 48, color = '#C0C0C0', opacity = 0.5, rotation = -45 } = config;

  const zip = new PizZip(docxBuffer);

  // Build VML watermark shape
  const hexColor = color.replace('#', '');
  const opacityStr = opacity.toString();
  const rotationStyle = `rotation:${rotation}`;

  const watermarkShape = `
    <v:shapetype id="_x0000_t136" coordsize="21600,21600" o:spt="136" adj="10800"
      path="m@7,l@8,m@5,21600l@6,21600e">
      <v:formulas><v:f eqn="sum #0 0 10800"/><v:f eqn="prod #0 2 1"/>
      <v:f eqn="sum 21600 0 @1"/><v:f eqn="sum 0 0 @2"/>
      <v:f eqn="sum 21600 0 @3"/><v:f eqn="if @0 @3 0"/>
      <v:f eqn="if @0 21600 @1"/><v:f eqn="if @0 0 @2"/>
      <v:f eqn="if @0 @4 21600"/><v:f eqn="mid @5 @6"/>
      <v:f eqn="mid @8 @5"/><v:f eqn="mid @7 @8"/>
      <v:f eqn="mid @6 @7"/><v:f eqn="sum @6 0 @5"/></v:formulas>
      <v:path textpathok="t" o:connecttype="custom" o:connectlocs="@9,0;@10,10800;@11,21600;@12,10800" o:connectangles="270,180,90,0"/>
      <v:textpath on="t" fitshape="t"/>
      <v:handles><v:h position="#0,bottomRight" xrange="6629,14971"/></v:handles>
      <o:lock v:ext="edit" text="t" shapetype="t"/>
    </v:shapetype>
    <v:shape id="PowerPlusWaterMarkObject" o:spid="_x0000_s2049" type="#_x0000_t136"
      style="position:absolute;margin-left:0;margin-top:0;width:468pt;height:117pt;${rotationStyle};z-index:-251657216;mso-position-horizontal:center;mso-position-horizontal-relative:margin;mso-position-vertical:center;mso-position-vertical-relative:margin"
      o:allowincell="f" fillcolor="#${hexColor}" stroked="f">
      <v:fill opacity="${opacityStr}"/>
      <v:textpath style="font-family:&quot;Calibri&quot;;font-size:${fontSize}pt" string="${escapeXml(text)}"/>
    </v:shape>`;

  // Find or create header
  const headerPath = findOrCreateHeader(zip, watermarkShape);

  return zip.generate({ type: 'nodebuffer' });
}

/**
 * Apply an image watermark to a .docx buffer.
 * Simplified: injects a background image reference into the header.
 *
 * @param {Buffer} docxBuffer - The rendered .docx buffer
 * @param {object} config - Watermark configuration
 * @param {string} config.imageBase64 - Base64 encoded image
 * @param {number} [config.opacity=0.3] - Opacity (0-1)
 * @returns {Promise<Buffer>} Modified .docx buffer
 */
async function applyImageWatermark(docxBuffer, config) {
  const { imageBase64, opacity = 0.3 } = config;
  if (!imageBase64) return docxBuffer;

  const zip = new PizZip(docxBuffer);

  // Add image to media folder
  const imgData = Buffer.from(imageBase64, 'base64');
  zip.file('word/media/watermark.png', imgData);

  // Add relationship for the image in header
  const watermarkShape = `
    <v:shape id="WatermarkImage" o:spid="_x0000_s2050"
      style="position:absolute;margin-left:0;margin-top:0;width:468pt;height:468pt;z-index:-251657216;mso-position-horizontal:center;mso-position-horizontal-relative:margin;mso-position-vertical:center;mso-position-vertical-relative:margin"
      o:allowincell="f">
      <v:imagedata r:id="rIdWatermark" o:title="watermark" gain="${Math.round(opacity * 65536)}f"/>
    </v:shape>`;

  findOrCreateHeader(zip, watermarkShape);

  return zip.generate({ type: 'nodebuffer' });
}

function findOrCreateHeader(zip, watermarkContent) {
  // Check if header1.xml exists
  let headerXml;
  const headerPath = 'word/header1.xml';

  if (zip.file(headerPath)) {
    headerXml = zip.file(headerPath).asText();
    // Insert watermark before closing </w:hdr>
    headerXml = headerXml.replace('</w:hdr>', `<w:p><w:r><w:pict>${watermarkContent}</w:pict></w:r></w:p></w:hdr>`);
  } else {
    headerXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
       xmlns:v="urn:schemas-microsoft-com:vml"
       xmlns:o="urn:schemas-microsoft-com:office:office"
       xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <w:p><w:r><w:pict>${watermarkContent}</w:pict></w:r></w:p>
</w:hdr>`;

    // Add header reference to document.xml.rels
    ensureHeaderRelationship(zip, headerPath);
  }

  zip.file(headerPath, headerXml);
  return headerPath;
}

function ensureHeaderRelationship(zip, headerPath) {
  const relsPath = 'word/_rels/document.xml.rels';
  if (zip.file(relsPath)) {
    let rels = zip.file(relsPath).asText();
    if (!rels.includes('header1.xml')) {
      const relId = 'rIdHeader1';
      const newRel = `<Relationship Id="${relId}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>`;
      rels = rels.replace('</Relationships>', `${newRel}</Relationships>`);
      zip.file(relsPath, rels);
    }
  }
}

function escapeXml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

module.exports = { applyTextWatermark, applyImageWatermark };
