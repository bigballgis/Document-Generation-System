const PizZip = require('pizzip');

async function applyTextWatermark(docxBuffer, config) {
  const { text = 'WATERMARK', fontSize = 48, color = '#C0C0C0', opacity = 0.5, rotation = -45 } = config;

  const zip = new PizZip(docxBuffer);

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

  const headerPath = findOrCreateHeader(zip, watermarkShape);

  return zip.generate({ type: 'nodebuffer' });
}

async function applyImageWatermark(docxBuffer, config) {
  const { imageBase64, opacity = 0.3 } = config;
  if (!imageBase64) return docxBuffer;

  const zip = new PizZip(docxBuffer);

  const imgData = Buffer.from(imageBase64, 'base64');
  zip.file('word/media/watermark.png', imgData);

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
  let headerXml;
  const headerPath = 'word/header1.xml';

  if (zip.file(headerPath)) {
    headerXml = zip.file(headerPath).asText();
    headerXml = headerXml.replace('</w:hdr>', `<w:p><w:r><w:pict>${watermarkContent}</w:pict></w:r></w:p></w:hdr>`);
  } else {
    headerXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
       xmlns:v="urn:schemas-microsoft-com:vml"
       xmlns:o="urn:schemas-microsoft-com:office:office"
       xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <w:p><w:r><w:pict>${watermarkContent}</w:pict></w:r></w:p>
  </w:hdr>`;

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
