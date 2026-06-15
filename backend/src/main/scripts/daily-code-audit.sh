#!/bin/bash
# Daily Code Audit Script
# 执行代码审核并生成报告

# 设置变量
PROJECT_DIR="/workspace"
REPORT_DIR="/workspace/docs/reports"
DATE=$(date +%Y-%m-%d)
REPORT_FILE="${REPORT_DIR}/code-audit-report-${DATE}.md"

# 确保报告目录存在
mkdir -p "${REPORT_DIR}"

# 开始生成报告
{
    echo "# 代码审核报告 - ${DATE}"
    echo ""
    echo "## 审核时间"
    echo "\`$(date '+%Y-%m-%d %H:%M:%S')\`"
    echo ""
    echo "## 项目路径"
    echo "\`${PROJECT_DIR}\`"
    echo ""

    # 检查 Git 状态
    echo "## Git 状态"
    cd "${PROJECT_DIR}" || exit 1
    if git rev-parse --git-dir > /dev/null 2>&1; then
        echo "\`\`\`"
        git status --short
        echo "\`\`\`"
        echo ""
        echo "### 最近提交 (最近5条)"
        echo "\`\`\`"
        git log --oneline -5
        echo "\`\`\`"
    else
        echo "*非 Git 项目*"
    fi
    echo ""

    # 代码统计
    echo "## 代码统计"
    echo "- Java 文件数: \`$(find "${PROJECT_DIR}/backend/src/main/java" -name "*.java" 2>/dev/null | wc -l)\`"
    echo "- JavaScript/TypeScript 文件数: \`$(find "${PROJECT_DIR}/frontend/src" -name "*.ts" -o -name "*.vue" 2>/dev/null | wc -l)\`"
    echo ""

    # 检查 TODO 和 FIXME
    echo "## 待办事项 (TODO/FIXME)"
    echo "\`\`\`"
    grep -rn --include="*.java" --include="*.ts" --include="*.vue" "TODO\|FIXME\|XXX" "${PROJECT_DIR}" 2>/dev/null | head -20 || echo "未发现"
    echo "\`\`\`"
    echo ""

    # 检查测试覆盖
    echo "## 测试文件检查"
    echo "- Backend 测试类数: \`$(find "${PROJECT_DIR}/backend/src/test" -name "*Test.java" 2>/dev/null | wc -l)\`"
    echo ""

    # 报告完成
    echo "## 审核完成"
    echo "报告生成时间: \`$(date '+%Y-%m-%d %H:%M:%S')\`"
} > "${REPORT_FILE}"

echo "代码审核报告已生成: ${REPORT_FILE}"
