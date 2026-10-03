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

本脚本判两类问题，都以 exit 1 报出：

  A. 未管理上下文：文件 extends AbstractMapperTest、设了非默认 enterpriseId，
     但既没有 useEnterprise( 也没有 withoutEnterpriseContext(。
  B. 上下文与目标企业不一致（有 useEnterprise，但与 setEnterpriseId 的目标企业不同）。
     **B 类更危险且此前完全不被检查**：上下文 = A、实体写 B 时，M2 的无条件覆盖
     会把实体静默改写成 A ⇒ 数据落到 A 而测试以为落在 B，无任何报错，
     表现为「另一个用例的断言莫名其妙」。这类代码能正常编译、正常跑绿，
     只能靠静态比对发现。

排除：AbstractMapperTest 自身、EnterpriseIdInjectionTest（故意伪造以验证封禁）、
以及只把值喂给 mock / DTO / VO 的文件（不经过 MyBatis-Plus 拦截链）。

用法：python3 scripts/check_tenant_fixture.py [src/test/java]
退出码 0 = 通过；1 = 存在可疑夹具（A 类和/或 B 类）。

残留盲区（已知，接受）：
1. 目标企业写成变量/字段时（如 setEnterpriseId(enterpriseId)）无法静态求值 ⇒ 跳过，
   不报错。文件内 `static final long/long/Integer NAME = 数字L` 形式的常量可求值。
2. `setEnterpriseId(1L)` 这类「默认值」不参与 B 类比对（1L 通常是基类默认上下文），
   故「上下文=2L + 实体=1L」这种反向错配不会被报出。
3. 第三种上下文写法（直接 `EnterpriseContextHolder.set(...)`）不被识别 —— 但若文件
   同时有非默认 setEnterpriseId 且无 useEnterprise/withoutEnterpriseContext，
   仍会被 A 类抓到。
"""
import re
import sys
import pathlib

DEFAULT_ENTERPRISE = re.compile(r'^(1L|DEFAULT_ENTERPRISE_ID|1)\b')
# 允许的「显式伪造」场景：验证入参封禁本身
EXEMPT_FILES = {"EnterpriseIdInjectionTest.java", "AbstractMapperTest.java"}
USE_ENTERPRISE = re.compile(r'useEnterprise\s*\(\s*([^)]*)\)', re.S)
WITHOUT_CTX = re.compile(r'withoutEnterpriseContext\s*\(')
EXTENDS_BASE = re.compile(r'extends\s+AbstractMapperTest\b')
# DTO/VO 接收者：不会走 MyBatis-Plus 拦截链
DTO_RECEIVER = re.compile(r'^\s*(dto|vo|req|request|response)\w*\s*\.\s*setEnterpriseId', re.I)
# 解析同文件内的简单常量：static final [long|Long] NAME = 1L;
CONST_DEF = re.compile(
    r'static\s+final\s+(?:long|Long|int|Integer)\s+(\w+)\s*=\s*([^;]+);')
NUM_LITERAL = re.compile(r'^(\d+)L?$')
SET_EID = re.compile(r'setEnterpriseId\s*\(\s*([^)]*)\)')


def resolve_constant_defs(text: str) -> dict:
    """收集本文件内可求值的常量：static final long NAME = 9901L; → {'NAME': 9901}"""
    consts = {}
    for m in CONST_DEF.finditer(text):
        name, raw = m.group(1), m.group(2).strip()
        num = NUM_LITERAL.match(raw)
        if num:
            consts[name] = int(num.group(1))
    return consts


def resolve(arg: str, consts: dict):
    """把实参求值成 int；无法静态求值（变量/字段/方法调用）时返回 None。"""
    value = arg.strip()
    num = NUM_LITERAL.match(value)
    if num:
        return int(num.group(1))
    return consts.get(value)


def is_default_enterprise(raw: str, consts: dict) -> bool:
    """默认企业 = 字面 1L / 常量名 DEFAULT_ENTERPRISE_ID / **任何求值为 1 的本地常量**。

    第三条最容易漏：`private static final Long ENTERPRISE_ID = 1L;` 这种
    「自-named 非默认常量」实际就是默认企业，早期版本只按常量名判断，
    导致 21 处误报（4 个文件）。判断「是不是默认」必须**求值**，不能看名字。
    """
    if DEFAULT_ENTERPRISE.match(raw):
        return True
    return consts.get(raw.strip()) == 1


def collect_set_eid(text: str, consts: dict):
    """返回 [(lineno, raw_value, resolved_or_None, snippet)]，跳过 DTO/VO 接收者。"""
    rows = []
    for lineno, line in enumerate(text.splitlines(), 1):
        if DTO_RECEIVER.match(line):
            continue
        m = SET_EID.search(line)
        if not m:
            continue
        raw = m.group(1).strip()
        rows.append((lineno, raw, resolve(raw, consts), line.strip()[:90]))
    return rows


def main() -> int:
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "backend/src/test/java")
    if not root.exists():
        print(f"[SKIP] 目录不存在: {root}")
        return 0

    unmanaged = []        # A 类：完全没管上下文
    mismatched = []       # B 类：上下文与目标企业不一致
    for path in root.rglob("*.java"):
        if path.name in EXEMPT_FILES:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        if "setEnterpriseId(" not in text:
            continue
        # 只有继承真库基类的测试才会真正经过 insertFill
        if not EXTENDS_BASE.search(text):
            continue

        consts = resolve_constant_defs(text)
        rows = collect_set_eid(text, consts)
        has_use = bool(USE_ENTERPRISE.search(text))
        has_escape = bool(WITHOUT_CTX.search(text))

        # A 类：设了非默认企业号，却既没切上下文也没声明跨租户出口
        if not has_use and not has_escape:
            for lineno, raw, _resolved, snippet in rows:
                if raw and not is_default_enterprise(raw, consts):
                    unmanaged.append((path, lineno, raw, snippet))
            continue

        # B 类：切了上下文，就要求实体目标企业与之一致
        if has_use:
            ctx_ids = {resolve(m.group(1).strip(), consts)
                       for m in USE_ENTERPRISE.finditer(text)}
            ctx_ids.discard(None)
            if ctx_ids:
                for lineno, raw, value, snippet in rows:
                    if value is None or is_default_enterprise(raw, consts):
                        continue
                    if value not in ctx_ids:
                        mismatched.append(
                            (path, lineno, raw, sorted(ctx_ids), snippet))

    if not unmanaged and not mismatched:
        print("[OK] 未发现「设了非默认 enterpriseId 却未切换上下文」"
              "或「上下文与目标企业不一致」的测试夹具")
        return 0

    if unmanaged:
        print(f"[FAIL] A 类：发现 {len(unmanaged)} 处可疑租户夹具"
              "（P102-M2 强制覆盖会使其失效）：\n")
        for path, lineno, value, snippet in unmanaged:
            print(f"  {path}:{lineno}  value={value}")
            print(f"      {snippet}")
        print("\n修法二选一：")
        print("  ① 在 @BeforeEach 调 useEnterprise(<同一企业>)（推荐，与 4 个兄弟测试一致）")
        print("  ② 确需跨租户造数时，用 AbstractMapperTest#withoutEnterpriseContext 包裹")

    if mismatched:
        print(f"\n[FAIL] B 类：发现 {len(mismatched)} 处「上下文企业 ≠ 实体目标企业」：\n")
        for path, lineno, value, ctx_ids, snippet in mismatched:
            print(f"  {path}:{lineno}  实体目标={value}  上下文={ctx_ids}")
            print(f"      {snippet}")
        print("\n症状：M2 的无条件覆盖会把实体静默改写成上下文企业，数据落到 "
              f"{ctx_ids}，\n      而测试以为落在 {value} —— 无报错，表现为「别的用例断言莫名失败」。")
        print("修法：把 useEnterprise(<企业>) 的实参与 setEnterpriseId(<企业>) 对齐为同一值。")

    return 1


if __name__ == "__main__":
    sys.exit(main())