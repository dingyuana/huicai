#!/bin/bash
# Daily Code Audit Script
# Runs code quality checks and generates report

REPORT_DIR="/workspace/docs/reports"
PROJECT_DIR="/workspace"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="${REPORT_DIR}/code_audit_${TIMESTAMP}.txt"

# Ensure report directory exists
mkdir -p "${REPORT_DIR}"

{
    echo "========================================="
    echo "代码审核报告 - $(date '+%Y-%m-%d %H:%M:%S')"
    echo "========================================="
    echo ""

    echo "--- 项目结构 ---"
    ls -la "${PROJECT_DIR}"
    echo ""

    echo "--- 代码统计 ---"
    echo "Java文件数量: $(find ${PROJECT_DIR} -name "*.java" 2>/dev/null | wc -l)"
    echo "Go文件数量: $(find ${PROJECT_DIR} -name "*.go" 2>/dev/null | wc -l)"
    echo "Python文件数量: $(find ${PROJECT_DIR} -name "*.py" 2>/dev/null | wc -l)"
    echo "JavaScript文件数量: $(find ${PROJECT_DIR} -name "*.js" 2>/dev/null | wc -l)"
    echo "TypeScript文件数量: $(find ${PROJECT_DIR} -name "*.ts" 2>/dev/null | wc -l)"
    echo ""

    echo "--- Git状态 ---"
    cd "${PROJECT_DIR}" && git status --short 2>/dev/null || echo "Not a git repository"
    echo ""

    echo "--- 未提交的更改 ---"
    cd "${PROJECT_DIR}" && git diff --stat 2>/dev/null || echo "N/A"
    echo ""

    echo "========================================="
    echo "审核完成"
    echo "========================================="
} > "${REPORT_FILE}"

echo "Report generated: ${REPORT_FILE}"
