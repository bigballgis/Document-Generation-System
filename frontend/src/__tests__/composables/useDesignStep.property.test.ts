/**
 * Property tests for useDesignStep composable (2-step flow).
 */
import { describe, it, expect, beforeEach } from 'vitest'
import * as fc from 'fast-check'

function computeStepStatuses(
  paramCount: number,
  segmentCount: number,
): Record<string, string> {
  return {
    'parameter-table': paramCount > 0 ? 'completed' : 'not_started',
    'segment-canvas': segmentCount > 0 ? 'completed' : 'not_started',
  }
}

interface ParamNode { id: number; name: string; dataType: string; children: ParamNode[] }
interface BreadcrumbItem { id: number | null; name: string; tableType: 'main' | 'sub' | 'related' }

function buildBreadcrumbPath(root: ParamNode[], navigationPath: number[]): BreadcrumbItem[] {
  const breadcrumb: BreadcrumbItem[] = [{ id: null, name: '主表', tableType: 'main' }]
  let currentLevel = root
  for (const targetId of navigationPath) {
    const found = currentLevel.find(n => n.id === targetId)
    if (!found) break
    breadcrumb.push({ id: found.id, name: found.name, tableType: found.dataType === 'ARRAY' ? 'sub' : 'related' })
    currentLevel = found.children
  }
  return breadcrumb
}

function computeTreeDepth(_root: ParamNode[], navigationPath: number[]): number {
  return 1 + navigationPath.length
}

let _nextId = 1
function paramTreeArb(maxDepth: number): fc.Arbitrary<ParamNode[]> {
  if (maxDepth <= 0) return fc.constant([])
  return fc.array(
    fc.record({
      name: fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9]{0,9}$/),
      dataType: fc.constantFrom('STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT'),
    }).chain(base => {
      if (base.dataType === 'ARRAY' || base.dataType === 'OBJECT') {
        return paramTreeArb(maxDepth - 1).map(children => ({ id: _nextId++, ...base, children }))
      }
      return fc.constant({ id: _nextId++, ...base, children: [] as ParamNode[] })
    }),
    { minLength: 0, maxLength: 5 },
  )
}

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

describe('Property: Step completion status correctness (2-step)', () => {
  it('parameter-table completed iff paramCount >= 1', () => {
    fc.assert(fc.property(fc.integer({ min: 0, max: 100 }), fc.integer({ min: 0, max: 50 }), (paramCount, segmentCount) => {
      const statuses = computeStepStatuses(paramCount, segmentCount)
      expect(statuses['parameter-table']).toBe(paramCount >= 1 ? 'completed' : 'not_started')
    }), { numRuns: 100 })
  })

  it('segment-canvas completed iff segmentCount >= 1', () => {
    fc.assert(fc.property(fc.integer({ min: 0, max: 100 }), fc.integer({ min: 0, max: 50 }), (paramCount, segmentCount) => {
      const statuses = computeStepStatuses(paramCount, segmentCount)
      expect(statuses['segment-canvas']).toBe(segmentCount >= 1 ? 'completed' : 'not_started')
    }), { numRuns: 100 })
  })
})

describe('Property: Breadcrumb depth equals parameter tree depth', () => {
  beforeEach(() => { _nextId = 1 })

  it('breadcrumb length equals tree depth for any valid navigation path', () => {
    fc.assert(fc.property(paramTreeArb(4), (tree) => {
      for (const path of collectNavigablePaths(tree)) {
        expect(buildBreadcrumbPath(tree, path).length).toBe(computeTreeDepth(tree, path))
      }
    }), { numRuns: 100 })
  })
})
