const Minio = require('minio');

function parseEndpoint(endpoint) {
  const raw = endpoint || 'http://localhost:9000';
  try {
    const url = new URL(raw);
    return {
      endPoint: url.hostname,
      port: parseInt(url.port, 10) || (url.protocol === 'https:' ? 443 : 9000),
      useSSL: url.protocol === 'https:',
    };
  } catch {
    return { endPoint: raw, port: 9000, useSSL: false };
  }
}

const parsed = parseEndpoint(process.env.MINIO_ENDPOINT);

const minioClient = new Minio.Client({
  endPoint: parsed.endPoint,
  port: parsed.port,
  useSSL: parsed.useSSL,
  accessKey: process.env.MINIO_ACCESS_KEY || 'minioadmin',
  secretKey: process.env.MINIO_SECRET_KEY || 'minioadmin',
});

const BUCKET_NAME = process.env.MINIO_BUCKET || 'docgen';

async function ensureBucket() {
  const exists = await minioClient.bucketExists(BUCKET_NAME);
  if (!exists) {
    await minioClient.makeBucket(BUCKET_NAME);
  }
}

async function getFileBuffer(filePath) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    minioClient.getObject(BUCKET_NAME, filePath, (err, stream) => {
      if (err) return reject(err);
      stream.on('data', chunk => chunks.push(chunk));
      stream.on('end', () => resolve(Buffer.concat(chunks)));
      stream.on('error', reject);
    });
  });
}

async function putFileBuffer(filePath, buffer, contentType) {
  await minioClient.putObject(BUCKET_NAME, filePath, buffer, buffer.length, {
    'Content-Type': contentType || 'application/octet-stream',
  });
}

module.exports = { minioClient, BUCKET_NAME, ensureBucket, getFileBuffer, putFileBuffer };
