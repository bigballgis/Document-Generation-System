import { ref, computed } from 'vue'
import type { AssemblySegmentEntry, AssemblyConfig } from '@/types/segment'

const MAX_HISTORY = 20

export function useAssemblyConfig(initial?: AssemblySegmentEntry[]) {
  const segments = ref<AssemblySegmentEntry[]>(initial ? [...initial] : [])
  const history = ref<string[]>([])
  const future = ref<string[]>([])

  function snapshot() {
    return JSON.stringify(segments.value)
  }

  function pushHistory() {
    history.value.push(snapshot())
    if (history.value.length > MAX_HISTORY) {
      history.value.shift()
    }
    // Clear redo stack on new action
    future.value = []
  }

  const canUndo = computed(() => history.value.length > 0)
  const canRedo = computed(() => future.value.length > 0)

  function undo() {
    if (!canUndo.value) return
    future.value.push(snapshot())
    const prev = history.value.pop()!
    segments.value = JSON.parse(prev)
  }

  function redo() {
    if (!canRedo.value) return
    history.value.push(snapshot())
    const next = future.value.pop()!
    segments.value = JSON.parse(next)
  }

  function setSegments(newSegments: AssemblySegmentEntry[]) {
    pushHistory()
    segments.value = newSegments
  }

  function addSegment(entry: AssemblySegmentEntry) {
    pushHistory()
    const maxPos = segments.value.length > 0
      ? Math.max(...segments.value.map(s => s.position)) + 1
      : 0
    segments.value = [...segments.value, { ...entry, position: maxPos }]
  }

  function removeSegment(index: number) {
    if (index < 0 || index >= segments.value.length) return
    pushHistory()
    const result = [...segments.value]
    result.splice(index, 1)
    segments.value = result.map((s, i) => ({ ...s, position: i }))
  }

  function removeSegments(indices: number[]) {
    if (indices.length === 0) return
    pushHistory()
    const sorted = [...indices].sort((a, b) => b - a)
    const result = [...segments.value]
    for (const idx of sorted) {
      if (idx >= 0 && idx < result.length) {
        result.splice(idx, 1)
      }
    }
    segments.value = result.map((s, i) => ({ ...s, position: i }))
  }

  function updateSegment(index: number, patch: Partial<AssemblySegmentEntry>) {
    if (index < 0 || index >= segments.value.length) return
    pushHistory()
    const result = [...segments.value]
    result[index] = { ...result[index], ...patch }
    segments.value = result
  }

  function batchEnable(indices: number[]) {
    if (indices.length === 0) return
    pushHistory()
    const result = [...segments.value]
    for (const idx of indices) {
      if (idx >= 0 && idx < result.length) {
        result[idx] = { ...result[idx], enabled: true }
      }
    }
    segments.value = result
  }

  function batchDisable(indices: number[]) {
    if (indices.length === 0) return
    pushHistory()
    const result = [...segments.value]
    for (const idx of indices) {
      if (idx >= 0 && idx < result.length) {
        result[idx] = { ...result[idx], enabled: false }
      }
    }
    segments.value = result
  }

  /** Serialize to AssemblyConfig format */
  function serialize(): AssemblyConfig {
    return { segments: segments.value }
  }

  /** Deserialize from AssemblyConfig */
  function deserialize(config: AssemblyConfig) {
    segments.value = config.segments ? [...config.segments] : []
    history.value = []
    future.value = []
  }

  return {
    segments,
    canUndo,
    canRedo,
    undo,
    redo,
    setSegments,
    addSegment,
    removeSegment,
    removeSegments,
    updateSegment,
    batchEnable,
    batchDisable,
    serialize,
    deserialize,
  }
}
