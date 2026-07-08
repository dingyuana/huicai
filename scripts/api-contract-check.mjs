#!/usr/bin/env node
/**
 * API 参数契约检查脚本
 *
 * 功能：扫描所有 API 模块的 export function，检查是否有必填参数
 * 在调用方被漏传的风险。上次 sourceDocType 漏传导致 500 的同类问题预防。
 *
 * 用法: node scripts/api-contract-check.mjs
 */

import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const API_DIR = path.resolve(__dirname, '../frontend/src/api/modules')
const VIEWS_DIR = path.resolve(__dirname, '../frontend/src/views')

// ===== 高风险参数模式 =====
// 这些参数在 API 定义中是必填，但在调用方常被遗忘
const HIGH_RISK_PARAMS = ['sourceDocType', 'customerId', 'vendorId', 'targetDocType', 'sourceDocId', 'targetDocId']

// 已知合法的缺参场景（参数在某些分支被故意省略）
const KNOWN_EXCEPTIONS = [
  'BusinessDocDetail.vue:onOpenReconcile',
]

// ===== 扫描所有 API 函数 =====
function scanAPIFunctions() {
  const apiFunctions = []
  const files = fs.readdirSync(API_DIR).filter(f => f.endsWith('.ts'))
  
  for (const file of files) {
    const content = fs.readFileSync(path.join(API_DIR, file), 'utf-8')
    const moduleName = file.replace('.ts', '')
    
    // 提取 export function
    const funcRegex = /export function (\w+)\(([^)]*)\)/g
    let match
    while ((match = funcRegex.exec(content)) !== null) {
      const funcName = match[1]
      const paramsStr = match[2]
      
      // 解析参数：找出必填参数（没有 ? 或 default 的）
      const params = paramsStr.split(',').map(p => p.trim()).filter(p => p)
      const requiredParams = params.filter(p => {
        // 排除可选参数（有 ? 或 = ）
        if (p.includes('?') || p.includes('=')) return false
        // 提取参数名
        const name = p.split(':')[0]?.split(' ').pop()?.trim()
        return name && name !== 'null' && name !== 'undefined'
      })
      
      apiFunctions.push({
        module: moduleName,
        funcName,
        params,
        requiredParamNames: requiredParams.map(p => {
          const nameMatch = p.match(/(\w+)\s*(?:\?|:|=)/)
          return nameMatch ? nameMatch[1] : p.split(':')[0]?.trim()
        }).filter(Boolean),
        full: `${moduleName}.${funcName}`,
      })
    }
  }
  return apiFunctions
}

// ===== 扫描 Vue 组件中对 API 的调用 =====
function scanVueAPIUsage() {
  const issues = []
  const apiFuncs = scanAPIFunctions()
  
  // 遍历所有 Vue 文件
  function walkDir(dir) {
    if (!fs.existsSync(dir)) return
    const entries = fs.readdirSync(dir, { withFileTypes: true })
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name)
      if (entry.isDirectory()) walkDir(fullPath)
      else if (entry.name.endsWith('.vue')) {
        const content = fs.readFileSync(fullPath, 'utf-8')
        const relPath = path.relative(VIEWS_DIR, fullPath)
        
        // 对每个 API 函数，检查调用方是否传了所有必填参数
        for (const api of apiFuncs) {
          if (content.includes(api.funcName)) {
            // 检查高风险参数是否都传了
            for (const param of HIGH_RISK_PARAMS) {
              if (api.requiredParamNames.includes(param) && !content.includes(param)) {
                issues.push({
                  level: '⚠️',
                  type: 'missing_required_param',
                  api: api.full,
                  param,
                  caller: relPath,
                  desc: `${api.funcName} 要求必填参数 ${param}，但调用方 ${relPath} 中未出现该参数名`,
                })
              }
            }
          }
        }
      }
    }
  }
  walkDir(VIEWS_DIR)
  return issues
}

// ===== 主流程 =====
console.log('📋 API 参数契约检查')
console.log(`   扫描 API 模块: ${path.basename(API_DIR)}`)
console.log(`   扫描 Vue 组件: ${path.basename(VIEWS_DIR)}`)
console.log()

const apiFuncs = scanAPIFunctions()
console.log(`📊 共发现 ${apiFuncs.length} 个 API 函数`)
console.log()

// 打印高风险 API 函数
const highRiskAPIs = apiFuncs.filter(f => 
  f.requiredParamNames.some(p => HIGH_RISK_PARAMS.includes(p))
)
console.log(`🔍 高风险 API（含 sourceDocType/customerId/vendorId 等必填参数）: ${highRiskAPIs.length} 个`)
for (const api of highRiskAPIs) {
  console.log(`   ${api.module}.${api.funcName} → 必填: ${api.requiredParamNames.join(', ')}`)
}

console.log()

// 扫描 Vue 组件中的调用
const issues = scanVueAPIUsage()
console.log(`🔍 检查结果: ${issues.length} 个潜在问题`)
console.log()

if (issues.length === 0) {
  console.log('✅ 所有 API 必填参数在调用方都有对应引用')
} else {
  for (const issue of issues) {
    console.log(`${issue.level} [${issue.api}] ${issue.desc}`)
    console.log(`   文件: ${issue.caller}`)
    console.log()
  }
}

// 退出码
process.exit(issues.length > 0 ? 1 : 0)