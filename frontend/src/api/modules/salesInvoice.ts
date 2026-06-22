import request from '@/api/request'

export function previewSalesInvoices(file: File): Promise<{
  total: number
  valid: number
  errors: any[]
  batchId: string
  previews: any[]
}> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/sales-invoices/preview', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function confirmSalesInvoicesImport(batchId: string): Promise<{
  total: number
  success: number
  docCreated: number
  voucherCreated: number
  errors: Array<{ row: number; invoiceNo: string; message: string }>
  batchId: string
}> {
  return request.post('/sales-invoices/confirm-import', null, { params: { batchId } })
}

export function importSalesInvoices(file: File): Promise<{
  total: number
  success: number
  docCreated: number
  voucherCreated: number
  errors: Array<{ row: number; invoiceNo: string; message: string }>
  batchId: string
}> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/sales-invoices/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function batchLinkRedFlush(): Promise<{ matched: number; skipped: number; total: number }> {
  return request.post('/sales-invoices/batch-link-red-flush')
}