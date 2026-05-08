/**
 * Feature: template-workflow-stages
 * Property 2: parameter tree to form control tree isomorphic mapping
 * **Validates: Requirements 8.1, 8.2, 8.3, 8.4**
 *
 * Verifies that buildFormFields output is isomorphic to the parameter tree:
 * 1. Each parameter node maps to exactly one FormField node, parent-child preserved
 * 2. Leaf type mapping: STRING→input/select, NUMBER→number-input/select, BOOLEAN→switch, DATE→date-picker
 * 3. OBJECT → collapse with matching children
 * 4. ARRAY → dynamic-list with isArray=true
 * 5. required propagated correctly
 * 6. defaultValue propagated correctly
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { buildFormFields } from '@/composables/useTestDataForm'
import type { ParameterDTO, DataType } from '@/types/parameter'
import type { FormField } from '@/types/testDataForm'


const leafDataTypes: DataType[] = ['STRING', 'NUMBER', 'BOOLEAN', 'DATE']

const arbEnumValues = fc.option(
  fc.array(fc.constantFrom('alpha', 'beta', 'gamma', 'delta', 'epsilon'), { minLength: 1, maxLength: 3 }),
  { nil: undefined },
)

const arbDefaultValue = fc.option(fc.constantFrom('hello', '42', 'true', '2024-01-01'), { nil: null })

const arbParamName = fc.constantFrom('name', 'age', 'email', 'active', 'date', 'items', 'addr', 'phone', 'type', 'code', 'val', 'desc', 'note', 'key', 'ref', 'tag')

let idCounter = 0

function arbParameterTree(maxDepth: number): fc.Arbitrary<ParameterDTO> {
  const arbLeaf: fc.Arbitrary<ParameterDTO> = fc.record({
    dataType: fc.constantFrom(...leafDataTypes),
    name: arbParamName,
    required: fc.boolean(),
    defaultValue: arbDefaultValue,
    enumValues: arbEnumValues,
  }).map(({ dataType, name, required, defaultValue, enumValues }) => {
    idCounter++
    return {
      id: idCounter,
      templateId: 1,
      parentId: null,
      name,
      parameterType: 'REQUEST' as const,
      dataType,
      required,
      defaultValue,
      description: null,
      sortOrder: 0,
      expressionText: null,
      expressionType: null,
      validationRules: enumValues ? { enum_values: enumValues } : null,
      version: 1,
      parameterPath: name,
      children: [],
      createdAt: '',
      updatedAt: '',
    }
  })

  if (maxDepth <= 1) return arbLeaf

  const arbObject: fc.Arbitrary<ParameterDTO> = fc.record({
    name: arbParamName,
    required: fc.boolean(),
    children: fc.array(arbParameterTree(maxDepth - 1), { minLength: 1, maxLength: 3 }),
  }).map(({ name, required, children }) => {
    idCounter++
    return {
      id: idCounter,
      templateId: 1,
      parentId: null,
      name,
      parameterType: 'REQUEST' as const,
      dataType: 'OBJECT' as DataType,
      required,
      defaultValue: null,
      description: null,
      sortOrder: 0,
      expressionText: null,
      expressionType: null,
      validationRules: null,
      version: 1,
      parameterPath: name,
      children,
      createdAt: '',
      updatedAt: '',
    }
  })

  const arbArray: fc.Arbitrary<ParameterDTO> = fc.record({
    name: arbParamName,
    required: fc.boolean(),
  }).map(({ name, required }) => {
    idCounter++
    return {
      id: idCounter,
      templateId: 1,
      parentId: null,
      name,
      parameterType: 'REQUEST' as const,
      dataType: 'ARRAY' as DataType,
      required,
      defaultValue: null,
      description: null,
      sortOrder: 0,
      expressionText: null,
      expressionType: null,
      validationRules: null,
      version: 1,
      parameterPath: name,
      children: [],
      createdAt: '',
      updatedAt: '',
    }
  })

  return fc.oneof(arbLeaf, arbObject, arbArray)
}

const arbParameterList = fc.array(arbParameterTree(3), { minLength: 1, maxLength: 5 })


function countNodes(params: ParameterDTO[]): number {
  let count = 0
  for (const p of params) {
    count++
    if (p.children?.length) count += countNodes(p.children)
  }
  return count
}

function countFieldNodes(fields: FormField[]): number {
  let count = 0
  for (const f of fields) {
    count++
    if (f.children?.length) count += countFieldNodes(f.children)
  }
  return count
}

function expectedControlType(param: ParameterDTO): string {
  const hasEnum = param.validationRules?.enum_values && param.validationRules.enum_values.length > 0
  switch (param.dataType) {
    case 'STRING': return hasEnum ? 'select' : 'input'
    case 'NUMBER': return hasEnum ? 'select' : 'number-input'
    case 'BOOLEAN': return 'switch'
    case 'DATE': return 'date-picker'
    case 'OBJECT': return 'collapse'
    case 'ARRAY': return 'dynamic-list'
    default: return 'input'
  }
}

function verifyIsomorphism(params: ParameterDTO[], fields: FormField[]): void {
  expect(fields.length).toBe(params.length)
  for (let i = 0; i < params.length; i++) {
    const param = params[i]
    const field = fields[i]

    // Name matches
    expect(field.name).toBe(param.name)
    // DataType matches
    expect(field.dataType).toBe(param.dataType)
    // Control type correct
    expect(field.controlType).toBe(expectedControlType(param))
    // Required propagated
    expect(field.required).toBe(param.required)
    // DefaultValue propagated
    expect(field.defaultValue).toBe(param.defaultValue)
    // ARRAY flag
    if (param.dataType === 'ARRAY') {
      expect(field.isArray).toBe(true)
    }
    // Recursive children check for OBJECT
    if (param.dataType === 'OBJECT' && param.children?.length) {
      expect(field.children).toBeDefined()
      verifyIsomorphism(param.children, field.children!)
    }
  }
}


describe('Property 2: Parameter tree to form field tree isomorphic mapping', () => {
  it('should produce isomorphic form field tree for any parameter tree', { timeout: 30000 }, () => {
    fc.assert(
      fc.property(arbParameterList, (params) => {
        idCounter = 0
        const fields = buildFormFields(params)

        // 1. Same total node count
        expect(countFieldNodes(fields)).toBe(countNodes(params))

        // 2-6. Full isomorphism check
        verifyIsomorphism(params, fields)
      }),
      { numRuns: 100 },
    )
  })
})

