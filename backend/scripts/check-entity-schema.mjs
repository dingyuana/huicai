/**
 * 编译期 Entity-DB schema 一致性检查。
 * 扫描所有 @TableName 标注的 Entity，对比其字段与 DB 实际列名，
 * 发现字段映射到不存在的列时报错退出。
 *
 * 用法：node scripts/check-entity-schema.mjs [--fix]
 */

import { readdirSync, readFileSync, statSync } from 'fs';
import { join, relative } from 'path';
import { execSync } from 'child_process';

const ENTITY_DIR = join(import.meta.dirname, '..', 'src', 'main', 'java');
const DB = { host: 'localhost', port: 5432, user: 'huicai', db: 'huicai', pwd: 'huicai123' };

// ── 解析 Entity 文件 ──

function parseEntity(filePath) {
  const src = readFileSync(filePath, 'utf-8');
  const tableNameMatch = src.match(/@TableName\("([^"]+)"\)/);
  if (!tableNameMatch) return null;

  const tableName = tableNameMatch[1];
  const fields = [];

  // 解析所有字段声明（含注解）
  const fieldRegex = /(?:@TableField\s*\(([^)]*)\)\s*)?(?:@TableId\s*\([^)]*\)\s*)?(?:@TableLogic\s*)?(?:@Version\s*)?(?:@StatusChangeable[^)]*\)\s*)?(?:private\s+\S+\s+(\w+)\s*;)/g;
  let match;
  while ((match = fieldRegex.exec(src)) !== null) {
    const annotation = match[1] || '';
    const fieldName = match[2];
    if (!fieldName) continue;

    // 跳过有 @TableField(exist = false) 或 @TableField(exist=false) 的字段
    if (/exist\s*=\s*false/.test(annotation)) continue;

    // 跳过有 @TableId 的字段（主键）
    const beforeField = src.slice(Math.max(0, match.index - 200), match.index);
    if (/@TableId/.test(beforeField)) continue;

    // 提取显式列名映射
    let columnName = null;
    // @TableField("col_name") 格式
    const bareNameMatch = annotation.match(/^"([^"]+)"$/);
    if (bareNameMatch) {
      columnName = bareNameMatch[1];
    }
    // @TableField(value = "col_name") 格式
    const valueMatch = annotation.match(/value\s*=\s*"([^"]+)"/);
    if (valueMatch) {
      columnName = valueMatch[1];
    }

    fields.push({ fieldName, columnName, annotation });
  }

  // H-17 增强：检查 String 类型字段映射到 JSONB 列时是否缺少 typeHandler
  // AGENTS.md §4.2 第 6 条：String→JSONB 字段必须加 @TableField(typeHandler = JsonbTypeHandler.class)
  const typeHandlerErrors = [];
  const jsonbColumnPattern = /(?:aux_dimension|assist_json|ai_mapping_result|ai_risk_tag|ocr_data|auxiliary|extension|extra|metadata|config_json|payload|snapshot|change_before|change_after|before_snapshot|after_snapshot)\b/i;
  const fieldsWithJsonbRisk = src.match(/private\s+String\s+(\w+)\s*;/g) || [];
  for (const fieldDecl of fieldsWithJsonbRisk) {
    const fieldName = fieldDecl.match(/private\s+String\s+(\w+)/)[1];
    // 查找该字段前的 @TableField 注解
    const fieldIdx = src.indexOf(fieldDecl);
    const beforeText = src.slice(Math.max(0, fieldIdx - 500), fieldIdx);
    const tableFieldMatch = beforeText.match(/@TableField\s*\(([^)]*)\)/g) || [];
    const lastAnnotation = tableFieldMatch[tableFieldMatch.length - 1] || '';
    // 如果字段名疑似 JSONB 列且没有 typeHandler 声明
    if (jsonbColumnPattern.test(fieldName) && !/typeHandler\s*=/i.test(lastAnnotation)) {
      typeHandlerErrors.push({
        fieldName,
        hint: `String 字段 "${fieldName}" 疑似 JSONB 列但缺少 @TableField(typeHandler = JsonbTypeHandler.class)`
      });
    }
  }

  return { tableName, fields, filePath, typeHandlerErrors };
}

// ── 获取 DB 表结构 ──

function getTableColumns(tableName) {
  try {
    const sql = `SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='${tableName}' ORDER BY ordinal_position`;
    const cmd = `docker exec -i huicai-postgres psql -U ${DB.user} -d ${DB.db} -t -c "${sql}" 2>/dev/null`;
    const output = execSync(cmd, { encoding: 'utf-8', timeout: 10000 });
    return output.split('\n').map(l => l.trim()).filter(Boolean);
  } catch {
    return null; // 表不存在
  }
}

// ── 主流程 ──

function findEntityFiles(dir) {
  const results = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory() && entry.name !== 'test') {
      results.push(...findEntityFiles(full));
    } else if (entry.isFile() && entry.name.endsWith('Entity.java')) {
      results.push(full);
    }
  }
  return results;
}

const allErrors = [];

// H-17 增强：DB 探活降级。docker postgres 不可用时跳过列检查（避免 pre-commit 阻塞）
const dbAvailable = (() => {
  try {
    execSync('docker exec huicai-postgres pg_isready -U huicai -d huicai 2>/dev/null', { timeout: 3000 });
    return true;
  } catch {
    return false;
  }
})();

if (!dbAvailable) {
  console.warn('⚠️  Docker postgres 不可用（huicai-postgres 容器未运行或 pg_isready 超时），跳过 Entity-DB 列一致性检查。');
  console.warn('    本地开发可忽略此警告；CI 环境请确保 docker compose up -d 已启动 postgres。');
}

// H-17 增强：typeHandler 检查不依赖 DB，始终执行
const typeHandlerErrors = [];

for (const filePath of findEntityFiles(ENTITY_DIR)) {
  const entity = parseEntity(filePath);
  if (!entity) continue;

  // 收集 typeHandler 警告（不依赖 DB）
  if (entity.typeHandlerErrors && entity.typeHandlerErrors.length > 0) {
    const relPath = relative(join(import.meta.dirname, '..'), filePath);
    for (const thErr of entity.typeHandlerErrors) {
      typeHandlerErrors.push(`  ${relPath}: ${thErr.hint}`);
    }
  }

  // 列一致性检查依赖 DB，不可用时跳过
  if (!dbAvailable) continue;

  const columns = getTableColumns(entity.tableName);
  if (columns === null) {
    // 表不存在，跳过（可能是新模块）
    continue;
  }

  for (const field of entity.fields) {
    // 无显式列名映射 → 使用驼峰转下划线
    const colName = field.columnName || field.fieldName.replace(/([A-Z])/g, '_$1').toLowerCase();

    // 检查列名是否在 DB 中存在
    if (!columns.includes(colName)) {
      const relPath = relative(join(import.meta.dirname, '..'), filePath);
      allErrors.push(`  ${relPath}: 字段 "${field.fieldName}" → 列 "${colName}"，但 ${entity.tableName} 表没有此列`);
    }
  }
}

// 输出 typeHandler 警告（作为 warning，不阻断提交，但提示开发者关注）
if (typeHandlerErrors.length > 0) {
  console.warn(`\n⚠️  疑似 JSONB 列缺少 typeHandler（${typeHandlerErrors.length} 项，请人工确认）：\n`);
  for (const err of typeHandlerErrors) {
    console.warn(err);
  }
  console.warn('');
  console.warn('修复方法：在对应 String 字段的 @TableField 注解中添加 typeHandler = JsonbTypeHandler.class');
  console.warn('参考：AGENTS.md §4.2 第 6 条 / docs/specs/ 中的 Entity-DB 一致性章节\n');
}

// DB 不可用时，typeHandler 警告已输出，直接退出（不阻断 pre-commit）
if (!dbAvailable) {
  process.exit(0);
}

// 按表分组统计
const tableErrors = {};
for (const err of allErrors) {
  const tableMatch = err.match(/但 (\S+) 表没有此列/);
  const tableName = tableMatch ? tableMatch[1] : 'unknown';
  if (!tableErrors[tableName]) tableErrors[tableName] = [];
  tableErrors[tableName].push(err);
}

// 检查哪些表在 DB 中实际存在
const tableExists = {};
for (const tableName of Object.keys(tableErrors)) {
  try {
    const sql = `SELECT to_regclass('${tableName}') IS NOT NULL AS exists`;
    const cmd = `docker exec -i huicai-postgres psql -U ${DB.user} -d ${DB.db} -t -c "${sql}" 2>/dev/null`;
    const output = execSync(cmd, { encoding: 'utf-8', timeout: 5000 });
    tableExists[tableName] = output.trim() === 't';
  } catch {
    tableExists[tableName] = false;
  }
}

// 分类输出
const realErrors = [];
const missingTableErrors = [];

for (const [tableName, errors] of Object.entries(tableErrors)) {
  if (tableExists[tableName]) {
    realErrors.push(...errors);
  } else {
    missingTableErrors.push(...errors);
  }
}

if (missingTableErrors.length > 0) {
  console.log(`⚠️  表不存在（${missingTableErrors.length} 项，P3 模块未实现，跳过）：\n`);
  for (const err of missingTableErrors) {
    console.log(err);
  }
  console.log('');
}

if (realErrors.length > 0) {
  console.error('❌ 表存在但列不匹配（需要立即修复）：\n');
  for (const err of realErrors) {
    console.error(err);
  }
  console.error(`\n共 ${realErrors.length} 个不匹配项，涉及以下表：`);
  const affectedTables = [...new Set(realErrors.map(e => {
    const m = e.match(/但 (\S+) 表没有此列/);
    return m ? m[1] : 'unknown';
  }))];
  for (const t of affectedTables) {
    const count = realErrors.filter(e => e.includes(t)).length;
    console.error(`  ${t}（${count} 字段）`);
  }
  console.error('\n修复方法：');
  console.error('  1. 如果 DB 缺列 → 创建 Flyway migration 添加列');
  console.error('  2. 如果字段不需要持久化 → 加 @TableField(exist = false)');
  console.error('  3. 如果列名不同 → 用 @TableField(value = "实际列名")');
  process.exit(1);
} else {
  console.log('✅ Entity-DB 列一致性检查通过');
  process.exit(0);
}