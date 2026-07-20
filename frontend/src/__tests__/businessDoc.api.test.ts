import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as businessDocApi from '@/api/modules/businessDoc'

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

describe('BusinessDoc API Module', () => {
  describe('Query APIs', () => {
    it('getBusinessDocPage calls correct endpoint', async () => {
      const mockPage = { records: [], total: 0, page: 1, size: 10, pages: 0 }
      mockRequest.post.mockResolvedValue(mockPage)

      const params: businessDocApi.BusinessDocQuery = {
        docType: 'RECEIPT',
        status: 'DRAFT',
        period: '2026-07',
        keyword: 'test',
        current: 1,
        size: 10,
      }
      const result = await businessDocApi.getBusinessDocPage(params)
      expect(mockRequest.post).toHaveBeenCalledWith('/sme/arap/v1/business-docs/page', params)
      expect(result).toEqual(mockPage)
    })

    it('getBusinessDoc calls correct endpoint', async () => {
      const mockDoc: businessDocApi.BusinessDocVO = {
        id: 1,
        docNo: 'BD2026070001',
        docType: 'RECEIPT',
        docDate: '2026-07-15',
        period: '2026-07',
        amount: 1000,
        status: 'DRAFT',
        entries: [],
      }
      mockRequest.get.mockResolvedValue(mockDoc)

      const result = await businessDocApi.getBusinessDoc(1)
      expect(mockRequest.get).toHaveBeenCalledWith('/sme/arap/v1/business-docs/1')
      expect(result).toEqual(mockDoc)
    })
  })

  describe('CRUD APIs', () => {
    it('createBusinessDoc calls correct endpoint', async () => {
      const mockDoc: businessDocApi.BusinessDocVO = {
        id: 1,
        docNo: 'BD2026070001',
        docType: 'RECEIPT',
        docDate: '2026-07-15',
        period: '2026-07',
        amount: 1000,
        status: 'DRAFT',
        entries: [{ amount: 1000 }],
      }
      mockRequest.post.mockResolvedValue(mockDoc)

      const data: businessDocApi.BusinessDocDTO = {
        docType: 'RECEIPT',
        docDate: '2026-07-15',
        period: '2026-07',
        amount: 1000,
        entries: [{ amount: 1000 }],
      }
      const result = await businessDocApi.createBusinessDoc(data)
      expect(mockRequest.post).toHaveBeenCalledWith('/sme/arap/v1/business-docs', data)
      expect(result).toEqual(mockDoc)
    })

    it('updateBusinessDoc calls correct endpoint', async () => {
      const mockDoc: businessDocApi.BusinessDocVO = {
        id: 1,
        docNo: 'BD2026070001',
        docType: 'RECEIPT',
        docDate: '2026-07-15',
        period: '2026-07',
        amount: 2000,
        status: 'DRAFT',
        entries: [{ amount: 2000 }],
      }
      mockRequest.put.mockResolvedValue(mockDoc)

      const data: businessDocApi.BusinessDocDTO = {
        docType: 'RECEIPT',
        docDate: '2026-07-15',
        period: '2026-07',
        amount: 2000,
        entries: [{ amount: 2000 }],
      }
      const result = await businessDocApi.updateBusinessDoc(1, data)
      expect(mockRequest.put).toHaveBeenCalledWith('/sme/arap/v1/business-docs/1', data)
      expect(result).toEqual(mockDoc)
    })

    it('deleteBusinessDoc calls correct endpoint', async () => {
      mockRequest.delete.mockResolvedValue(undefined)

      await businessDocApi.deleteBusinessDoc(1)
      expect(mockRequest.delete).toHaveBeenCalledWith('/sme/arap/v1/business-docs/1')
    })
  })

  describe('Status Transition APIs', () => {
    it('submitBusinessDoc calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await businessDocApi.submitBusinessDoc(1)
      expect(mockRequest.post).toHaveBeenCalledWith('/sme/arap/v1/business-docs/1/submit')
    })

    it('approveBusinessDoc calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await businessDocApi.approveBusinessDoc(1)
      expect(mockRequest.post).toHaveBeenCalledWith('/sme/arap/v1/business-docs/1/approve')
    })

    it('rejectBusinessDoc calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await businessDocApi.rejectBusinessDoc(1)
      expect(mockRequest.post).toHaveBeenCalledWith('/sme/arap/v1/business-docs/1/reject')
    })

    it('generateVoucherFromDoc calls correct endpoint', async () => {
      const mockDoc: businessDocApi.BusinessDocVO = {
        id: 1,
        docNo: 'BD2026070001',
        docType: 'RECEIPT',
        docDate: '2026-07-15',
        period: '2026-07',
        amount: 1000,
        status: 'VOUCHERED',
        voucherId: 10,
        voucherNo: 'V2026070010',
        entries: [{ amount: 1000 }],
      }
      mockRequest.post.mockResolvedValue(mockDoc)

      const result = await businessDocApi.generateVoucherFromDoc(1)
      expect(mockRequest.post).toHaveBeenCalledWith('/sme/arap/v1/business-docs/1/generate-voucher')
      expect(result).toEqual(mockDoc)
    })

    it('reverseBusinessDoc calls correct endpoint', async () => {
      const mockDoc: businessDocApi.BusinessDocVO = {
        id: 2,
        docNo: 'BD2026070002',
        docType: 'RECEIPT',
        docDate: '2026-07-15',
        period: '2026-07',
        amount: 1000,
        status: 'REVERSED',
        entries: [],
      }
      mockRequest.post.mockResolvedValue(mockDoc)

      const result = await businessDocApi.reverseBusinessDoc(1)
      expect(mockRequest.post).toHaveBeenCalledWith('/sme/arap/v1/business-docs/1/reverse')
      expect(result).toEqual(mockDoc)
    })
  })
})