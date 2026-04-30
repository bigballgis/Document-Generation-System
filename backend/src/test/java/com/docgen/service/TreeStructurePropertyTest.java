package com.docgen.service;

import com.docgen.dto.ParameterDTO;
import com.docgen.entity.ParameterDefinition;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based test for tree structure correctness.
 *
 * <p><b>Validates: Requirements 2.2</b></p>
 */
@Tag("feature-template-parameter-redesign")
class TreeStructurePropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 10: The GET tree response nesting matches parent_id relationships.
     * Each parameter's children array contains exactly the parameters whose parent_id
     * equals the parameter's id, and roots have parent_id == null.
     */
    @Property(tries = 200)
    @Tag("property-10-tree-structure-correctness")
    void treeNestingMatchesParentIdRelationships(
            @ForAll("randomParameterTrees") List<ParameterDefinition> flatParams
    ) {
        ParameterService service = createServiceWithMocks();

        List<ParameterDTO> tree = service.buildTree(flatParams);

        // Build expected parent->children mapping from flat list
        Map<Long, List<Long>> expectedChildrenByParent = new HashMap<>();
        Set<Long> rootIds = new LinkedHashSet<>();
        for (ParameterDefinition p : flatParams) {
            if (p.getParentId() == null) {
                rootIds.add(p.getId());
            } else {
                expectedChildrenByParent
                        .computeIfAbsent(p.getParentId(), k -> new ArrayList<>())
                        .add(p.getId());
            }
        }

        // Verify roots: tree should contain exactly the root-level parameters
        Set<Long> actualRootIds = tree.stream().map(ParameterDTO::getId).collect(Collectors.toSet());
        assertEquals(rootIds, actualRootIds,
                "Tree roots should be exactly the parameters with parent_id == null");

        // Verify all nodes are present in the tree (flatten and count)
        List<ParameterDTO> allFromTree = new ArrayList<>();
        flattenTree(tree, allFromTree);
        assertEquals(flatParams.size(), allFromTree.size(),
                "Total nodes in tree should equal total flat parameters");

        // Verify each node's children match the expected parent_id relationships
        Map<Long, ParameterDTO> dtoMap = allFromTree.stream()
                .collect(Collectors.toMap(ParameterDTO::getId, d -> d));

        for (ParameterDTO dto : allFromTree) {
            List<Long> expectedChildIds = expectedChildrenByParent
                    .getOrDefault(dto.getId(), Collections.emptyList());
            List<Long> actualChildIds = dto.getChildren() != null
                    ? dto.getChildren().stream().map(ParameterDTO::getId).toList()
                    : Collections.emptyList();

            assertEquals(new HashSet<>(expectedChildIds), new HashSet<>(actualChildIds),
                    "Children of parameter '" + dto.getName() + "' (id=" + dto.getId()
                            + ") should match parent_id references");
        }
    }

    /**
     * Property 10: Children at each level are ordered by sortOrder ascending.
     */
    @Property(tries = 200)
    @Tag("property-10-tree-structure-correctness")
    void childrenAreOrderedBySortOrder(
            @ForAll("randomParameterTreesWithSortOrder") List<ParameterDefinition> flatParams
    ) {
        ParameterService service = createServiceWithMocks();

        List<ParameterDTO> tree = service.buildTree(flatParams);

        // Verify sort order at every level
        verifyChildrenSortOrder(tree);
    }

    /**
     * Property 10: Leaf nodes (no children in flat list) have empty children arrays in tree.
     */
    @Property(tries = 200)
    @Tag("property-10-tree-structure-correctness")
    void leafNodesHaveEmptyChildren(
            @ForAll("randomParameterTrees") List<ParameterDefinition> flatParams
    ) {
        ParameterService service = createServiceWithMocks();

        // Identify which IDs are parents
        Set<Long> parentIds = flatParams.stream()
                .map(ParameterDefinition::getParentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<ParameterDTO> tree = service.buildTree(flatParams);
        List<ParameterDTO> allFromTree = new ArrayList<>();
        flattenTree(tree, allFromTree);

        for (ParameterDTO dto : allFromTree) {
            if (!parentIds.contains(dto.getId())) {
                // This is a leaf node
                assertTrue(dto.getChildren() == null || dto.getChildren().isEmpty(),
                        "Leaf node '" + dto.getName() + "' should have empty children");
            }
        }
    }


    private void flattenTree(List<ParameterDTO> nodes, List<ParameterDTO> result) {
        if (nodes == null) return;
        for (ParameterDTO node : nodes) {
            result.add(node);
            if (node.getChildren() != null) {
                flattenTree(node.getChildren(), result);
            }
        }
    }

    private void verifyChildrenSortOrder(List<ParameterDTO> siblings) {
        if (siblings == null || siblings.isEmpty()) return;

        // Verify this level is sorted by sortOrder
        for (int i = 1; i < siblings.size(); i++) {
            assertTrue(siblings.get(i - 1).getSortOrder() <= siblings.get(i).getSortOrder(),
                    "Children should be ordered by sortOrder: "
                            + siblings.get(i - 1).getName() + " (sort=" + siblings.get(i - 1).getSortOrder()
                            + ") should come before "
                            + siblings.get(i).getName() + " (sort=" + siblings.get(i).getSortOrder() + ")");
        }

        // Recurse into each child's children
        for (ParameterDTO child : siblings) {
            verifyChildrenSortOrder(child.getChildren());
        }
    }

    private ParameterService createServiceWithMocks() {
        return new ParameterService(
                mock(ParameterRepository.class),
                mock(TemplateRepository.class),
                mock(TemplateScanService.class),
                mock(ExpressionEngine.class),
                objectMapper,
                mock(AuditLogService.class),
                mock(AggregationResolver.class)
        );
    }

    private static ParameterDefinition makeParam(Long id, Long templateId, Long parentId,
                                                   String name, String dataType, int sortOrder) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(templateId);
        p.setParentId(parentId);
        p.setName(name);
        p.setDataType(dataType);
        p.setParameterType("REQUEST");
        p.setSortOrder(sortOrder);
        return p;
    }


    @Provide
    Arbitrary<List<ParameterDefinition>> randomParameterTrees() {
        return Arbitraries.integers().between(1, 15).flatMap(size ->
                Arbitraries.randomValue(random -> generateTree(size, random, false))
        );
    }

    @Provide
    Arbitrary<List<ParameterDefinition>> randomParameterTreesWithSortOrder() {
        return Arbitraries.integers().between(2, 15).flatMap(size ->
                Arbitraries.randomValue(random -> generateTree(size, random, true))
        );
    }

    private List<ParameterDefinition> generateTree(int size, Random random, boolean varySortOrder) {
        List<ParameterDefinition> tree = new ArrayList<>();
        Long templateId = 1L;

        // First node is always root
        int sortOrder = varySortOrder ? random.nextInt(10) : 0;
        tree.add(makeParam(1L, templateId, null, "root", "OBJECT", sortOrder));

        for (int i = 2; i <= size; i++) {
            // Pick a random existing node as parent (ensure it's a container type)
            int parentIdx = random.nextInt(tree.size());
            ParameterDefinition parent = tree.get(parentIdx);
            // Ensure parent is OBJECT or ARRAY for valid tree
            if (!"OBJECT".equals(parent.getDataType()) && !"ARRAY".equals(parent.getDataType())) {
                parent.setDataType("OBJECT");
            }

            Long parentId = parent.getId();
            String name = "node" + i;
            boolean isLast = (i == size);
            String dataType = isLast ? "STRING" : (random.nextBoolean() ? "OBJECT" : "ARRAY");
            int so = varySortOrder ? random.nextInt(10) : 0;
            tree.add(makeParam((long) i, templateId, parentId, name, dataType, so));
        }

        // Sort by sortOrder to simulate what the repository returns
        tree.sort(Comparator.comparingInt(ParameterDefinition::getSortOrder));
        return tree;
    }
}

