import type { Role } from '@/types/enums'

export interface User {
  id: number
  name: string
  email: string
  role: Role
  permissions: string[]
  tenant_id?: number
}

export interface LoginResult {
  token: string
  user_id: number
  role: Role
  expires_at: string
}

export interface LoginParams {
  account: string
  password: string
}