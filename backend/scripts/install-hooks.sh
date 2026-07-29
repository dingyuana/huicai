#!/usr/bin/env bash
# 安装 git hooks（H-17 修复配套脚本）
# 用法：bash backend/scripts/install-hooks.sh
#
# 将 pre-commit hook 复制到 .git/hooks/ 并赋予执行权限。
# 本脚本可重复执行。

set -e

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HOOKS_DIR="$REPO_ROOT/.git/hooks"
SOURCE_HOOK="$REPO_ROOT/.git/hooks/pre-commit"

# 如果 source hook 不存在（首次克隆仓库），从模板创建
if [ ! -f "$SOURCE_HOOK" ]; then
  cat > "$SOURCE_HOOK" <<'HOOK_EOF'
#!/usr/bin/env bash
# Pre-commit hook: Entity-DB schema 一致性检查 + JdbcTemplate SQL 表名审计 + 自定义 SQL 列引用检查 (H-17 修复)
set -e
SCRIPT="backend/scripts/check-entity-schema.mjs"
if [ ! -f "$SCRIPT" ]; then exit 0; fi

# 检查变更文件：Entity / Mapper / Controller / Service 文件
CHANGED_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep -E 'backend/src/main/java/.*(Entity\.java|Mapper\.java|Controller\.java|Service\.java|ServiceImpl\.java)$|backend/src/main/resources/.*Mapper\.xml$' || true)
if [ -z "$CHANGED_FILES" ]; then exit 0; fi

echo "🔍 检测到 Entity/Mapper/Controller/Service 文件变更，运行 schema 一致性和 SQL 列引用审计..."
if ! command -v node >/dev/null 2>&1; then echo "⚠️  node 不可用，跳过"; exit 0; fi
node "$SCRIPT"
RESULT=$?
if [ $RESULT -eq 0 ]; then echo "✅ 检查通过"; else
  echo "❌ 检查失败，请修复后再提交（或 git commit --no-verify 跳过）"
  exit 1
fi
HOOK_EOF
fi

chmod +x "$SOURCE_HOOK"
echo "✅ pre-commit hook 已安装到 $SOURCE_HOOK"
echo "   功能：提交涉及 Entity/Mapper/Controller/Service 文件变更时自动运行 check-entity-schema.mjs"
echo "   降级：docker postgres 未运行时自动跳过，仅做 typeHandler 警告"
