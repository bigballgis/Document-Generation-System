/**
 * Replace segment docx files in MinIO with updated versions from the ZIP.
 * Reads the assembly config from the API, extracts segment names and file paths,
 * then uploads the new docx files from the ZIP to the same MinIO paths.
 */
import * as fs from 'fs';
import * as path from 'path';
import { Readable } from 'stream';

const API_BASE = 'http://localhost:8080';
const MINIO_ENDPOINT = 'http://localhost:9000';
const MINIO_ACCESS_KEY = 'minioadmin';
const MINIO_SECRET_KEY = 'minioadmin';
const BUCKET = 'docgen';
const TEMPLATE_ID = 12;

// We'll use the unzipped files approach - extract from ZIP first
import { createReadStream } from 'fs';
import { createInterface } from 'readline';

async function getToken() {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'demo', password: 'Demo@2026' }),
  });
  const data = await res.json();
  return data.accessToken;
}

async function getAssemblyConfig(token) {
  const res = await fetch(`${API_BASE}/api/composite-templates/${TEMPLATE_ID}/assembly-config`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return res.json();
}

// Upload to MinIO using S3 PUT
async function uploadToMinio(objectPath, buffer) {
  const date = new Date().toISOString().replace(/[-:]/g, '').split('.')[0] + 'Z';
  const dateShort = date.substring(0, 8);
  
  // Use presigned URL approach via the app's MinIO client instead
  // Actually, let's use the mc CLI tool from the minio container
  return objectPath;
}

async function main() {
  const token = await getToken();
  const config = await getAssemblyConfig(token);
  
  console.log(`Found ${config.segments.length} segments`);
  
  // Build name -> filePath mapping
  const segmentMap = {};
  for (const seg of config.segments) {
    segmentMap[seg.name] = seg.filePath;
    console.log(`  ${seg.name} -> ${seg.filePath}`);
  }
  
  // Extract ZIP and upload each segment
  const AdmZip = (await import('adm-zip')).default;
  const zipPath = path.resolve('output/fol-template-import.zip');
  
  if (!fs.existsSync(zipPath)) {
    // Try the URL-encoded path
    const altPath = zipPath.replace('Document Generation System', 'Document%20Generation%20System');
    if (fs.existsSync(altPath)) {
      console.log('Using URL-encoded path');
    }
  }
  
  // Use archiver to read - actually let's just use the docker cp approach
  // Write segment mapping to a file for the shell script
  const mappingFile = path.resolve('output/segment-mapping.json');
  fs.writeFileSync(mappingFile, JSON.stringify(segmentMap, null, 2));
  console.log(`\nMapping written to ${mappingFile}`);
  console.log('\nUse the following to upload via docker:');
  for (const [name, filePath] of Object.entries(segmentMap)) {
    console.log(`  mc cp "segments/${name}.docx" minio/${BUCKET}/${filePath}`);
  }
}

main().catch(console.error);
