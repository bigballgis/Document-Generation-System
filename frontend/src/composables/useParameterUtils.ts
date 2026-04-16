import type { ParameterDTO, DataType } from '@/types/parameter'

/**
 * Compute the full parameter path by traversing the tree.
 * For a flat list with parentId, use computeParameterPath.
 */
export function computeParameterPath(param: ParameterDTO, allParams: ParameterDTO[]): string {
  const segments: string[] = []
  let current: ParameterDTO | undefined = param
  while (current) {
    segments.unshift(current.name)
    current = current.parentId != null
      ? findById(allParams, current.parentId)
      : undefined
  }
  return segments.join('.')
}

function findById(params: ParameterDTO[], id: number): ParameterDTO | undefined {
  for (const p of params) {
    if (p.id === id) return p
    if (p.children?.length) {
      const found = findById(p.children, id)
      if (found) return found
    }
  }
  return undefined
}

/**
 * Recommend data type based on placeholder name keywords.
 */
export function recommendDataType(name: string): DataType {
  const lower = name.toLowerCase()
  const numberKeywords = ['price', 'amount', 'count', 'total', 'qty', 'quantity']
  const dateKeywords = ['date', 'time', 'created', 'updated']
  const boolKeywords = ['is', 'has', 'enable', 'active', 'flag']

  if (numberKeywords.some(k => lower.includes(k))) return 'NUMBER'
  if (dateKeywords.some(k => lower.includes(k))) return 'DATE'
  if (boolKeywords.some(k => lower.includes(k))) return 'BOOLEAN'
  return 'STRING'
}

/**
 * Generate a JSON Schema object from a parameter tree.
 */
export function generateJsonSchema(parameters: ParameterDTO[]): Record<string, unknown> {
  const schema: Record<string, unknown> = {
    type: 'object',
    properties: {} as Record<string, unknown>,
    required: [] as string[],
  }

  const properties = schema.properties as Record<string, unknown>
  const required = schema.required as string[]

  for (const param of parameters) {
    if (param.parameterType === 'DERIVED') continue
    properties[param.name] = buildSchemaNode(param)
    if (param.required) required.push(param.name)
  }

  if (required.length === 0) delete schema.required
  return schema
}

function buildSchemaNode(param: ParameterDTO): Record<string, unknown> {
  const node: Record<string, unknown> = {}

  if (param.dataType === 'OBJECT') {
    node.type = 'object'
    const props: Record<string, unknown> = {}
    const req: string[] = []
    for (const child of param.children ?? []) {
      if (child.parameterType === 'DERIVED') continue
      props[child.name] = buildSchemaNode(child)
      if (child.required) req.push(child.name)
    }
    node.properties = props
    if (req.length > 0) node.required = req
  } else if (param.dataType === 'ARRAY') {
    node.type = 'array'
    const itemProps: Record<string, unknown> = {}
    const itemReq: string[] = []
    for (const child of param.children ?? []) {
      if (child.parameterType === 'DERIVED') continue
      itemProps[child.name] = buildSchemaNode(child)
      if (child.required) itemReq.push(child.name)
    }
    node.items = { type: 'object', properties: itemProps }
    if (itemReq.length > 0) (node.items as any).required = itemReq
  } else {
    node.type = mapDataTypeToJsonSchemaType(param.dataType)
  }

  if (param.description) node.description = param.description

  // Map validation rules to JSON Schema constraints
  if (param.validationRules) {
    const rules = param.validationRules
    if (rules.min_length != null) node.minLength = rules.min_length
    if (rules.max_length != null) node.maxLength = rules.max_length
    if (rules.min != null) node.minimum = rules.min
    if (rules.max != null) node.maximum = rules.max
    if (rules.pattern) node.pattern = rules.pattern
    if (rules.enum_values?.length) node.enum = rules.enum_values
    if (rules.min_items != null) node.minItems = rules.min_items
    if (rules.max_items != null) node.maxItems = rules.max_items
  }

  return node
}

function mapDataTypeToJsonSchemaType(dataType: DataType): string {
  switch (dataType) {
    case 'STRING': return 'string'
    case 'NUMBER': return 'number'
    case 'DATE': return 'string'
    case 'BOOLEAN': return 'boolean'
    default: return 'string'
  }
}

/**
 * Generate a sample request body from a parameter tree.
 */
export function generateSampleBody(parameters: ParameterDTO[]): Record<string, unknown> {
  const body: Record<string, unknown> = {}
  for (const param of parameters) {
    if (param.parameterType === 'DERIVED') continue
    body[param.name] = buildSampleValue(param)
  }
  return body
}

function buildSampleValue(param: ParameterDTO): unknown {
  if (param.defaultValue != null && param.defaultValue !== '') {
    return parseDefaultValue(param.defaultValue, param.dataType)
  }

  switch (param.dataType) {
    case 'STRING': return 'string'
    case 'NUMBER': return 0
    case 'DATE': return '2024-01-01'
    case 'BOOLEAN': return false
    case 'OBJECT': {
      const obj: Record<string, unknown> = {}
      for (const child of param.children ?? []) {
        if (child.parameterType === 'DERIVED') continue
        obj[child.name] = buildSampleValue(child)
      }
      return obj
    }
    case 'ARRAY': {
      const item: Record<string, unknown> = {}
      for (const child of param.children ?? []) {
        if (child.parameterType === 'DERIVED') continue
        item[child.name] = buildSampleValue(child)
      }
      return [item]
    }
    default: return 'string'
  }
}

function parseDefaultValue(value: string, dataType: DataType): unknown {
  switch (dataType) {
    case 'NUMBER': {
      const n = Number(value)
      return isNaN(n) ? 0 : n
    }
    case 'BOOLEAN': return value === 'true'
    default: return value
  }
}
