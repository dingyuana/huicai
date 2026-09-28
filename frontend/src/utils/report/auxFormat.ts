/**
 * 辅助核算项的通用展示格式化（P97/REQ-097）。
 *
 * assist_json 的 schema 在本项目中**无任何定义**（前端/后端/单据转换全链路透传），
 * 因此这里不假设 vendorName/customerId 之类具体键，而是把 JSON 对象的键值对
 * 通用拼接为「键: 值」。上游改键名时展示自动跟随，不会静默变空。
 */
export const formatAuxItem = (raw: unknown): string => {
  if (raw == null || raw === '') return ''
  if (typeof raw !== 'string') return String(raw)
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    return raw
  }
  if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) return String(parsed)
  const pairs = Object.entries(parsed as Record<string, unknown>)
    .filter(([, v]) => v != null && v !== '')
    .map(([k, v]) => `${k}: ${v}`)
  return pairs.join(' / ')
}

/** 按科目聚合辅助明细行 → Map<subjectCode, string[]>，供页面展开行展示 */
export const groupAuxBySubject = (
  rows: Array<Record<string, unknown>>,
): Map<string, string[]> => {
  const out = new Map<string, string[]>()
  for (const r of rows || []) {
    const code = String(r.subject_code ?? r.subjectCode ?? '')
    const text = formatAuxItem(r.assist_json ?? r.assistJson)
    if (!code || !text) continue
    const list = out.get(code) ?? []
    list.push(text)
    out.set(code, list)
  }
  return out
}
