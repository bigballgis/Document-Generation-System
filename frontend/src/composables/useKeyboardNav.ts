import { ref } from 'vue'
import type { Ref } from 'vue'
import type { ParameterDTO } from '@/types/parameter'
import type { UseUndoRedoReturn } from './useUndoRedo'

export interface UseKeyboardNavReturn {
  handleKeyDown: (event: KeyboardEvent) => void
  focusedRowId: Ref<number | null>
  isEditing: Ref<boolean>
}

export interface KeyboardNavOptions {
  parameters: Ref<ParameterDTO[]>
  onDelete: (id: number) => void
  onDuplicate: (id: number) => void
  undoRedo: UseUndoRedoReturn
}

/**
 * Flatten a parameter tree into a visible row list (depth-first).
 */
function flattenParams(params: ParameterDTO[]): ParameterDTO[] {
  const result: ParameterDTO[] = []
  for (const p of params) {
    result.push(p)
    if (p.children?.length) {
      result.push(...flattenParams(p.children))
    }
  }
  return result
}

export function useKeyboardNav(options: KeyboardNavOptions): UseKeyboardNavReturn {
  const focusedRowId = ref<number | null>(null)
  const isEditing = ref(false)

  function handleKeyDown(event: KeyboardEvent) {
    // Don't handle keyboard nav while editing
    if (isEditing.value) {
      // Only handle Ctrl+Z / Ctrl+Shift+Z while editing
      if (event.ctrlKey && event.key === 'z' && !event.shiftKey) {
        event.preventDefault()
        options.undoRedo.undo()
        return
      }
      if (event.ctrlKey && event.key === 'z' && event.shiftKey) {
        event.preventDefault()
        options.undoRedo.redo()
        return
      }
      return
    }

    const flat = flattenParams(options.parameters.value)
    const currentIndex = flat.findIndex(p => p.id === focusedRowId.value)

    switch (event.key) {
      case 'ArrowUp':
        event.preventDefault()
        if (currentIndex > 0) {
          focusedRowId.value = flat[currentIndex - 1].id
        }
        break

      case 'ArrowDown':
        event.preventDefault()
        if (currentIndex < flat.length - 1) {
          focusedRowId.value = flat[currentIndex + 1].id
        } else if (currentIndex === -1 && flat.length > 0) {
          focusedRowId.value = flat[0].id
        }
        break

      case 'Delete':
        event.preventDefault()
        if (focusedRowId.value !== null) {
          options.onDelete(focusedRowId.value)
        }
        break

      case 'd':
        if (event.ctrlKey) {
          event.preventDefault()
          if (focusedRowId.value !== null) {
            options.onDuplicate(focusedRowId.value)
          }
        }
        break

      case 'z':
        if (event.ctrlKey && !event.shiftKey) {
          event.preventDefault()
          options.undoRedo.undo()
        } else if (event.ctrlKey && event.shiftKey) {
          event.preventDefault()
          options.undoRedo.redo()
        }
        break

      case 'Z':
        if (event.ctrlKey && event.shiftKey) {
          event.preventDefault()
          options.undoRedo.redo()
        }
        break
    }
  }

  return { handleKeyDown, focusedRowId, isEditing }
}
