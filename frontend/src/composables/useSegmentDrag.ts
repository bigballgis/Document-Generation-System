import { ref } from 'vue'
import type { AssemblySegmentEntry } from '@/types/segment'

export function useSegmentDrag() {
  const draggingIndex = ref<number | null>(null)
  const dropTargetIndex = ref<number | null>(null)

  function onDragStart(index: number) {
    draggingIndex.value = index
  }

  function onDragOver(index: number) {
    dropTargetIndex.value = index
  }

  function onDragEnd() {
    draggingIndex.value = null
    dropTargetIndex.value = null
  }

  /**
   * Reorder segments by moving item from `fromIndex` to `toIndex`.
   * Returns a new array with updated position fields.
   */
  function reorder(
    segments: AssemblySegmentEntry[],
    fromIndex: number,
    toIndex: number,
  ): AssemblySegmentEntry[] {
    if (fromIndex === toIndex) return segments
    if (fromIndex < 0 || toIndex < 0) return segments
    if (fromIndex >= segments.length || toIndex >= segments.length) return segments

    const result = [...segments]
    const [moved] = result.splice(fromIndex, 1)
    result.splice(toIndex, 0, moved)
    return result.map((s, i) => ({ ...s, position: i }))
  }

  /**
   * Move item up (Alt+↑). Returns new array or null if no change.
   */
  function moveUp(
    segments: AssemblySegmentEntry[],
    index: number,
  ): AssemblySegmentEntry[] | null {
    if (index <= 0 || index >= segments.length) return null
    return reorder(segments, index, index - 1)
  }

  /**
   * Move item down (Alt+↓). Returns new array or null if no change.
   */
  function moveDown(
    segments: AssemblySegmentEntry[],
    index: number,
  ): AssemblySegmentEntry[] | null {
    if (index < 0 || index >= segments.length - 1) return null
    return reorder(segments, index, index + 1)
  }

  /**
   * Handle drop: reorder from draggingIndex to dropTargetIndex.
   */
  function onDrop(segments: AssemblySegmentEntry[]): AssemblySegmentEntry[] {
    if (draggingIndex.value == null || dropTargetIndex.value == null) return segments
    const result = reorder(segments, draggingIndex.value, dropTargetIndex.value)
    onDragEnd()
    return result
  }

  return {
    draggingIndex,
    dropTargetIndex,
    onDragStart,
    onDragOver,
    onDragEnd,
    onDrop,
    reorder,
    moveUp,
    moveDown,
  }
}
