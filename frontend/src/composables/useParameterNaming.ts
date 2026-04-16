import type { DataType, ParameterDTO } from '@/types/parameter'

export interface UseParameterNamingReturn {
  generateName: (parentId: number | null, parentDataType: DataType | null, siblings: ParameterDTO[]) => string
}

/**
 * Smart parameter naming composable.
 * - root level → param_N
 * - OBJECT child → field_N
 * - ARRAY child → item_N
 * Auto-increments N to avoid collisions with existing siblings.
 */
export function useParameterNaming(): UseParameterNamingReturn {
  function generateName(
    parentId: number | null,
    parentDataType: DataType | null,
    siblings: ParameterDTO[],
  ): string {
    const prefix = getPrefix(parentId, parentDataType)
    const existingNames = new Set(siblings.map(s => s.name))
    let n = 1
    while (existingNames.has(`${prefix}_${n}`)) {
      n++
    }
    return `${prefix}_${n}`
  }

  return { generateName }
}

/**
 * Exported for testing — determines the prefix based on scope.
 */
export function getPrefix(parentId: number | null, parentDataType: DataType | null): string {
  if (parentId === null || parentId === undefined) return 'param'
  if (parentDataType === 'ARRAY') return 'item'
  if (parentDataType === 'OBJECT') return 'field'
  return 'param'
}

/**
 * Pure function for testing — generates a name given prefix and existing names.
 */
export function generateNamePure(prefix: string, existingNames: string[]): string {
  const nameSet = new Set(existingNames)
  let n = 1
  while (nameSet.has(`${prefix}_${n}`)) {
    n++
  }
  return `${prefix}_${n}`
}
