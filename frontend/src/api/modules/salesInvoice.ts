import request from '@/api/request'

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