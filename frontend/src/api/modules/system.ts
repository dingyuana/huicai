import request from '@/api/request'
import type { PageResult } from '@/types/api'

// ==================== User ====================
export interface UserVO {
  id: number
  username: string
  realName: string
  nickname: string
  email: string
  phone: string
  avatar: string
  deptId: number
  deptName: string
  status: string
  remark: string
  lastLoginIp: string
  lastLoginAt: string
  roleIds: number[]
  createdAt: string
}

export interface UserParam {
  username?: string
  realName?: string
  nickname?: string
  email?: string
  phone?: string
  deptId?: number
  status?: string
  password?: string
  roleIds?: number[]
}

export function getUserPage(params: { page: number; size: number; keyword?: string; deptId?: number; status?: string }): Promise<PageResult<UserVO>> {
  return request.get('/v1/system/user/page', { params })
}

export function getUser(id: number): Promise<UserVO> {
  return request.get(`/v1/system/user/${id}`)
}

export function createUser(data: UserParam): Promise<void> {
  return request.post('/v1/system/user', data)
}

export function updateUser(id: number, data: UserParam): Promise<void> {
  return request.put(`/v1/system/user/${id}`, data)
}

export function updateUserStatus(id: number, status: string): Promise<void> {
  return request.put(`/v1/system/user/${id}/status`, { status })
}

export function resetPwd(id: number, newPassword: string): Promise<void> {
  return request.put(`/v1/system/user/${id}/reset-pwd`, { newPassword })
}

export function deleteUser(id: number): Promise<void> {
  return request.delete(`/v1/system/user/${id}`)
}

// ==================== 数据维护 ====================
export function clearBankStatements(): Promise<{ deleted: number; message: string }> {
  return request.post('/v1/system/clear-bank-statements')
}
export function clearInvoiceRecords(): Promise<{ deleted: number; message: string }> {
  return request.post('/v1/system/clear-invoice-records')
}
export function clearVouchers(): Promise<{ deleted: number; message: string }> {
  return request.post('/v1/system/clear-vouchers')
}
export function clearReportData(): Promise<{ deleted: number; message: string }> {
  return request.post('/v1/system/clear-report-data')
}
export function clearBusinessDocs(): Promise<{ deleted: number; message: string }> {
  return request.post('/v1/system/clear-business-docs')
}
export function clearAll(): Promise<{ deleted: number; message: string }> {
  return request.post('/v1/system/clear-all')
}
export function clearReceivables(): Promise<{ deleted: number; message: string }> {
  return request.post('/v1/system/clear-receivables')
}
export function clearPayables(): Promise<{ deleted: number; message: string }> {
  return request.post('/v1/system/clear-payables')
}

export function clearSettlements(): Promise<{ deleted: number; message: string }> {
  return request.post('/v1/system/clear-settlements')
}

// ==================== Role ====================
export interface RoleVO {
  id: number
  code: string
  name: string
  description: string
  status: string
  sortOrder: number
  dataScope: string
}

export interface RoleParam {
  code?: string
  name?: string
  description?: string
  status?: string
  sortOrder?: number
}

export function getRolePage(params: { page: number; size: number; keyword?: string; status?: string }): Promise<PageResult<RoleVO>> {
  return request.get('/v1/system/role/page', { params })
}

export function getRole(id: number): Promise<RoleVO> {
  return request.get(`/v1/system/role/${id}`)
}

export function createRole(data: RoleParam): Promise<RoleVO> {
  return request.post('/v1/system/role', data)
}

export function updateRole(id: number, data: RoleParam): Promise<void> {
  return request.put(`/v1/system/role/${id}`, data)
}

export function updateRoleStatus(id: number, status: string): Promise<void> {
  return request.put(`/v1/system/role/${id}/status`, { status })
}

export function deleteRole(id: number): Promise<void> {
  return request.delete(`/v1/system/role/${id}`)
}

export function getRoleMenus(id: number): Promise<number[]> {
  return request.get(`/v1/system/role/${id}/menus`)
}

export function assignRoleMenus(id: number, menuIds: number[]): Promise<void> {
  return request.put(`/v1/system/role/${id}/menus`, { menuIds })
}

export function getAllRoles(): Promise<RoleVO[]> {
  return request.get('/v1/system/user/roles')
}

// ==================== Menu ====================
export interface MenuVO {
  id: number
  name: string
  permissionCode: string
  type: string
  parentId: number | null
  path: string
  component: string
  icon: string
  sortOrder: number
  isActive: boolean
  isVisible: boolean
  keepAlive: boolean
  alwaysShow: boolean
  children: MenuVO[]
}

export interface MenuParam {
  name?: string
  permissionCode?: string
  type?: string
  parentId?: number | null
  path?: string
  component?: string
  icon?: string
  sortOrder?: number
  isActive?: boolean
  isVisible?: boolean
  keepAlive?: boolean
  alwaysShow?: boolean
}

export function getMenuTree(): Promise<MenuVO[]> {
  return request.get('/v1/system/menu/tree')
}

export function getMenuOptions(): Promise<MenuVO[]> {
  return request.get('/v1/system/menu/options')
}

export function getMenu(id: number): Promise<MenuVO> {
  return request.get(`/v1/system/menu/${id}`)
}

export function createMenu(data: MenuParam): Promise<void> {
  return request.post('/v1/system/menu', data)
}

export function updateMenu(id: number, data: MenuParam): Promise<void> {
  return request.put(`/v1/system/menu/${id}`, data)
}

export function deleteMenu(id: number): Promise<void> {
  return request.delete(`/v1/system/menu/${id}`)
}

// ==================== Dept ====================
export interface DeptVO {
  id: number
  name: string
  parentId: number | null
  sortOrder: number
  status: string
  leader: string
  phone: string
  email: string
  children: DeptVO[]
}

export interface DeptParam {
  name?: string
  parentId?: number | null
  sortOrder?: number
  status?: string
  leader?: string
  phone?: string
  email?: string
}

export function getDeptTree(): Promise<DeptVO[]> {
  return request.get('/v1/system/dept/tree')
}

export function getDept(id: number): Promise<DeptVO> {
  return request.get(`/v1/system/dept/${id}`)
}

export function createDept(data: DeptParam): Promise<void> {
  return request.post('/v1/system/dept', data)
}

export function updateDept(id: number, data: DeptParam): Promise<void> {
  return request.put(`/v1/system/dept/${id}`, data)
}

export function deleteDept(id: number): Promise<void> {
  return request.delete(`/v1/system/dept/${id}`)
}

// ==================== Audit Log ====================
export interface AuditLogVO {
  id: number
  username: string
  module: string
  action: string
  resourceType: string
  resourceId: string
  ip: string
  userAgent: string
  requestParams: any
  status: string
  errorMsg: string
  createdAt: string
}

export function getAuditLogPage(params: { page: number; size: number; module?: string; status?: string; startDate?: string; endDate?: string }): Promise<PageResult<AuditLogVO>> {
  return request.get('/v1/system/audit-log/page', { params })
}

export function getAuditLog(id: number): Promise<AuditLogVO> {
  return request.get(`/v1/system/audit-log/${id}`)
}