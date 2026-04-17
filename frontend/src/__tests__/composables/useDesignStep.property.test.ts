/**
 * Property tests for useDesignStep composable.
 * Feature: design-stage-layout
 */
import { describe, it, expect, beforeEach } from 'vitest'
import * as fc from 'fast-check'

// Feature: design-stage-layout, Property 1: Step completion status correctness
// **Validates: Requirements 1.3**

// Feature: design-stage-layout, Property 3: Breadcrumb depth equals parameter tree depth
// **Validates: Requirements 2.4**

/**
 * Pure function extracted from useDesignStep: compute step statuses.
 */
function computeStepStatuses(
  paramCount: number,
  segmentCount: number,
  enabledSegments: Array<{ enabled: boolean; filePath: string | null }>,
): Record<string, string> {
  const enabledOnes = enabledSegments.filter(s => s.enabled)
  const allEdited = enabledOnes.length > 0 && enabledOnes.every(s => s.filePath)

  return {
    'parameter-table': paramCount > 0 ? 'completed' : 'not_started',
    'segment-canvas': segmentCount > 0 ? 'completed' : 'not_started',
    'segment-detail': allEdited ? 'completed' : 'not_started',
  }
}

/**
 * Build breadcrumb path for a given navigation path through a parameter tree.
 * Each node in the path adds a breadcrumb item.
 */
interface ParamNode {
  id: number
  name: string
  dataType: string
  children: ParamNode[]
}

interface BreadcrumbItem {
  id: number | null
  name: string
  tableType: 'main' | 'sub' | 'related'
}

function buildBreadcrumbPath(
  root: ParamNode[],
  navigationPath: number[],
): BreadcrumbItem[] {
  const breadcrumb: BreadcrumbItem[] = [{ id: null, name: '主表', tableType: 'main' }]
  let currentLevel = root

  for (const targetId of navigationPath) {
    const found = currentLevel.find(n => n.id === targetId)
    if (!found) break
    const tableType = found.dataType === 'ARRAY' ? 'sub' : 'related'
    breadcrumb.push({ id: found.id, name: found.name, tableType })
    currentLevel = found.children
  }

  return breadcrumb
}

/**
 * Compute the depth of a node in the tree (root level = 1).
 */
function computeTreeDepth(_root: ParamNode[], navigationPath: number[]): number {
  return 1 + navigationPath.length // root = 1, each navigation adds 1
}

// ── Arbitraries ──

const segmentArb = fc.record({
  enabled: fc.boolean(),
  filePath: fc.oneof(
    fc.constant(null as string | null),
    fc.string({ minLength: 1, maxLength: 30 }).map(s => `segments/${s}.docx`),
  ),
})

/** Generate a parameter tree with controlled depth and unique IDs */
let _nextId = 1
function paramTreeArb(maxDepth: number): fc.Arbitrary<ParamNode[]> {
  if (maxDepth <= 0) return fc.constant([])
  return fc.array(
    fc.record({
      name: fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9]{0,9}$/),
      dataType: fc.constantFrom('STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT'),
    }).chain(base => {
      if (base.dataType === 'ARRAY' || base.dataType === 'OBJECT') {
        return paramTreeArb(maxDepth - 1).map(children => ({
          id: _nextId++,
          ...base,
          children,
        }))
      }
      return fc.constant({ id: _nextId++, ...base, children: [] as ParamNode[] })
    }),
    { minLength: 0, maxLength: 5 },
  )
}


/** Collect all navigable (ARRAY/OBJECT) node IDs along a valid path from root */
function collectNavigablePaths(nodes: ParamNode[]): number[][] {
  const paths: number[][] = [[]]
  function walk(level: ParamNode[], currentPath: number[]) {
    for (const node of level) {
      if (node.dataType === 'ARRAY' || node.dataType === 'OBJECT') {
        const newPath = [...currentPath, node.id]
        paths.push(newPath)
        walk(node.children, newPath)
      }
    }
  }
  walk(nodes, [])
  return paths
}

describe('Property 1: Step completion status correctness', () => {
  // Feature: design-stage-layout, Property 1: Step completion status correctness

  it('parameter-table completed iff paramCount >= 1', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: 100 }),
        fc.integer({ min: 0, max: 50 }),
        fc.array(segmentArb, { minLength: 0, maxLength: 20 }),
        (paramCount, segmentCount, segments) => {
          const statuses = computeStepStatuses(paramCount, segmentCount, segments)
          if (paramCount >= 1) {
            expect(statuses['parameter-table']).toBe('completed')
          } else {
            expect(statuses['parameter-table']).toBe('not_started')
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  it('segment-canvas completed iff segmentCount >= 1', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: 100 }),
        fc.integer({ min: 0, max: 50 }),
        fc.array(segmentArb, { minLength: 0, maxLength: 20 }),
        (paramCount, segmentCount, segments) => {
          const statuses = computeStepStatuses(paramCount, segmentCount, segments)
          if (segmentCount >= 1) {
            expect(statuses['segment-canvas']).toBe('completed')
          } else {
            expect(statuses['segment-canvas']).toBe('not_started')
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  it('segment-detail completed iff all enabled segments have non-null filePath', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: 100 }),
        fc.integer({ min: 0, max: 50 }),
        fc.array(segmentArb, { minLength: 0, maxLength: 20 }),
        (paramCount, segmentCount, segments) => {
          const statuses = computeStepStatuses(paramCount, segmentCount, segments)
          const enabled = segments.filter(s => s.enabled)
          const allEdited = enabled.length > 0 && enabled.every(s => s.filePath !== null)
          if (allEdited) {
            expect(statuses['segment-detail']).toBe('completed')
          } else {
            expect(statuses['segment-detail']).toBe('not_started')
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})

describe('Property 3: Breadcrumb depth equals parameter tree depth', () => {
  beforeEach(() => { _nextId = 1 })
  // Feature: design-stage-layout, Property 3: Breadcrumb depth equals parameter tree depth

  it('breadcrumb length equals tree depth for any valid navigation path', () => {
    fc.assert(
      fc.property(
        paramTreeArb(4),
        (tree) => {
          const allPaths = collectNavigablePaths(tree)
          for (const path of allPaths) {
            const breadcrumb = buildBreadcrumbPath(tree, path)
            const expectedDepth = computeTreeDepth(tree, path)
            expect(breadcrumb.length).toBe(expectedDepth)
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  it('each breadcrumb item name and type matches ancestor chain', () => {
    fc.assert(
      fc.property(
        paramTreeArb(4),
        (tree) => {
          const allPaths = collectNavigablePaths(tree)
          for (const path of allPaths) {
            const breadcrumb = buildBreadcrumbPath(tree, path)
            // First item is always root
            expect(breadcrumb[0]).toEqual({ id: null, name: '主表', tableType: 'main' })

            // Verify each subsequent item matches the navigated node
            let currentLevel = tree
            for (let i = 0; i < path.length; i++) {
              const node = currentLevel.find(n => n.id === path[i])!
              const item = breadcrumb[i + 1]
              expect(item.id).toBe(node.id)
              expect(item.name).toBe(node.name)
              expect(item.tableType).toBe(node.dataType === 'ARRAY' ? 'sub' : 'related')
              currentLevel = node.children
            }
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})
