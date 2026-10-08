import request from '@/api/request'

export interface Employee {
  id?: number
  code: string
  name: string
  // 2026-10-06（P102 批次3）：原先声明的 department 全仓无人读取，且后端从未返回过该字段
  // —— 页面用的 deptName 同样不是后端字段（EmployeeEntity 只有 deptId: Long）。
  // 故把死字段换成后端真实存在的 deptId，让接口反映真实用法。
  deptId?: number
  position?: string
  phone?: string
  email?: string
  isActive?: boolean
  remark?: string
}

export function pageEmployee(params: any): Promise<any> {
  return request.get('/v1/employees/page', { params })
}

export function listEmployee(): Promise<Employee[]> {
  return request.get('/v1/employees/list')
}

export function getEmployee(id: number): Promise<Employee> {
  return request.get(`/v1/employees/${id}`)
}

export function getEmployeeByName(name: string): Promise<Employee> {
  return request.get('/v1/employees/by-name', { params: { name } })
}

export function createEmployee(data: Employee): Promise<Employee> {
  return request.post('/v1/employees', data)
}

export function updateEmployee(id: number, data: Employee): Promise<Employee> {
  return request.put(`/v1/employees/${id}`, data)
}

export function deleteEmployee(id: number): Promise<void> {
  return request.delete(`/v1/employees/${id}`)
}