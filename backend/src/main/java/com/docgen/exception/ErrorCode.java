package com.docgen.exception;

/**
 * Error code constants organized by module prefix.
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    public static final String AUTH_INVALID_TOKEN = "AUTH_INVALID_TOKEN";
    public static final String AUTH_TOKEN_EXPIRED = "AUTH_TOKEN_EXPIRED";
    public static final String AUTH_ACCOUNT_LOCKED = "AUTH_ACCOUNT_LOCKED";
    public static final String AUTH_ACCESS_DENIED = "AUTH_ACCESS_DENIED";
    public static final String AUTH_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";

    public static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";
    public static final String TENANT_DISABLED = "TENANT_DISABLED";
    public static final String TENANT_QUOTA_EXCEEDED = "TENANT_QUOTA_EXCEEDED";

    public static final String TEMPLATE_NOT_FOUND = "TEMPLATE_NOT_FOUND";
    public static final String TEMPLATE_INVALID_STATE_TRANSITION = "TEMPLATE_INVALID_STATE_TRANSITION";
    public static final String TEMPLATE_REVIEW_REQUIRED = "TEMPLATE_REVIEW_REQUIRED";
    public static final String TEMPLATE_ALREADY_EXISTS = "TEMPLATE_ALREADY_EXISTS";
    public static final String TEMPLATE_VERSION_NOT_FOUND = "TEMPLATE_VERSION_NOT_FOUND";
    public static final String TEMPLATE_EXPORT_NOT_ACTIVE = "TEMPLATE_EXPORT_NOT_ACTIVE";

    public static final String EXPRESSION_SYNTAX_ERROR = "EXPRESSION_SYNTAX_ERROR";
    public static final String EXPRESSION_SANDBOX_VIOLATION = "EXPRESSION_SANDBOX_VIOLATION";
    public static final String EXPRESSION_EVALUATION_FAILED = "EXPRESSION_EVALUATION_FAILED";

    public static final String GENERATE_FAILED = "GENERATE_FAILED";
    /** Single-request BOTH output is not supported; clients must call /word and /pdf (sync or async) separately. */
    public static final String GENERATE_BOTH_NOT_SUPPORTED = "GENERATE_BOTH_NOT_SUPPORTED";
    public static final String GENERATE_TIMEOUT = "GENERATE_TIMEOUT";
    public static final String GENERATE_RENDER_FAILED = "GENERATE_RENDER_FAILED";
    public static final String GENERATE_PDF_CONVERSION_FAILED = "GENERATE_PDF_CONVERSION_FAILED";
    public static final String GENERATE_STORAGE_FAILED = "GENERATE_STORAGE_FAILED";
    public static final String GENERATE_VERSION_NOT_ALLOWED = "GENERATE_VERSION_NOT_ALLOWED";
    public static final String GENERATE_BATCH_LIMIT_EXCEEDED = "GENERATE_BATCH_LIMIT_EXCEEDED";

    public static final String TASK_NOT_FOUND = "TASK_NOT_FOUND";
    public static final String TASK_NOT_COMPLETED = "TASK_NOT_COMPLETED";

    public static final String DOCUMENT_NOT_FOUND = "DOCUMENT_NOT_FOUND";
    public static final String DOCUMENT_EXPIRED = "DOCUMENT_EXPIRED";
    public static final String DOCUMENT_DOWNLOAD_FAILED = "DOCUMENT_DOWNLOAD_FAILED";

    public static final String VALIDATION_REQUIRED_FIELD = "VALIDATION_REQUIRED_FIELD";
    public static final String VALIDATION_TYPE_MISMATCH = "VALIDATION_TYPE_MISMATCH";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String RATE_LIMIT_QUOTA_EXHAUSTED = "RATE_LIMIT_QUOTA_EXHAUSTED";

    public static final String REVIEW_ALREADY_COMPLETED = "REVIEW_ALREADY_COMPLETED";
    public static final String REVIEW_NOT_AUTHORIZED = "REVIEW_NOT_AUTHORIZED";
    public static final String REVIEW_NOT_FOUND = "REVIEW_NOT_FOUND";

    public static final String ENCRYPTION_FAILED = "ENCRYPTION_FAILED";
    public static final String DECRYPTION_FAILED = "DECRYPTION_FAILED";
    public static final String ENCRYPTION_KEY_INVALID = "ENCRYPTION_KEY_INVALID";

    public static final String PIPELINE_STAGE_FAILED = "PIPELINE_STAGE_FAILED";
    public static final String PIPELINE_CIRCULAR_DEPENDENCY = "PIPELINE_CIRCULAR_DEPENDENCY";

    public static final String COVERAGE_CHECK_FAILED = "COVERAGE_CHECK_FAILED";
    public static final String COVERAGE_BELOW_THRESHOLD = "COVERAGE_BELOW_THRESHOLD";

    public static final String IMPORT_INVALID_FILE = "IMPORT_INVALID_FILE";
    public static final String IMPORT_INVALID_CONFIG = "IMPORT_INVALID_CONFIG";
    public static final String EXPORT_FAILED = "EXPORT_FAILED";

    public static final String ONLYOFFICE_URL_FAILED = "ONLYOFFICE_URL_FAILED";
    public static final String ONLYOFFICE_CALLBACK_FAILED = "ONLYOFFICE_CALLBACK_FAILED";
    public static final String ONLYOFFICE_CONTENT_ISOLATION_VIOLATION = "ONLYOFFICE_CONTENT_ISOLATION_VIOLATION";

    public static final String WEBHOOK_NOT_FOUND = "WEBHOOK_NOT_FOUND";
    public static final String WEBHOOK_SEND_FAILED = "WEBHOOK_SEND_FAILED";

    public static final String SCHEDULED_TASK_NOT_FOUND = "SCHEDULED_TASK_NOT_FOUND";
    public static final String SCHEDULED_TASK_INVALID_CRON = "SCHEDULED_TASK_INVALID_CRON";
    public static final String SCHEDULED_TASK_EXECUTION_FAILED = "SCHEDULED_TASK_EXECUTION_FAILED";

    public static final String AUDIT_EXPORT_FAILED = "AUDIT_EXPORT_FAILED";

    public static final String API_KEY_NOT_FOUND = "API_KEY_NOT_FOUND";

    public static final String WATERMARK_FAILED = "WATERMARK_FAILED";
    public static final String WATERMARK_INVALID_CONFIG = "WATERMARK_INVALID_CONFIG";

    public static final String MERGE_INVALID_REQUEST = "MERGE_INVALID_REQUEST";
    public static final String MERGE_INVALID_DOCUMENT_IDS = "MERGE_INVALID_DOCUMENT_IDS";
    public static final String MERGE_FAILED = "MERGE_FAILED";

    public static final String TEST_CASE_NOT_FOUND = "TEST_CASE_NOT_FOUND";
    public static final String TEST_CASE_EXECUTION_FAILED = "TEST_CASE_EXECUTION_FAILED";

    public static final String COMPOSITE_TEMPLATE_EMPTY = "COMPOSITE_TEMPLATE_EMPTY";
    public static final String GENERATE_ALL_SEGMENTS_SKIPPED = "GENERATE_ALL_SEGMENTS_SKIPPED";
    public static final String ASSEMBLY_CONFIG_INVALID = "ASSEMBLY_CONFIG_INVALID";
    public static final String SEGMENT_FILE_NOT_FOUND = "SEGMENT_FILE_NOT_FOUND";
    /** Concurrent segment publishes raced on version allocation; retries exhausted. */
    public static final String SEGMENT_VERSION_PUBLISH_CONFLICT = "SEGMENT_VERSION_PUBLISH_CONFLICT";
    public static final String CONTENT_DIFF_EXTRACTION_FAILED = "CONTENT_DIFF_EXTRACTION_FAILED";

    public static final String PARAMETER_NOT_FOUND = "PARAMETER_NOT_FOUND";
    public static final String PARAMETER_DUPLICATE_NAME = "PARAMETER_DUPLICATE_NAME";
    public static final String PARAMETER_EXPRESSION_REQUIRED = "PARAMETER_EXPRESSION_REQUIRED";
    public static final String PARAMETER_INVALID_NAME = "PARAMETER_INVALID_NAME";
    public static final String PARAMETER_INVALID_PARENT = "PARAMETER_INVALID_PARENT";
    public static final String PARAMETER_PARENT_TYPE_INVALID = "PARAMETER_PARENT_TYPE_INVALID";
    public static final String PARAMETER_MAX_DEPTH_EXCEEDED = "PARAMETER_MAX_DEPTH_EXCEEDED";
    public static final String PARAMETER_CIRCULAR_DEPENDENCY = "PARAMETER_CIRCULAR_DEPENDENCY";
    public static final String PARAMETER_CONCURRENT_MODIFICATION = "PARAMETER_CONCURRENT_MODIFICATION";
    public static final String PARAMETER_VALIDATION_RULE_INCOMPATIBLE = "PARAMETER_VALIDATION_RULE_INCOMPATIBLE";
    public static final String PARAMETER_INVALID_PATTERN = "PARAMETER_INVALID_PATTERN";
    public static final String PARAMETER_MISSING_REQUIRED = "PARAMETER_MISSING_REQUIRED";
    public static final String PARAMETER_TYPE_MISMATCH = "PARAMETER_TYPE_MISMATCH";
    public static final String PARAMETER_VALIDATION_FAILED = "PARAMETER_VALIDATION_FAILED";
    public static final String PARAMETER_EXPRESSION_EVALUATION_FAILED = "PARAMETER_EXPRESSION_EVALUATION_FAILED";
    public static final String PARAMETER_SCAN_FAILED = "PARAMETER_SCAN_FAILED";
    public static final String PARAMETER_BATCH_INVALID_IDS = "PARAMETER_BATCH_INVALID_IDS";
    public static final String PARAMETER_BATCH_VALIDATION_FAILED = "PARAMETER_BATCH_VALIDATION_FAILED";
    public static final String PARAMETER_JSON_IMPORT_FAILED = "PARAMETER_JSON_IMPORT_FAILED";
    public static final String PARAMETER_JSON_IMPORT_DEPTH_EXCEEDED = "PARAMETER_JSON_IMPORT_DEPTH_EXCEEDED";
    public static final String PARAMETER_EXPRESSION_INVALID_SCOPE = "PARAMETER_EXPRESSION_INVALID_SCOPE";

    public static final String MIGRATION_FILE_ACCESS_FAILED = "MIGRATION_FILE_ACCESS_FAILED";

    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
