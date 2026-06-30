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
        return None

    # Extract YAML block (look for fenced code block or plain YAML)
    yaml_text = content[idx:]

    # Try fenced code block first
    fence_match = re.search(r'```yaml\s*\n(.*?)```', yaml_text, re.DOTALL)
    if fence_match:
        yaml_text = fence_match.group(1)
    else:
        # Plain YAML after marker — skip comment lines and blank lines until
        # we hit a line that looks like a YAML key (word followed by colon).
        lines = yaml_text.split('\n')
        yaml_lines = []
        started = False
        for line in lines[1:]:  # skip marker line
            stripped = line.strip()
            if not started:
                # Skip comments (> ...) and blank lines until we find real YAML
                if stripped == '' or stripped.startswith('> ') or stripped.startswith('#'):
                    continue
                started = True
            if started:
                yaml_lines.append(line)
        yaml_text = '\n'.join(yaml_lines)

    try:
        contract = yaml.safe_load(yaml_text)
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


def validate_structure(contract: dict, result: ValidationResult):
    """Validate YAML structure: required fields, types, ID formats."""
    # Required fields
    if contract.get('contract_version') != '1.0':
        result.add_error(f"contract_version must be '1.0', got '{contract.get('contract_version')}'")

    states = contract.get('states', {})
    if not states:
        result.add_error("No 'states' defined — contract is meaningless without states")
        return

    # Check state IDs are valid Java identifiers
    for state_name in states:
        if not re.match(r'^[A-Z][A-Z0-9_]*$', state_name):
            result.add_warning(f"State '{state_name}' doesn't match PascalCase convention (should match InvoiceStatus constant)")

    transitions = contract.get('transitions', [])
    for t in transitions:
        tid = t.get('id', '?')
        if not re.match(r'^T-\d+$', tid):
            result.add_error(f"Transition ID '{tid}' doesn't match T-NN format")

        # from/to must reference defined states
        from_state = t.get('from', '')
        to_state = t.get('to', '')
        if from_state != 'ANY_NON_TERMINAL' and from_state not in states:
            result.add_error(f"Transition {tid}: 'from' state '{from_state}' not defined in states")
        if to_state not in states and to_state.startswith('('):
            # Special: (new X) creation transition — OK
            pass
        elif to_state not in states:
            result.add_error(f"Transition {tid}: 'to' state '{to_state}' not defined in states")

    # Check for duplicate transition IDs
    ids = [t['id'] for t in transitions]
    if len(ids) != len(set(ids)):
        result.add_error(f"Duplicate transition IDs: {[i for i in ids if ids.count(i) > 1]}")


def validate_state_machine_logic(contract: dict, result: ValidationResult):
    """Validate business rules: terminal states can't have outgoing transitions."""
    states = contract.get('states', {})
    transitions = contract.get('transitions', [])

    # Build set of terminal states
    terminal_states = {name for name, props in states.items() if props.get('terminal', False)}

    if not terminal_states:
        result.add_warning("No terminal states defined — consider marking absorbing states (VOIDED, REVERSED, FULLY_RECONCILED)")

    # Check no transition FROM a terminal state
    for t in transitions:
        from_state = t.get('from', '')
        if from_state in terminal_states:
            result.add_error(f"Transition {t['id']}: cannot transition FROM terminal state '{from_state}'")

    # Check each non-terminal state has at least one outgoing transition
    states_with_outgoing = set()
    for t in transitions:
        from_state = t.get('from', '')
        if from_state != 'ANY_NON_TERMINAL':
            states_with_outgoing.add(from_state)

    for state_name in states:
        if not states[state_name].get('terminal', False) and state_name not in states_with_outgoing:
            result.add_warning(f"State '{state_name}' has no outgoing transitions — is it reachable?")


def validate_trigger_uniqueness(contract: dict, result: ValidationResult):
    """Each trigger method should only map to one transition per 'from' state."""
    transitions = contract.get('transitions', [])
    trigger_map = {}  # (from_state, trigger) -> [transition_ids]

    for t in transitions:
        from_state = t.get('from', '')
        trigger = t.get('trigger', '')
        if from_state == 'ANY_NON_TERMINAL':
            continue
        key = (from_state, trigger)
        trigger_map.setdefault(key, []).append(t['id'])

    for (state, trigger), tids in trigger_map.items():
        if len(tids) > 1:
            result.add_warning(
                f"Trigger '{trigger}' from state '{state}' has {len(tids)} transitions: {tids}. "
                f"This may be intentional (e.g., different preconditions) but should be verified."
            )


def validate_acceptance_tests(contract: dict, result: ValidationResult):
    """Check acceptance tests reference valid transition IDs and have proper status."""
    transitions = contract.get('transitions', [])
    tests = contract.get('acceptance_tests', [])

    # Build transition lookup
    trans_by_id = {t['id']: t for t in transitions}

    for test in tests:
        tid = test.get('id', '?')
        status = test.get('status', '')
        if status and status not in ('covered', 'partial', 'missing'):
            result.add_warning(f"Test {tid}: unknown status '{status}', expected covered/partial/missing")

        if status == 'missing':
            result.add_warning(f"Test {tid}: marked as 'missing' — test not yet implemented")

    # Check for duplicate test IDs
    test_ids = [t['id'] for t in tests]
    if len(test_ids) != len(set(test_ids)):
        result.add_error(f"Duplicate acceptance test IDs: {[i for i in test_ids if test_ids.count(i) > 1]}")


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

    if not impl_file.exists():
        result.add_warning(f"Implementation file not found, searched: {impl_file}")
        return

    impl_content = impl_file.read_text(encoding="utf-8")

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
    parser.add_argument("--project-root", default="/root/data/disk/huicai", help="Project root directory")

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

    for spec_path in files_to_check:
        print(f"\n{'='*60}")
        print(f"VALIDATING: {spec_path}")
        print(f"{'='*60}")

        contract = extract_yaml_contract(spec_path)
        if contract is None:
            all_passed = False
            continue

        result = ValidationResult()
        result.add_info(f"Contract version: {contract.get('contract_version')}")
        result.add_info(f"Entity: {contract.get('entity')}")
        result.add_info(f"States defined: {len(contract.get('states', {}))}")
        result.add_info(f"Transitions defined: {len(contract.get('transitions', []))}")
        result.add_info(f"Acceptance tests defined: {len(contract.get('acceptance_tests', []))}")

        # Structural validation (always run)
        validate_structure(contract, result)
        validate_state_machine_logic(contract, result)
        validate_trigger_uniqueness(contract, result)
        validate_acceptance_tests(contract, result)

        # Implementation check (optional)
        if args.check_implementation:
            print(f"\n  → Checking code implementation...")
            check_code_implementation(contract, args.project_root, result)

        # Test coverage check (optional)
        if args.check_tests:
            print(f"\n  → Checking test coverage...")
            check_test_coverage(contract, args.project_root, result)

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

    sys.exit(0 if all_passed else 1)


if __name__ == "__main__":
    main()
