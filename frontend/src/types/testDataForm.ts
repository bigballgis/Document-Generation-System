import type { DataType } from './parameter'

/** Control type mapped from parameter DataType + validation rules */
export type FormControlType = 'input' | 'number-input' | 'switch' | 'date-picker' | 'select' | 'collapse' | 'dynamic-list'

/** A single form field node, mirrors the parameter tree structure */
export interface FormField {
  parameterId: number
  name: string
  path: string
  dataType: DataType
  controlType: FormControlType
  required: boolean
  defaultValue: string | null
  enumValues: string[] | null
  children?: FormField[]
  isArray?: boolean
}

/** State for the TestDataForm component */
export interface TestDataFormState {
  fields: FormField[]
  values: Record<string, unknown>
  mode: 'form' | 'json'
}
