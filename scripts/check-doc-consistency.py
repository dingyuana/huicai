#!/usr/bin/env python3
"""
文档一致性检测脚本
用法: python3 scripts/check-doc-consistency.py [--since HEAD~1] [--report path]
"""

import subprocess
import re
import sys
import os
from datetime import datetime
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# ===== 映射表：代码文件 → 对应设计文档 =====
CODE_TO_DOC = {
    # 应收应付模块
    'arap/': ['docs/design/02-arap-design.md', 'docs/specs/P30-reconciliation-workbench-enhance.md'],
    # 总账/凭证模块
    'finance/': ['docs/design/01-gl-design.md'],
    # 税务/发票模块
    'tax/': ['docs/design/06-tax-design.md'],
    # 资金模块
    'cash/': ['docs/design/03-cash-design.md'],
    # 资产模块
    'asset/': ['docs/design/04-asset-design.md'],
    # 费用报销
    'expense/': ['docs/design/05-expense-design.md'],
    # 预算
    'budget/': ['docs/design/08-budget-design.md'],
    # 报表
    'report/': ['docs/design/09-report-design.md'],
    # 系统基础
    'system/': ['docs/design/00-system-design.md'],
    # AI 服务
    'ai/': ['docs/design/10-ai-orchestration-design.md'],
    # 存储
    'storage/': ['docs/design/00-system-design.md'],
}

# Entity → 对应设计文档 + DB migration
ENTITY_DOC_MAP = {
    'OutputInvoiceEntity': ('docs/design/06-tax-design.md', 'db/migration/V8__init_tax_tables.sql'),
    'InputInvoiceEntity': ('docs/design/06-tax-design.md', 'db/migration/V8__init_tax_tables.sql'),
    'BusinessDocEntity': ('docs/design/02-arap-design.md', 'db/migration/V5__init_business_doc_tables.sql'),
    'VoucherEntity': ('docs/design/01-gl-design.md', 'db/migration/V4__init_finance_tables.sql'),
    'ArapSettlementEntity': ('docs/design/02-arap-design.md', 'db/migration/V24__p5_reconciliation_log.sql'),
}

DESIGN_DOCS = {
    'docs/design/00-system-design.md': '基础数据',
    'docs/design/01-gl-design.md': '总账管理',
    'docs/design/02-arap-design.md': '应收应付',
    'docs/design/03-cash-design.md': '资金管理',
    'docs/design/04-asset-design.md': '固定资产',
    'docs/design/05-expense-design.md': '费用报销',
    'docs/design/06-tax-design.md': '发票税务',
    'docs/design/07-salary-design.md': '工资薪酬',
    'docs/design/08-budget-design.md': '预算管理',
    'docs/design/09-report-design.md': '财务报表',
    'docs/design/10-ai-orchestration-design.md': 'AI 编排',
}


def run_git(args):
    """执行 git 命令"""
    result = subprocess.run(
        ['git', '-C', str(REPO_ROOT)] + args,
        capture_output=True, text=True, timeout=30
    )
    return result.stdout.strip()


def get_changed_files(since='HEAD~1'):
    """获取变更文件列表"""
    files = run_git(['diff', '--name-only', since, 'HEAD'])
    if not files:
        files = run_git(['diff', '--name-only', 'HEAD'])
    return [f for f in files.split('\n') if f.strip()]


def classify_changes(files):
    """对变更文件分类"""
    categories = {
        'migrations': [],
        'entities': [],
        'controllers': [],
        'services': [],
        'state_machines': [],
        'docs': [],
        'frontend': [],
        'tests': [],
        'other': [],
    }
    for f in files:
        if f.startswith('backend/src/main/resources/db/migration/'):
            categories['migrations'].append(f)
        elif f.endswith('Entity.java'):
            categories['entities'].append(f)
        elif f.endswith('Controller.java'):
            categories['controllers'].append(f)
        elif f.endswith('StateMachineServiceImpl.java'):
            categories['state_machines'].append(f)
        elif f.endswith('ServiceImpl.java'):
            categories['services'].append(f)
        elif f.startswith('docs/'):
            categories['docs'].append(f)
        elif f.startswith('frontend/'):
            categories['frontend'].append(f)
        elif 'test/' in f:
            categories['tests'].append(f)
        else:
            categories['other'].append(f)
    return categories


def get_entity_fields(entity_file):
    """从 Entity 文件中提取所有 private 字段"""
    filepath = REPO_ROOT / entity_file
    if not filepath.exists():
        return []
    fields = []
    with open(filepath) as f:
        content = f.read()
    # 匹配 private 字段定义
    pattern = re.compile(r'@TableField\(.*?\)\s*\n\s*private\s+\w+\s+(\w+)|private\s+\w+\s+(\w+)')
    for match in pattern.finditer(content):
        field = match.group(1) or match.group(2)
        fields.append(field)
    return fields


def get_doc_version(doc_file):
    """读取设计文档版本号"""
    filepath = REPO_ROOT / doc_file
    if not filepath.exists():
        return None
    with open(filepath) as f:
        first_lines = ''.join([f.readline() for _ in range(10)])
    match = re.search(r'版本[：:].*?V([\d.]+)', first_lines)
    return match.group(1) if match else None


def check_rule1_migration_vs_doc(migrations):
    """规则 1：Migration 变更 → 检查设计文档"""
    findings = []
    for m in migrations:
        # 提取表名
        content = (REPO_ROOT / m).read_text()
        tables = re.findall(r'(?:TABLE|table)\s+(?:IF NOT EXISTS\s+)?(\w+)', content, re.IGNORECASE)
        for table in tables:
            # 找对应设计文档
            for code_prefix, docs in CODE_TO_DOC.items():
                if table in ['t_' + code_prefix.rstrip('/')] or any(table.startswith(t) for t in ['t_']):
                    for doc in docs:
                        doc_path = REPO_ROOT / doc
                        if doc_path.exists():
                            doc_content = doc_path.read_text()
                            if table not in doc_content:
                                findings.append({
                                    'level': '🔴',
                                    'type': 'migration_doc_mismatch',
                                    'file': doc,
                                    'desc': f'Migration 修改了 {table}，但设计文档 {doc} 中未提及该表',
                                    'action': f'更新 {doc} 的数据模型章节，添加/修改 {table} 的描述',
                                })
        # 检测是否为新增 migration
        if 'ADD COLUMN' in content:
            columns = re.findall(r'ADD COLUMN\s+(?:IF NOT EXISTS\s+)?(\w+)', content)
            findings.append({
                'level': '🟡',
                'type': 'migration_add_column',
                'file': m,
                'desc': f'Migration 新增了列: {", ".join(columns)}',
                'action': '检查对应 Entity 的 @TableField 注解是否同步',
            })
    return findings


def check_rule2_entity_vs_db(entity_file):
    """规则 2：Entity 字段变更 → 检查 DB 和设计文档"""
    findings = []
    entity_name = os.path.basename(entity_file).replace('.java', '')
    
    # 找对应的文档
    doc_info = ENTITY_DOC_MAP.get(entity_name)
    if doc_info:
        doc_path, migration_path = doc_info
        doc_file = REPO_ROOT / doc_path
        if doc_file.exists():
            doc_content = doc_file.read_text()
            # 检查 Entity 的关键字段是否在文档中
            fields = get_entity_fields(entity_file)
            key_fields = ['doc_type', 'status', 'amount', 'settled_amount', 'unsettled_amount',
                         'invoice_no', 'voucher_no', 'doc_no']
            for field in key_fields:
                if field in fields and field not in doc_content:
                    findings.append({
                        'level': '🟡',
                        'type': 'entity_field_missing_in_doc',
                        'file': doc_path,
                        'desc': f'{entity_name} 有字段 {field}，但设计文档 {doc_path} 未提及',
                        'action': f'更新 {doc_path} 数据模型章节，补充 {field} 字段说明',
                    })
    
    # 检查 @TableField 注解
    content = (REPO_ROOT / entity_file).read_text()
    if 'auditedBy' in content and '@TableField(exist = false)' not in content.split('auditedBy')[0][-50:]:
        findings.append({
            'level': '🔴',
            'type': 'entity_field_missing_annotation',
            'file': entity_file,
            'desc': f'{entity_name}.auditedBy 缺少 @TableField(exist = false)',
            'action': '添加 @TableField(exist = false) 注解',
        })
    if 'aiMappingResult' in content and 'JsonbTypeHandler' not in content.split('aiMappingResult')[0][-100:]:
        findings.append({
            'level': '🔴',
            'type': 'entity_jsonb_missing_typehandler',
            'file': entity_file,
            'desc': f'{entity_name}.aiMappingResult 缺少 typeHandler = JsonbTypeHandler.class',
            'action': '添加 @TableField(typeHandler = JsonbTypeHandler.class) 注解',
        })
    return findings


def check_rule3_controller_vs_spec(controllers):
    """规则 3：Controller/API 变更 → 检查 SPEC"""
    findings = []
    for ctrl in controllers:
        content = (REPO_ROOT / ctrl).read_text()
        # 提取所有 @RequestMapping
        base_paths = re.findall(r'@RequestMapping\(["\']([^"\']+)["\']\)', content)
        base = base_paths[0] if base_paths else ''
        # 提取所有映射方法
        mappings = re.findall(r'@(GetMapping|PostMapping|PutMapping|DeleteMapping)\(["\']([^"\']+)["\']\)', content)
        for method, path in mappings:
            full_path = f'{base}{path}'
            # 检查所有 SPEC 文档是否包含该路径
            specs_dir = REPO_ROOT / 'docs/specs'
            found_in_spec = False
            if specs_dir.exists():
                for spec_file in specs_dir.glob('*.md'):
                    spec_content = spec_file.read_text()
                    if full_path in spec_content:
                        found_in_spec = True
                        break
            if not found_in_spec:
                findings.append({
                    'level': '🟡',
                    'type': 'api_missing_in_spec',
                    'file': ctrl,
                    'desc': f'API {full_path} 未在任何 SPEC 文档中记录',
                    'action': '更新对应 SPEC 文档的 API 端点表格',
                })
    return findings


def check_rule5_version_staleness():
    """规则 5：文档版本号滞后"""
    findings = []
    for doc_path, doc_name in DESIGN_DOCS.items():
        doc_file = REPO_ROOT / doc_path
        if not doc_file.exists():
            findings.append({
                'level': '🟡',
                'type': 'doc_missing',
                'file': doc_path,
                'desc': f'设计文档 {doc_path} 不存在',
                'action': '创建或恢复该设计文档',
            })
            continue
        version = get_doc_version(doc_path)
        if version is None:
            findings.append({
                'level': '🟡',
                'type': 'doc_no_version',
                'file': doc_path,
                'desc': f'设计文档 {doc_path} 缺少版本号',
                'action': '添加版本号头部',
            })
    return findings


def check_rule0_code_changed_no_doc(changed_files):
    """规则 0：代码改了但相关文档完全没动"""
    findings = []
    # 找出后端代码变更
    backend_files = [f for f in changed_files if f.startswith('backend/src/main/java/')]
    for bf in backend_files:
        # 找对应的设计文档
        for code_prefix, docs in CODE_TO_DOC.items():
            if code_prefix in bf:
                for doc in docs:
                    if doc not in changed_files:
                        findings.append({
                            'level': '🔴',
                            'type': 'code_change_no_doc_update',
                            'file': bf,
                            'desc': f'代码文件 {bf} 有变更，但关联设计文档 {doc} 未更新',
                            'action': f'审查 {doc} 是否需要同步更新',
                        })
                break
    return findings


def generate_report(findings, since):
    """生成差距报告"""
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    report = [
        f'# 文档一致性差距报告',
        f'',
        f'> **生成时间**：{now}',
        f'> **检测范围**：{since} → HEAD',
        f'> **检出问题**：{len(findings)} 项',
        f'',
        f'---',
        f'',
    ]
    
    if not findings:
        report.append('✅ **全部通过，文档与代码一致。**')
    else:
        critical = [f for f in findings if f['level'] == '🔴']
        warning = [f for f in findings if f['level'] == '🟡']
        
        report.append(f'| 等级 | 数量 |')
        report.append(f'|------|------|')
        report.append(f'| 🔴 严重 | {len(critical)} |')
        report.append(f'| 🟡 警告 | {len(warning)} |')
        report.append(f'')
        report.append(f'---')
        report.append(f'')
        
        if critical:
            report.append(f'## 🔴 严重问题（需修复后才能合并）')
            report.append(f'')
            for f in critical:
                report.append(f'### {f["type"]}')
                report.append(f'- **文件**：{f["file"]}')
                report.append(f'- **问题**：{f["desc"]}')
                report.append(f'- **建议**：{f["action"]}')
                report.append(f'')
        
        if warning:
            report.append(f'## 🟡 警告（建议修复）')
            report.append(f'')
            for f in warning:
                report.append(f'### {f["type"]}')
                report.append(f'- **文件**：{f["file"]}')
                report.append(f'- **问题**：{f["desc"]}')
                report.append(f'- **建议**：{f["action"]}')
                report.append(f'')
    
    return '\n'.join(report)


def main():
    since = 'HEAD~1'
    report_path = None
    
    for i, arg in enumerate(sys.argv[1:]):
        if arg == '--since' and i + 2 < len(sys.argv):
            since = sys.argv[i + 2]
        elif arg == '--report' and i + 2 < len(sys.argv):
            report_path = sys.argv[i + 2]
    
    print(f'📋 文档一致性检测 [{since} → HEAD]')
    print()
    
    # 1. 获取变更文件
    changed_files = get_changed_files(since)
    if not changed_files:
        print('✅ 无变更文件')
        return
    
    print(f'📁 变更文件: {len(changed_files)} 个')
    categories = classify_changes(changed_files)
    
    for cat, files in categories.items():
        if files:
            print(f'   {cat}: {len(files)} 个')
    
    print()
    
    # 2. 执行各项检测
    all_findings = []
    
    if categories['migrations']:
        print('🔍 规则 1: Migration → 设计文档...')
        all_findings.extend(check_rule1_migration_vs_doc(categories['migrations']))
    
    for ef in categories['entities']:
        print(f'🔍 规则 2: Entity → DB/文档 ({os.path.basename(ef)})...')
        all_findings.extend(check_rule2_entity_vs_db(ef))
    
    if categories['controllers']:
        print('🔍 规则 3: Controller → SPEC...')
        all_findings.extend(check_rule3_controller_vs_spec(categories['controllers']))
    
    print('🔍 规则 0: 代码变更但文档未动...')
    all_findings.extend(check_rule0_code_changed_no_doc(changed_files))
    
    print('🔍 规则 5: 文档版本号检查...')
    all_findings.extend(check_rule5_version_staleness())
    
    # 3. 去重
    seen = set()
    unique_findings = []
    for f in all_findings:
        key = (f['type'], f['file'], f['desc'])
        if key not in seen:
            seen.add(key)
            unique_findings.append(f)
    
    # 4. 输出报告
    report = generate_report(unique_findings, since)
    
    if report_path:
        report_file = Path(report_path)
        report_file.parent.mkdir(parents=True, exist_ok=True)
        report_file.write_text(report)
        print(f'\n📄 报告已写入: {report_path}')
    
    print()
    print(report)
    
    # 5. 返回退出码
    critical_count = len([f for f in unique_findings if f['level'] == '🔴'])
    if critical_count > 0:
        print(f'\n❌ 发现 {critical_count} 个严重问题，建议修复后再合并')
        sys.exit(1)
    else:
        print('\n✅ 无严重问题')
        sys.exit(0)


if __name__ == '__main__':
    main()