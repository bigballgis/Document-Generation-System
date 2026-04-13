import { describe, it, expect, vi } from 'vitest'
import { useAssemblyConfig } from '@/composables/useAssemblyConfig'
import type { AssemblySegmentEntry, AssemblyConfig } from '@/types/segment'

// Mock Vue lifecycle hooks since we're testing outside a component
vi.mock('vue', async () => {
  const actual = await vi.importActual('vue')
  return {
    ...(actual as any),
    onBeforeUnmount: vi.fn(),
  }
})

function makeEntry(segmentId: number, position: number, enabled = true): AssemblySegmentEntry {
  return {
    segmentId,
    position,
    enabled,
    pageBreakBefore: false,
    lockedVersion: null,
    conditionExpression: null,
    dataScope: null,
  }
}

describe('useAssemblyConfig', () => {
  describe('basic operations', () => {
    it('initializes with empty segments', () => {
      const config = useAssemblyConfig()
      expect(config.segments.value).toEqual([])
    })

    it('initializes with provided segments', () => {
      const initial = [makeEntry(1, 0), makeEntry(2, 1)]
      const config = useAssemblyConfig(initial)
      expect(config.segments.value).toHaveLength(2)
      expect(config.segments.value[0].segmentId).toBe(1)
    })

    it('adds a segment', () => {
      const config = useAssemblyConfig()
      config.addSegment(makeEntry(10, 0))
      expect(config.segments.value).toHaveLength(1)
      expect(config.segments.value[0].segmentId).toBe(10)
    })

    it('removes a segment and reindexes positions', () => {
      const config = useAssemblyConfig([makeEntry(1, 0), makeEntry(2, 1), makeEntry(3, 2)])
      config.removeSegment(1)
      expect(config.segments.value.map(s => s.segmentId)).toEqual([1, 3])
      expect(config.segments.value.map(s => s.position)).toEqual([0, 1])
    })

    it('updates a segment property', () => {
      const config = useAssemblyConfig([makeEntry(1, 0)])
      config.updateSegment(0, { enabled: false })
      expect(config.segments.value[0].enabled).toBe(false)
    })
  })

  describe('batch operations', () => {
    it('batch enables segments', () => {
      const config = useAssemblyConfig([
        makeEntry(1, 0, false),
        makeEntry(2, 1, false),
        makeEntry(3, 2, true),
      ])
      config.batchEnable([0, 1])
      expect(config.segments.value[0].enabled).toBe(true)
      expect(config.segments.value[1].enabled).toBe(true)
      expect(config.segments.value[2].enabled).toBe(true)
    })

    it('batch disables segments', () => {
      const config = useAssemblyConfig([
        makeEntry(1, 0, true),
        makeEntry(2, 1, true),
      ])
      config.batchDisable([0, 1])
      expect(config.segments.value[0].enabled).toBe(false)
      expect(config.segments.value[1].enabled).toBe(false)
    })

    it('batch removes segments', () => {
      const config = useAssemblyConfig([
        makeEntry(1, 0),
        makeEntry(2, 1),
        makeEntry(3, 2),
      ])
      config.removeSegments([0, 2])
      expect(config.segments.value.map(s => s.segmentId)).toEqual([2])
      expect(config.segments.value[0].position).toBe(0)
    })
  })

  describe('undo/redo', () => {
    it('can undo an add operation', () => {
      const config = useAssemblyConfig()
      config.addSegment(makeEntry(1, 0))
      expect(config.segments.value).toHaveLength(1)
      expect(config.canUndo.value).toBe(true)

      config.undo()
      expect(config.segments.value).toHaveLength(0)
      expect(config.canUndo.value).toBe(false)
    })

    it('can redo after undo', () => {
      const config = useAssemblyConfig()
      config.addSegment(makeEntry(1, 0))
      config.undo()
      expect(config.canRedo.value).toBe(true)

      config.redo()
      expect(config.segments.value).toHaveLength(1)
      expect(config.segments.value[0].segmentId).toBe(1)
    })

    it('clears redo stack on new action', () => {
      const config = useAssemblyConfig()
      config.addSegment(makeEntry(1, 0))
      config.undo()
      expect(config.canRedo.value).toBe(true)

      config.addSegment(makeEntry(2, 0))
      expect(config.canRedo.value).toBe(false)
    })

    it('supports multiple undo steps', () => {
      const config = useAssemblyConfig()
      config.addSegment(makeEntry(1, 0))
      config.addSegment(makeEntry(2, 1))
      config.addSegment(makeEntry(3, 2))

      config.undo()
      expect(config.segments.value).toHaveLength(2)
      config.undo()
      expect(config.segments.value).toHaveLength(1)
      config.undo()
      expect(config.segments.value).toHaveLength(0)
    })

    it('limits history to 20 steps', () => {
      const config = useAssemblyConfig()
      for (let i = 0; i < 25; i++) {
        config.addSegment(makeEntry(i, i))
      }
      // Should have 20 undo steps max
      let undoCount = 0
      while (config.canUndo.value) {
        config.undo()
        undoCount++
      }
      expect(undoCount).toBe(20)
    })

    it('undo does nothing when history is empty', () => {
      const config = useAssemblyConfig([makeEntry(1, 0)])
      config.undo()
      expect(config.segments.value).toHaveLength(1)
    })

    it('redo does nothing when future is empty', () => {
      const config = useAssemblyConfig([makeEntry(1, 0)])
      config.redo()
      expect(config.segments.value).toHaveLength(1)
    })
  })

  describe('serialize/deserialize', () => {
    it('serializes to AssemblyConfig format', () => {
      const config = useAssemblyConfig([makeEntry(1, 0), makeEntry(2, 1)])
      const serialized = config.serialize()
      expect(serialized).toEqual({
        segments: config.segments.value,
      })
    })

    it('deserializes from AssemblyConfig', () => {
      const config = useAssemblyConfig()
      const input: AssemblyConfig = {
        segments: [makeEntry(10, 0), makeEntry(20, 1)],
      }
      config.deserialize(input)
      expect(config.segments.value).toHaveLength(2)
      expect(config.segments.value[0].segmentId).toBe(10)
      // History should be cleared after deserialize
      expect(config.canUndo.value).toBe(false)
      expect(config.canRedo.value).toBe(false)
    })

    it('round-trips correctly', () => {
      const original: AssemblyConfig = {
        segments: [
          {
            segmentId: 5,
            position: 0,
            enabled: true,
            pageBreakBefore: true,
            lockedVersion: 3,
            conditionExpression: 'data.show === true',
            dataScope: { name: 'global.company.name' },
          },
          {
            segmentId: 8,
            position: 1,
            enabled: false,
            pageBreakBefore: false,
            lockedVersion: null,
            conditionExpression: null,
            dataScope: null,
          },
        ],
      }
      const config = useAssemblyConfig()
      config.deserialize(original)
      const serialized = config.serialize()
      expect(serialized).toEqual(original)
    })
  })
})
