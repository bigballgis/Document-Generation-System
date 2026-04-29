import { ref, computed, type ComputedRef, type Ref } from 'vue'
import type { ParameterDTO } from '@/types/parameter'


export interface AddPayload {
  parameterId: number
  templateId: number
}

export interface EditPayload {
  parameterId: number
  field: string
  oldValue: any
  newValue: any
  version: number
}

export interface DeletePayload {
  snapshot: ParameterDTO
  templateId: number
}

export interface ReorderPayload {
  templateId: number
  parentId: number | null
  oldOrder: Array<{ id: number; sortOrder: number }>
  newOrder: Array<{ id: number; sortOrder: number }>
}

export interface BatchDeletePayload {
  snapshots: ParameterDTO[]
  templateId: number
}

export type OperationPayload =
  | AddPayload
  | EditPayload
  | DeletePayload
  | ReorderPayload
  | BatchDeletePayload

export interface UndoRedoOperation {
  type: 'add' | 'edit' | 'delete' | 'reorder' | 'batchDelete'
  timestamp: number
  payload: OperationPayload
}

export interface UseUndoRedoReturn {
  canUndo: ComputedRef<boolean>
  canRedo: ComputedRef<boolean>
  undoStack: Ref<UndoRedoOperation[]>
  redoStack: Ref<UndoRedoOperation[]>
  push: (op: UndoRedoOperation) => void
  undo: () => Promise<void>
  redo: () => Promise<void>
  clear: () => void
}

const DEFAULT_MAX_SIZE = 50

/**
 * Undo/Redo composable for parameter operations.
 * Maintains undo and redo stacks with a configurable max size.
 * Actual API calls for undo/redo are delegated to callbacks.
 */
export function useUndoRedo(
  maxSize: number = DEFAULT_MAX_SIZE,
  onUndo?: (op: UndoRedoOperation) => Promise<void>,
  onRedo?: (op: UndoRedoOperation) => Promise<void>,
): UseUndoRedoReturn {
  const undoStack = ref<UndoRedoOperation[]>([])
  const redoStack = ref<UndoRedoOperation[]>([])

  const canUndo = computed(() => undoStack.value.length > 0)
  const canRedo = computed(() => redoStack.value.length > 0)

  function push(op: UndoRedoOperation) {
    undoStack.value.push(op)
    // Clear redo stack on new operation
    redoStack.value = []
    // Enforce max size
    if (undoStack.value.length > maxSize) {
      undoStack.value.splice(0, undoStack.value.length - maxSize)
    }
  }

  async function undo() {
    if (undoStack.value.length === 0) return
    const op = undoStack.value.pop()!
    redoStack.value.push(op)
    if (onUndo) await onUndo(op)
  }

  async function redo() {
    if (redoStack.value.length === 0) return
    const op = redoStack.value.pop()!
    undoStack.value.push(op)
    if (onRedo) await onRedo(op)
  }

  function clear() {
    undoStack.value = []
    redoStack.value = []
  }

  return { canUndo, canRedo, undoStack, redoStack, push, undo, redo, clear }
}

