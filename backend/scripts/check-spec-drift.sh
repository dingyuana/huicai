#!/usr/bin/env bash
# SPEC 漂移检测脚本
# 检查：当业务代码（Service/Controller）变更时，验证关联的 SPEC 文件是否也在同一次提交中更新
# 否则给出警告，提示开发者确认 SPEC 是否需要同步更新
#
# 使用方式：添加到 pre-commit hook 末尾，或直接运行
# 参考：AGENTS.md §4.3 SPEC 漂移检测

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
DOCS_SPEC_DIR="$ROOT_DIR/docs/specs"
BACKEND_DIR="$ROOT_DIR/backend/src/main/java"

# 仅当提交涉及后端 Service/Controller/Entity 时才触发
CHANGED=$(git diff --cached --name-only --diff-filter=ACM | grep -E "backend/src/main/java/.+/(service|controller|entity)/" || true)
if [ -z "$CHANGED" ]; then
  exit 0
fi

# 统计变更文件
COUNT=$(echo "$CHANGED" | wc -l)

echo ""
echo "🔍 检测到后端业务代码变更（$COUNT 个文件），请确认关联 SPEC 是否同步更新："
echo "$CHANGED" | sed 's|^|  - |'

echo ""
echo "⚠️  SPEC 漂移检查规则："
echo "   1. 如果本次修改涉及状态流转逻辑 → 对应 SPEC 的 state 定义必须同步"
echo "   2. 如果本次修改涉及 API 路径/参数 → 对应 SPEC 的 API 定义必须同步"
echo "   3. 如果本次修改涉及新增业务规则 → 对应 SPEC 的 rules 必须同步"
echo "   4. 如果本次修改不涉及以上三类 → 在 commit message 中说明"
echo ""
echo "参考命令：git diff --cached docs/specs/ 查看本次是否含 SPEC 变更"
echo ""