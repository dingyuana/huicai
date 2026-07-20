import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as taxApi from '@/api/modules/tax'

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

describe('Tax API Module', () => {
  describe('Input Invoice APIs', () => {
    it('pageInputInvoice calls correct endpoint', async () => {
      mockRequest.get.mockResolvedValue({ records: [], total: 0 })
      const result = await taxApi.pageInputInvoice({ current: 1, size: 10 })
      expect(mockRequest.get).toHaveBeenCalledWith('/sme/tax/v1/input-invoices/page', { params: { current: 1, size: 10 } })
      expect(result).toEqual({ records: [], total: 0 })
    })

    it('inputInvoiceSummary calls correct endpoint', async () => {
      mockRequest.get.mockResolvedValue({ total: 100, audited: 80 })
      const result = await taxApi.inputInvoiceSummary('202607')
      expect(mockRequest.get).toHaveBeenCalledWith('/sme/tax/v1/input-invoices/summary', { params: { period: '202607' } })
      expect(result).toEqual({ total: 100, audited: 80 })
    })

    it('certifyInputInvoice calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue({ id: 1, status: 'CERTIFIED' })
      const result = await taxApi.certifyInputInvoice(1, '202607')
      expect(mockRequest.post).toHaveBeenCalledWith('/sme/tax/v1/input-invoices/1/certify', null, { params: { deductionPeriod: '202607' } })
      expect(result).toEqual({ id: 1, status: 'CERTIFIED' })
    })

    it('confirmInputInvoice calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)
      await taxApi.confirmInputInvoice(1)
      expect(mockRequest.post).toHaveBeenCalledWith('/sme/tax/v1/input-invoices/1/confirm')
    })

    it('rejectInputInvoice calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)
      await taxApi.rejectInputInvoice(1, '信息不符')
      expect(mockRequest.post).toHaveBeenCalledWith('/sme/tax/v1/input-invoices/1/reject', null, { params: { reason: '信息不符' } })
    })

    it('reverseInputInvoice calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(1)
      const result = await taxApi.reverseInputInvoice(1, '红冲原因')
      expect(mockRequest.post).toHaveBeenCalledWith('/sme/tax/v1/input-invoices/1/reverse', null, { params: { reason: '红冲原因' } })
      expect(result).toBe(1)
    })
  })

  describe('Output Invoice APIs', () => {
    it('pageOutputInvoice calls correct endpoint', async () => {
      mockRequest.get.mockResolvedValue({ records: [], total: 0 })
      await taxApi.pageOutputInvoice({ current: 1, size: 10 })
      expect(mockRequest.get).toHaveBeenCalledWith('/sme/tax/v1/output-invoices/page', { params: { current: 1, size: 10 } })
    })

    it('getOutputInvoice calls correct endpoint', async () => {
      mockRequest.get.mockResolvedValue({ id: 1, invoiceNo: 'INV-001' })
      const result = await taxApi.getOutputInvoice(1)
      expect(mockRequest.get).toHaveBeenCalledWith('/sme/tax/v1/output-invoices/1')
      expect(result).toEqual({ id: 1, invoiceNo: 'INV-001' })
    })

    it('confirmOutputInvoice calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)
      await taxApi.confirmOutputInvoice(1)
      expect(mockRequest.post).toHaveBeenCalledWith('/sme/tax/v1/output-invoices/1/confirm')
    })

    it('voidOutputInvoice calls correct endpoint', async () => {
      mockRequest.post.mockResolvedValue(undefined)
      await taxApi.voidOutputInvoice(1, '开票错误')
      expect(mockRequest.post).toHaveBeenCalledWith('/sme/tax/v1/output-invoices/1/void', null, { params: { reason: '开票错误' } })
    })

    it('outputInvoiceSummary calls correct endpoint', async () => {
      mockRequest.get.mockResolvedValue({ total: 50, audited: 40 })
      const result = await taxApi.outputInvoiceSummary('202607')
      expect(mockRequest.get).toHaveBeenCalledWith('/sme/tax/v1/output-invoices/summary', { params: { period: '202607' } })
      expect(result).toEqual({ total: 50, audited: 40 })
    })

    it('calculateVat calls correct endpoint', async () => {
      mockRequest.get.mockResolvedValue({ outputTax: 13000, inputTax: 10000, payable: 3000 })
      const result = await taxApi.calculateVat('202607')
      expect(mockRequest.get).toHaveBeenCalledWith('/sme/tax/v1/tax/vat/calculate', { params: { period: '202607' } })
      expect(result).toEqual({ outputTax: 13000, inputTax: 10000, payable: 3000 })
    })
  })

  describe('Tax Type APIs', () => {
    it('listTaxType calls correct endpoint', async () => {
      mockRequest.get.mockResolvedValue([{ id: 1, name: '增值税' }])
      const result = await taxApi.listTaxType()
      expect(mockRequest.get).toHaveBeenCalledWith('/sme/tax/v1/tax/types/list')
      expect(result).toEqual([{ id: 1, name: '增值税' }])
    })

    it('pageTaxType calls correct endpoint', async () => {
      mockRequest.get.mockResolvedValue({ records: [], total: 0 })
      await taxApi.pageTaxType({ current: 1, size: 10 })
      expect(mockRequest.get).toHaveBeenCalledWith('/sme/tax/v1/tax/types/page', { params: { current: 1, size: 10 } })
    })

    it('deleteTaxType calls correct endpoint', async () => {
      mockRequest.delete.mockResolvedValue(undefined)
      await taxApi.deleteTaxType(1)
      expect(mockRequest.delete).toHaveBeenCalledWith('/sme/tax/v1/tax/types/1')
    })
  })
})