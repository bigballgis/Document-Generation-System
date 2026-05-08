package com.docgen.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionSecretGuardTest {

    @Test
    void nonProductionProfile_doesNotValidate() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"test", "local"});

        assertDoesNotThrow(() -> ProductionSecretGuard.assertProductionSecrets(env));
    }

    @Test
    void productionProfile_placeholderJwt_failsWithoutLeakingSecret() {
        Environment env = mockProdEnv(
                ProductionSecretGuard.DEFAULT_JWT_SECRET,
                "non-default-encryption-key-32bytes!!",
                "access-not-default",
                "secret-not-default",
                "non-default-onlyoffice-jwt");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> ProductionSecretGuard.assertProductionSecrets(env));

        assertTrue(ex.getMessage().contains("jwt.secret"));
        assertFalse(ex.getMessage().contains(ProductionSecretGuard.DEFAULT_JWT_SECRET));
    }

    @Test
    void productionProfile_placeholderEncryptionKey_fails() {
        Environment env = mockProdEnv(
                "non-default-jwt-secret-key-256-bits-minimum!!",
                ProductionSecretGuard.DEFAULT_ENCRYPTION_KEY,
                "access-not-default",
                "secret-not-default",
                "non-default-onlyoffice-jwt");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> ProductionSecretGuard.assertProductionSecrets(env));

        assertTrue(ex.getMessage().contains("encryption.key"));
        assertFalse(ex.getMessage().contains(ProductionSecretGuard.DEFAULT_ENCRYPTION_KEY));
    }

    @Test
    void productionProfile_defaultMinioCredentials_fails() {
        Environment env = mockProdEnv(
                "non-default-jwt-secret-key-256-bits-minimum!!",
                "non-default-encryption-key-32bytes!!",
                ProductionSecretGuard.DEFAULT_MINIO_CREDENTIAL,
                "secret-not-default",
                "non-default-onlyoffice-jwt");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> ProductionSecretGuard.assertProductionSecrets(env));

        assertTrue(ex.getMessage().contains("minio.access-key"));
        assertFalse(ex.getMessage().toLowerCase().contains("minioadmin"));
    }

    @Test
    void productionProfile_defaultOnlyOfficeJwt_fails() {
        Environment env = mockProdEnv(
                "non-default-jwt-secret-key-256-bits-minimum!!",
                "non-default-encryption-key-32bytes!!",
                "access-not-default",
                "secret-not-default",
                ProductionSecretGuard.DEFAULT_ONLYOFFICE_JWT_SECRET);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> ProductionSecretGuard.assertProductionSecrets(env));

        assertTrue(ex.getMessage().contains("onlyoffice.jwt-secret"));
        assertFalse(ex.getMessage().contains(ProductionSecretGuard.DEFAULT_ONLYOFFICE_JWT_SECRET));
    }

    @Test
    void productionProfile_blankJwt_fails() {
        Environment env = mockProdEnv(
                "   ",
                "non-default-encryption-key-32bytes!!",
                "access-not-default",
                "secret-not-default",
                "non-default-onlyoffice-jwt");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> ProductionSecretGuard.assertProductionSecrets(env));

        assertTrue(ex.getMessage().contains("jwt.secret"));
        assertTrue(ex.getMessage().contains("missing or blank"));
    }

    @Test
    void productionProfile_strongSecrets_passes() {
        Environment env = mockProdEnv(
                "non-default-jwt-secret-key-256-bits-minimum!!",
                "non-default-encryption-key-32bytes!!",
                "access-not-default",
                "secret-not-default",
                "non-default-onlyoffice-jwt");

        assertDoesNotThrow(() -> ProductionSecretGuard.assertProductionSecrets(env));
    }

    @Test
    void isProductionProfile_trueWhenProductionAliasActive() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"production"});

        assertTrue(ProductionSecretGuard.isProductionProfile(env));
    }

    private static Environment mockProdEnv(String jwt, String encryption, String minioAccess,
                                            String minioSecret, String onlyOfficeJwt) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(env.getProperty("jwt.secret")).thenReturn(jwt);
        when(env.getProperty("encryption.key")).thenReturn(encryption);
        when(env.getProperty("minio.access-key")).thenReturn(minioAccess);
        when(env.getProperty("minio.secret-key")).thenReturn(minioSecret);
        when(env.getProperty("onlyoffice.jwt-secret")).thenReturn(onlyOfficeJwt);
        return env;
    }
}
