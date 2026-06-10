// 后端统一响应格式 R<T>
export interface ApiResponse<T = any> {
  code: number
  msg: string
  data: T
}

// 分页请求参数
export interface PageParams {
  page?: number
  size?: number
}

// 分页响应
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}