#!/usr/bin/env python3
"""
SPEC Contract Validator — 校验 SPEC 文件中的 YAML 契约与代码/测试的一致性。

用法:
    # 基本校验（YAML 语法 + 结构）
    python scripts/validate_spec_contract.py --path docs/specs/P21-sales-invoice-state-machine.md

    # 校验 + 检查代码实现是否存在
    python scripts/validate_spec_contract.py --path docs/specs/P21-sales-invoice-state-machine.md --check-implementation

    # 校验 + 检查测试覆盖
    python scripts/validate_spec_contract.py --path docs/specs/P21-sales-invoice-state-machine.md --check-tests

    # 严格模式（任何 warning 都返回 exit code 1）
    python scripts/validate_spec_contract.py --path docs/specs/P21-sales-invoice-state-machine.md --strict

    # 批量校验所有 SPEC
    python scripts/validate_spec_contract.py --dir docs/specs/ --check-tests
"""

import argparse
import re
import sys
import yaml
from pathlib import Path
from dataclasses import dataclass, field
from typing import Optional


# ---------------------------------------------------------------------------
# YAML extraction
# ---------------------------------------------------------------------------

# 策略哨兵：SPEC 尚未写机器可读契约 → 记警告、不判失败。
# 与「写了但 YAML 坏掉」严格区分：前者是覆盖率问题（存量 91/96），后者是真回归。
CONTRACT_MISSING = object()


def extract_yaml_contract(md_path: str) -> Optional[dict]:
    """Extract YAML block after '# MACHINE-READABLE CONTRACT' marker."""
    path = Path(md_path)
    if not path.exists():
        print(f"❌ SPEC file not found: {md_path}")
        return None

    content = path.read_text(encoding="utf-8")

    # Find the contract marker
    marker = "# MACHINE-READABLE CONTRACT"
    idx = content.find(marker)
    if idx == -1:
        print(f"⚠️  No machine-readable contract found in {md_path}")
        print(f"   Expected marker: '{marker}'")
        return CONTRACT_MISSING

    # Extract the YAML block. 围栏与 marker 的相对位置有两种合法写法：
    #   A) marker 在围栏内（```yaml 换行 后紧跟 # MACHINE-READABLE CONTRACT）—— P37 即此
    #   B) marker 在围栏外，契约块写在 marker 之后
    # 旧实现只支持 B；遇到 A 会把 marker 之后的整段 markdown（含 ``` 与表格）当 YAML 解析，
    # 必然 YAML parse error，导致该门禁恒红。
    before = content[:idx]
    after = content[idx:]

    # Case A：marker 落在未闭合围栏内（其前 ``` 计数为奇数），此时最近的 ``` 就是开启围栏
    yaml_text = None
    if before.count('```') % 2 == 1:
        opens = list(re.finditer(r'```[^\n]*\r?\n', before))
        if opens:
            body_start = opens[-1].end()
            close_idx = content.find('```', idx)
            if close_idx != -1:
                yaml_text = content[body_start:close_idx]
    if yaml_text is None:
        # Case B：marker 之后才是围栏
        fence_match = re.search(r'```(?:yaml|yml)?[ \t]*\r?\n(.*?)```', after, re.DOTALL)
        if fence_match:
            yaml_text = fence_match.group(1)

    if yaml_text is None:
        # Case C：无围栏的裸 YAML —— 收集到 markdown 结构边界为止
        lines = after.split('\n')
        yaml_lines = []
        started = False
        for line in lines[1:]:  # skip marker line
            stripped = line.strip()
            if stripped.startswith('```'):
                if started:
                    break
                continue
            if not started:
                # Skip comments (> ...) and blank lines until we find real YAML
                if stripped == '' or stripped.startswith('> ') or stripped.startswith('#'):
                    continue
                if stripped == '---' or stripped.startswith('## '):
                    break
                started = True
            if started:
                # 撞到下一个 markdown 结构即停，防止把正文吞进 YAML
                if stripped == '---' or stripped.startswith('## '):
                    break
                yaml_lines.append(line)
        yaml_text = '\n'.join(yaml_lines)

    try:
        contract = yaml.safe_load(yaml_text)
        if not isinstance(contract, dict):
            print(f"❌ Contract is not a YAML mapping (got {type(contract).__name__}) — expected contract_version/states/transitions")
            return None
        return contract
    except yaml.YAMLError as e:
        print(f"❌ YAML parse error: {e}")
        return None


# ---------------------------------------------------------------------------
# Validation checks
# ---------------------------------------------------------------------------

@dataclass
class ValidationResult:
    errors: list = field(default_factory=list)
    warnings: list = field(default_factory=list)
    infos: list = field(default_factory=list)

    @property
    def passed(self) -> bool:
        return len(self.errors) == 0

    @property
    def has_warnings(self) -> bool:
        return len(self.warnings) > 0

    def add_error(self, msg: str):
        self.errors.append(msg)
        print(f"  ❌ {msg}")

    def add_warning(self, msg: str):
        self.warnings.append(msg)
        print(f"  ⚠️  {msg}")

    def add_info(self, msg: str):
        self.infos.append(msg)
        print(f"  ℹ️  {msg}")


# ---------------------------------------------------------------------------
# 状态引用归一化
#
# 契约里的 from/to 有四种合法写法，本脚本必须全部支持，否则会直接崩溃：
#   1. 标量            from: CONFIRMED
#   2. 多源/多目标列表   from: [PENDING, CLASSIFIED]   to: [VOUCHER_GENERATED, PAYMENT_CREATED]
#   3. 通配            from: "*"     （任意状态）
#   4. 语义通配         from: ANY_NON_TERMINAL
#   另：列表里可含 null，表示「由无到有」的创建型转移（如 from: [null, PENDING]），
#       此时 null 视为「无前驱」，跳过校验。
#
# 背景：多源转移正是 AGENTS §4.2 第 19 条所要求的形态 ——
# 「任何『待人工决策』态都必须同时是 confirm 和 reject 的合法输入」，
# 即一个 trigger 的合法 from 往往不止一个状态。
# ---------------------------------------------------------------------------
WILDCARD_STATES = frozenset({'*', 'ANY_NON_TERMINAL', 'ANY'})


def normalize_states(value) -> list:
    """把契约里的 from/to 值归一化为状态名列表（过滤 None 与空串）。"""
    if value is None:
        return []
    if isinstance(value, str):
        return [value] if value.strip() else []
    if isinstance(value, (list, tuple, set)):
        out = []
        for item in value:
            out.extend(normalize_states(item))
        return out
    return [str(value)]


def is_wildcard(state: str) -> bool:
    return state in WILDCARD_STATES


def transition_state_key(value) -> tuple:
    """给 trigger 唯一性检查用的可哈希 key：列表归一化为排序后的元组。"""
    states = sorted(normalize_states(value))
    if not states or all(is_wildcard(s) for s in states):
        return ('<ANY>',)
    return tuple(states)


def normalize_states_schema(states) -> dict:
    """把 states 归一化为 {STATE_NAME: props} 映射。

    契约里 states 有两种合法写法，本脚本必须都支持：
      A. 映射式   states: { DRAFT: { terminal: false }, ... }
      B. 列表式   states: [ { name: DRAFT, terminal: false, ... }, ... ]
    列表式（B）携带 label/initial/description 等更丰富信息，是较新的写法。
    """
    if isinstance(states, dict):
        return {k: (v if isinstance(v, dict) else {}) for k, v in states.items()}
    if isinstance(states, (list, tuple)):
        out = {}
        for item in states:
            if isinstance(item, dict) and item.get('name'):
                props = {k: v for k, v in item.items() if k != 'name'}
                out[item['name']] = props
            elif isinstance(item, str):
                out[item] = {}
        return out
    return {}


def as_text(value) -> str:
    """把任意 YAML 标量安全转成字符串（dict/list 等只取 repr，避免 re.match 崩溃）。"""
    if isinstance(value, str):
        return value
    if value is None:
        return ''
    return str(value)


def contract_kind(contract: dict) -> str:
    """契约分两类，校验模板不同，必须分派：
      - state_machine：有 states/transitions（发票/流水/单据状态机）
      - rules        ：有 rules（如 P37 凭证类型映射规则，本就无状态可言）
    """
    if contract.get('states') or contract.get('transitions'):
        return 'state_machine'
    if contract.get('rules'):
        return 'rules'
    return 'unknown'


def validate_rules(contract: dict, result: ValidationResult):
    """规则型契约校验：rules 条目结构 + acceptance_tests。"""
    rules = contract.get('rules')
    if not isinstance(rules, (list, tuple)) or not rules:
        result.add_error("'rules' must be a non-empty list for a rules-type contract")
        return

    ids = []
    for r in rules:
        if not isinstance(r, dict):
            result.add_warning(f"Rule entry is not a mapping, skipped: {r!r}")
            continue
        rid = as_text(r.get('id', '?'))
        ids.append(rid)
        if not rid or rid == '?':
            result.add_error("Rule without 'id'")
        if not r.get('source'):
            result.add_warning(f"Rule {rid}: no 'source' declared")
        if not r.get('mapping'):
            result.add_warning(f"Rule {rid}: no 'mapping' declared")
        if not r.get('implementation'):
            result.add_warning(f"Rule {rid}: no 'implementation' declared (无法反查代码)")
        elif not isinstance(r.get('mapping'), dict):
            result.add_error(f"Rule {rid}: 'mapping' must be a mapping")

    dup = {i for i in ids if ids.count(i) > 1}
    if dup:
        result.add_error(f"Duplicate rule IDs: {sorted(dup)}")

    validate_acceptance_tests(contract, result)


def validate_structure(contract: dict, result: ValidationResult):
    """Validate YAML structure: required fields, types, ID formats."""
    # Required fields
    if contract.get('contract_version') != '1.0':
        result.add_error(f"contract_version must be '1.0', got '{contract.get('contract_version')}'")

    states = normalize_states_schema(contract.get('states'))
    if not states:
        result.add_error("No 'states' defined — contract is meaningless without states")
        return

    # Check state IDs are valid Java identifiers
    for state_name in states:
        if not re.match(r'^[A-Z][A-Z0-9_]*$', as_text(state_name)):
            result.add_warning(f"State '{state_name}' doesn't match PascalCase convention (should match InvoiceStatus constant)")

    transitions = contract.get('transitions', [])
    if not isinstance(transitions, (list, tuple)):
        result.add_error(f"'transitions' must be a list, got {type(transitions).__name__}")
        return
    ids_in_contract: list = []
    for t in transitions:
        if not isinstance(t, dict):
            result.add_warning(f"Transition entry is not a mapping, skipped: {t!r}")
            continue
        tid = as_text(t.get('id', '?'))
        # 两种合法 ID 风格并存：
        #   T-NN       —— P23 等按编号组织的契约
        #   语义式 snake_case —— P40 的 confirm/reject/void_confirmed 等
        # 全库无任何 `transition: T-NN` 交叉引用（grep 实测 0 命中），故两种都接受；
        # 单份契约内混用才值得提醒。
        if re.match(r'^T-\d+$', tid) or re.match(r'^[a-z][a-z0-9_]*$', tid):
            pass
        else:
            result.add_error(f"Transition ID '{tid}' must be 'T-NN' or snake_case (e.g. 'confirm')")
        ids_in_contract.append('T' if re.match(r'^T-\d+$', tid) else 'S')

        # from/to must reference defined states
        # 兼容标量 / 列表 / 通配（见 normalize_states 说明）
        from_states = normalize_states(t.get('from'))
        to_states = normalize_states(t.get('to'))

        if not from_states:
            result.add_warning(f"Transition {tid}: no 'from' state defined")
        for fs in from_states:
            if is_wildcard(fs):
                continue
            if fs not in states:
                result.add_error(f"Transition {tid}: 'from' state '{fs}' not defined in states")

        if not to_states:
            result.add_warning(f"Transition {tid}: no 'to' state defined")
        for ts in to_states:
            if is_wildcard(ts):
                continue
            if ts.startswith('('):
                # Special: (new X) creation transition — OK
                continue
            if ts not in states:
                result.add_error(f"Transition {tid}: 'to' state '{ts}' not defined in states")

    if ids_in_contract and len(set(ids_in_contract)) > 1:
        result.add_warning(
            "Transitions mix two ID styles ('T-NN' and snake_case) — pick one for this contract")

    # Check for duplicate transition IDs
    ids = [as_text(t.get('id')) for t in transitions if isinstance(t, dict)]
    if len(ids) != len(set(ids)):
        result.add_error(f"Duplicate transition IDs: {[i for i in ids if ids.count(i) > 1]}")


def validate_state_machine_logic(contract: dict, result: ValidationResult):
    """Validate business rules: terminal states can't have outgoing transitions."""
    states = normalize_states_schema(contract.get('states'))
    transitions = [t for t in (contract.get('transitions') or []) if isinstance(t, dict)]

    # Build set of terminal states
    terminal_states = {name for name, props in states.items() if props.get('terminal', False)}

    if not terminal_states:
        result.add_warning("No terminal states defined — consider marking absorbing states (VOIDED, REVERSED, FULLY_RECONCILED)")

    # Check no transition FROM a terminal state
    for t in transitions:
        for from_state in normalize_states(t.get('from')):
            if from_state in terminal_states:
                result.add_error(
                    f"Transition {as_text(t.get('id'))}: cannot transition FROM terminal state '{from_state}'")

    # Check each non-terminal state has at least one outgoing transition
    states_with_outgoing = set()
    for t in transitions:
        for from_state in normalize_states(t.get('from')):
            if not is_wildcard(from_state):
                states_with_outgoing.add(from_state)

    for state_name in states:
        if not states[state_name].get('terminal', False) and state_name not in states_with_outgoing:
            result.add_warning(f"State '{state_name}' has no outgoing transitions — is it reachable?")


def validate_trigger_uniqueness(contract: dict, result: ValidationResult):
    """Each trigger method should only map to one transition per 'from' state."""
    transitions = [t for t in (contract.get('transitions') or []) if isinstance(t, dict)]
    trigger_map = {}  # (from_state_tuple, trigger) -> [transition_ids]

    for t in transitions:
        trigger = as_text(t.get('trigger'))
        key = (transition_state_key(t.get('from')), trigger)
        trigger_map.setdefault(key, []).append(as_text(t.get('id')))

    for (state, trigger), tids in trigger_map.items():
        if len(tids) > 1:
            result.add_warning(
                f"Trigger '{trigger}' from state {list(state)} has {len(tids)} transitions: {tids}. "
                f"This may be intentional (e.g., different preconditions) but should be verified."
            )


def validate_acceptance_tests(contract: dict, result: ValidationResult):
    """Check acceptance tests reference valid transition IDs and have proper status."""
    transitions = contract.get('transitions', [])
    tests = contract.get('acceptance_tests', [])

    # Build transition lookup
    trans_by_id = {t['id']: t for t in transitions}

    for test in tests:
        tid = as_text(test.get('id', '?'))
        status = as_text(test.get('status', ''))
        if status and status not in ('covered', 'partial', 'missing'):
            result.add_warning(f"Test {tid}: unknown status '{status}', expected covered/partial/missing")

        if status == 'missing':
            result.add_warning(f"Test {tid}: marked as 'missing' — test not yet implemented")

    # Check for duplicate test IDs
    test_ids = [as_text(t.get('id')) for t in tests if isinstance(t, dict)]
    if len(test_ids) != len(set(test_ids)):
        result.add_error(f"Duplicate acceptance test IDs: {[i for i in test_ids if test_ids.count(i) > 1]}")


def check_rule_implementations(contract: dict, project_root: str, result: ValidationResult):
    """规则型契约：反查每条 rule 的 implementation 类在 backend 里是否存在。"""
    rules = [r for r in (contract.get('rules') or []) if isinstance(r, dict)]
    main_src = Path(project_root) / "backend" / "src" / "main" / "java"

    # 类名 -> 文件路径，一次建索引比逐条 glob 快
    index = {}
    if main_src.exists():
        for f in main_src.rglob("*.java"):
            index.setdefault(f.stem, f)

    for r in rules:
        rid = as_text(r.get('id', '?'))
        impl = as_text(r.get('implementation')).strip()
        if not impl:
            continue
        # "AutoGenerationService.resolveVoucherType()" -> "AutoGenerationService"
        # "ArapSettlementServiceImpl"                -> "ArapSettlementServiceImpl"
        cls = re.split(r'[.(]', impl)[0].strip()
        if not cls:
            result.add_warning(f"Rule {rid}: cannot parse class name from implementation '{impl}'")
            continue
        if cls not in index:
            result.add_warning(f"Rule {rid}: implementation class '{cls}' not found under backend/src/main/java")
        else:
            result.add_info(f"Rule {rid}: '{cls}' found ✓")

        # 形如 Class.method() 的还要确认方法存在
        m = re.match(r'^([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_]*)\(\)$', impl)
        if m and m.group(1) in index:
            body = index[m.group(1)].read_text(encoding="utf-8")
            if not re.search(r'\b' + re.escape(m.group(2)) + r'\s*\(', body):
                result.add_warning(f"Rule {rid}: method '{m.group(2)}()' not found in {m.group(1)}")


def check_code_implementation(contract: dict, project_root: str, result: ValidationResult):
    """Check if transition trigger methods exist in the state machine implementation."""
    entity = contract.get('entity', '')
    module = contract.get('module', '')
    transitions = contract.get('transitions', [])

    # Build expected service class name from entity
    # Entity: OutputInvoiceEntity → Service: OutputInvoiceStateMachineService
    # Heuristic: strip "Entity" suffix, append "StateMachineService"
    service_base = entity.replace('Entity', '') if entity.endswith('Entity') else entity
    service_name = f"{service_base}StateMachineService"

    # Search for implementation file
    impl_pattern = Path(project_root) / "backend" / "src" / "main" / "java" / "com" / "huicai" / "module" / module / "service" / "impl"
    impl_file = impl_pattern / f"{service_name}Impl.java"

    # Fallback: try direct entity name + Impl
    if not impl_file.exists():
        impl_file = impl_pattern / f"{entity.replace('Entity', 'ServiceImpl')}.java"

    # Fallback 2: 包结构猜不中时（全仓按类名定位）。
    # 原实现只按 module 拼 com/huicai/module/<module>/service/impl/ 单一路径，
    # 而本仓库实际布局是 com/huicai/<domain>/<module>/service/impl/
    # （例：VoucherServiceImpl 在 base/voucher 而非 module/finance）
    # 候选类名收集（**不再因猜不中而 return**，后面还有契约内显式 implementation 兜底）
    java_root = Path(project_root) / "backend" / "src" / "main" / "java"
    # entity 声明方式有两种，候选名需都覆盖：
    #   ArapSettlementEntity → ArapSettlementServiceImpl（replace('Entity','ServiceImpl') 有效）
    #   ArapSettlement       → ArapSettlementServiceImpl（**replace 是空操作**，原实现在此失效）
    base = entity[:-6] if entity.endswith('Entity') else entity
    cand_names = [f"{service_name}Impl.java", f"{base}ServiceImpl.java"]
    impl_files = []
    for cand_name in cand_names:
        impl_files.extend(list(java_root.rglob(cand_name)) if java_root.exists() else [])
    if impl_file.exists() and impl_file not in impl_files:
        impl_files.insert(0, impl_file)

    # 优先使用契约里显式声明的 implementation 类名（transition 级 implementation 字段
    # 形如 "ArapSettlementServiceImpl.approve()"，取其类名部分）。
    # 原实现只靠 entity 名猜类名，而 entity 声明方式有两种：
    #   ArapSettlementEntity → ArapSettlementServiceImpl   （replace('Entity','ServiceImpl') 有效）
    #   ArapSettlement       → ArapSettlementServiceImpl   （**replace 是空操作**，原实现失效）
    declared = set()
    for t in transitions:
        raw = t.get('implementation', '')
        if not isinstance(raw, str):
            continue
        first = raw.strip().split('(')[0].split('.')[0].strip()
        if first and first[0].isupper():
            declared.add(f"{first}.java")
    if declared:
        java_root = Path(project_root) / "backend" / "src" / "main" / "java"
        for cand_name in sorted(declared):
            hits = list(java_root.rglob(cand_name)) if java_root.exists() else []
            if hits:
                impl_files.append(hits[0])
        if impl_files:
            result.add_info("Implementation resolved from contract 'implementation' field: "
                            + ", ".join(f.name for f in impl_files))

    impl_content = "\n".join(f.read_text(encoding="utf-8") for f in impl_files)
    if not impl_files:
        result.add_info("No implementation file resolved; trigger verification skipped")

    # Check each trigger method exists
    for t in transitions:
        trigger = t.get('trigger', '')
        tid = t.get('id', '?')
        if trigger and trigger not in impl_content:
            result.add_warning(f"Transition {tid}: trigger method '{trigger}' not found in implementation")
        elif trigger:
            result.add_info(f"Transition {tid}: trigger '{trigger}' found in implementation ✓")

    # Also check the interface
    interface_file = impl_pattern.parent / f"{service_name}.java"
    if interface_file.exists():
        interface_content = interface_file.read_text(encoding="utf-8")
        for t in transitions:
            trigger = t.get('trigger', '')
            tid = t.get('id', '?')
            if trigger and trigger not in interface_content:
                result.add_warning(f"Transition {tid}: trigger '{trigger}' not declared in interface {service_name}")


def check_test_coverage(contract: dict, project_root: str, result: ValidationResult):
    """Check if referenced test methods exist in the test file."""
    entity = contract.get('entity', '')
    module = contract.get('module', '')
    transitions = contract.get('transitions', [])
    tests = contract.get('acceptance_tests', [])

    # Build test class name from entity
    service_base = entity.replace('Entity', '') if entity.endswith('Entity') else entity
    test_class_name = f"{service_base}StateMachineServiceImplTest"

    # Find test file
    test_pattern = Path(project_root) / "backend" / "src" / "test" / "java" / "com" / "huicai" / "module" / module / "service" / "impl"
    test_file = test_pattern / f"{test_class_name}.java"

    # Fallback: try with entity name
    if not test_file.exists():
        test_file = test_pattern / f"{entity.replace('Entity', 'ServiceImpl')}Test.java"

    if not test_file.exists():
        result.add_warning(f"Test file not found, searched: {test_file}")
        # List what we found in the directory
        if test_pattern.exists():
            found = list(test_pattern.glob("*Test*.java"))
            if found:
                result.add_info(f"Available test files: {[f.name for f in found]}")
        return

    test_content = test_file.read_text(encoding="utf-8")

    # Check transition test_refs
    for t in transitions:
        test_ref = t.get('test_ref', '')
        tid = t.get('id', '?')
        if test_ref and test_ref not in test_content:
            result.add_warning(f"Transition {tid}: test method '{test_ref}' not found in test file")

    # Check acceptance test methods
    for at in tests:
        method = at.get('method', '')
        aid = at.get('id', '?')
        if method and method != 'n/a' and method not in test_content:
            result.add_warning(f"Acceptance test {aid}: method '{method}' not found in test file")

    # Count actual test methods
    test_count = len(re.findall(r'@DisplayName\(".*?"\)', test_content))
    result.add_info(f"Test file has {test_count} @DisplayName-annotated test methods")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="SPEC Contract Validator")
    parser.add_argument("--path", help="Path to SPEC markdown file")
    parser.add_argument("--dir", help="Directory containing SPEC files (batch mode)")
    parser.add_argument("--check-implementation", action="store_true", help="Cross-reference with code")
    parser.add_argument("--check-tests", action="store_true", help="Cross-reference with tests")
    parser.add_argument("--strict", action="store_true", help="Fail on warnings too")
    parser.add_argument("--require-contract", action="store_true",
                        help="Fail when a SPEC has no machine-readable contract (strict coverage mode)")
    # 项目根默认由本文件位置推导（scripts/ 的上一级），不再硬编码绝对路径。
    # 原默认 "/root/data/disk/huicai" 是他人机器的残留，在任何其它 checkout 上
    # --check-implementation 都会直接 PermissionError 崩溃 —— 门禁因环境而崩，
    # 比不跑更糟（AGENTS §4.5 第 21 条：门禁必须可执行且能变红）。
    _default_root = Path(__file__).resolve().parent.parent
    parser.add_argument("--project-root", default=str(_default_root),
                        help="Project root directory (default: 仓库根目录，由本脚本位置推导)")

    args = parser.parse_args()

    if not args.path and not args.dir:
        parser.error("Must specify --path or --dir")

    files_to_check = []
    if args.path:
        files_to_check = [args.path]
    elif args.dir:
        dir_path = Path(args.dir)
        files_to_check = [str(f) for f in dir_path.glob("P*-*.md")]

    all_passed = True
    missing_count = 0
    broken_count = 0

    for spec_path in files_to_check:
        print(f"\n{'='*60}")
        print(f"VALIDATING: {spec_path}")
        print(f"{'='*60}")

        contract = extract_yaml_contract(spec_path)
        if contract is CONTRACT_MISSING:
            # 策略：无契约 = 覆盖率警告，不判失败（否则存量 91/96 会让门禁恒红，
            # 红灯被当噪音，真实回归反而被淹没）。
            missing_count += 1
            print("  ⚠️  skipped — no machine-readable contract (coverage gap, not a regression)")
            continue
        if contract is None:
            # 写了契约但 YAML 坏 / 结构非映射 → 真回归，必须失败
            broken_count += 1
            all_passed = False
            continue

        result = ValidationResult()
        n_states = len(normalize_states_schema(contract.get('states')))
        result.add_info(f"Contract version: {contract.get('contract_version')}")
        result.add_info(f"Entity: {contract.get('entity')}")
        result.add_info(f"States defined: {n_states}")
        result.add_info(f"Transitions defined: {len(contract.get('transitions') or [])}")
        result.add_info(f"Acceptance tests defined: {len(contract.get('acceptance_tests') or [])}")

        # 单文件异常隔离：单份 SPEC 的格式问题不得中断整批校验，
        # 否则一个坏文件会让后续所有 SPEC 都被跳过（门禁失去意义）。
        try:
            kind = contract_kind(contract)
            result.add_info(f"Contract kind: {kind}")

            # Structural validation (always run)
            if kind == 'rules':
                validate_rules(contract, result)
                if args.check_implementation:
                    print(f"\n  → Checking rule implementations...")
                    check_rule_implementations(contract, args.project_root, result)
            elif kind == 'state_machine':
                validate_structure(contract, result)
                validate_state_machine_logic(contract, result)
                validate_trigger_uniqueness(contract, result)
                validate_acceptance_tests(contract, result)

                # Implementation check (optional) — 状态机专属
                if args.check_implementation:
                    print(f"\n  → Checking code implementation...")
                    check_code_implementation(contract, args.project_root, result)

                # Test coverage check (optional) — 状态机专属
                if args.check_tests:
                    print(f"\n  → Checking test coverage...")
                    check_test_coverage(contract, args.project_root, result)
            else:
                # 既无 states/transitions 也无 rules —— 契约无实质内容
                result.add_error("Contract has neither 'states/transitions' nor 'rules' — nothing to validate")
        except Exception as exc:  # noqa: BLE001 - 门禁必须报出问题而不是崩掉
            result.add_error(f"Validator crashed on this contract: {type(exc).__name__}: {exc}")

        # Summary
        print(f"\n{'─'*60}")
        if result.errors:
            print(f"  ERRORS: {len(result.errors)}")
            all_passed = False
        if result.warnings:
            print(f"  WARNINGS: {len(result.warnings)}")
            if args.strict:
                all_passed = False
        if result.infos:
            print(f"  INFO: {len(result.infos)}")
        if not result.errors and not result.warnings:
            print(f"  ✅ ALL CHECKS PASSED")
        print(f"{'─'*60}")

    # 收尾汇总：覆盖率与失败数分开报，避免「91 个缺契约」把真实回归淹掉
    total = len(files_to_check)
    validated = total - missing_count - broken_count
    print(f"\n{'='*60}")
    print(f"SUMMARY: total={total}  validated={validated}  "
          f"no_contract={missing_count}  broken={broken_count}")
    if missing_count:
        print(f"  ⚠️  {missing_count}/{total} SPEC 尚无机器可读契约（覆盖率 "
              f"{validated}/{total}）—— 默认不判失败；"
              f"补齐后可加 --require-contract 转为强制")
    if args.require_contract and missing_count:
        print(f"  ❌ --require-contract: {missing_count} SPEC 缺契约")
        all_passed = False
    if all_passed and not broken_count:
        print("  ✅ SPEC contract gate PASSED")
    else:
        print("  ❌ SPEC contract gate FAILED")
    print(f"{'='*60}")

    sys.exit(0 if all_passed else 1)


if __name__ == "__main__":
    main()
