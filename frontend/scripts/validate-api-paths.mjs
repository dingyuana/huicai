/**
 * API 路径校验脚本
 *
 * 扫描前端源码中所有 request.get/post/put/delete 调用的 API 路径，
 * 校验是否符合项目约定的路径格式。
 *
 * 用法: node scripts/validate-api-paths.mjs
 * 钩入构建: package.json scripts 中 "build": "node scripts/validate-api-paths.mjs && vue-tsc --noEmit && vite build"
 */

import { readFileSync, readdirSync, statSync, existsSync } from 'fs'
import { join, dirname } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = join(__dirname, '..')

// 合法的路径前缀模式 (不含 baseURL /api)
const ALLOWED_PREFIXES = [
  /^\/v1\//,           // 基础模块: /api/v1/periods
  /^\/sme\/\w+\/v1\//, // SME 模块: /api/sme/cash/v1/cash-journals
  /^\/base\/\w+\/v1\//,// 基础模块: /api/base/voucher/v1/vouchers
  /^\/agent\//,        // AI Agent 服务: /api/agent/route
]

// 所有 request 方法调用 (匹配字符串字面量路径)
const RE_METHOD = /request\.(get|post|put|delete|patch)\s*\(\s*['"]([^'"]+)['"]/g

let errors = []
let total = 0

function scanFile(filePath) {
  if (!existsSync(filePath)) return
  const content = readFileSync(filePath, 'utf-8')

  let match
  while ((match = RE_METHOD.exec(content)) !== null) {
    total++
    const method = match[1]
    const path = match[2]

    // 跳过动态路径 (包含模板字符串 ${...})
    if (path.includes('${')) continue

    // 跳过 URL 和绝对路径
    if (path.startsWith('http://') || path.startsWith('https://')) continue

    // 校验前缀
    const valid = ALLOWED_PREFIXES.some(prefix => prefix.test(path))
    if (!valid) {
      const lineNo = content.substring(0, match.index).split('\n').length
      const relPath = filePath.replace(ROOT + '/', '')
      errors.push({ file: relPath, line: lineNo, method, path })
    }
  }
}

function collectFiles(dir) {
  const files = []
  if (!existsSync(dir)) return files
  const entries = readdirSync(dir)
  for (const entry of entries) {
    const full = join(dir, entry)
    const st = statSync(full)
    if (st.isDirectory()) {
      files.push(...collectFiles(full))
    } else if (entry.endsWith('.ts') || entry.endsWith('.vue')) {
      files.push(full)
    }
  }
  return files
}

// 收集所有要扫描的文件
const scanFiles = new Set()

// 1. api/modules 下的所有 .ts 文件
const apiDir = join(ROOT, 'src/api/modules')
for (const f of collectFiles(apiDir)) scanFiles.add(f)

// 2. views 下的所有 .vue 和 .ts 文件
const viewsDir = join(ROOT, 'src/views')
for (const f of collectFiles(viewsDir)) scanFiles.add(f)

// 3. 检查是否有直接 import request 的页面文件
for (const dir of ['src/layouts', 'src/router']) {
  const d = join(ROOT, dir)
  if (existsSync(d)) {
    for (const f of collectFiles(d)) scanFiles.add(f)
  }
}

// 执行扫描
for (const f of scanFiles) {
  scanFile(f)
}

// 报告
console.log(`\n=== API 路径校验结果 ===`)
console.log(`扫描文件: ${scanFiles.size} 个, 共 ${total} 个 API 调用`)

if (errors.length === 0) {
  console.log(`✅ 所有 ${total} 个 API 路径均符合规范`)
  process.exit(0)
} else {
  console.log(`❌ 发现 ${errors.length} 个不合规 API 路径:\n`)
  for (const e of errors) {
    console.log(`  ${e.file}:${e.line}  [${e.method.toUpperCase()}] ${e.path}`)
  }
  console.log(`\n✅ 合法路径格式: /v1/... 或 /sme/{module}/v1/... 或 /base/{module}/v1/...`)
  process.exit(1)
}