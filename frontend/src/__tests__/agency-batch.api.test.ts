import { beforeEach, describe, expect, it, vi } from 'vitest'
import { batchAuditInvoices, batchImport } from '@/api/modules/agency'

const mockRequest = vi.hoisted(() => ({
  post: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: mockRequest,
}))

describe('代理批量接口契约', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.post.mockResolvedValue({ total: 0, success: 0, failed: 0, details: [] })
  })

  it('导入请求必须传发票类型和目标企业', async () => {
    const formData = new FormData()

    await batchImport(formData, 8, 'input')

    expect(mockRequest.post).toHaveBeenCalledWith('/v1/agency/batch/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      params: { enterpriseId: 8, invoiceType: 'input' },
    })
  })

  it('发票审核请求必须传明细类型，避免进销项 ID 空间混淆', async () => {
    await batchAuditInvoices([11, 12], 8, 'output')

    expect(mockRequest.post).toHaveBeenCalledWith('/v1/agency/batch/audit-invoices', [11, 12], {
      params: { enterpriseId: 8, invoiceType: 'output' },
    })
  })
})
