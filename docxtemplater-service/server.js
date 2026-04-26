const express = require('express');
const { minioClient, ensureBucket } = require('./src/minio-client');
const renderRouter = require('./src/routes/render');
const evaluateRouter = require('./src/routes/evaluate');
const convertPdfRouter = require('./src/routes/convert-pdf');
const mergeSegmentsRouter = require('./src/routes/merge-segments');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Health check endpoint
app.get('/health', async (_req, res) => {
  const checks = { minio: 'UP' };
  try {
    await minioClient.listBuckets();
  } catch (err) {
    checks.minio = 'DOWN';
  }
  const overallStatus = Object.values(checks).every(s => s === 'UP') ? 'UP' : 'DEGRADED';
  res.json({ status: overallStatus, service: 'docxtemplater-service', checks });
});

// Routes
app.use('/render', renderRouter);
app.use('/evaluate', evaluateRouter);
app.use('/convert-pdf', convertPdfRouter);
// Backend document merge calls POST /merge-segments (see DocumentMergeService in Java).
app.use('/merge-segments', mergeSegmentsRouter);

// Global error handler
app.use((err, _req, res, _next) => {
  console.error('Unhandled error:', err);
  res.status(500).json({ error: { code: 'INTERNAL_ERROR', message: err.message } });
});

if (require.main === module) {
  ensureBucket().then(() => {
    app.listen(PORT, () => {
      console.log(`Docxtemplater service listening on port ${PORT}`);
    });
  }).catch(err => {
    console.error('Failed to initialize MinIO bucket:', err.message);
    app.listen(PORT, () => {
      console.log(`Docxtemplater service listening on port ${PORT} (MinIO unavailable)`);
    });
  });
}

module.exports = app;
