#!/usr/bin/env node
/**
 * CI 检查：Entity 的 @TableId(type = IdType.AUTO) 是否与 Flyway migration 一致
 *
 * 扫描所有 Entity 文件，检查使用了 IdType.AUTO 的实体，
 * 对应的 Flyway migration 中该表的 id 列是否声明了 IDENTITY。
 *
 * 不一致时退出码 1，输出修复建议。
 *
 * 使用方式：
 *   node scripts/check-identity-consistency.mjs
 *
 * 集成到 CI：
 *   - 在 .github/workflows/ci.yml 中添加：
 *       - run: cd backend && node scripts/check-identity-consistency.mjs
 */

import fs from 'fs';
import path from 'path';

const ROOT = process.cwd();
const JAVA_DIR = path.join(ROOT, 'src/main/java');
const MIGRATION_DIR = path.join(ROOT, 'src/main/resources/db/migration');

/**
 * 白名单：表尚未创建 migration 的 Entity，暂不检查。
 * 这些表的 CREATE TABLE migration 还未提交，所以无法检查 IDENTITY。
 * 当创建表时，必须确保 id 列包含 GENERATED ALWAYS AS IDENTITY。
 */
const PENDING_MIGRATION_TABLES = new Set([
    't_ai_anomaly_tag',
    't_cash_flow_rule',
    't_financial_metric',
    't_attachment',
    't_voucher_template_line',
    't_bad_debt_provision_detail',
    't_bad_debt_provision_scheme',
    't_bad_debt_provision_scheme_item',
    't_reconciliation_dispute',
    't_dispute',
    't_reconciliation_outstanding',
    't_outstanding_item',
    't_purchase_return',
    't_reconciliation_log',
    't_budget_adjustment',
    't_budget_entry',
]);

// 1. 扫描所有 Entity 文件，找出使用 IdType.AUTO 的
const entities = [];
function scanDir(dir) {
    if (!fs.existsSync(dir)) return;
    for (const f of fs.readdirSync(dir)) {
        const fp = path.join(dir, f);
        const stat = fs.statSync(fp);
        if (stat.isDirectory()) { scanDir(fp); continue; }
        if (!f.endsWith('.java')) continue;
        if (/Mapper|Test|Aspect|Config|Validator|Controller|Service|DTO|VO|Param/.test(f)) continue;
        const content = fs.readFileSync(fp, 'utf-8');
        if (!content.includes('@TableId') || !content.includes('IdType.AUTO')) continue;
        // 提取 @TableName 或从类名推导
        let tableName = null;
        const tnMatch = content.match(/@TableName\s*\(\s*"([^"]+)"\s*\)/);
        if (tnMatch) {
            tableName = tnMatch[1];
        }
        if (!tableName) {
            const classMatch = content.match(/public\s+class\s+(\w+)\s/);
            if (classMatch) {
                let cn = classMatch[1];
                cn = cn.replace(/Entity$/, '');
                tableName = 't_' + cn.replace(/([A-Z])/g, (m) => '_' + m.toLowerCase()).replace(/^_/, '');
            }
        }
        const relativePath = path.relative(ROOT, fp);
        entities.push({ file: relativePath, table: tableName });
    }
}
scanDir(JAVA_DIR);

// 2. 扫描所有 migration 文件，收集内容
const migrationContent = [];
if (fs.existsSync(MIGRATION_DIR)) {
    for (const f of fs.readdirSync(MIGRATION_DIR)) {
        if (f.endsWith('.sql')) {
            const content = fs.readFileSync(path.join(MIGRATION_DIR, f), 'utf-8');
            migrationContent.push({ file: f, content });
        }
    }
}

// 检查某张表的 id 列在 migration 中是否有 IDENTITY
function hasIdentity(tableName) {
    for (const m of migrationContent) {
        // CREATE TABLE 中包含该表且有 IDENTITY
        const createPattern = new RegExp(
            `CREATE\\s+TABLE\\b[^;]{0,2000}?,?\\s*${tableName.replace(/_/g, '\\_')}\\b[^;]{0,3000}IDENTITY`,
            'is'
        );
        if (createPattern.test(m.content)) return true;
        // ALTER TABLE ... ADD GENERATED ALWAYS AS IDENTITY
        const alterPattern = new RegExp(
            `ALTER\\s+TABLE\\s+${tableName.replace(/_/g, '\\_')}\\s+ALTER\\s+COLUMN\\s+id\\s+ADD\\s+GENERATED\\s+ALWAYS\\s+AS\\s+IDENTITY`,
            'is'
        );
        if (alterPattern.test(m.content)) return true;
    }
    return false;
}

// 检查表是否存在于 migration 中（有 CREATE TABLE 或 ALTER TABLE）
function hasMigration(tableName) {
    for (const m of migrationContent) {
        if (new RegExp(`\\b${tableName.replace(/_/g, '\\_')}\\b`, 'i').test(m.content)) return true;
    }
    return false;
}

// 3. 报告
let exitCode = 0;
let inconsistentCount = 0;
let pendingCount = 0;

console.log('\n=== Entity-DB IDENTITY 一致性检查 ===');
console.log(`扫描到 ${entities.length} 个使用 IdType.AUTO 的 Entity\n`);

for (const e of entities) {
    if (PENDING_MIGRATION_TABLES.has(e.table)) {
        if (!hasMigration(e.table)) {
            console.log(`⏳ ${e.table} (${e.file}) — 表尚未创建 migration`);
            pendingCount++;
        } else {
            // 表有 migration 但不在白名单中，继续正常检查
            if (hasIdentity(e.table)) {
                console.log(`✅ ${e.table} (${e.file})`);
            } else {
                console.log(`❌ ${e.table} (${e.file})`);
                console.log(`   migration 中 id 列缺少 IDENTITY`);
                console.log(`   修复: ALTER TABLE ${e.table} ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;`);
                inconsistentCount++;
                exitCode = 1;
            }
        }
        continue;
    }

    if (!hasMigration(e.table)) {
        console.log(`⏳ ${e.table} (${e.file}) — 表尚未创建 migration`);
        pendingCount++;
        continue;
    }

    if (hasIdentity(e.table)) {
        console.log(`✅ ${e.table} (${e.file})`);
    } else {
        console.log(`❌ ${e.table} (${e.file})`);
        console.log(`   migration 中 id 列缺少 IDENTITY`);
        console.log(`   修复: ALTER TABLE ${e.table} ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;`);
        inconsistentCount++;
        exitCode = 1;
    }
}

console.log(`\n==============================`);
console.log(`✅ 一致: ${entities.length - inconsistentCount - pendingCount}`);
if (pendingCount > 0) {
    console.log(`⏳ 待建 migration: ${pendingCount}`);
}
if (inconsistentCount > 0) {
    console.log(`❌ 不一致: ${inconsistentCount}`);
    console.log('请修复 migration 后重试');
}
if (exitCode === 0) {
    console.log('✅ 所有已建表的 Entity 的 id 列与 DB migration 一致');
}
process.exit(exitCode);
