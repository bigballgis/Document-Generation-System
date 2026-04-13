package com.docgen.integration;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for MinIO object storage: file upload and download.
 * Validates: Requirement 1 (template file storage)
 */
class MinioIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @BeforeEach
    void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    @Test
    void shouldUploadAndDownloadFile() throws Exception {
        String objectName = "test/upload-download-" + System.nanoTime() + ".txt";
        String content = "Hello, MinIO integration test!";
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        // Upload
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(new ByteArrayInputStream(contentBytes), contentBytes.length, -1)
                .contentType("text/plain")
                .build());

        // Download and verify
        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build())) {
            String downloaded = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(downloaded).isEqualTo(content);
        }

        // Cleanup
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build());
    }

    @Test
    void shouldUploadBinaryFile() throws Exception {
        String objectName = "test/binary-" + System.nanoTime() + ".docx";
        // Simulate a small binary file
        byte[] binaryContent = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(new ByteArrayInputStream(binaryContent), binaryContent.length, -1)
                .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .build());

        // Verify file exists by getting its stat
        StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build());

        assertThat(stat.size()).isEqualTo(binaryContent.length);
        assertThat(stat.contentType()).contains("application/vnd.openxmlformats");

        // Cleanup
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build());
    }

    @Test
    void shouldOverwriteExistingFile() throws Exception {
        String objectName = "test/overwrite-" + System.nanoTime() + ".txt";

        // Upload v1
        byte[] v1 = "version 1".getBytes(StandardCharsets.UTF_8);
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(new ByteArrayInputStream(v1), v1.length, -1)
                .contentType("text/plain")
                .build());

        // Upload v2 (overwrite)
        byte[] v2 = "version 2".getBytes(StandardCharsets.UTF_8);
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(new ByteArrayInputStream(v2), v2.length, -1)
                .contentType("text/plain")
                .build());

        // Download should return v2
        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build())) {
            String downloaded = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(downloaded).isEqualTo("version 2");
        }

        // Cleanup
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build());
    }

    @Test
    void shouldReturn404ForNonExistentObject() {
        assertThatThrownBy(() -> minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object("nonexistent/file-" + System.nanoTime() + ".txt")
                .build()))
                .isInstanceOf(ErrorResponseException.class);
    }
}
