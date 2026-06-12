import request from '@/api/request'

export function pageTicket(params: any): Promise<any> {
  return request.get('/tickets/page', { params })
}
export function getTicket(id: number): Promise<any> {
  return request.get(`/tickets/${id}`)
}
export function createTicket(data: any): Promise<any> {
  return request.post('/tickets', data)
}
export function updateTicket(id: number, data: any): Promise<void> {
  return request.put(`/tickets/${id}`, data)
}
export function deleteTicket(id: number): Promise<void> {
  return request.delete(`/tickets/${id}`)
}
export function issueTicket(id: number): Promise<any> {
  return request.post(`/tickets/${id}/issue`)
}
export function cashTicket(id: number): Promise<any> {
  return request.post(`/tickets/${id}/cash`)
}
export function voidTicket(id: number): Promise<any> {
  return request.post(`/tickets/${id}/void`)
}
export function getTicketTransactions(ticketId: number): Promise<any[]> {
  return request.get(`/tickets/${ticketId}/transactions`)
}