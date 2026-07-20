import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as voucherApi from '@/api/modules/voucher'

const mockRequest = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: mockRequest,
}))

beforeEach(() => {
  vi.clearAllMocks()
})

describe('Voucher API Module', () => {
  describe('Query APIs', () => {
    it('getVoucherPage calls correct endpoint', async () => {
      const mockPage = { records: [], total: 0, page: 1, size: 10, pages: 0 }
      mockRequest.post.mockResolvedValue(mockPage)

      const result = await voucherApi.getVoucherPage({ current: 1, size: 10, period: '2026-07', status: 'DRAFT' })
      expect(mockRequest.post).toHaveBeenCalledWith('/base/voucher/v1/vouchers/page', {
        current: 1,
        size: 10,
        period: '2026-07',
        status: 'DRAFT',
      })
      expect(result).toEqual(mockPage)
    })

    it('getVoucher calls correct endpoint', async () => {
      const mockVoucher: voucherApi.VoucherVO = {
        id: 1,
        voucherNo: 'V2026070001',
        period: '2026-07',
        voucherTypeId: 1,
        status: 'DRAFT',
        totalDebit: 1000,
        totalCredit: 1000,
      }
      mockRequest.get.mockResolvedValue(mockVoucher)

      const result = await voucherApi.getVoucher(1)
      expect(mockRequest.get).toHaveBeenCalledWith('/base/voucher/v1/vouchers/1')
      expect(result).toEqual(mockVoucher)
    })

    it('getTemplateByVoucherType calls correct endpoint', async () => {
      const mockTemplate = { id: 1, voucherTypeId: 1, entries: [] }
      mockRequest.get.mockResolvedValue(mockTemplate)

      const result = await voucherApi.getTemplateByVoucherType(1)
      expect(mockRequest.get).toHaveBeenCalledWith('/base/voucher/v1/vouchers/template-by-type/1')
      expect(result).toEqual(mockTemplate)
    })
  })

  describe('CRUD APIs', () => {
    it('createVoucher calls correct endpoint', async () => {
      const mockVoucher: voucherApi.VoucherVO = {
        id: 1,
        voucherNo: 'V2026070001',
        period: '2026-07',
        voucherTypeId: 1,
        status: 'DRAFT',
        totalDebit: 1000,
        totalCredit: 1000,
      }
      mockRequest.post.mockResolvedValue(mockVoucher)

      const data: voucherApi.VoucherCreateDTO = {
        period: '2026-07',
        voucherTypeId: 1,
        entries: [{ subjectId: 1, debit: 1000, credit: 0 }],
      }
      const result = await voucherApi.createVoucher(data)
      expect(mockRequest.post).toHaveBeenCalledWith('/base/voucher/v1/vouchers', data)
      expect(result).toEqual(mockVoucher)
    })

    it('updateVoucher calls correct endpoint', async () => {
      mockRequest.put.mockResolvedValue(undefined)

      const data: voucherApi.VoucherCreateDTO = {
        period: '2026-07',
        voucherTypeId: 1,
        entries: [{ subjectId: 1, debit: 1000, credit: 0 }],
      }
      await voucherApi.updateVoucher(1, data)
      expect(mockRequest.put).toHaveBeenCalledWith('/base/voucher/v1/vouchers/1', data)
    })

    it('deleteVoucher calls correct endpoint', async () => {
      mockRequest.delete.mockResolvedValue(undefined)

      await voucherApi.deleteVoucher(1)
      expect(mockRequest.delete).toHaveBeenCalledWith('/base/voucher/v1/vouchers/1')
    })
  })

  describe('Status Transition APIs', () => {
    it('submitVoucher calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await voucherApi.submitVoucher(1)
      expect(mockRequest.post).toHaveBeenCalledWith('/base/voucher/v1/vouchers/1/submit')
    })

    it('batchSubmitVouchers calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await voucherApi.batchSubmitVouchers({ ids: [1, 2] })
      expect(mockRequest.post).toHaveBeenCalledWith('/base/voucher/v1/vouchers/batch-submit', { ids: [1, 2] })
    })

    it('auditVoucher calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await voucherApi.auditVoucher(1)
      expect(mockRequest.post).toHaveBeenCalledWith('/base/voucher/v1/vouchers/1/audit')
    })

    it('batchAuditVouchers calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await voucherApi.batchAuditVouchers({ ids: [1, 2] })
      expect(mockRequest.post).toHaveBeenCalledWith('/base/voucher/v1/vouchers/batch-audit', { ids: [1, 2] })
    })

    it('postVoucher calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await voucherApi.postVoucher(1)
      expect(mockRequest.post).toHaveBeenCalledWith('/base/voucher/v1/vouchers/1/post')
    })

    it('batchPostVouchers calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await voucherApi.batchPostVouchers({ ids: [1, 2] })
      expect(mockRequest.post).toHaveBeenCalledWith('/base/voucher/v1/vouchers/batch-post', { ids: [1, 2] })
    })

    it('reverseVoucher calls correct endpoint', async () => {
      const mockReversed: voucherApi.VoucherVO = {
        id: 2,
        voucherNo: 'V2026070002',
        period: '2026-07',
        voucherTypeId: 1,
        status: 'POSTED',
        totalDebit: 1000,
        totalCredit: 1000,
      }
      mockRequest.post.mockResolvedValue(mockReversed)

      const result = await voucherApi.reverseVoucher(1)
      expect(mockRequest.post).toHaveBeenCalledWith('/base/voucher/v1/vouchers/1/reverse')
      expect(result).toEqual(mockReversed)
    })

    it('rejectVoucher calls correct endpoint with params', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await voucherApi.rejectVoucher(1, '摘要不清晰')
      expect(mockRequest.post).toHaveBeenCalledWith('/base/voucher/v1/vouchers/1/reject', null, {
        params: { reason: '摘要不清晰' },
      })
    })

    it('unpostVoucher calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await voucherApi.unpostVoucher(1)
      expect(mockRequest.post).toHaveBeenCalledWith('/base/voucher/v1/vouchers/1/unpost')
    })
  })
})