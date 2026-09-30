#!/usr/bin/env python3
"""
租户夹具一致性检查（P102 / REQ-2026-129）

背景：P102-M2 起 `MyMetaObjectHandler.insertFill` 会把 `enterpriseId` **无条件覆盖**
为 `EnterpriseContextHolder` 的值（原先用 `strictInsertFill` 只在字段为 null 时填，
故请求体可伪造他人租户）。于是测试夹具里「只给实体设 `enterpriseId` 而不切上下文」
的做法会失效 —— 实测已造成 3 处回归，其中 `CashFlowPeriodRangeRealDBTest`
因科目被改写到 enterprise_id=1 而撞上种子（uq_subject_code_ent）。

正确范式（4 个兄弟测试 OpeningContinuity / AuxiliaryDetail / CashSubjectBalance /
IncomeStatementCaliber 已如此）：**把上下文切到目标企业** `useEnterprise(ENT_ID)`，
而不是逐个包裹 `withoutEnterpriseContext`。

本脚本标出「设了非默认 enterpriseId 却没有把上下文切过去」的测试文件。
排除：AbstractMapperTest 自身、EnterpriseIdInjectionTest（故意伪造以验证封禁）、
以及只把值喂给 mock / DTO / VO 的文件（不经过 MyBatis-Plus 拦截链）。

用法：python3 scripts/check_tenant_fixture.py [src/test/java]
退出码 0 = 通过；1 = 存在可疑夹具。
"""
import re
import sys
import pathlib

DEFAULT_ENTERPRISE = re.compile(r'^(1L|DEFAULT_ENTERPRISE_ID|1)\b')
# 允许的「显式伪造」场景：验证入参封禁本身
EXEMPT_FILES = {"EnterpriseIdInjectionTest.java", "AbstractMapperTest.java"}
USE_ENTERPRISE = re.compile(r'useEnterprise\s*\(')
WITHOUT_CTX = re.compile(r'withoutEnterpriseContext\s*\(')
EXTENDS_BASE = re.compile(r'extends\s+AbstractMapperTest\b')
# DTO/VO 接收者：不会走 MyBatis-Plus 拦截链
DTO_RECEIVER = re.compile(r'^\s*(dto|vo|req|request|response)\w*\s*\.\s*setEnterpriseId', re.I)
# 解析同文件内的简单常量：static final [long|Long] NAME = 1L;
CONST_DEF = re.compile(
    r'static\s+final\s+(?:long|Long|int|Integer)\s+(\w+)\s*=\s*([^;]+);')


def resolve_default_constants(text: str) -> set:
    """收集本文件内被赋为 1（或 1L）的常量名，用于识别「看起来非默认、实则等于默认」的值。"""
    names = set()
    for m in CONST_DEF.finditer(text):
        name, raw = m.group(1), m.group(2).strip()
        if re.match(r'^1L?$', raw):
            names.add(name)
    return names


def main() -> int:
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "backend/src/test/java")
    if not root.exists():
        print(f"[SKIP] 目录不存在: {root}")
        return 0

    suspicious = []
    for path in root.rglob("*.java"):
        if path.name in EXEMPT_FILES:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        if "setEnterpriseId(" not in text:
            continue
        # 只有继承真库基类的测试才会真正经过 insertFill
        if not EXTENDS_BASE.search(text):
            continue
        # 已切上下文 或 已显式声明跨租户造数出口 —— 均为合规范式
        if USE_ENTERPRISE.search(text) or WITHOUT_CTX.search(text):
            continue

        default_consts = resolve_default_constants(text)

        for lineno, line in enumerate(text.splitlines(), 1):
            if DTO_RECEIVER.match(line):
                continue
            m = re.search(r'setEnterpriseId\s*\(\s*([^)]*)\)', line)
            if not m:
                continue
            value = m.group(1).strip()
            if not value or DEFAULT_ENTERPRISE.match(value):
                continue
            if value in default_consts:
                continue
            suspicious.append((path, lineno, value, line.strip()[:90]))

    if not suspicious:
        print("[OK] 未发现「设了非默认 enterpriseId 却未切换上下文」的测试夹具")
        return 0

    print(f"[FAIL] 发现 {len(suspicious)} 处可疑租户夹具（P102-M2 强制覆盖会使其失效）：\n")
    for path, lineno, value, snippet in suspicious:
        print(f"  {path}:{lineno}  value={value}")
        print(f"      {snippet}")
    print("\n修法二选一：")
    print("  ① 在 @BeforeEach 调 useEnterprise(<同一企业>)（推荐，与 4 个兄弟测试一致）")
    print("  ② 确需跨租户造数时，用 AbstractMapperTest#withoutEnterpriseContext 包裹")
    return 1


if __name__ == "__main__":
    sys.exit(main())
