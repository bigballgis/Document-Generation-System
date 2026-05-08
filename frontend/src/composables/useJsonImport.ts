import type { CreateParameterRequest, DataType } from '@/types/parameter'

export interface JsonImportResult {
  parameters: CreateParameterRequest[]
  warnings: string[]
}

export interface UseJsonImportReturn {
  parseAndInfer: (jsonStr: string) => JsonImportResult
  validateJson: (jsonStr: string) => { valid: boolean; error?: string }
}

const MAX_DEPTH = 5

/** English warnings for client-side import preview; same text as i18n en-US jsonImport keys. */
const WARNING_EMPTY_JSON =
  'JSON data is empty, cannot generate parameters'
const WARNING_MAX_DEPTH_FLATTENED =
  'JSON nesting exceeds 5 levels, deep data has been flattened to STRING'

export function useJsonImport(): UseJsonImportReturn {
  return { parseAndInfer, validateJson }
}

export function validateJson(jsonStr: string): { valid: boolean; error?: string } {
  try {
    JSON.parse(jsonStr)
    return { valid: true }
  } catch (e: any) {
    return { valid: false, error: e.message }
  }
}

export function parseAndInfer(jsonStr: string): JsonImportResult {
  const warnings: string[] = []
  let parsed: unknown
  try {
    parsed = JSON.parse(jsonStr)
  } catch (e: any) {
    return { parameters: [], warnings: [e.message] }
  }

  if (
    (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) && Object.keys(parsed).length === 0) ||
    (Array.isArray(parsed) && parsed.length === 0)
  ) {
    warnings.push(WARNING_EMPTY_JSON)
    return { parameters: [], warnings }
  }

  if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
    const params = inferObject(parsed as Record<string, unknown>, 1, warnings)
    return { parameters: params, warnings }
  }

  if (Array.isArray(parsed)) {
    const params = inferArrayRoot(parsed, warnings)
    return { parameters: params, warnings }
  }

  // Single primitive at root — wrap as single param
  const dt = inferPrimitiveType(parsed)
  const param: CreateParameterRequest = {
    name: 'value',
    dataType: dt,
    required: parsed !== null,
  }
  return { parameters: [param], warnings }
}

function inferObject(
  obj: Record<string, unknown>,
  depth: number,
  warnings: string[],
  sortStart = 0,
): CreateParameterRequest[] {
  const params: CreateParameterRequest[] = []
  let sortOrder = sortStart
  for (const [key, value] of Object.entries(obj)) {
    params.push(inferValue(key, value, depth, warnings, sortOrder++))
  }
  return params
}

function inferValue(
  name: string,
  value: unknown,
  depth: number,
  warnings: string[],
  sortOrder: number,
): CreateParameterRequest {
  if (value === null) {
    return { name, dataType: 'STRING', required: false, sortOrder }
  }

  // At max depth, flatten complex types to STRING
  if (depth >= MAX_DEPTH && (typeof value === 'object' || Array.isArray(value))) {
    if (!warnings.includes(WARNING_MAX_DEPTH_FLATTENED)) {
      warnings.push(WARNING_MAX_DEPTH_FLATTENED)
    }
    return {
      name,
      dataType: 'STRING',
      defaultValue: JSON.stringify(value),
      sortOrder,
    }
  }

  if (Array.isArray(value)) {
    return inferArray(name, value, depth, warnings, sortOrder)
  }

  if (typeof value === 'object') {
    const children = inferObject(value as Record<string, unknown>, depth + 1, warnings)
    return { name, dataType: 'OBJECT', sortOrder, children } as any
  }

  const dt = inferPrimitiveType(value)
  const param: CreateParameterRequest = { name, dataType: dt, sortOrder }
  if (typeof value === 'string') param.defaultValue = value
  else if (typeof value === 'number') param.defaultValue = String(value)
  else if (typeof value === 'boolean') param.defaultValue = String(value)
  return param
}

function inferArray(
  name: string,
  arr: unknown[],
  depth: number,
  warnings: string[],
  sortOrder: number,
): CreateParameterRequest {
  if (arr.length === 0) {
    return { name, dataType: 'ARRAY', sortOrder }
  }

  // Check if array contains objects
  const objectElements = arr.filter(
    (el): el is Record<string, unknown> => typeof el === 'object' && el !== null && !Array.isArray(el),
  )

  if (objectElements.length > 0) {
    // Union of all keys from all object elements
    const allKeys = new Set<string>()
    for (const obj of objectElements) {
      for (const key of Object.keys(obj)) {
        allKeys.add(key)
      }
    }
    // Use first element's values for type inference, fallback to STRING
    const firstObj = objectElements[0]
    const children: CreateParameterRequest[] = []
    let childSort = 0
    for (const key of allKeys) {
      const sampleValue = firstObj[key] ?? null
      children.push(inferValue(key, sampleValue, depth + 1, warnings, childSort++))
    }
    return { name, dataType: 'ARRAY', sortOrder, children } as any
  }

  // Array of primitives — no children, describe element type
  const elementType = inferPrimitiveType(arr[0])
  return {
    name,
    dataType: 'ARRAY',
    sortOrder,
    description: `Array of ${elementType} elements`,
  }
}

function inferArrayRoot(arr: unknown[], warnings: string[]): CreateParameterRequest[] {
  // Treat root array as a single ARRAY parameter named "items"
  const param = inferArray('items', arr, 1, warnings, 0)
  return [param]
}

/**
 * Exported for testing — infers DataType from a primitive JS value.
 */
export function inferPrimitiveType(value: unknown): DataType {
  if (value === null) return 'STRING'
  if (typeof value === 'string') return 'STRING'
  if (typeof value === 'number') return 'NUMBER'
  if (typeof value === 'boolean') return 'BOOLEAN'
  if (Array.isArray(value)) return 'ARRAY'
  if (typeof value === 'object') return 'OBJECT'
  return 'STRING'
}

/**
 * Exported for testing — infers a single value to a CreateParameterRequest.
 */
export { inferValue, inferObject }
