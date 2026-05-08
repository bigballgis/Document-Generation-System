import type { ParameterDTO } from '@/types/parameter'
import type { FormField, FormControlType } from '@/types/testDataForm'

/**
 * Determine the form control type for a parameter based on its DataType and validation rules.
 */
function resolveControlType(param: ParameterDTO): FormControlType {
  const hasEnum = param.validationRules?.enum_values && param.validationRules.enum_values.length > 0

  switch (param.dataType) {
    case 'STRING':
      return hasEnum ? 'select' : 'input'
    case 'NUMBER':
      return hasEnum ? 'select' : 'number-input'
    case 'BOOLEAN':
      return 'switch'
    case 'DATE':
      return 'date-picker'
    case 'OBJECT':
      return 'collapse'
    case 'ARRAY':
      return 'dynamic-list'
    default:
      return 'input'
  }
}

/**
 * Build a form field tree from a parameter tree.
 * Pure function — no side effects.
 *
 * Each parameter node maps to exactly one FormField node.
 * Parent-child relationships are preserved.
 */
export function buildFormFields(parameters: ParameterDTO[], parentPath = ''): FormField[] {
  return parameters.map((param) => {
    const path = parentPath ? `${parentPath}.${param.name}` : param.name
    const controlType = resolveControlType(param)
    const enumValues = param.validationRules?.enum_values ?? null

    const field: FormField = {
      parameterId: param.id,
      name: param.name,
      path,
      dataType: param.dataType,
      controlType,
      required: param.required,
      defaultValue: param.defaultValue,
      enumValues: enumValues && enumValues.length > 0 ? enumValues : null,
      isArray: param.dataType === 'ARRAY',
    }

    if (param.children && param.children.length > 0) {
      field.children = buildFormFields(param.children, path)
    }

    return field
  })
}

/**
 * Build initial form values from form fields, using defaultValue where available.
 */
export function buildInitialValues(fields: FormField[]): Record<string, unknown> {
  const values: Record<string, unknown> = {}
  for (const field of fields) {
    if (field.dataType === 'OBJECT' && field.children) {
      values[field.name] = buildInitialValues(field.children)
    } else if (field.dataType === 'ARRAY') {
      values[field.name] = []
    } else if (field.dataType === 'BOOLEAN') {
      values[field.name] = field.defaultValue === 'true'
    } else if (field.dataType === 'NUMBER') {
      values[field.name] = field.defaultValue != null ? Number(field.defaultValue) : null
    } else {
      values[field.name] = field.defaultValue ?? null
    }
  }
  return values
}
