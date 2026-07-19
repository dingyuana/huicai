import request from '@/api/request'

export function pageTicket(params: any): Promise<any> {
  return request.get('/sme/cash/v1/tickets/page', { params })
}
export function getTicket(id: number): Promise<any> {
  return request.get(`/sme/cash/v1/tickets/${id}`)
}
export function createTicket(data: any): Promise<any> {
  return request.post('/sme/cash/v1/tickets', data)
}
export function updateTicket(id: number, data: any): Promise<void> {
  return request.put(`/sme/cash/v1/tickets/${id}`, data)
}
export function deleteTicket(id: number): Promise<void> {
  return request.delete(`/sme/cash/v1/tickets/${id}`)
}
export function issueTicket(id: number): Promise<any> {
  return request.post(`/sme/cash/v1/tickets/${id}/issue`)
}
export function cashTicket(id: number): Promise<any> {
  return request.post(`/sme/cash/v1/tickets/${id}/cash`)
}
export function voidTicket(id: number): Promise<any> {
  return request.post(`/sme/cash/v1/tickets/${id}/void`)
}
export function getTicketTransactions(ticketId: number): Promise<any[]> {
  return request.get(`/sme/cash/v1/tickets/${ticketId}/transactions`)
}