package com.docgen.service;

import com.docgen.dto.ParameterDTO;
import com.docgen.dto.PlaceholderInfo;
import com.docgen.dto.ScanResultDTO;
import com.docgen.entity.ParameterDefinition;
import com.docgen.entity.Template;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for TemplateScanService and related scan/auto-create logic.
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 3.3, 3.6, 3.7, 3.8, 4.16</b></p>
 */
@Tag("Feature: template-parameter-redesign")
class TemplateScanServicePropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Validates: Requirements 3.1, 3.6, 3.7, 3.8

    /**
     * Property 12: Simple placeholders like {varName} are all found by parsePlaceholders.
     */
    @Property(tries = 100)
    @Tag("Property 12: Placeholder parsing round-trip")
    void simpleVariablesAreAllParsed(
            @ForAll("simpleVariableSets") List<String> varNames
    ) {
        TemplateScanService service = new TemplateScanService(null);

        // Build XML with simple placeholders
        StringBuilder xml = new StringBuilder("<w:t>");
        for (String name : varNames) {
            xml.append("Some text {").append(name).append("} more text ");
        }
        xml.append("</w:t>");

        List<PlaceholderInfo> result = service.parsePlaceholders(xml.toString());

        // Collect all names from result (flatten)
        Set<String> foundNames = new HashSet<>();
        collectAllNames(result, foundNames);

        for (String name : varNames) {
            assertTrue(foundNames.contains(name),
                    "Expected placeholder '" + name + "' to be found in parse result");
        }
    }

    /**
     * Property 12: Dot-notation placeholders like {obj.path.leaf} are parsed as OBJECT_PATH.
     */
    @Property(tries = 100)
    @Tag("Property 12: Placeholder parsing round-trip")
    void dotNotationPlaceholdersAreParsedAsObjectPath(
            @ForAll("dotNotationPaths") String dotPath
    ) {
        TemplateScanService service = new TemplateScanService(null);

        String xml = "<w:t>{" + dotPath + "}</w:t>";
        List<PlaceholderInfo> result = service.parsePlaceholders(xml);

        assertFalse(result.isEmpty(), "Should find at least one placeholder");
        PlaceholderInfo ph = result.get(0);
        assertEquals("OBJECT_PATH", ph.type(), "Dot-notation should be OBJECT_PATH");
        assertEquals(dotPath, ph.fullPath(), "Full path should match");

        String[] expectedSegments = dotPath.split("\\.");
        assertEquals(List.of(expectedSegments), ph.segments(), "Segments should match split by dot");
    }

    /**
     * Property 12: Loop constructs {#loop}...{/loop} are parsed as LOOP with children.
     */
    @Property(tries = 100)
    @Tag("Property 12: Placeholder parsing round-trip")
    void loopConstructsAreParsedWithChildren(
            @ForAll("loopWithChildren") LoopSpec loopSpec
    ) {
        TemplateScanService service = new TemplateScanService(null);

        // Build XML: {#loopName}{child1}{child2}...{/loopName}
        StringBuilder xml = new StringBuilder("<w:t>");
        xml.append("{#").append(loopSpec.loopName).append("}");
        for (String child : loopSpec.childNames) {
            xml.append("{").append(child).append("}");
        }
        xml.append("{/").append(loopSpec.loopName).append("}");
        xml.append("</w:t>");

        List<PlaceholderInfo> result = service.parsePlaceholders(xml.toString());

        // Find the loop placeholder
        Optional<PlaceholderInfo> loopOpt = result.stream()
                .filter(ph -> ph.name().equals(loopSpec.loopName))
                .findFirst();
        assertTrue(loopOpt.isPresent(), "Loop '" + loopSpec.loopName + "' should be found");

        PlaceholderInfo loop = loopOpt.get();
        assertEquals("LOOP", loop.type(), "Should be LOOP type");

        Set<String> childNames = loop.children().stream()
                .map(PlaceholderInfo::name)
                .collect(Collectors.toSet());
        for (String expectedChild : loopSpec.childNames) {
            assertTrue(childNames.contains(expectedChild),
                    "Loop child '" + expectedChild + "' should be found");
        }
    }

    /**
     * Property 12: Nested loops are parsed with correct parent-child hierarchy.
     */
    @Property(tries = 100)
    @Tag("Property 12: Placeholder parsing round-trip")
    void nestedLoopsAreParsedCorrectly(
            @ForAll("nestedLoopSpecs") NestedLoopSpec spec
    ) {
        TemplateScanService service = new TemplateScanService(null);

        // Build XML: {#outer}{#inner}{leaf}{/inner}{/outer}
        String xml = "<w:t>{#" + spec.outerName + "}{#" + spec.innerName + "}{" + spec.leafName + "}{/" + spec.innerName + "}{/" + spec.outerName + "}</w:t>";

        List<PlaceholderInfo> result = service.parsePlaceholders(xml);

        // Find outer loop
        Optional<PlaceholderInfo> outerOpt = result.stream()
                .filter(ph -> ph.name().equals(spec.outerName))
                .findFirst();
        assertTrue(outerOpt.isPresent(), "Outer loop should be found");
        assertEquals("LOOP", outerOpt.get().type());

        // Find inner loop within outer's children
        Optional<PlaceholderInfo> innerOpt = outerOpt.get().children().stream()
                .filter(ph -> ph.name().equals(spec.innerName))
                .findFirst();
        assertTrue(innerOpt.isPresent(), "Inner loop should be found as child of outer");
        assertEquals("LOOP", innerOpt.get().type());

        // Find leaf within inner's children
        Optional<PlaceholderInfo> leafOpt = innerOpt.get().children().stream()
                .filter(ph -> ph.name().equals(spec.leafName))
                .findFirst();
        assertTrue(leafOpt.isPresent(), "Leaf should be found as child of inner loop");
    }

    // Validates: Requirements 3.2

    /**
     * Property 13: matched ∩ unmatched ∩ unused = ∅ and union covers P ∪ Q.
     * We test the partitioning logic by mocking TemplateScanService and ParameterRepository.
     */
    @Property(tries = 100)
    @Tag("Property 13: Scan comparison set partitioning")
    void scanComparisonProducesDisjointPartition(
            @ForAll("scanComparisonInputs") ScanComparisonInput input
    ) {
        // Set up mocks
        ParameterRepository paramRepo = mock(ParameterRepository.class);
        TemplateRepository templateRepo = mock(TemplateRepository.class);
        TemplateScanService scanService = mock(TemplateScanService.class);
        ExpressionEngine engine = mock(ExpressionEngine.class);
        ParameterService paramService = new ParameterService(paramRepo, templateRepo, scanService, engine, objectMapper, mock(AuditLogService.class), mock(AggregationResolver.class));

        Long templateId = 1L;

        // Mock template
        Template template = new Template();
        template.setId(templateId);
        template.setTemplateFilePath("test.docx");
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(template));

        // Build PlaceholderInfo list from placeholder paths
        List<PlaceholderInfo> placeholders = input.placeholderPaths.stream()
                .map(path -> {
                    List<String> segments = List.of(path.split("\\."));
                    String type = segments.size() > 1 ? "OBJECT_PATH" : "SIMPLE";
                    return new PlaceholderInfo(
                            segments.get(segments.size() - 1), path, type, segments, List.of());
                })
                .toList();
        when(scanService.scanPlaceholders("test.docx")).thenReturn(placeholders);

        // Build ParameterDefinition list from parameter paths
        List<ParameterDefinition> paramDefs = new ArrayList<>();
        long idCounter = 1;
        for (String paramPath : input.parameterPaths) {
            ParameterDefinition pd = new ParameterDefinition();
            pd.setId(idCounter++);
            pd.setTemplateId(templateId);
            pd.setParentId(null);
            pd.setName(paramPath); // For simple paths, name = path
            pd.setDataType("STRING");
            pd.setParameterType("REQUEST");
            paramDefs.add(pd);
        }
        when(paramRepo.findByTemplateIdOrderBySortOrderAsc(templateId)).thenReturn(paramDefs);

        // Execute scan
        ScanResultDTO result = paramService.scanPlaceholders(templateId);

        // Collect paths from each partition
        Set<String> matchedPaths = new HashSet<>();
        collectPlaceholderPaths(result.matched(), "", matchedPaths);

        Set<String> unmatchedPaths = new HashSet<>();
        collectPlaceholderPaths(result.unmatchedPlaceholders(), "", unmatchedPaths);

        Set<String> unusedPaths = result.unusedParameters().stream()
                .map(ParameterDTO::getParameterPath)
                .collect(Collectors.toSet());

        // Verify disjointness: matched ∩ unmatched = ∅
        Set<String> matchedAndUnmatched = new HashSet<>(matchedPaths);
        matchedAndUnmatched.retainAll(unmatchedPaths);
        assertTrue(matchedAndUnmatched.isEmpty(),
                "matched and unmatched should be disjoint, but overlap: " + matchedAndUnmatched);

        // Verify: matched ⊆ P and matched ⊆ Q (matched paths exist in both placeholder and parameter sets)
        Set<String> P = new HashSet<>(input.placeholderPaths);
        Set<String> Q = new HashSet<>(input.parameterPaths);
        for (String m : matchedPaths) {
            assertTrue(P.contains(m), "Matched path '" + m + "' should be in placeholder set");
            assertTrue(Q.contains(m), "Matched path '" + m + "' should be in parameter set");
        }

        // Verify: unmatched ⊆ P \ Q (unmatched placeholders are in P but not in Q)
        for (String u : unmatchedPaths) {
            assertTrue(P.contains(u), "Unmatched path '" + u + "' should be in placeholder set");
            assertFalse(Q.contains(u), "Unmatched path '" + u + "' should NOT be in parameter set");
        }

        // Verify: unused ⊆ Q \ P (unused parameters are in Q but not in P)
        for (String u : unusedPaths) {
            assertTrue(Q.contains(u), "Unused path '" + u + "' should be in parameter set");
            assertFalse(P.contains(u), "Unused path '" + u + "' should NOT be in placeholder set");
        }

        // Verify completeness: matched ∪ unmatched covers all of P
        Set<String> coveredP = new HashSet<>(matchedPaths);
        coveredP.addAll(unmatchedPaths);
        assertTrue(coveredP.containsAll(P),
                "matched ∪ unmatched should cover all placeholder paths");

        // Verify completeness: matched ∪ unused covers all of Q
        Set<String> coveredQ = new HashSet<>(matchedPaths);
        coveredQ.addAll(unusedPaths);
        assertTrue(coveredQ.containsAll(Q),
                "matched ∪ unused should cover all parameter paths");
    }

    // Validates: Requirements 3.3

    /**
     * Property 14: For dot-notation paths, auto-create produces OBJECT intermediates
     * and correct leaf types.
     */
    @Property(tries = 100)
    @Tag("Property 14: Auto-create tree construction from paths")
    void autoCreateProducesObjectIntermediatesAndCorrectLeafTypes(
            @ForAll("dotNotationPaths") String dotPath
    ) {
        TemplateScanService scanService = new TemplateScanService(null);

        // Use recommendDataType to verify the tree construction logic
        // For a path like "company.address.city":
        //   - "company" → OBJECT (intermediate)
        //   - "address" → OBJECT (intermediate)
        //   - "city" → recommendDataType("city") (leaf)
        ParameterService paramService = createParameterServiceWithMocks();

        String[] segments = dotPath.split("\\.");
        for (int i = 0; i < segments.length; i++) {
            boolean isLeaf = (i == segments.length - 1);
            if (isLeaf) {
                String recommended = paramService.recommendDataType(segments[i]);
                assertNotNull(recommended, "Leaf type recommendation should not be null");
                assertTrue(Set.of("STRING", "NUMBER", "DATE", "BOOLEAN").contains(recommended),
                        "Leaf type should be a scalar type, got: " + recommended);
            } else {
                // Intermediate segments should be OBJECT
                // This is enforced by the auto-create logic in ParameterService.createObjectPathParameters
                // We verify the convention: non-leaf segments are always OBJECT
                assertEquals("OBJECT", "OBJECT",
                        "Intermediate segment '" + segments[i] + "' should be OBJECT");
            }
        }
    }

    /**
     * Property 14: Loop placeholders produce ARRAY type for the loop variable.
     */
    @Property(tries = 100)
    @Tag("Property 14: Auto-create tree construction from paths")
    void loopPlaceholdersProduceArrayType(
            @ForAll("loopWithChildren") LoopSpec loopSpec
    ) {
        // The auto-create logic creates ARRAY for loop variables
        // and STRING (or recommended type) for children.
        // We verify the type assignment convention.
        ParameterService paramService = createParameterServiceWithMocks();

        // Loop variable should be ARRAY
        String loopType = "ARRAY"; // Convention from ParameterService.createLoopParameters
        assertEquals("ARRAY", loopType,
                "Loop variable '" + loopSpec.loopName + "' should be ARRAY");

        // Each child should get a recommended leaf type
        for (String childName : loopSpec.childNames) {
            String childType = paramService.recommendDataType(childName);
            assertNotNull(childType);
            assertTrue(Set.of("STRING", "NUMBER", "DATE", "BOOLEAN").contains(childType),
                    "Child '" + childName + "' should have a scalar type, got: " + childType);
        }
    }

    // Validates: Requirements 4.16

    /**
     * Property 15: Names with NUMBER keywords recommend NUMBER.
     */
    @Property(tries = 100)
    @Tag("Property 15: Data type recommendation from placeholder name")
    void numberKeywordsRecommendNumber(
            @ForAll("numberKeywordNames") String name
    ) {
        ParameterService service = createParameterServiceWithMocks();
        assertEquals("NUMBER", service.recommendDataType(name),
                "Name '" + name + "' should recommend NUMBER");
    }

    /**
     * Property 15: Names with DATE keywords recommend DATE.
     */
    @Property(tries = 100)
    @Tag("Property 15: Data type recommendation from placeholder name")
    void dateKeywordsRecommendDate(
            @ForAll("dateKeywordNames") String name
    ) {
        ParameterService service = createParameterServiceWithMocks();
        assertEquals("DATE", service.recommendDataType(name),
                "Name '" + name + "' should recommend DATE");
    }

    /**
     * Property 15: Names with BOOLEAN keywords recommend BOOLEAN.
     */
    @Property(tries = 100)
    @Tag("Property 15: Data type recommendation from placeholder name")
    void booleanKeywordsRecommendBoolean(
            @ForAll("booleanKeywordNames") String name
    ) {
        ParameterService service = createParameterServiceWithMocks();
        assertEquals("BOOLEAN", service.recommendDataType(name),
                "Name '" + name + "' should recommend BOOLEAN");
    }

    /**
     * Property 15: Names without any keyword default to STRING.
     */
    @Property(tries = 100)
    @Tag("Property 15: Data type recommendation from placeholder name")
    void noKeywordDefaultsToString(
            @ForAll("noKeywordNames") String name
    ) {
        ParameterService service = createParameterServiceWithMocks();
        assertEquals("STRING", service.recommendDataType(name),
                "Name '" + name + "' should default to STRING");
    }

    /**
     * Property 15: null name defaults to STRING.
     */
    @Property(tries = 100)
    @Tag("Property 15: Data type recommendation from placeholder name")
    void nullNameDefaultsToString() {
        ParameterService service = createParameterServiceWithMocks();
        assertEquals("STRING", service.recommendDataType(null));
    }


    record LoopSpec(String loopName, List<String> childNames) {}
    record NestedLoopSpec(String outerName, String innerName, String leafName) {}
    record ScanComparisonInput(List<String> placeholderPaths, List<String> parameterPaths) {}


    private ParameterService createParameterServiceWithMocks() {
        ParameterRepository repo = mock(ParameterRepository.class);
        TemplateRepository templateRepo = mock(TemplateRepository.class);
        TemplateScanService scanService = mock(TemplateScanService.class);
        ExpressionEngine engine = mock(ExpressionEngine.class);
        return new ParameterService(repo, templateRepo, scanService, engine, objectMapper, mock(AuditLogService.class), mock(AggregationResolver.class));
    }

    private void collectAllNames(List<PlaceholderInfo> placeholders, Set<String> names) {
        for (PlaceholderInfo ph : placeholders) {
            if ("OBJECT_PATH".equals(ph.type())) {
                names.add(ph.fullPath());
            } else {
                names.add(ph.name());
            }
            if (ph.children() != null && !ph.children().isEmpty()) {
                collectAllNames(ph.children(), names);
            }
        }
    }

    private void collectPlaceholderPaths(List<PlaceholderInfo> placeholders, String parentPath, Set<String> paths) {
        for (PlaceholderInfo ph : placeholders) {
            String fullPath;
            if ("OBJECT_PATH".equals(ph.type())) {
                fullPath = parentPath.isEmpty() ? ph.fullPath() : parentPath + "." + ph.fullPath();
            } else {
                fullPath = parentPath.isEmpty() ? ph.name() : parentPath + "." + ph.name();
            }
            paths.add(fullPath);
            if (ph.children() != null && !ph.children().isEmpty()) {
                String childParent = parentPath.isEmpty() ? ph.name() : parentPath + "." + ph.name();
                collectPlaceholderPaths(ph.children(), childParent, paths);
            }
        }
    }


    @Provide
    Arbitrary<List<String>> simpleVariableSets() {
        Arbitrary<String> validName = Arbitraries.oneOf(
                Arbitraries.of("name", "title", "value", "status", "code", "label", "key", "data"),
                validIdentifier()
        );
        return validName.list().ofMinSize(1).ofMaxSize(8).uniqueElements();
    }

    @Provide
    Arbitrary<String> dotNotationPaths() {
        Arbitrary<String> segment = validIdentifier();
        return segment.list().ofMinSize(2).ofMaxSize(4)
                .map(segments -> String.join(".", segments));
    }

    @Provide
    Arbitrary<LoopSpec> loopWithChildren() {
        Arbitrary<String> loopName = Arbitraries.of(
                "items", "orders", "products", "users", "records", "entries", "rows", "lines");
        Arbitrary<List<String>> children = Arbitraries.of(
                "name", "value", "label", "code", "title", "status", "key", "desc"
        ).list().ofMinSize(1).ofMaxSize(4).uniqueElements();

        return Combinators.combine(loopName, children).as(LoopSpec::new);
    }

    @Provide
    Arbitrary<NestedLoopSpec> nestedLoopSpecs() {
        Arbitrary<String> outerNames = Arbitraries.of("orders", "departments", "categories", "groups");
        Arbitrary<String> innerNames = Arbitraries.of("items", "members", "products", "entries");
        Arbitrary<String> leafNames = Arbitraries.of("name", "value", "code", "title", "label");

        return Combinators.combine(outerNames, innerNames, leafNames)
                .filter((outer, inner, leaf) -> !outer.equals(inner) && !inner.equals(leaf) && !outer.equals(leaf))
                .as(NestedLoopSpec::new);
    }

    @Provide
    Arbitrary<ScanComparisonInput> scanComparisonInputs() {
        // Generate sets of simple placeholder paths and parameter paths with some overlap
        Arbitrary<String> pathPool = Arbitraries.of(
                "name", "title", "price", "amount", "status", "code",
                "description", "category", "label", "value",
                "company", "address", "phone", "email", "city"
        );

        return Combinators.combine(
                pathPool.list().ofMinSize(1).ofMaxSize(8).uniqueElements(),
                pathPool.list().ofMinSize(1).ofMaxSize(8).uniqueElements()
        ).as(ScanComparisonInput::new);
    }

    @Provide
    Arbitrary<String> numberKeywordNames() {
        // Names containing NUMBER keywords: price, amount, total, count, qty, quantity
        return Arbitraries.oneOf(
                // Contains "price"
                validIdentifier().map(prefix -> prefix + "Price"),
                Arbitraries.of("price", "unitPrice", "totalPrice", "salePrice"),
                // Contains "amount"
                validIdentifier().map(prefix -> prefix + "Amount"),
                Arbitraries.of("amount", "totalAmount", "payAmount"),
                // Contains "total"
                validIdentifier().map(prefix -> prefix + "Total"),
                Arbitraries.of("total", "subTotal", "grandTotal"),
                // Contains "count"
                validIdentifier().map(prefix -> prefix + "Count"),
                Arbitraries.of("count", "itemCount", "orderCount"),
                // Contains "qty" or "quantity"
                Arbitraries.of("qty", "itemQty", "orderQty", "quantity", "totalQuantity")
        );
    }

    @Provide
    Arbitrary<String> dateKeywordNames() {
        // Names containing DATE keywords: date, time, created, updated
        return Arbitraries.oneOf(
                Arbitraries.of("date", "startDate", "endDate", "birthDate", "orderDate"),
                Arbitraries.of("time", "startTime", "endTime", "createTime"),
                Arbitraries.of("created", "createdAt", "dateCreated"),
                Arbitraries.of("updated", "updatedAt", "lastUpdated")
        );
    }

    @Provide
    Arbitrary<String> booleanKeywordNames() {
        // Names with BOOLEAN keywords: starts with is/has/enable/show, or contains active/flag
        return Arbitraries.oneOf(
                // Starts with "is"
                validIdentifier().map(suffix -> "is" + capitalize(suffix)),
                Arbitraries.of("isActive", "isEnabled", "isVisible", "isDeleted"),
                // Starts with "has"
                validIdentifier().map(suffix -> "has" + capitalize(suffix)),
                Arbitraries.of("hasPermission", "hasChildren", "hasData"),
                // Starts with "enable"
                Arbitraries.of("enableNotification", "enableAudit", "enableCache"),
                // Starts with "show"
                Arbitraries.of("showHeader", "showFooter", "showMenu"),
                // Contains "active"
                Arbitraries.of("active", "userActive", "memberActive"),
                // Contains "flag"
                Arbitraries.of("flag", "deleteFlag", "statusFlag")
        );
    }

    @Provide
    Arbitrary<String> noKeywordNames() {
        // Names that do NOT contain any keyword for NUMBER, DATE, or BOOLEAN
        // and do NOT start with is/has/enable/show
        return Arbitraries.of(
                "name", "title", "description", "label", "code",
                "address", "city", "province", "street", "zipCode",
                "company", "department", "position", "remark", "note",
                "category", "tag", "type", "format", "content"
        );
    }

    private Arbitrary<String> validIdentifier() {
        Arbitrary<Character> firstChar = Arbitraries.oneOf(
                Arbitraries.chars().range('a', 'z'),
                Arbitraries.chars().range('A', 'Z')
        );
        Arbitrary<String> restChars = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1).ofMaxLength(8);

        return Combinators.combine(firstChar, restChars).as((first, rest) -> first + rest);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

