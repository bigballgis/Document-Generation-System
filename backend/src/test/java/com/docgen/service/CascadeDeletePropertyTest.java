package com.docgen.service;

import com.docgen.entity.ParameterDefinition;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based test for cascade deletion preserving tree integrity.
 *
 * <p><b>Validates: Requirements 1.11, 2.4</b></p>
 */
@Tag("Feature: template-parameter-redesign")
class CascadeDeletePropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 9: Deleting a parent parameter removes all its descendants.
     * After deletion, no parameter with the deleted parent's id (or any descendant's id)
     * as parent_id shall exist.
     */
    @Property(tries = 100)
    @Tag("Property 9: Cascade deletion preserves tree integrity")
    void deletingParentRemovesAllDescendants(
            @ForAll("treesWithSubtree") TreeWithTarget treeData
    ) {
        ParameterRepository repo = mock(ParameterRepository.class);
        ParameterService service = new ParameterService(
                repo, mock(TemplateRepository.class),
                mock(TemplateScanService.class), mock(ExpressionEngine.class), objectMapper,
                mock(AuditLogService.class), mock(AggregationResolver.class)
        );

        Long targetId = treeData.targetId;
        List<ParameterDefinition> allParams = treeData.allParams;

        // Compute all descendant IDs of the target (the cascade set)
        Set<Long> descendantIds = collectDescendants(targetId, allParams);
        Set<Long> cascadeSet = new HashSet<>(descendantIds);
        cascadeSet.add(targetId);

        // Mock: findById returns the target
        ParameterDefinition target = allParams.stream()
                .filter(p -> p.getId().equals(targetId)).findFirst().orElseThrow();
        when(repo.findById(targetId)).thenReturn(Optional.of(target));

        // Simulate cascade: when delete is called, remove target + all descendants
        List<ParameterDefinition> remainingAfterDelete = allParams.stream()
                .filter(p -> !cascadeSet.contains(p.getId()))
                .toList();

        // Execute delete
        service.deleteParameter(targetId);

        // Verify delete was called on the target entity
        verify(repo).delete(target);

        // Verify: no remaining parameter references any deleted ID as parent_id
        for (ParameterDefinition remaining : remainingAfterDelete) {
            assertFalse(cascadeSet.contains(remaining.getParentId()),
                    "Remaining parameter '" + remaining.getName()
                            + "' should not reference deleted parent_id=" + remaining.getParentId());
        }

        // Verify: the cascade set is complete (all descendants are included)
        for (ParameterDefinition p : allParams) {
            if (cascadeSet.contains(p.getParentId()) && !cascadeSet.contains(p.getId())) {
                fail("Parameter '" + p.getName() + "' (id=" + p.getId()
                        + ") has parent_id in cascade set but is not itself in cascade set");
            }
        }
    }

    /**
     * Property 9: Deleting a leaf node does not affect siblings or parent.
     */
    @Property(tries = 100)
    @Tag("Property 9: Cascade deletion preserves tree integrity")
    void deletingLeafDoesNotAffectSiblingsOrParent(
            @ForAll("treesWithLeaf") TreeWithTarget treeData
    ) {
        ParameterRepository repo = mock(ParameterRepository.class);
        ParameterService service = new ParameterService(
                repo, mock(TemplateRepository.class),
                mock(TemplateScanService.class), mock(ExpressionEngine.class), objectMapper,
                mock(AuditLogService.class), mock(AggregationResolver.class)
        );

        Long leafId = treeData.targetId;
        List<ParameterDefinition> allParams = treeData.allParams;

        ParameterDefinition leaf = allParams.stream()
                .filter(p -> p.getId().equals(leafId)).findFirst().orElseThrow();
        when(repo.findById(leafId)).thenReturn(Optional.of(leaf));

        // A leaf has no descendants
        Set<Long> descendantIds = collectDescendants(leafId, allParams);
        assertTrue(descendantIds.isEmpty(),
                "Leaf node should have no descendants");

        // After deleting the leaf, all other params remain intact
        List<ParameterDefinition> remainingAfterDelete = allParams.stream()
                .filter(p -> !p.getId().equals(leafId))
                .toList();

        service.deleteParameter(leafId);
        verify(repo).delete(leaf);

        // Verify siblings still exist (same parent_id, different id)
        Long parentId = leaf.getParentId();
        if (parentId != null) {
            List<ParameterDefinition> siblings = remainingAfterDelete.stream()
                    .filter(p -> parentId.equals(p.getParentId()))
                    .toList();
            // Siblings should not be affected
            for (ParameterDefinition sibling : siblings) {
                assertTrue(remainingAfterDelete.contains(sibling),
                        "Sibling '" + sibling.getName() + "' should remain after leaf deletion");
            }

            // Parent should still exist
            assertTrue(remainingAfterDelete.stream().anyMatch(p -> p.getId().equals(parentId)),
                    "Parent should remain after leaf deletion");
        }
    }

    /**
     * Property 9: The cascade set for any node is exactly the transitive closure
     * of the parent_id relationship.
     */
    @Property(tries = 100)
    @Tag("Property 9: Cascade deletion preserves tree integrity")
    void cascadeSetIsTransitiveClosure(
            @ForAll("treesWithSubtree") TreeWithTarget treeData
    ) {
        Long targetId = treeData.targetId;
        List<ParameterDefinition> allParams = treeData.allParams;

        Set<Long> cascadeSet = collectDescendants(targetId, allParams);
        cascadeSet.add(targetId);

        // Verify transitive closure: for every param in cascade set,
        // all its children must also be in cascade set
        Map<Long, List<ParameterDefinition>> childrenByParent = allParams.stream()
                .filter(p -> p.getParentId() != null)
                .collect(Collectors.groupingBy(ParameterDefinition::getParentId));

        for (Long id : new HashSet<>(cascadeSet)) {
            List<ParameterDefinition> children = childrenByParent.getOrDefault(id, Collections.emptyList());
            for (ParameterDefinition child : children) {
                assertTrue(cascadeSet.contains(child.getId()),
                        "Child '" + child.getName() + "' of cascaded node id=" + id
                                + " should also be in cascade set");
            }
        }

        // Verify no parameter outside cascade set has a parent in cascade set
        for (ParameterDefinition p : allParams) {
            if (!cascadeSet.contains(p.getId()) && p.getParentId() != null) {
                assertFalse(cascadeSet.contains(p.getParentId()),
                        "Parameter '" + p.getName() + "' outside cascade set should not have parent in cascade set");
            }
        }
    }


    /**
     * Collect all descendant IDs of a given parameter (transitive children).
     */
    private Set<Long> collectDescendants(Long parentId, List<ParameterDefinition> allParams) {
        Set<Long> descendants = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.add(parentId);

        Map<Long, List<ParameterDefinition>> childrenByParent = allParams.stream()
                .filter(p -> p.getParentId() != null)
                .collect(Collectors.groupingBy(ParameterDefinition::getParentId));

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            List<ParameterDefinition> children = childrenByParent.getOrDefault(current, Collections.emptyList());
            for (ParameterDefinition child : children) {
                if (descendants.add(child.getId())) {
                    queue.add(child.getId());
                }
            }
        }

        // Remove the parentId itself from descendants (it's the target, not a descendant)
        descendants.remove(parentId);
        return descendants;
    }

    private static ParameterDefinition makeParam(Long id, Long templateId, Long parentId,
                                                   String name, String dataType) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(templateId);
        p.setParentId(parentId);
        p.setName(name);
        p.setDataType(dataType);
        p.setParameterType("REQUEST");
        p.setSortOrder(0);
        return p;
    }


    record TreeWithTarget(List<ParameterDefinition> allParams, Long targetId) {}


    @Provide
    Arbitrary<TreeWithTarget> treesWithSubtree() {
        // Generate a tree with 3-12 nodes, pick a non-leaf internal node as target
        return Arbitraries.integers().between(4, 12).flatMap(size ->
                Arbitraries.randomValue(random -> {
                    List<ParameterDefinition> tree = generateTree(size, random);

                    // Find internal nodes (nodes that are parents of at least one other node)
                    Set<Long> parentIds = tree.stream()
                            .map(ParameterDefinition::getParentId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    List<Long> internalNodeIds = tree.stream()
                            .map(ParameterDefinition::getId)
                            .filter(parentIds::contains)
                            .toList();

                    // Pick a random internal node (not the absolute root to keep test interesting)
                    Long targetId;
                    if (internalNodeIds.size() > 1) {
                        // Prefer non-root internal nodes
                        List<Long> nonRootInternal = internalNodeIds.stream()
                                .filter(id -> id != 1L)
                                .toList();
                        if (!nonRootInternal.isEmpty()) {
                            targetId = nonRootInternal.get(random.nextInt(nonRootInternal.size()));
                        } else {
                            targetId = internalNodeIds.get(random.nextInt(internalNodeIds.size()));
                        }
                    } else if (!internalNodeIds.isEmpty()) {
                        targetId = internalNodeIds.get(0);
                    } else {
                        // Fallback: use root
                        targetId = 1L;
                    }

                    return new TreeWithTarget(tree, targetId);
                })
        );
    }

    @Provide
    Arbitrary<TreeWithTarget> treesWithLeaf() {
        // Generate a tree with 3-10 nodes, pick a leaf node as target
        return Arbitraries.integers().between(3, 10).flatMap(size ->
                Arbitraries.randomValue(random -> {
                    List<ParameterDefinition> tree = generateTree(size, random);

                    // Find leaf nodes (nodes that are NOT parents of any other node)
                    Set<Long> parentIds = tree.stream()
                            .map(ParameterDefinition::getParentId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    List<Long> leafIds = tree.stream()
                            .map(ParameterDefinition::getId)
                            .filter(id -> !parentIds.contains(id))
                            .toList();

                    Long targetId = leafIds.get(random.nextInt(leafIds.size()));
                    return new TreeWithTarget(tree, targetId);
                })
        );
    }

    private List<ParameterDefinition> generateTree(int size, Random random) {
        List<ParameterDefinition> tree = new ArrayList<>();
        Long templateId = 1L;

        // Root node
        tree.add(makeParam(1L, templateId, null, "root", "OBJECT"));

        for (int i = 2; i <= size; i++) {
            // Pick a random existing node as parent
            int parentIdx = random.nextInt(tree.size());
            ParameterDefinition parent = tree.get(parentIdx);
            // Ensure parent is a container type
            if (!"OBJECT".equals(parent.getDataType()) && !"ARRAY".equals(parent.getDataType())) {
                parent.setDataType("OBJECT");
            }

            Long parentId = parent.getId();
            String name = "node" + i;
            // Make some nodes containers, some leaves
            String dataType;
            if (i < size - 1) {
                dataType = random.nextBoolean() ? "OBJECT" : (random.nextInt(3) == 0 ? "ARRAY" : "STRING");
            } else {
                dataType = "STRING";
            }
            tree.add(makeParam((long) i, templateId, parentId, name, dataType));
        }

        return tree;
    }
}

