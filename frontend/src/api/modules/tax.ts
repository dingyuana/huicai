import request from '@/api/request'

export interface TaxType {
  id?: number
  code: string
  name: string
  taxCategory: string
  rate: number
  isActive?: boolean
  remark?: string
}

export interface InputInvoice {
  id?: number
  invoiceNo: string
  invoiceDate: string
  period?: string
  vendorId?: number
  vendorName?: string
  amount: number
  taxRate: number
  taxAmount?: number
  totalAmount?: number
  invoiceType: string
  certificationStatus?: string
  deductionPeriod?: string
  deductionAmount?: number
  remark?: string
}

export interface OutputInvoice {
  id?: number
  invoiceNo: string
  invoiceDate: string
  period?: string
  customerId?: number
  customerName?: string
  amount: number
  taxRate: number
  taxAmount?: number
  totalAmount?: number
  invoiceType: string
  status?: string
  remark?: string
  aiRiskTag?: string
  originalInvoiceNo?: string
  originalInvoiceId?: number
  reversedByInvoiceId?: number
  reversedByInvoiceNo?: string
  docNo?: string
  receivableNo?: string
  voucherNo?: string
}

export function pageTaxType(params: any): Promise<any> {
  return request.get('/sme/tax/v1/tax/types/page', { params })
}
export function listTaxType(): Promise<TaxType[]> {
  return request.get('/sme/tax/v1/tax/types/list')
}
export function createTaxType(data: TaxType): Promise<TaxType> {
  return request.post('/sme/tax/v1/tax/types', data)
}
export function deleteTaxType(id: number): Promise<void> {
  return request.delete(`/sme/tax/v1/tax/types/${id}`)
}
export function pageInputInvoice(params: any): Promise<any> {
  return request.get('/sme/tax/v1/tax/input-invoices/page', { params })
}
export function createInputInvoice(data: InputInvoice): Promise<InputInvoice> {
  return request.post('/sme/tax/v1/tax/input-invoices', data)
}
export function certifyInputInvoice(id: number, deductionPeriod?: string): Promise<InputInvoice> {
  return request.post(`/sme/tax/v1/tax/input-invoices/${id}/certify`, null, { params: { deductionPeriod } })
}
export function inputInvoiceSummary(period: string): Promise<any> {
  return request.get('/sme/tax/v1/tax/input-invoices/summary', { params: { period } })
}
// ====== 进项发票状态机 (P40) ======
export function submitInputReview(id: number): Promise<void> {
  return request.post(`/sme/tax/v1/tax/input-invoices/${id}/submit-review`)
}
export function confirmInputInvoice(id: number): Promise<void> {
  return request.post(`/sme/tax/v1/tax/input-invoices/${id}/confirm`)
}
export function rejectInputInvoice(id: number, reason: string): Promise<void> {
  return request.post(`/sme/tax/v1/tax/input-invoices/${id}/reject`, null, { params: { reason } })
}
export function revertInputInvoice(id: number): Promise<void> {
  return request.post(`/sme/tax/v1/tax/input-invoices/${id}/revert`)
}
export function voidInputInvoice(id: number, reason: string): Promise<void> {
  return request.post(`/sme/tax/v1/tax/input-invoices/${id}/void`, null, { params: { reason } })
}
export function reverseInputInvoice(id: number, reason: string): Promise<number> {
  return request.post(`/sme/tax/v1/tax/input-invoices/${id}/reverse`, null, { params: { reason } })
}
export function pageOutputInvoice(params: any): Promise<any> {
  return request.get('/sme/tax/v1/tax/output-invoices/page', { params })
}
export function getOutputInvoice(id: number): Promise<OutputInvoice> {
  return request.get(`/sme/tax/v1/tax/output-invoices/${id}`)
}
export function deleteOutputInvoice(id: number): Promise<void> {
  return request.delete(`/sme/tax/v1/tax/output-invoices/${id}`)
}
// ====== 销项发票状态机 ======
export function submitForReview(id: number): Promise<void> {
  return request.post(`/sme/tax/v1/tax/output-invoices/${id}/submit-review`)
}
export function confirmOutputInvoice(id: number): Promise<void> {
  return request.post(`/sme/tax/v1/tax/output-invoices/${id}/confirm`)
}
export function rejectOutputInvoice(id: number, reason: string): Promise<void> {
  return request.post(`/sme/tax/v1/tax/output-invoices/${id}/reject`, null, { params: { reason } })
}
export function revertOutputInvoice(id: number): Promise<void> {
  return request.post(`/sme/tax/v1/tax/output-invoices/${id}/revert`)
}
export function voidOutputInvoice(id: number, reason: string): Promise<void> {
  return request.post(`/sme/tax/v1/tax/output-invoices/${id}/void`, null, { params: { reason } })
}
export function markVouchered(id: number): Promise<void> {
  return request.post(`/sme/tax/v1/tax/output-invoices/${id}/mark-vouchered`)
}
export function createOutputInvoice(data: OutputInvoice): Promise<OutputInvoice> {
  return request.post('/sme/tax/v1/tax/output-invoices', data)
}
export function outputInvoiceSummary(params?: Record<string, any>): Promise<any> {
  return request.get('/sme/tax/v1/tax/output-invoices/summary', { params })
}
export function calculateVat(period: string): Promise<any> {
  return request.get('/sme/tax/v1/tax/vat/calculate', { params: { period } })
}
// ====== P56 销项发票批量操作 ======
export interface BatchFailure {
  id: number
  reason: string
}
export interface BatchResult {
  success: number[]
  failure: BatchFailure[]
}

export function batchSubmitForReview(ids: number[]): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/submit-review', { ids })
}
export function batchConfirmOutputInvoice(ids: number[]): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/confirm', { ids })
}
export function batchRejectOutputInvoice(ids: number[], reason: string): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/reject', { ids, reason })
}
export function batchRevertOutputInvoice(ids: number[]): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/revert', { ids })
}
export function batchMarkVouchered(ids: number[]): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/mark-vouchered', { ids })
}
export function batchVoidOutputInvoice(ids: number[], reason: string): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/void', { ids, reason })
}
export function batchReverseOutputInvoice(ids: number[], reason: string): Promise<BatchResult> {
  return request.post('/sme/tax/v1/tax/output-invoices/batch/reverse', { ids, reason })
}
// ====== P58 发票-收付款勾稽（三流合一只读视图） ======
export interface InvoiceReconcileVO {
  invoiceId: number
  invoiceNo: string
  invoiceDate?: string
  vendorName?: string
  customerName?: string
  amount?: number
  taxAmount?: number
  certificationStatus?: string
  declaredStatus?: string
  paidAmount?: number
  unpaidAmount?: number
  reconcileStatus?: 'UNPAID' | 'PARTIAL' | 'PAID'
  hasRedFlushed?: boolean
}

export function queryInputReconcile(params: { period?: string; vendorId?: number }): Promise<InvoiceReconcileVO[]> {
  return request.get('/sme/tax/v1/invoice-reconcile/input', { params })
}
export function queryOutputReconcile(params: { period?: string; customerId?: number }): Promise<InvoiceReconcileVO[]> {
  return request.get('/sme/tax/v1/invoice-reconcile/output', { params })
}
// ⚠️ 原 `aiSubjectMapping()` 调用 `/agent/route`，**后端从无此端点**
// （`rg 'agent/route' backend/src/main/java` 零命中；AI 侧只有
//   `/api/v1/ai/tasks` 异步下发端点与 Python 的 ocr/embedding/anomaly/qa）。
// 该函数被 `OutputInvoiceList.vue` 的「AI 推荐科目」按钮调用 ⇒ 点一下必然 404。
// 对应需求 `REQ-2026-038`（AI 科目映射）状态仍是 `PLANNED`，
// 即「功能未实现」而非「契约陈旧」（AGENTS §4.5 第 39 条判据：页面真的在用）。
// 故按「诚实标注缺口」处置：删除死函数 + 按钮禁用并说明，不伪造端点。
