import request from '@/api/request'

/** 附件 —— 按 t_attachment 真实列声明的出参契约（P102 批次 7）。此前全为 Promise<any>。 */
export interface Attachment {
  id?: number
  bizType?: string
  bizId?: number
  fileName?: string
  originalName?: string
  fileSize?: number
  contentType?: string
  uploadedBy?: number
  createdAt?: string
}

export function uploadFile(file: File, bizType: string, bizId?: number, uploaderId?: number): Promise<Attachment> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', bizType)
  if (bizId) formData.append('bizId', String(bizId))
  if (uploaderId) formData.append('uploaderId', String(uploaderId))
  return request.post('/v1/attachments/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function listAttachments(bizType: string, bizId: number): Promise<Attachment[]> {
  return request.get('/v1/attachments/list', { params: { bizType, bizId } })
}

export function getAttachmentUrl(id: number): Promise<{ url: string }> {
  return request.get(`/v1/attachments/${id}/url`)
}

export function deleteAttachment(id: number): Promise<void> {
  return request.delete(`/v1/attachments/${id}`)
}
