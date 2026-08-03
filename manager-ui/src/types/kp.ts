export type LineType = 'KIT' | 'PART' | 'DRONE'
export type ProposalStatus = 'DRAFT' | 'FINAL'

export interface DroneModel {
  id: string
  code: string
  name: string
  defaultPrice: number
  dronePrice?: number
  vatMode?: string
  sortOrder: number
  active: boolean
  hasZipPackage?: boolean
}

export interface ZipPackageItem {
  id: string
  name: string
  sku?: string | null
  qty: number
  unitPrice: number
  sortOrder?: number
}

export interface ZipPackage {
  droneModelId: string
  name: string
  price: number
  items: ZipPackageItem[]
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
}

export interface ProposalUpsertRequest {
  recipient: string
  droneModelId: string
  kitQty: number
  unitKitPrice: number
  droneVatPct: 0 | 22
  extraLines: ProposalLine[]
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
  kitQty: number
  unitKitPrice: number
  droneVatPct?: 0 | 22
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

export interface CalcPreview {
  priceKey: string
  vatMode: string
  kitQty: number
  startPrice: number
  unitKitPrice: number
  priceDiff: number
  baseDronePrice: number
  unitDronePrice: number
  droneTotal: number
  grandTotal: number
  ndsTotal: number
  droneVatPct?: 0 | 22
  lines: Array<{
    lineType: LineType
    name: string
    qty: number
    unitPrice: number
  }>
}

export interface KitPreset {
  code: string
  dronePrice: number
  startPrice: number
  vatMode: string
  lines: CalcPreview['lines']
}
