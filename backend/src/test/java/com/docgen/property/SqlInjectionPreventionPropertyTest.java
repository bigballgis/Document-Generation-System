package com.docgen.property;

import com.docgen.service.DatabaseDataSourceService;
import com.docgen.service.DatabaseDataSourceService.NamedParameterResult;
import com.docgen.service.EncryptionService;
import net.jqwik.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Property-based tests for SQL injection prevention.
 *
 * <p>Verifies that {@link DatabaseDataSourceService#replaceNamedParameters} converts
 * named parameters ({@code :paramName}) to positional placeholders ({@code ?}) and
 * places the actual values into a separate list for binding via PreparedStatement.
 * This ensures injection payloads are never interpolated into the SQL string.</p>
 *
 * <p><b>Validates: Requirements 33.6</b></p>
 */
@Tag("Feature: low-code-document-generation-system, Property 10: SQL 注入防护")
class SqlInjectionPreventionPropertyTest {

    private final DatabaseDataSourceService service;

    SqlInjectionPreventionPropertyTest() {
        EncryptionService encryptionService = mock(EncryptionService.class);
        this.service = new DatabaseDataSourceService(encryptionService);
    }

    /**
     * Property 10: For any SQL injection pattern used as a parameter value,
     * replaceNamedParameters should produce a SQL string with '?' placeholder
     * and the injection payload should appear only in the values list, never
     * interpolated into the resulting SQL string.
     */
    @Property(tries = 100)
    void injectionPayloadShouldNeverAppearInResultingSql(
            @ForAll("sqlInjectionPatterns") String injectionPayload
    ) {
        String expectedSql = "SELECT id FROM users WHERE name = ?";
        String templateSql = "SELECT id FROM users WHERE name = :name";
        Map<String, Object> params = Map.of("name", injectionPayload);

        var result = service.replaceNamedParameters(templateSql, params);

        // The named parameter :name must be replaced with ? — the SQL is parameterized
        assertEquals(expectedSql, result.sql(),
                "Named parameter :name must be replaced with positional placeholder ?");

        // The injection payload must be in the values list for PreparedStatement binding
        assertEquals(1, result.values().size(),
                "Exactly one value should be collected for binding");
        assertEquals(injectionPayload, result.values().get(0),
                "The injection payload must be placed in the values list as-is");
    }

    /**
     * Property 10: For any SQL injection pattern used across multiple parameters,
     * all values should be bound via placeholders and none should leak into the SQL.
     */
    @Property(tries = 100)
    void multipleInjectionPayloadsShouldAllBeBoundViaPlaceholders(
            @ForAll("sqlInjectionPatterns") String payload1,
            @ForAll("sqlInjectionPatterns") String payload2
    ) {
        String expectedSql = "SELECT id FROM users WHERE name = ? AND email = ?";
        String templateSql = "SELECT id FROM users WHERE name = :name AND email = :email";
        Map<String, Object> params = Map.of("name", payload1, "email", payload2);

        var result = service.replaceNamedParameters(templateSql, params);

        // Both named parameters must be replaced with ?
        assertEquals(expectedSql, result.sql());

        // Both values must be in the values list for PreparedStatement binding
        assertEquals(2, result.values().size());
        assertTrue(result.values().contains(payload1));
        assertTrue(result.values().contains(payload2));
    }

    /**
     * Property 10: For any random string used as a parameter value (not just
     * known injection patterns), the replacement mechanism should always produce
     * parameterized SQL with the value in the bind list.
     */
    @Property(tries = 100)
    void anyArbitraryStringValueShouldBeBoundNotInterpolated(
            @ForAll("arbitraryParamValues") String value
    ) {
        String templateSql = "INSERT INTO logs (message) VALUES (:msg)";
        Map<String, Object> params = Map.of("msg", value);

        var result = service.replaceNamedParameters(templateSql, params);

        assertEquals("INSERT INTO logs (message) VALUES (?)", result.sql());
        assertEquals(1, result.values().size());
        assertEquals(value, result.values().get(0));

        // The value must not be present in the SQL (unless it's a substring of the
        // template SQL itself, e.g. empty string or single chars like '(' or ')')
        if (value.length() > 2 && !templateSql.contains(value)) {
            assertFalse(result.sql().contains(value),
                    "Parameter value must not be interpolated into SQL");
        }
    }

    // ── Generators ──

    @Provide
    Arbitrary<String> sqlInjectionPatterns() {
        return Arbitraries.oneOf(
                // Classic SQL injection patterns
                Arbitraries.of(
                        "'; DROP TABLE users; --",
                        "1 OR 1=1",
                        "' UNION SELECT * FROM passwords --",
                        "'; DELETE FROM users WHERE '1'='1",
                        "1; UPDATE users SET role='admin' WHERE id=1; --",
                        "' OR ''='",
                        "admin'--",
                        "1' OR '1'='1' /*",
                        "'; EXEC xp_cmdshell('dir'); --",
                        "' AND 1=CONVERT(int,(SELECT TOP 1 table_name FROM information_schema.tables))--"
                ),
                // Randomized injection-like strings with SQL keywords
                Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(100)
                        .map(s -> "' " + s + " --"),
                // Strings containing SQL keywords mixed with random content
                Arbitraries.oneOf(
                        Arbitraries.of("DROP", "DELETE", "UPDATE", "INSERT", "SELECT",
                                "UNION", "ALTER", "EXEC", "TRUNCATE", "CREATE")
                ).flatMap(keyword ->
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                                .map(suffix -> "'; " + keyword + " " + suffix + "; --")
                ),
                // Strings with special SQL characters
                Arbitraries.strings().withChars('\'', '"', ';', '-', '/', '*', '\\', '\0')
                        .ofMinLength(1).ofMaxLength(50)
        );
    }

    @Provide
    Arbitrary<String> arbitraryParamValues() {
        return Arbitraries.oneOf(
                // Normal strings
                Arbitraries.strings().ascii().ofMinLength(0).ofMaxLength(200),
                // Strings with unicode
                Arbitraries.strings().ofMinLength(1).ofMaxLength(100),
                // SQL injection patterns
                sqlInjectionPatterns()
        );
    }
}
