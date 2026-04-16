package com.docgen.service;

import com.docgen.dto.PlaceholderInfo;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for scanning .docx template files to extract Docxtemplater placeholders.
 * Supports simple variables, dot-notation object paths, loop constructs, nested loops,
 * and conditional blocks.
 *
 * Extracted and enhanced from TemplateVariableService.extractVariableNames.
 */
@Service
public class TemplateScanService {

    private static final Logger log = LoggerFactory.getLogger(TemplateScanService.class);

    /**
     * Pattern to match Docxtemplater placeholders in XML content.
     * Captures:
     *   group 1 — tag prefix: # (open block), / (close block), ^ (inverted), or empty (simple)
     *   group 2 — variable/block name, supports dot-notation (e.g., company.address.city)
     *   group 3 — optional argument after space (e.g., {#if showTotal} → argument = showTotal)
     */
    static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "\\{([#/^]?)([a-zA-Z_][a-zA-Z0-9_.]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*)(?:\\s+([a-zA-Z_][a-zA-Z0-9_.]*))?\\}");

    private final MinioClient minioClient;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public TemplateScanService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * Scan a .docx template file stored in MinIO for all Docxtemplater placeholders.
     *
     * @param templateFilePath the object path in MinIO
     * @return list of top-level PlaceholderInfo (loops/conditions contain children)
     */
    public List<PlaceholderInfo> scanPlaceholders(String templateFilePath) {
        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(templateFilePath)
                .build())) {

            String xmlContent = extractXmlFromDocx(is);
            List<PlaceholderInfo> placeholders = parsePlaceholders(xmlContent);
            log.info("Scanned template '{}': found {} top-level placeholders", templateFilePath, placeholders.size());
            return placeholders;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMETER_SCAN_FAILED,
                    "扫描模板占位符失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Read all XML parts from a .docx (ZIP) file and concatenate their content.
     * Docxtemplater variables can appear in document.xml, header*.xml, footer*.xml, etc.
     */
    String extractXmlFromDocx(InputStream docxStream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(docxStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.startsWith("word/") && entryName.endsWith(".xml")) {
                    sb.append(new String(zis.readAllBytes()));
                }
            }
        }
        return sb.toString();
    }

    /**
     * Parse XML content for Docxtemplater placeholders and build a structured list.
     * Handles:
     * - Simple variables: {variableName}
     * - Dot-notation paths: {company.address.city}
     * - Loop constructs: {#items}...{/items}
     * - Nested loops: {#orders}{#items}...{/items}{/orders}
     * - Conditions: {#if condition}...{/if}
     *
     * @param xmlContent concatenated XML from the .docx file
     * @return list of top-level PlaceholderInfo with nested children for loops/conditions
     */
    List<PlaceholderInfo> parsePlaceholders(String xmlContent) {
        List<Token> tokens = tokenize(xmlContent);
        return buildTree(tokens);
    }

    // ── Internal token representation ──

    /**
     * Represents a parsed token from the template XML.
     */
    enum TokenType { SIMPLE, OPEN_BLOCK, CLOSE_BLOCK, CONDITION_OPEN, CONDITION_CLOSE }

    record Token(TokenType type, String name, String fullMatch) {}

    /**
     * Tokenize the XML content into a flat list of placeholder tokens.
     */
    List<Token> tokenize(String xmlContent) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(xmlContent);

        while (matcher.find()) {
            String prefix = matcher.group(1);
            String name = matcher.group(2);
            String argument = matcher.group(3);
            String fullMatch = matcher.group(0);

            switch (prefix) {
                case "#" -> {
                    if ("if".equals(name)) {
                        // {#if condition} — condition open, the actual variable is the argument
                        String conditionVar = (argument != null) ? argument : name;
                        tokens.add(new Token(TokenType.CONDITION_OPEN, conditionVar, fullMatch));
                    } else {
                        // {#loopName} — loop/block open
                        tokens.add(new Token(TokenType.OPEN_BLOCK, name, fullMatch));
                    }
                }
                case "/" -> {
                    if ("if".equals(name)) {
                        tokens.add(new Token(TokenType.CONDITION_CLOSE, "if", fullMatch));
                    } else {
                        // {/loopName} — loop/block close
                        tokens.add(new Token(TokenType.CLOSE_BLOCK, name, fullMatch));
                    }
                }
                case "^" -> {
                    // {^invertedBlock} — treat as open block (inverted section)
                    tokens.add(new Token(TokenType.OPEN_BLOCK, name, fullMatch));
                }
                default -> {
                    // Simple variable or dot-notation path
                    tokens.add(new Token(TokenType.SIMPLE, name, fullMatch));
                }
            }
        }
        return tokens;
    }

    /**
     * Build a tree of PlaceholderInfo from the flat token list.
     * Uses a stack to track open blocks (loops/conditions) and nest children.
     */
    List<PlaceholderInfo> buildTree(List<Token> tokens) {
        // Each level in the stack: (blockName, children list)
        Deque<BuildContext> stack = new ArrayDeque<>();
        stack.push(new BuildContext(null, null, new ArrayList<>()));

        // Track seen placeholders at each scope level to avoid duplicates
        Deque<Set<String>> seenStack = new ArrayDeque<>();
        seenStack.push(new LinkedHashSet<>());

        for (Token token : tokens) {
            switch (token.type()) {
                case SIMPLE -> {
                    String name = token.name();
                    Set<String> seen = seenStack.peek();
                    if (seen != null && !seen.contains(name)) {
                        seen.add(name);
                        stack.peek().children().add(buildSimplePlaceholder(name));
                    }
                }
                case OPEN_BLOCK -> {
                    stack.push(new BuildContext(token.name(), "LOOP", new ArrayList<>()));
                    seenStack.push(new LinkedHashSet<>());
                }
                case CONDITION_OPEN -> {
                    stack.push(new BuildContext(token.name(), "CONDITION", new ArrayList<>()));
                    seenStack.push(new LinkedHashSet<>());
                }
                case CLOSE_BLOCK -> {
                    if (stack.size() > 1) {
                        BuildContext closed = stack.pop();
                        seenStack.pop();
                        PlaceholderInfo loopInfo = new PlaceholderInfo(
                                closed.name(),
                                closed.name(),
                                "LOOP",
                                List.of(closed.name()),
                                List.copyOf(closed.children())
                        );
                        Set<String> parentSeen = seenStack.peek();
                        if (parentSeen != null && !parentSeen.contains(closed.name())) {
                            parentSeen.add(closed.name());
                            stack.peek().children().add(loopInfo);
                        }
                    }
                }
                case CONDITION_CLOSE -> {
                    if (stack.size() > 1 && "CONDITION".equals(stack.peek().type())) {
                        BuildContext closed = stack.pop();
                        seenStack.pop();
                        PlaceholderInfo condInfo = new PlaceholderInfo(
                                closed.name(),
                                closed.name(),
                                "CONDITION",
                                List.of(closed.name()),
                                List.copyOf(closed.children())
                        );
                        Set<String> parentSeen = seenStack.peek();
                        if (parentSeen != null && !parentSeen.contains(closed.name())) {
                            parentSeen.add(closed.name());
                            stack.peek().children().add(condInfo);
                        }
                    }
                }
            }
        }

        // Return the root-level children
        return List.copyOf(stack.peek().children());
    }

    record BuildContext(String name, String type, List<PlaceholderInfo> children) {}

    /**
     * Build a PlaceholderInfo for a simple variable or dot-notation path.
     * - "name" → SIMPLE with segments ["name"]
     * - "company.address.city" → OBJECT_PATH with segments ["company", "address", "city"]
     */
    private PlaceholderInfo buildSimplePlaceholder(String name) {
        List<String> segments = List.of(name.split("\\."));
        if (segments.size() > 1) {
            return new PlaceholderInfo(
                    segments.get(segments.size() - 1),
                    name,
                    "OBJECT_PATH",
                    segments,
                    List.of()
            );
        } else {
            return new PlaceholderInfo(
                    name,
                    name,
                    "SIMPLE",
                    segments,
                    List.of()
            );
        }
    }
}
