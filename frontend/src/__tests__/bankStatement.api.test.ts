import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as bsApi from '@/api/modules/bankStatement'

const mockRequest = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: mockRequest,
}))

beforeEach(() => { vi.clearAllMocks() })

describe('BankStatement API Module', () => {
  it('getBankStatementPage calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue({ records: [], total: 0 })
    await bsApi.getBankStatementPage({ current: 1, size: 10 })
    expect(mockRequest.get).toHaveBeenCalledWith('/sme/cash/v1/bank-statements/page', { params: { current: 1, size: 10 } })
  })

  it('getClassificationCounts calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue({ income: 5, expense: 3 })
    const result = await bsApi.getClassificationCounts('1')
    expect(mockRequest.get).toHaveBeenCalledWith('/sme/cash/v1/bank-statements/classification-counts', { params: { accountId: '1' } })
    expect(result).toEqual({ income: 5, expense: 3 })
  })

  it('getBankStatementDetail calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue({ id: 1, amount: 1000 })
    const result = await bsApi.getBankStatementDetail(1)
    expect(mockRequest.get).toHaveBeenCalledWith('/sme/cash/v1/bank-statements/1')
    expect(result).toEqual({ id: 1, amount: 1000 })
  })

  it('importStatementExcel calls correct endpoint', async () => {
    const file = new File(['test'], 'test.xlsx')
    mockRequest.post.mockResolvedValue({ total: 10, success: 8, classified: 8, errors: [], batchId: 'b1' })
    const result = await bsApi.importStatementExcel('1', file)
    expect(mockRequest.post).toHaveBeenCalledWith('/sme/cash/v1/bank-statements/import-excel', expect.any(FormData), { params: { accountId: '1' }, headers: { 'Content-Type': 'multipart/form-data' } })
    expect(result).toEqual({ total: 10, success: 8, classified: 8, errors: [], batchId: 'b1' })
  })

  it('confirmStatementImport calls correct endpoint', async () => {
    mockRequest.post.mockResolvedValue({ total: 10, success: 10, classified: 10, batchId: 'b1' })
    const result = await bsApi.confirmStatementImport('b1')
    expect(mockRequest.post).toHaveBeenCalledWith('/sme/cash/v1/bank-statements/confirm-import', null, { params: { batchId: 'b1' } })
    expect(result).toEqual({ total: 10, success: 10, classified: 10, batchId: 'b1' })
  })

  it('batchAuditStatements calls correct endpoint', async () => {
    mockRequest.post.mockResolvedValue(5)
    const result = await bsApi.batchAuditStatements([1, 2, 3])
    expect(mockRequest.post).toHaveBeenCalledWith('/sme/cash/v1/bank-statements/batch-audit', [1, 2, 3])
    expect(result).toBe(5)
  })

  it('batchReviewStatements calls correct endpoint', async () => {
    mockRequest.post.mockResolvedValue(3)
    const result = await bsApi.batchReviewStatements([1, 2])
    expect(mockRequest.post).toHaveBeenCalledWith('/sme/cash/v1/bank-statements/batch-review', [1, 2])
    expect(result).toBe(3)
  })

  it('autoMatchStatements calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue([])
    await bsApi.autoMatchStatements('1')
    expect(mockRequest.get).toHaveBeenCalledWith('/sme/cash/v1/bank-statements/auto-match', { params: { accountId: '1' } })
  })

  it('deleteStatement calls correct endpoint', async () => {
    mockRequest.delete.mockResolvedValue(undefined)
    await bsApi.deleteStatement(1)
    expect(mockRequest.delete).toHaveBeenCalledWith('/sme/cash/v1/bank-statements/1')
  })

  it('updateStatementClassification calls correct endpoint', async () => {
    mockRequest.put.mockResolvedValue({ id: 1, classification: 'SALES' })
    const result = await bsApi.updateStatementClassification(1, 'SALES')
    expect(mockRequest.put).toHaveBeenCalledWith('/sme/cash/v1/bank-statements/1/classification', { classification: 'SALES' })
    expect(result).toEqual({ id: 1, classification: 'SALES' })
  })
})