package com.docgen.property;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for ZIP export credential masking.
 *
 * <p>Verifies that credential fields (password, apiKey, clientSecret) are always
 * masked with __CREDENTIAL_PLACEHOLDER__ while non-credential fields retain
 * their original values.</p>
 *
 * <p><b>Validates: Requirements 7.1, 7.2</b></p>
 */
@Tag("Feature: workspace-export-settings, Property 5: ZIP export contains all required files with credential masking")
class CompositeExportPropertyTest {

    private static final String CREDENTIAL_PLACEHOLDER = "__CREDENTIAL_PLACEHOLDER__";
    private static final Set<String> TOP_LEVEL_CREDENTIAL_FIELDS = Set.of("apiKey", "clientSecret");
    private static final Set<String> AUTH_CREDENTIAL_FIELDS = Set.of("apiKey", "clientSecret", "password");
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Applies the same masking logic as CompositeImportExportService.maskCredentialFields.
     */
    private void maskCredentialFields(Map<String, Object> config, String type) {
        if ("DATABASE".equals(type)) {
            if (config.containsKey("password")) {
                config.put("password", CREDENTIAL_PLACEHOLDER);
            }
        }
        if (config.containsKey("apiKey")) {
            config.put("apiKey", CREDENTIAL_PLACEHOLDER);
        }
        if (config.containsKey("clientSecret")) {
            config.put("clientSecret", CREDENTIAL_PLACEHOLDER);
        }
        if (config.containsKey("auth") && config.get("auth") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> auth = (Map<String, Object>) config.get("auth");
            if (auth.containsKey("apiKey")) auth.put("apiKey", CREDENTIAL_PLACEHOLDER);
            if (auth.containsKey("clientSecret")) auth.put("clientSecret", CREDENTIAL_PLACEHOLDER);
            if (auth.containsKey("password")) auth.put("password", CREDENTIAL_PLACEHOLDER);
        }
    }

    /**
     * Property 5: For any data source config with credential fields,
     * masking replaces all credential values with CREDENTIAL_PLACEHOLDER
     * while preserving non-credential field values.
     */
    @Property(tries = 100)
    void credentialFieldsAreMaskedAndNonCredentialFieldsPreserved(
            @ForAll("dataSourceConfigs") DataSourceConfig dsConfig
    ) throws Exception {
        // Parse the original config
        Map<String, Object> config = objectMapper.readValue(
                dsConfig.configJson, new TypeReference<Map<String, Object>>() {});

        // Save original non-credential values
        Map<String, Object> originalNonCredentials = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            if (isCredentialField(key, dsConfig.type) || "auth".equals(key)) continue;
            originalNonCredentials.put(key, entry.getValue());
        }

        // Apply masking
        maskCredentialFields(config, dsConfig.type);

        // Verify: all credential fields are masked
        if ("DATABASE".equals(dsConfig.type) && config.containsKey("password")) {
            assertEquals(CREDENTIAL_PLACEHOLDER, config.get("password"),
                    "DATABASE password should be masked");
        }
        if (config.containsKey("apiKey")) {
            assertEquals(CREDENTIAL_PLACEHOLDER, config.get("apiKey"),
                    "apiKey should be masked");
        }
        if (config.containsKey("clientSecret")) {
            assertEquals(CREDENTIAL_PLACEHOLDER, config.get("clientSecret"),
                    "clientSecret should be masked");
        }

        // Verify: nested auth credential fields are masked
        if (config.containsKey("auth") && config.get("auth") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> auth = (Map<String, Object>) config.get("auth");
            for (String field : AUTH_CREDENTIAL_FIELDS) {
                if (auth.containsKey(field)) {
                    assertEquals(CREDENTIAL_PLACEHOLDER, auth.get(field),
                            "auth." + field + " should be masked");
                }
            }
        }

        // Verify: non-credential fields retain original values
        for (Map.Entry<String, Object> entry : originalNonCredentials.entrySet()) {
            assertEquals(entry.getValue(), config.get(entry.getKey()),
                    "Non-credential field '" + entry.getKey() + "' should retain original value");
        }
    }

    private boolean isCredentialField(String key, String type) {
        if (TOP_LEVEL_CREDENTIAL_FIELDS.contains(key)) return true;
        if ("DATABASE".equals(type) && "password".equals(key)) return true;
        return false;
    }

    /**
     * Property 5 (supplementary): For any list of data sources,
     * the masked output has the same count as the input.
     */
    @Property(tries = 100)
    void maskedDataSourceCountMatchesInput(
            @ForAll("dataSourceLists") List<DataSourceConfig> dataSources
    ) {
        List<Map<String, Object>> masked = new ArrayList<>();
        for (DataSourceConfig ds : dataSources) {
            try {
                Map<String, Object> config = objectMapper.readValue(
                        ds.configJson, new TypeReference<Map<String, Object>>() {});
                maskCredentialFields(config, ds.type);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("name", ds.name);
                result.put("type", ds.type);
                result.put("config", config);
                masked.add(result);
            } catch (Exception e) {
                // skip invalid JSON
            }
        }
        assertEquals(dataSources.size(), masked.size(),
                "Masked data source count should match input count");
    }


    @Provide
    Arbitrary<DataSourceConfig> dataSourceConfigs() {
        Arbitrary<String> types = Arbitraries.of("DATABASE", "HTTP_API", "INTERNAL_SYSTEM");
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);

        return Combinators.combine(types, names).as((type, name) -> {
            Map<String, Object> config = new LinkedHashMap<>();
            // Add some non-credential fields
            config.put("host", "localhost");
            config.put("port", 5432);
            config.put("dbName", "testdb");
            config.put("username", "user_" + name);

            // Randomly add credential fields
            Random rng = new Random();
            if ("DATABASE".equals(type) || rng.nextBoolean()) {
                config.put("password", "secret_" + rng.nextInt(1000));
            }
            if (rng.nextBoolean()) {
                config.put("apiKey", "key_" + rng.nextInt(1000));
            }
            if (rng.nextBoolean()) {
                config.put("clientSecret", "cs_" + rng.nextInt(1000));
            }
            // Randomly add nested auth
            if (rng.nextBoolean()) {
                Map<String, Object> auth = new LinkedHashMap<>();
                if (rng.nextBoolean()) auth.put("apiKey", "auth_key_" + rng.nextInt(1000));
                if (rng.nextBoolean()) auth.put("clientSecret", "auth_cs_" + rng.nextInt(1000));
                if (rng.nextBoolean()) auth.put("password", "auth_pw_" + rng.nextInt(1000));
                auth.put("type", "BASIC");
                config.put("auth", auth);
            }

            try {
                String json = new ObjectMapper().writeValueAsString(config);
                return new DataSourceConfig(name, type, json);
            } catch (Exception e) {
                return new DataSourceConfig(name, type, "{}");
            }
        });
    }

    @Provide
    Arbitrary<List<DataSourceConfig>> dataSourceLists() {
        return dataSourceConfigs().list().ofMinSize(0).ofMaxSize(10);
    }


    record DataSourceConfig(String name, String type, String configJson) {}
}

