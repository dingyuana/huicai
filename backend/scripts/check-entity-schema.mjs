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

// ── 扫描 JdbcTemplate SQL 中的表名 ──

function findControllerFiles(dir) {
  const results = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory() && entry.name !== 'test') {
      results.push(...findControllerFiles(full));
    } else if (entry.isFile() && (entry.name.endsWith('Controller.java') || entry.name.endsWith('Service.java') || entry.name.endsWith('ServiceImpl.java'))) {
      const src = readFileSync(full, 'utf-8');
      if (src.includes('JdbcTemplate')) results.push(full);
    }
  }
  return results;
}

function extractTableNamesFromSql(src) {
  const tables = new Set();
  // 匹配 DELETE FROM / INSERT INTO / UPDATE / FROM / JOIN 后的表名
  const patterns = [
    /DELETE\s+FROM\s+(\w+)/gi,
    /INSERT\s+INTO\s+(\w+)/gi,
    /UPDATE\s+(\w+)\s+SET/gi,
  ];
  for (const pattern of patterns) {
    let m;
    while ((m = pattern.exec(src)) !== null) {
      const table = m[1];
      // 排除系统表、非 t_ 前缀的表和 information_schema
      if (table.startsWith('t_') && table !== 't_') {
        tables.add(table);
      }
    }
  }
  return [...tables];
}

function getAllDbTables() {
  try {
    const cmd = `docker exec -i huicai-postgres psql -U ${DB.user} -d ${DB.db} -t -c "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name" 2>/dev/null`;
    const output = execSync(cmd, { encoding: 'utf-8', timeout: 10000 });
    return output.split('\n').map(l => l.trim()).filter(Boolean);
  } catch {
    return null;
  }
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

// ── 自定义 SQL 列引用检查（@Select / XML Mapper） ──

const SQL_KEYWORDS = new Set([
  'true', 'false', 'null', 'asc', 'desc', 'limit', 'offset', 'and', 'or',
  'not', 'in', 'is', 'like', 'between', 'exists', 'all', 'any', 'some',
  'cast', 'coalesce', 'nullif', 'ifnull', 'nvl', 'concat', 'substring',
  'substr', 'trim', 'upper', 'lower', 'length', 'abs', 'round', 'ceil',
  'floor', 'mod', 'power', 'sqrt', 'count', 'sum', 'avg', 'min', 'max',
  'distinct', 'on', 'as', 'from', 'where', 'having', 'group', 'order',
  'by', 'select', 'insert', 'update', 'delete', 'into', 'values', 'set',
  'table', 'varchar', 'integer', 'bigint', 'numeric', 'boolean', 'timestamp',
  'date', 'text', 'jsonb', 'first', 'last', 'current_timestamp', 'current_date',
  'now', 'case', 'when', 'then', 'else', 'end', 'returning', 'primary', 'key',
  'default', 'check', 'unique', 'constraint', 'foreign', 'references',
  'index', 'create', 'alter', 'drop', 'add', 'column', 'if', 'public',
  'cast', 'row', 'rows', 'range', 'unbounded', 'preceding', 'following',
  'current', 'over', 'partition', 'cross', 'inner', 'outer', 'left', 'right',
  'full', 'join', 'natural', 'using', 'except', 'intersect', 'union',
  'some', 'every', 'array', 'row', 'record', 'refcursor', 'pg_catalog',
  'information_schema', 'schema', 'schemata', 'table_name', 'table_schema',
  'table_type', 'column_name', 'ordinal_position', 'is_nullable', 'data_type',
  'character_maximum_length', 'numeric_precision', 'numeric_scale',
]);

function isSqlKeyword(word) {
  return SQL_KEYWORDS.has(word.toLowerCase());
}

/**
 * 从 SQL 文本中提取列引用（WHERE 条件、ORDER BY、GROUP BY 中的列名）。
 * 返回小写列名数组。
 */
function extractColumnRefs(sql) {
  // 清理：移除注释、字符串字面量、参数占位符、数字
  let cleaned = sql
    .replace(/--[^\n]*/g, ' ')
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/'[^']*'/g, ' ')
    .replace(/#\{[^}]+\}/g, ' ')
    .replace(/\$\{[^}]+\}/g, ' ')
    .replace(/\b\d+(\.\d+)?\b/g, ' ')
    .replace(/\s+/g, ' ');

  const refs = new Set();

  // 1. 提取 WHERE 条件中的列引用: column = / IS / IN / LIKE / BETWEEN / < > != <>
  const whereRefRe = /(\w+)\s*(?=\s*[=!<>]=?\s*|\s+IS\s+|\s+IN\s*\(|\s+LIKE\s+|\s+BETWEEN\s+)/gi;
  let m;
  while ((m = whereRefRe.exec(cleaned)) !== null) {
    const candidate = m[1];
    if (!isSqlKeyword(candidate) && !/^\d/.test(candidate)) {
      refs.add(candidate.toLowerCase());
    }
  }

  // 2. 提取 "AND col" 或 "OR col" 简写（WHERE 子句中）
  //   先定位 WHERE 子句
  const whereMatch = cleaned.match(/\bWHERE\s+(.+?)(?:\bORDER\s+BY\b|\bGROUP\s+BY\b|\bHAVING\b|\bLIMIT\b|$)/is);
  if (whereMatch) {
    const whereClause = whereMatch[1];
    const bareColRe = /\b(?:AND|OR|,)\s+(\w+)(?:\s+|$)/gi;
    while ((m = bareColRe.exec(whereClause)) !== null) {
      const candidate = m[1];
      if (!isSqlKeyword(candidate) && !/^\d/.test(candidate)) {
        refs.add(candidate.toLowerCase());
      }
    }
  }

  // 3. 提取 ORDER BY 中的列
  const orderByMatch = cleaned.match(/\bORDER\s+BY\s+(.+?)(?:\bLIMIT\b|\bOFFSET\b|$)/is);
  if (orderByMatch) {
    const items = orderByMatch[1].split(',');
    for (const item of items) {
      const parts = item.trim().split(/\s+/);
      let col = parts[0];
      if (col.includes('.')) col = col.split('.')[1]; // 去掉表别名前缀
      if (col && !isSqlKeyword(col) && !/^\d/.test(col)) {
        refs.add(col.toLowerCase());
      }
    }
  }

  // 4. 提取 GROUP BY 中的列
  // 注意：GROUP BY 可以使用 SELECT 中的别名，需要排除别名
  const selectAliases = extractSelectAliases(cleaned);
  const groupByMatch = cleaned.match(/\bGROUP\s+BY\s+(.+?)(?:\bORDER\s+BY\b|\bHAVING\b|\bLIMIT\b|$)/is);
  if (groupByMatch) {
    const items = groupByMatch[1].split(',');
    for (const item of items) {
      let col = item.trim().split(/\s+/)[0];
      if (col.includes('.')) col = col.split('.')[1];
      if (col && !isSqlKeyword(col) && !/^\d/.test(col) && !selectAliases.has(col.toLowerCase())) {
        refs.add(col.toLowerCase());
      }
    }
  }

  return [...refs];
}

/**
 * 从 SQL 中提取 SELECT 子句中的别名（column AS alias）。
 */
function extractSelectAliases(sql) {
  const aliases = new Set();
  // 定位 SELECT 和 FROM 之间的内容
  const selectMatch = sql.match(/\bSELECT\s+(.+?)\bFROM\b/is);
  if (!selectMatch) return aliases;

  const selectClause = selectMatch[1];
  // 匹配 "expression AS alias" 或 "expression alias" 模式
  const aliasRe = /\bAS\s+(\w+)\b/gi;
  let m;
  while ((m = aliasRe.exec(selectClause)) !== null) {
    aliases.add(m[1].toLowerCase());
  }
  return aliases;
}

/**
 * 从 SQL 中提取主表名和所有 JOIN 表名（仅 t_ 前缀）。
 * 返回数组，第一个元素是主表。
 */
function extractTables(sql) {
  const tables = [];
  // 主表：FROM 子句后的第一个 t_ 表
  const fromMatch = sql.match(/\bFROM\s+(?:ONLY\s+)?(\w+)/i);
  if (fromMatch) {
    const table = fromMatch[1];
    if (table.startsWith('t_')) tables.push(table);
  }
  // UPDATE 表
  const updateMatch = sql.match(/\bUPDATE\s+(\w+)\s+SET\b/i);
  if (updateMatch) {
    const table = updateMatch[1];
    if (table.startsWith('t_')) tables.push(table);
  }
  // JOIN 表
  const joinRe = /\bJOIN\s+(\w+)/gi;
  let m;
  while ((m = joinRe.exec(sql)) !== null) {
    const table = m[1];
    if (table.startsWith('t_') && !tables.includes(table)) {
      tables.push(table);
    }
  }
  return tables;
}

/**
 * 从 @Select 注解中提取 SQL 文本。
 */
function extractSelectSql(src) {
  const sqls = [];
  // 匹配 @Select("...") 和 @Select("""...""")
  const selectRe = /@Select\s*\(\s*("(?:[^"\\]|\\.)*"|"""(?:[^"]|"(?!""))*""")\)/gs;
  let m;
  while ((m = selectRe.exec(src)) !== null) {
    let sql = m[1];
    // 处理文本块：移除引号
    if (sql.startsWith('"""')) {
      sql = sql.slice(3, -3);
    } else {
      sql = sql.slice(1, -1);
    }
    // 处理转义
    sql = sql.replace(/\\n/g, '\n').replace(/\\t/g, '\t').replace(/\\"/g, '"');
    sqls.push(sql.trim());
  }
  return sqls;
}

/**
 * 移除 MyBatis XML 动态标签，保留标签内的 SQL 文本。
 */
function stripMyBatisTags(xml) {
  return xml
    // 移除 CDATA
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    // 移除 MyBatis 动态标签（保留内容）
    .replace(/<if\b[^>]*>([\s\S]*?)<\/if>/g, '$1')
    .replace(/<where\b[^>]*>([\s\S]*?)<\/where>/g, '$1')
    .replace(/<choose\b[^>]*>([\s\S]*?)<\/choose>/g, '$1')
    .replace(/<when\b[^>]*>([\s\S]*?)<\/when>/g, '$1')
    .replace(/<otherwise\b[^>]*>([\s\S]*?)<\/otherwise>/g, '$1')
    .replace(/<foreach\b[^>]*>([\s\S]*?)<\/foreach>/g, '$1')
    .replace(/<trim\b[^>]*>([\s\S]*?)<\/trim>/g, '$1')
    .replace(/<set\b[^>]*>([\s\S]*?)<\/set>/g, '$1')
    .replace(/<bind\b[^>]*\/>/g, '')
    // 移除剩余 XML 标签
    .replace(/<[^>]+>/g, ' ')
    // 清理空白
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * 从 XML Mapper 文件中提取 SQL 文本。
 */
function extractXmlSql(src) {
  const sqls = [];
  const sqlElementRe = /<(?:select|insert|update|delete)\b[^>]*id="([^"]+)"[^>]*>([\s\S]*?)<\/(?:select|insert|update|delete)>/gi;
  let m;
  while ((m = sqlElementRe.exec(src)) !== null) {
    const id = m[1];
    let content = stripMyBatisTags(m[2]);
    if (content.length > 10) {
      sqls.push({ id, sql: content });
    }
  }
  return sqls;
}

/**
 * 查找所有包含 @Select 注解的 Java Mapper 文件。
 */
function findMapperFiles(dir) {
  const results = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory() && entry.name !== 'test') {
      results.push(...findMapperFiles(full));
    } else if (entry.isFile() && entry.name.endsWith('Mapper.java')) {
      const src = readFileSync(full, 'utf-8');
      if (src.includes('@Select')) {
        results.push({ filePath: full, src });
      }
    }
  }
  return results;
}

/**
 * 查找所有 XML Mapper 文件。
 */
function findXmlMapperFiles(dir) {
  const results = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...findXmlMapperFiles(full));
    } else if (entry.isFile() && entry.name.endsWith('Mapper.xml')) {
      results.push({ filePath: full, src: readFileSync(full, 'utf-8') });
    }
  }
  return results;
}

// ── 检查继承 BaseEntity 但表缺少 deleted 列 ──

function findEntitiesWithDeletedCheck(dir) {
  const results = []; // [{ filePath, tableName, extendsBaseEntity }]
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory() && entry.name !== 'test') {
      results.push(...findEntitiesWithDeletedCheck(full));
    } else if (entry.isFile() && entry.name.endsWith('Entity.java')) {
      const src = readFileSync(full, 'utf-8');
      if (!src.includes('extends BaseEntity')) continue;
      const tableNameMatch = src.match(/@TableName\("([^"]+)"\)/);
      if (!tableNameMatch) continue;
      results.push({ filePath: full, tableName: tableNameMatch[1] });
    }
  }
  return results;
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
  console.warn('⚠️  Docker postgres 不可用（huicai-postgres 容器未运行或 pg_isready 超时），跳过 Entity-DB 列一致性检查和 JdbcTemplate SQL 审计。');
  console.warn('    本地开发可忽略此警告；CI 环境请确保 docker compose up -d 已启动 postgres。');
}

// ── JdbcTemplate SQL 表名审计 ──

const jdbcErrors = [];
const allDbTables = dbAvailable ? getAllDbTables() : null;

if (allDbTables) {
  const dbTableSet = new Set(allDbTables);
  const jdbcFiles = findControllerFiles(ENTITY_DIR);
  const seen = new Set();

  for (const filePath of jdbcFiles) {
    const src = readFileSync(filePath, 'utf-8');
    const tableNames = extractTableNamesFromSql(src);
    for (const table of tableNames) {
      if (!dbTableSet.has(table)) {
        const relPath = relative(join(import.meta.dirname, '..'), filePath);
        const key = `${relPath}:${table}`;
        if (!seen.has(key)) {
          seen.add(key);
          jdbcErrors.push(`  ${relPath}: SQL 引用了不存在的表 "${table}"`);
        }
      }
    }
  }
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

// ── Mapper 方法缺少 SQL 绑定检查 ──

const BASE_MAPPER_METHODS = new Set([
  'insert', 'deleteById', 'deleteByMap', 'delete', 'deleteBatchIds',
  'updateById', 'update', 'updateBatchById',
  'selectById', 'selectOne', 'exists', 'selectBatchIds', 'selectByMap',
  'selectCount', 'selectList', 'selectMaps', 'selectObjs',
  'selectPage', 'selectMapsPage',
]);

/**
 * 扫描 Mapper.java 接口，找出所有非 BaseMapper 的自定义方法。
 * 返回数组 [{ filePath, methods: [{ methodName, lineNum, hasAnnotation, src }] }]
 */
function findMapperMethods(dir) {
  const results = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory() && entry.name !== 'test') {
      results.push(...findMapperMethods(full));
    } else if (entry.isFile() && entry.name.endsWith('Mapper.java')) {
      const src = readFileSync(full, 'utf-8');
      // 移除注释和字符串字面量（保留换行，行号计算用原始 src）
      const cleaned = src
        .replace(/\/\/.*$/gm, '\n')
        .replace(/\/\*[\s\S]*?\*\//g, '')
        // 移除文本块和字符串字面量，防止 SQL 关键字被误认为方法名
        .replace(/"""(?:[^"]|"(?!""))*"""/g, '""')
        .replace(/"([^"\\]|\\.)*"/g, '""');

      // 收集所有方法名：找到 `word(` 模式，排除注解参数
      const methodCandidates = new Map(); // methodName → firstIndex
      // 匹配行首或空格后的单词紧跟 ( ，排除 @ 注解形式
      const candidateRe = /(?:\s|^)([a-z]\w*)\s*\(/gi;
      let m;
      while ((m = candidateRe.exec(cleaned)) !== null) {
        const name = m[1];
        // 跳过注解参数（@Xxx(word) 中的 word）—— 检查前一个字符
        if (m.index > 0 && cleaned[m.index - 1] === '@') continue;
        // 跳过已知的 BaseMapper 方法
        if (BASE_MAPPER_METHODS.has(name)) continue;
        if (name.length < 2) continue;
        if (!methodCandidates.has(name)) {
          methodCandidates.set(name, m.index);
        }
      }

      // 排除接口中的 return 类型误匹配（如 `int`, `void`, `String`, `List`, `IPage`, `BigDecimal`, `Boolean`, `Map`）
      const TYPE_KEYWORDS = new Set([
        'int', 'long', 'double', 'float', 'boolean', 'void', 'char', 'byte', 'short',
        'String', 'Integer', 'Long', 'Double', 'Float', 'Boolean', 'Character', 'Byte', 'Short',
        'BigDecimal', 'List', 'Set', 'Map', 'Collection', 'IPage', 'Page', 'Object',
      ]);

      const methods = [];
      for (const [methodName, index] of methodCandidates) {
        if (TYPE_KEYWORDS.has(methodName)) continue;

        // 计算方法在原始源中的行号
        const lineNum = (src.slice(0, index).match(/\n/g) || []).length + 1;

        // 检查方法前是否有 SQL 注解（扫描整个文件从开头到方法前）
        const beforeCode = cleaned.slice(0, index);
        // 匹配 @Select("...") 或 @Select("""...""") 等 SQL 注解（支持多行注解体）
        const annotationRe = /@(?:Select|Insert|Update|Delete)\s*\(\s*(?:"(?:[^"\\]|\\.)*"|"""(?:[^"]|"(?!""))*""")/g;
        const allAnnotations = beforeCode.match(annotationRe) || [];
        const hasAnnotation = allAnnotations.length > 0;

        methods.push({ methodName, lineNum, hasAnnotation });
      }

      if (methods.length > 0) {
        // 提取包名，用于 XML namespace 匹配
        const pkgMatch = src.match(/^package\s+([^;]+);/m);
        const pkg = pkgMatch ? pkgMatch[1] : '';
        const className = entry.name.replace('.java', '');
        const fqn = pkg ? `${pkg}.${className}` : className;
        results.push({ filePath: full, fqn, methods, src });
      }
    }
  }
  return results;
}

/**
 * 从 XML Mapper 文件中提取 namespace 和所有 id。
 */
function extractXmlMappings(dir) {
  const mappings = []; // { namespace: 'com...', ids: Set(['method1', ...]) }
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      mappings.push(...extractXmlMappings(full));
    } else if (entry.isFile() && entry.name.endsWith('Mapper.xml')) {
      const src = readFileSync(full, 'utf-8');
      const nsMatch = src.match(/namespace\s*=\s*"([^"]+)"/);
      if (!nsMatch) continue;
      const namespace = nsMatch[1];
      const ids = new Set();
      const idRe = /<(?:select|insert|update|delete)\b[^>]*id="([^"]+)"/gi;
      let m;
      while ((m = idRe.exec(src)) !== null) {
        ids.add(m[1]);
      }
      mappings.push({ namespace, ids });
    }
  }
  return mappings;
}

// ── 自定义 SQL 列引用检查（@Select / XML Mapper） ──

const mapperSqlErrors = [];

if (dbAvailable) {
  const dbTableColumns = new Map(); // 缓存表结构

  function getCachedColumns(tableName) {
    if (!dbTableColumns.has(tableName)) {
      const cols = getTableColumns(tableName);
      dbTableColumns.set(tableName, cols);
    }
    return dbTableColumns.get(tableName);
  }

  // 检查 Java Mapper 中的 @Select 注解
  const javaMapperFiles = findMapperFiles(ENTITY_DIR);
  for (const { filePath, src } of javaMapperFiles) {
    const sqls = extractSelectSql(src);
    for (const sql of sqls) {
      const tableNames = extractTables(sql);
      if (tableNames.length === 0) continue;
      const colRefs = extractColumnRefs(sql);
      if (colRefs.length === 0) continue;

      // 收集所有表的所有列
      let allColumns = [];
      let allTablesResolved = true;
      for (const tn of tableNames) {
        const cols = getCachedColumns(tn);
        if (cols) {
          allColumns = allColumns.concat(cols);
        } else {
          allTablesResolved = false;
        }
      }
      if (!allTablesResolved) continue;

      const columnSet = new Set(allColumns);
      for (const colRef of colRefs) {
        if (!columnSet.has(colRef)) {
          const relPath = relative(join(import.meta.dirname, '..'), filePath);
          mapperSqlErrors.push(`  ${relPath}: @Select SQL 引用了列 "${colRef}"，但 ${tableNames.join('/')} 表均无此列`);
        }
      }
    }
  }

  // 检查 XML Mapper 文件
  const xmlMapperFiles = findXmlMapperFiles(join(import.meta.dirname, '..', 'src', 'main', 'resources'));
  for (const { filePath, src } of xmlMapperFiles) {
    const sqls = extractXmlSql(src);
    for (const { id, sql } of sqls) {
      const tableNames = extractTables(sql);
      if (tableNames.length === 0) continue;
      const colRefs = extractColumnRefs(sql);
      if (colRefs.length === 0) continue;

      // 收集所有表的所有列
      let allColumns = [];
      let allTablesResolved = true;
      for (const tn of tableNames) {
        const cols = getCachedColumns(tn);
        if (cols) {
          allColumns = allColumns.concat(cols);
        } else {
          allTablesResolved = false;
        }
      }
      if (!allTablesResolved) continue;

      const columnSet = new Set(allColumns);
      for (const colRef of colRefs) {
        if (!columnSet.has(colRef)) {
          const relPath = relative(join(import.meta.dirname, '..'), filePath);
          mapperSqlErrors.push(`  ${relPath}: <${id}> SQL 引用了列 "${colRef}"，但 ${tableNames.join('/')} 表均无此列`);
        }
      }
    }
  }
}

// ── Mapper 方法缺少 SQL 绑定检查 ──
const mapperMethods = findMapperMethods(ENTITY_DIR);
const xmlMappings = extractXmlMappings(join(import.meta.dirname, '..', 'src', 'main', 'resources'));

// 构建 namespace → ids 映射
const xmlNsIds = new Map();
for (const mapping of xmlMappings) {
  xmlNsIds.set(mapping.namespace, mapping.ids);
}

const unboundMethods = [];
for (const { filePath, fqn, methods } of mapperMethods) {
  const xmlIds = xmlNsIds.get(fqn) || new Set();
  for (const method of methods) {
    if (method.hasAnnotation) continue; // 有 @Select/@Insert/... 注解，绑定正常
    if (xmlIds.has(method.methodName)) continue; // XML Mapper 中有对应条目，绑定正常
    const relPath = relative(join(import.meta.dirname, '..'), filePath);
    unboundMethods.push(`  ${relPath}:${method.lineNum} — 方法 "${method.methodName}()" 无 SQL 注解且 XML Mapper 无对应条目`);
  }
}

// ── 检查继承 BaseEntity 但表缺少 deleted 列 ──

const missingDeletedErrors = [];
if (dbAvailable) {
  const entitiesWithDeleted = findEntitiesWithDeletedCheck(ENTITY_DIR);
  for (const { filePath, tableName } of entitiesWithDeleted) {
    const columns = getTableColumns(tableName);
    if (columns === null) continue; // 表不存在，跳过
    if (!columns.includes('deleted')) {
      const relPath = relative(join(import.meta.dirname, '..'), filePath);
      missingDeletedErrors.push(`  ${relPath}: 继承 BaseEntity（含 @TableLogic），但 ${tableName} 表缺少 deleted 列`);
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

// 输出 JdbcTemplate SQL 表名错误
if (jdbcErrors.length > 0) {
  console.error('❌ JdbcTemplate SQL 引用了不存在的表（需要立即修复）：\n');
  for (const err of jdbcErrors) {
    console.error(err);
  }
  console.error(`\n共 ${jdbcErrors.length} 个不存在的表引用。\n`);
  console.error('修复方法：');
  console.error('  1. 对照 \\dt 确认实际表名');
  console.error('  2. 如果表未创建 → 创建 Flyway migration');
  console.error('  3. 如果表名写错 → 修正 SQL 中的表名');
  // 合并错误，让后续流程也视为失败
  for (const err of jdbcErrors) {
    realErrors.push(err);
  }
}

// 输出自定义 SQL 列引用错误（@Select / XML Mapper）
if (mapperSqlErrors.length > 0) {
  console.error('❌ 自定义 SQL 引用了不存在的列（需要立即修复）：\n');
  for (const err of mapperSqlErrors) {
    console.error(err);
  }
  console.error(`\n共 ${mapperSqlErrors.length} 个不存在的列引用。\n`);
  console.error('修复方法：');
  console.error('  1. 如果 DB 缺列 → 创建 Flyway migration 添加列');
  console.error('  2. 如果列名写错 → 修正 SQL 中的列名');
  // 合并错误，让后续流程也视为失败
  for (const err of mapperSqlErrors) {
    realErrors.push(err);
  }
}

// 输出 Mapper 方法缺少 SQL 绑定错误
if (unboundMethods.length > 0) {
  console.error('❌ Mapper 接口方法缺少 SQL 绑定（需要立即修复）：\n');
  for (const err of unboundMethods) {
    console.error(err);
  }
  console.error(`\n共 ${unboundMethods.length} 个未绑定方法。\n`);
  console.error('修复方法：');
  console.error('  1. 添加 @Select/@Insert/@Update/@Delete 注解内联 SQL');
  console.error('  2. 或在对应 XML Mapper 中添加 <select>/<insert>/<update>/<delete> 条目');
  for (const err of unboundMethods) {
    realErrors.push(err);
  }
}

// 输出继承 BaseEntity 但表缺少 deleted 列的错误
if (missingDeletedErrors.length > 0) {
  console.error('❌ 继承 BaseEntity 的表缺少 deleted 列（需要立即修复）：\n');
  for (const err of missingDeletedErrors) {
    console.error(err);
  }
  console.error(`\n共 ${missingDeletedErrors.length} 张表缺少 deleted 列。\n`);
  console.error('修复方法：');
  console.error('  1. 创建 Flyway migration: ALTER TABLE t_xxx ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;');
  for (const err of missingDeletedErrors) {
    realErrors.push(err);
  }
}

if (realErrors.length > 0) {
  console.error('❌ 表存在但列不匹配（需要立即修复）：\n');
  for (const err of realErrors) {
    console.error(err);
  }
  console.error(`\n共 ${realErrors.length} 个不匹配项，涉及以下表：`);
  const affectedTables = [...new Set(realErrors.map(e => {
    const m = e.match(/但 (\S+) 表/);
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