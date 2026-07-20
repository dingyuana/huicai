import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as arapApi from '@/api/modules/arap'

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

describe('ARAP API Module', () => {
  it('pageCustomer calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue({ records: [], total: 0 })
    await arapApi.pageCustomer({ current: 1, size: 10 })
    expect(mockRequest.get).toHaveBeenCalledWith('/v1/customers/page', { params: { current: 1, size: 10 } })
  })

  it('listCustomer calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue([])
    await arapApi.listCustomer()
    expect(mockRequest.get).toHaveBeenCalledWith('/v1/customers/list')
  })

  it('createCustomer calls correct endpoint', async () => {
    mockRequest.post.mockResolvedValue({ id: 1 })
    await arapApi.createCustomer({ name: '测试客户', code: 'C001' })
    expect(mockRequest.post).toHaveBeenCalledWith('/v1/customers', { name: '测试客户', code: 'C001' })
  })

  it('pageVendor calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue({ records: [], total: 0 })
    await arapApi.pageVendor({ current: 1, size: 10 })
    expect(mockRequest.get).toHaveBeenCalledWith('/v1/vendors/page', { params: { current: 1, size: 10 } })
  })

  it('listVendor calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue([])
    await arapApi.listVendor()
    expect(mockRequest.get).toHaveBeenCalledWith('/v1/vendors/list')
  })

  it('pageReceivable calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue({ records: [], total: 0 })
    await arapApi.pageReceivable({ current: 1, size: 10 })
    expect(mockRequest.get).toHaveBeenCalledWith('/sme/arap/v1/receivables/page', { params: { current: 1, size: 10 } })
  })

  it('overdueReceivables calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue([])
    await arapApi.overdueReceivables()
    expect(mockRequest.get).toHaveBeenCalledWith('/sme/arap/v1/receivables/overdue')
  })

  it('customerUnsettledSummary calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue({ total: 10000, unsettled: 5000 })
    const result = await arapApi.customerUnsettledSummary()
    expect(mockRequest.get).toHaveBeenCalledWith('/v1/customers/unsettled-summary')
    expect(result).toEqual({ total: 10000, unsettled: 5000 })
  })

  it('pagePayable calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue({ records: [], total: 0 })
    await arapApi.pagePayable({ current: 1, size: 10 })
    expect(mockRequest.get).toHaveBeenCalledWith('/sme/arap/v1/payables/page', { params: { current: 1, size: 10 } })
  })

  it('receivableAging calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue([])
    await arapApi.receivableAging()
    expect(mockRequest.get).toHaveBeenCalledWith('/sme/arap/v1/receivables/aging', { params: { customerId: undefined } })
  })

  it('payableAging calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue([])
    await arapApi.payableAging(1)
    expect(mockRequest.get).toHaveBeenCalledWith('/sme/arap/v1/payables/aging', { params: { vendorId: 1 } })
  })

  it('pageBadDebt calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue({ records: [], total: 0 })
    await arapApi.pageBadDebt({ current: 1, size: 10 })
    expect(mockRequest.get).toHaveBeenCalledWith('/sme/arap/v1/bad-debts/page', { params: { current: 1, size: 10 } })
  })

  it('getAgingSummary calls correct endpoint', async () => {
    mockRequest.get.mockResolvedValue({ buckets: [] })
    const result = await arapApi.getAgingSummary({ period: '202607' })
    expect(mockRequest.get).toHaveBeenCalledWith('/sme/arap/v1/aging-analysis/summary', { params: { period: '202607' } })
    expect(result).toEqual({ buckets: [] })
  })
})