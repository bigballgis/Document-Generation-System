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

function makeEntry(filePath: string, name: string, position: number, enabled = true): AssemblySegmentEntry {
  return {
    filePath,
    name,
    segmentType: null,
    position,
    enabled,
    pageBreakBefore: false,
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
      const initial = [makeEntry('f1.docx', 'Seg1', 0), makeEntry('f2.docx', 'Seg2', 1)]
      const config = useAssemblyConfig(initial)
      expect(config.segments.value).toHaveLength(2)
      expect(config.segments.value[0].filePath).toBe('f1.docx')
    })

    it('adds a segment', () => {
      const config = useAssemblyConfig()
      config.addSegment(makeEntry('f1.docx', 'Seg1', 0))
      expect(config.segments.value).toHaveLength(1)
      expect(config.segments.value[0].name).toBe('Seg1')
    })

    it('removes a segment and reindexes positions', () => {
      const config = useAssemblyConfig([makeEntry('f1.docx', 'A', 0), makeEntry('f2.docx', 'B', 1), makeEntry('f3.docx', 'C', 2)])
      config.removeSegment(1)
      expect(config.segments.value.map(s => s.name)).toEqual(['A', 'C'])
      expect(config.segments.value.map(s => s.position)).toEqual([0, 1])
    })

    it('updates a segment property', () => {
      const config = useAssemblyConfig([makeEntry('f1.docx', 'A', 0)])
      config.updateSegment(0, { enabled: false })
      expect(config.segments.value[0].enabled).toBe(false)
    })
  })

  describe('batch operations', () => {
    it('batch enables segments', () => {
      const config = useAssemblyConfig([
        makeEntry('f1.docx', 'A', 0, false),
        makeEntry('f2.docx', 'B', 1, false),
        makeEntry('f3.docx', 'C', 2, true),
      ])
      config.batchEnable([0, 1])
      expect(config.segments.value[0].enabled).toBe(true)
      expect(config.segments.value[1].enabled).toBe(true)
    })

    it('batch disables segments', () => {
      const config = useAssemblyConfig([
        makeEntry('f1.docx', 'A', 0, true),
        makeEntry('f2.docx', 'B', 1, true),
      ])
      config.batchDisable([0, 1])
      expect(config.segments.value[0].enabled).toBe(false)
      expect(config.segments.value[1].enabled).toBe(false)
    })

    it('batch removes segments', () => {
      const config = useAssemblyConfig([
        makeEntry('f1.docx', 'A', 0),
        makeEntry('f2.docx', 'B', 1),
        makeEntry('f3.docx', 'C', 2),
      ])
      config.removeSegments([0, 2])
      expect(config.segments.value.map(s => s.name)).toEqual(['B'])
      expect(config.segments.value[0].position).toBe(0)
    })
  })

  describe('undo/redo', () => {
    it('can undo an add operation', () => {
      const config = useAssemblyConfig()
      config.addSegment(makeEntry('f1.docx', 'A', 0))
      expect(config.segments.value).toHaveLength(1)
      expect(config.canUndo.value).toBe(true)

      config.undo()
      expect(config.segments.value).toHaveLength(0)
    })

    it('can redo after undo', () => {
      const config = useAssemblyConfig()
      config.addSegment(makeEntry('f1.docx', 'A', 0))
      config.undo()
      expect(config.canRedo.value).toBe(true)

      config.redo()
      expect(config.segments.value).toHaveLength(1)
    })

    it('limits history to 20 steps', () => {
      const config = useAssemblyConfig()
      for (let i = 0; i < 25; i++) {
        config.addSegment(makeEntry(`f${i}.docx`, `Seg${i}`, i))
      }
      let undoCount = 0
      while (config.canUndo.value) {
        config.undo()
        undoCount++
      }
      expect(undoCount).toBe(20)
    })
  })

  describe('serialize/deserialize', () => {
    it('serializes to AssemblyConfig format', () => {
      const config = useAssemblyConfig([makeEntry('f1.docx', 'A', 0), makeEntry('f2.docx', 'B', 1)])
      const serialized = config.serialize()
      expect(serialized).toEqual({ segments: config.segments.value })
    })

    it('deserializes from AssemblyConfig', () => {
      const config = useAssemblyConfig()
      const input: AssemblyConfig = {
        segments: [makeEntry('f1.docx', 'A', 0), makeEntry('f2.docx', 'B', 1)],
      }
      config.deserialize(input)
      expect(config.segments.value).toHaveLength(2)
      expect(config.segments.value[0].filePath).toBe('f1.docx')
      expect(config.canUndo.value).toBe(false)
    })

    it('round-trips correctly', () => {
      const original: AssemblyConfig = {
        segments: [
          {
            filePath: 'segments/1/cover.docx',
            name: 'Cover',
            segmentType: 'COVER',
            position: 0,
            enabled: true,
            pageBreakBefore: true,
            conditionExpression: 'data.show === true',
            dataScope: { name: 'global.company.name' },
          },
          {
            filePath: 'segments/1/ch1.docx',
            name: 'Chapter 1',
            segmentType: 'CHAPTER',
            position: 1,
            enabled: false,
            pageBreakBefore: false,
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
