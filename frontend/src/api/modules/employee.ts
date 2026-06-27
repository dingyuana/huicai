import request from '@/api/request'

export interface Employee {
  id?: number
  code: string
  name: string
  department?: string
  position?: string
  phone?: string
  email?: string
  isActive?: boolean
  remark?: string
}

export function pageEmployee(params: any): Promise<any> {
  return request.get('/employees/page', { params })
}

export function listEmployee(): Promise<Employee[]> {
  return request.get('/employees/list')
}

export function getEmployee(id: number): Promise<Employee> {
  return request.get(`/employees/${id}`)
}

export function getEmployeeByName(name: string): Promise<Employee> {
  return request.get('/employees/by-name', { params: { name } })
}

export function createEmployee(data: Employee): Promise<Employee> {
  return request.post('/employees', data)
}

export function updateEmployee(id: number, data: Employee): Promise<Employee> {
  return request.put(`/employees/${id}`, data)
}

export function deleteEmployee(id: number): Promise<void> {
  return request.delete(`/employees/${id}`)
}