import request from '@/api/request'

/** 票据 —— 按 t_ticket 真实列声明的出参契约（P102 批次 7）。此前 pageTicket 全为 Promise<any>。 */
export interface Ticket {
  id?: number
  ticketNo?: string
  ticketType?: string
  amount: number
  bankId?: number
  payee?: string
  drawer?: string
  issueDate?: string
  expireDate?: string
  status?: string
  remark?: string
  createdAt?: string
}

/** 票据流水 —— 按 t_ticket_transaction 真实列声明（P102 批次 7）。 */
export interface TicketTransaction {
  id?: number
  ticketId?: number
  transType?: string
  transDate?: string
  recipient?: string
  amount?: number
  remark?: string
  operatorId?: number
  voucherId?: number
  createdAt?: string
}

export function pageTicket(params: any): Promise<any> {
  return request.get('/sme/cash/v1/tickets/page', { params })
}
export function getTicket(id: number): Promise<Ticket> {
  return request.get(`/sme/cash/v1/tickets/${id}`)
}
export function createTicket(data: any): Promise<Ticket> {
  return request.post('/sme/cash/v1/tickets', data)
}
export function updateTicket(id: number, data: any): Promise<Ticket> {
  return request.put(`/sme/cash/v1/tickets/${id}`, data)
}
export function deleteTicket(id: number): Promise<void> {
  return request.delete(`/sme/cash/v1/tickets/${id}`)
}
export function issueTicket(id: number): Promise<Ticket> {
  return request.post(`/sme/cash/v1/tickets/${id}/issue`)
}
export function cashTicket(id: number): Promise<Ticket> {
  return request.post(`/sme/cash/v1/tickets/${id}/cash`)
}
export function voidTicket(id: number): Promise<Ticket> {
  return request.post(`/sme/cash/v1/tickets/${id}/void`)
}
export function getTicketTransactions(ticketId: number): Promise<TicketTransaction[]> {
  return request.get(`/sme/cash/v1/tickets/${ticketId}/transactions`)
}
