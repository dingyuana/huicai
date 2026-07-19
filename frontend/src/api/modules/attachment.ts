import request from '@/api/request'

export function uploadFile(file: File, bizType: string, bizId?: number, uploaderId?: number) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', bizType)
  if (bizId) formData.append('bizId', String(bizId))
  if (uploaderId) formData.append('uploaderId', String(uploaderId))
  return request.post('/v1/attachments/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function listAttachments(bizType: string, bizId: number): Promise<any[]> {
  return request.get('/v1/attachments/list', { params: { bizType, bizId } })
}

export function getAttachmentUrl(id: number): Promise<{ url: string }> {
  return request.get(`/v1/attachments/${id}/url`)
}

export function deleteAttachment(id: number): Promise<void> {
  return request.delete(`/v1/attachments/${id}`)
}
