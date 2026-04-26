package com.docgen.config;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Validates that well-known placeholder and default secrets are not used when a production
 * Spring profile is active. Used at startup; error messages list property keys only.
 */
public final class ProductionSecretGuard {

    /**
     * Default from {@code application.yml}; must stay in sync for placeholder detection.
     */
    static final String DEFAULT_JWT_SECRET = "your-256-bit-secret-key-change-in-production";
    static final String DEFAULT_ENCRYPTION_KEY = "your-aes-256-encryption-key-change-me";
    static final String DEFAULT_MINIO_CREDENTIAL = "minioadmin";
    static final String DEFAULT_ONLYOFFICE_JWT_SECRET = "my_jwt_secret";

    private ProductionSecretGuard() {
    }

    /**
     * Throws {@link IllegalStateException} when {@code prod} or {@code production} is active
     * and any guarded property is missing, blank, or matches a known insecure default.
     */
    public static void assertProductionSecrets(Environment env) {
        if (!isProductionProfile(env)) {
            return;
        }

        List<String> violations = new ArrayList<>();

        checkProperty(env, "jwt.secret", violations, ProductionSecretGuard::isInvalidJwtSecret);
        checkProperty(env, "encryption.key", violations, ProductionSecretGuard::isInvalidEncryptionKey);
        checkProperty(env, "minio.access-key", violations, ProductionSecretGuard::isInvalidMinioCredential);
        checkProperty(env, "minio.secret-key", violations, ProductionSecretGuard::isInvalidMinioCredential);
        checkProperty(env, "onlyoffice.jwt-secret", violations, ProductionSecretGuard::isInvalidOnlyOfficeJwtSecret);

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Production profile is active but insecure or placeholder configuration was detected for: "
                            + String.join(", ", violations)
                            + ". Replace these values with strong secrets (values are not logged).");
        }
    }

    static boolean isProductionProfile(Environment env) {
        for (String p : env.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    private static void checkProperty(Environment env, String key, List<String> violations,
                                      Predicate<String> invalidWhenPresent) {
        String value = env.getProperty(key);
        if (!StringUtils.hasText(value)) {
            violations.add(key + " (missing or blank)");
            return;
        }
        if (invalidWhenPresent.test(value.trim())) {
            violations.add(key + " (placeholder or default)");
        }
    }

    private static boolean isInvalidJwtSecret(String value) {
        return DEFAULT_JWT_SECRET.equals(value);
    }

    private static boolean isInvalidEncryptionKey(String value) {
        return DEFAULT_ENCRYPTION_KEY.equals(value);
    }

    private static boolean isInvalidMinioCredential(String value) {
        return DEFAULT_MINIO_CREDENTIAL.equalsIgnoreCase(value);
    }

    private static boolean isInvalidOnlyOfficeJwtSecret(String value) {
        return DEFAULT_ONLYOFFICE_JWT_SECRET.equalsIgnoreCase(value);
    }
}
