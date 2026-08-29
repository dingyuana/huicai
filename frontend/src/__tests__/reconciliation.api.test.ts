import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as reconciliationApi from '@/api/modules/reconciliation'

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

describe('Reconciliation API Module', () => {
  describe('executeReconciliation', () => {
    it('calls POST /sme/arap/v1/reconciliation/execute with data body', async () => {
      const mockResponse = { id: 1, status: 'SUCCESS' }
      mockRequest.post.mockResolvedValue(mockResponse)

      const data = {
        sourceDocType: 'RECEIPT',
        sourceDocId: 100,
        targetDocType: 'INVOICE',
        targetDocId: 200,
        amount: 1500.00,
        matchScore: 95.5,
        matchMethod: 'MANUAL',
        customerId: 50,
        remark: 'Manual reconciliation',
      }
      const result = await reconciliationApi.executeReconciliation(data)

      expect(mockRequest.post).toHaveBeenCalledWith(
        '/sme/arap/v1/reconciliation/execute',
        data,
      )
      expect(result).toEqual(mockResponse)
    })

    it('works with minimal required fields', async () => {
      mockRequest.post.mockResolvedValue({ id: 2 })

      const data = {
        sourceDocType: 'PAYMENT',
        sourceDocId: 101,
        targetDocType: 'INVOICE',
        targetDocId: 201,
        amount: 500.00,
        matchScore: 80.0,
        matchMethod: 'AUTO',
      }
      await reconciliationApi.executeReconciliation(data)

      expect(mockRequest.post).toHaveBeenCalledWith(
        '/sme/arap/v1/reconciliation/execute',
        data,
      )
    })
  })

  describe('reverseReconciliation', () => {
    it('calls POST /sme/arap/v1/reconciliation/{logId}/reverse with reason param', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await reconciliationApi.reverseReconciliation(42, 'Incorrect match')

      expect(mockRequest.post).toHaveBeenCalledWith(
        '/sme/arap/v1/reconciliation/42/reverse',
        null,
        { params: { reason: 'Incorrect match' } },
      )
    })

    it('passes empty string as reason when omitted', async () => {
      mockRequest.post.mockResolvedValue(undefined)

      await reconciliationApi.reverseReconciliation(99)

      expect(mockRequest.post).toHaveBeenCalledWith(
        '/sme/arap/v1/reconciliation/99/reverse',
        null,
        { params: { reason: '' } },
      )
    })
  })

  describe('getReconciliationRecords', () => {
    it('calls GET /sme/arap/v1/reconciliation/records with query params', async () => {
      const mockRecords = [
        { id: 1, sourceDocType: 'RECEIPT', targetDocType: 'INVOICE', amount: 1000 },
      ]
      mockRequest.get.mockResolvedValue(mockRecords)

      const result = await reconciliationApi.getReconciliationRecords('RECEIPT', 100)

      expect(mockRequest.get).toHaveBeenCalledWith(
        '/sme/arap/v1/reconciliation/records',
        { params: { sourceDocType: 'RECEIPT', sourceDocId: 100 } },
      )
      expect(result).toEqual(mockRecords)
    })
  })

  describe('preCheckReconciliation', () => {
    it('calls POST /sme/arap/v1/reconciliation/pre-check with data body', async () => {
      const mockPreCheck: reconciliationApi.PreCheckResult = {
        allPassed: true,
        checks: [
          { checkName: 'AmountMatch', passed: true, message: 'ok' },
        ],
      }
      mockRequest.post.mockResolvedValue(mockPreCheck)

      const data = {
        sourceDocType: 'RECEIPT',
        sourceDocId: 100,
        targetDocType: 'INVOICE',
        targetDocId: 200,
        amount: 1500.00,
        period: '2026-07',
      }
      const result = await reconciliationApi.preCheckReconciliation(data)

      expect(mockRequest.post).toHaveBeenCalledWith(
        '/sme/arap/v1/reconciliation/pre-check',
        data,
      )
      expect(result).toEqual(mockPreCheck)
    })
  })

  describe('getReconciliationTrace', () => {
    it('calls GET /sme/arap/v1/reconciliation/{id}/trace', async () => {
      const mockTrace: reconciliationApi.ReconciliationTraceVO = {
        traceId: 'TRACE001',
        settlement: { id: 1, settlementNo: 'STL001', amount: 1000, status: 'SETTLED', createdAt: '2026-07-15T10:00:00' },
        upstream: {
          bankTransaction: { id: 1, transactionNo: 'TXN001', amount: 1000, counterAccount: 'ACME' },
          receipt: { id: 1, docNo: 'RC001', amount: 1000, status: 'CONFIRMED' },
        },
        downstream: {
          businessDocs: [{ id: 1, docNo: 'BD001', docType: 'INVOICE', amount: 1000, settledAmount: 1000, unsettledAmount: 0 }],
          invoices: [{ id: 1, invoiceNo: 'INV001', amount: 1000, status: 'ISSUED', invoiceType: 'INVOICE_OUT' }],
        },
        operationTrail: [{ operationType: 'MATCH', operator: 'admin', time: '2026-07-15T10:00:00', remark: 'Auto matched' }],
        voucher: { id: 1, voucherNo: 'V2026070001', status: 'POSTED' },
      }
      mockRequest.get.mockResolvedValue(mockTrace)

      const result = await reconciliationApi.getReconciliationTrace(1)

      expect(mockRequest.get).toHaveBeenCalledWith('/sme/arap/v1/reconciliation/1/trace')
      expect(result).toEqual(mockTrace)
    })
  })

  describe('autoFifoReconciliation', () => {
    it('calls POST /sme/arap/v1/reconciliation/auto-fifo with params', async () => {
      const mockFifoResult: reconciliationApi.ReconciliationFifoPreview[] = [
        {
          sourceDocId: 1, sourceDocNo: 'SRC001', sourceDocType: 'RECEIPT',
          sourceAmount: 3000, sourceUnsettledAmount: 3000,
          targetDocId: 2, targetDocNo: 'TGT002', targetDocType: 'INVOICE_OUT',
          targetAmount: 3000, targetUnsettledAmount: 3000,
          amount: 3000,
        },
      ]
      mockRequest.post.mockResolvedValue(mockFifoResult)

      const params = {
        partyId: 10,
        targetDocType: 'INVOICE',
        amount: 5000,
        sourceDocType: 'RECEIPT',
        sourceDocId: 1,
        period: '2026-07',
      }
      const result = await reconciliationApi.autoFifoReconciliation(params)

      expect(mockRequest.post).toHaveBeenCalledWith(
        '/sme/arap/v1/reconciliation/auto-fifo',
        null,
        { params },
      )
      expect(result).toEqual(mockFifoResult)
    })

    it('works without optional period and summary', async () => {
      mockRequest.post.mockResolvedValue([])

      const params = {
        partyId: 5,
        targetDocType: 'INVOICE',
        amount: 1000,
        sourceDocType: 'PAYMENT',
        sourceDocId: 2,
      }
      await reconciliationApi.autoFifoReconciliation(params)

      expect(mockRequest.post).toHaveBeenCalledWith(
        '/sme/arap/v1/reconciliation/auto-fifo',
        null,
        { params },
      )
    })
  })

  describe('batchExecuteReconciliation', () => {
    it('calls POST /sme/arap/v1/reconciliation/batch-execute with requests body', async () => {
      const mockLogs = [{ id: 1, status: 'CONFIRMED' }, { id: 2, status: 'CONFIRMED' }]
      mockRequest.post.mockResolvedValue(mockLogs)

      const requests = [
        {
          sourceDocType: 'receipt',
          sourceDocId: 1,
          targetDocType: 'INVOICE_OUT',
          targetDocId: 2,
          amount: 3000,
          matchScore: 100,
          matchMethod: 'AUTO',
        },
      ]
      const result = await reconciliationApi.batchExecuteReconciliation(requests)

      expect(mockRequest.post).toHaveBeenCalledWith(
        '/sme/arap/v1/reconciliation/batch-execute',
        requests,
      )
      expect(result).toEqual(mockLogs)
    })
  })

  describe('getNumberingTrace', () => {
    it('calls GET /base/voucher/v1/vouchers/trace?no={docNo}', async () => {
      const mockTrace = {
        traceId: 'TRACE002',
        settlement: null,
        upstream: null,
        downstream: null,
        operationTrail: [],
        voucher: { id: 1, voucherNo: 'V2026070001', status: 'POSTED' },
      }
      mockRequest.get.mockResolvedValue(mockTrace)

      const result = await reconciliationApi.getNumberingTrace('V2026070001')

      expect(mockRequest.get).toHaveBeenCalledWith(
        '/base/voucher/v1/vouchers/trace?no=V2026070001',
      )
      expect(result).toEqual(mockTrace)
    })

    it('encodes special characters in docNo', async () => {
      mockRequest.get.mockResolvedValue({})

      await reconciliationApi.getNumberingTrace('V2026/001')

      expect(mockRequest.get).toHaveBeenCalledWith(
        '/base/voucher/v1/vouchers/trace?no=V2026/001',
      )
    })
  })

  describe('getReceiptRecommend', () => {
    it('calls POST /sme/arap/v1/reconciliation/receipt/{id}/recommend with params', async () => {
      const mockRecommend: reconciliationApi.ReconciliationRecommendResult = {
        message: 'Found 2 matches',
        items: [
          { targetDocId: 1, targetDocNo: 'INV001', targetDocType: 'INVOICE', originalAmount: 1000, unsettledAmount: 500, matchScore: 95, matchLevel: 'HIGH', suggestedAmount: 500 },
        ],
      }
      mockRequest.post.mockResolvedValue(mockRecommend)

      const params = {
        sourceDocType: 'RECEIPT',
        customerId: 50,
        amount: 1000,
        summary: 'Payment for invoice INV001',
      }
      const result = await reconciliationApi.getReceiptRecommend(42, params)

      expect(mockRequest.post).toHaveBeenCalledWith(
        '/sme/arap/v1/reconciliation/receipt/42/recommend',
        null,
        { params },
      )
      expect(result).toEqual(mockRecommend)
    })
  })

  describe('getPaymentRecommend', () => {
    it('calls POST /sme/arap/v1/reconciliation/payment/{id}/recommend with params', async () => {
      const mockRecommend: reconciliationApi.ReconciliationRecommendResult = {
        message: 'Found 1 match',
        items: [
          { targetDocId: 3, targetDocNo: 'INV003', targetDocType: 'INVOICE', originalAmount: 2000, unsettledAmount: 2000, matchScore: 88, matchLevel: 'MEDIUM', suggestedAmount: 1500 },
        ],
      }
      mockRequest.post.mockResolvedValue(mockRecommend)

      const params = {
        sourceDocType: 'PAYMENT',
        vendorId: 30,
        amount: 2000,
        counterpartyName: 'Supplier ABC',
      }
      const result = await reconciliationApi.getPaymentRecommend(77, params)

      expect(mockRequest.post).toHaveBeenCalledWith(
        '/sme/arap/v1/reconciliation/payment/77/recommend',
        null,
        { params },
      )
      expect(result).toEqual(mockRecommend)
    })
  })
})