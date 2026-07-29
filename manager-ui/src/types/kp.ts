export type LineType = 'KIT' | 'PART' | 'DRONE'
export type ProposalStatus = 'DRAFT' | 'FINAL'

export interface DroneModel {
  id: string
  code: string
  name: string
  defaultPrice: number
  sortOrder: number
  active: boolean
}

export interface CatalogItem {
  id: string
  sku: string
  name: string
  price: number
  currency: string
}

export interface KitCatalogItem {
  partId: string
  partSku: string
  partName: string
  qty: number
  partPrice: number
}

export interface KitCatalogDetail {
  id: string
  sku: string
  name: string
  price: number
  currency: string
  items: KitCatalogItem[]
}

export interface ProposalLine {
  lineType: LineType
  refId: string
  sku: string
  name: string
  qty: number
  unitPrice: number
  discountPct: number
  kitItems?: Array<{
    partId: string
    partSku: string
    partName: string
    qty: number
    partPrice: number
  }>
}

export interface ProposalLineDto extends ProposalLine {
  id: string
  lineTotal: number
  kitItems?: ProposalLine['kitItems']
}

export interface ProposalUpsertRequest {
  recipient: string
  droneModelId: string
  dronePrice: number
  lines: ProposalLine[]
}

export interface Proposal {
  id: string
  number: number
  managerId: string
  managerUsername: string
  recipient: string
  droneModelId: string
  droneModelName: string
  dronePrice: number
  status: ProposalStatus
  subtotal: number
  discountTotal: number
  grandTotal: number
  ndsTotal: number
  pdfPath: string | null
  lines: ProposalLineDto[]
  createdAt: string
  updatedAt: string
}

export interface KitPreset {
  code: string
  dronePrice: number
  lines: Array<{
    lineType: LineType
    refId: string | null
    sku: string | null
    name: string
    qty: number
    unitPrice: number
    discountPct: number
  }>
}
