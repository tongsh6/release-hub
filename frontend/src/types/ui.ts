export type FieldType = 'input' | 'select' | 'dateRange' | 'datetimeRange'

export interface SearchField {
  prop: string
  label: string
  type: FieldType
  placeholder?: string
  options?: Array<{ label: string; value: any }>
  span?: number
  clearable?: boolean
}

export interface SearchSchema {
  fields: SearchField[]
}

export interface TableColumn {
  prop?: string
  label: string
  width?: number | string
  minWidth?: number | string
  fixed?: 'left' | 'right'
  align?: 'left' | 'center' | 'right'
  slot?: string
}
