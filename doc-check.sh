#!/bin/bash
# 文档一致性检测 + API 契约检查
cd "$(dirname "$0")"
echo "=== 文档一致性检测 ==="
python3 scripts/check-doc-consistency.py "$@"
echo ""
echo "=== API 参数契约检查 ==="
node scripts/api-contract-check.mjs
