export type Role = 'admin' | 'cashier' | 'accountant' | 'boss'

export const RoleLabel: Record<Role, string> = {
  admin: '系统管理员',
  cashier: '出纳',
  accountant: '会计',
  boss: '老板',
}