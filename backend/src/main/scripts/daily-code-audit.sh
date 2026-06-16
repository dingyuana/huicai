#!/bin/bash
# 每日代码审核脚本
# 用途：对项目执行代码质量检查，生成审核报告

PROJECT_DIR="/workspace"
REPORT_DIR="/workspace/docs/reports"
DATE=$(date +%Y-%m-%d)
REPORT_FILE="${REPORT_DIR}/code-audit-${DATE}.txt"

# 确保 reports 目录存在
mkdir -p "${REPORT_DIR}"

# 开始审核
echo "========================================" > "${REPORT_FILE}"
echo "代码审核报告 - ${DATE}" >> "${REPORT_FILE}"
echo "审核时间: $(date '+%Y-%m-%d %H:%M:%S')" >> "${REPORT_FILE}"
echo "========================================" >> "${REPORT_FILE}"
echo "" >> "${REPORT_FILE}"

# 1. Git 状态检查
echo "【1. Git 仓库状态】" >> "${REPORT_FILE}"
cd "${PROJECT_DIR}"
if [ -d ".git" ]; then
    echo "当前分支: $(git branch --show-current 2>/dev/null || echo 'N/A')" >> "${REPORT_FILE}"
    echo "未提交更改:" >> "${REPORT_FILE}"
    git status --short 2>/dev/null | head -20 >> "${REPORT_FILE}" || echo "无法获取git状态" >> "${REPORT_FILE}"
    echo "" >> "${REPORT_FILE}"

    echo "最近提交 (5条):" >> "${REPORT_FILE}"
    git log --oneline -5 2>/dev/null >> "${REPORT_FILE}" || echo "无法获取提交历史" >> "${REPORT_FILE}"
    echo "" >> "${REPORT_FILE}"
else
    echo "非Git仓库或无法访问" >> "${REPORT_FILE}"
    echo "" >> "${REPORT_FILE}"
fi

# 2. 代码统计
echo "【2. 代码统计】" >> "${REPORT_FILE}"
if [ -d "backend/src/main/java" ]; then
    echo "Java 代码行数: $(find backend/src/main/java -name "*.java" -exec cat {} \; 2>/dev/null | wc -l)" >> "${REPORT_FILE}"
fi
if [ -d "frontend/src" ]; then
    echo "前端代码行数: $(find frontend/src -name "*.ts" -o -name "*.vue" -o -name "*.tsx" 2>/dev/null | xargs cat 2>/dev/null | wc -l)" >> "${REPORT_FILE}"
fi
echo "" >> "${REPORT_FILE}"

# 3. 检测 TODO 和 FIXME
echo "【3. 待办事项 (TODO/FIXME/BUG)】" >> "${REPORT_FILE}"
TODO_COUNT=0
for dir in backend frontend; do
    if [ -d "${dir}/src" ]; then
        TODO_IN_DIR=$(grep -r -n "TODO\|FIXME\|BUG\|XXX\|HACK" "${dir}/src" --include="*.java" --include="*.ts" --include="*.vue" 2>/dev/null | wc -l)
        TODO_COUNT=$((TODO_COUNT + TODO_IN_DIR))
        if [ "${TODO_IN_DIR}" -gt 0 ]; then
            echo "${dir} 目录中发现 ${TODO_IN_DIR} 处标记:" >> "${REPORT_FILE}"
            grep -r -n "TODO\|FIXME\|BUG\|XXX\|HACK" "${dir}/src" --include="*.java" --include="*.ts" --include="*.vue" 2>/dev/null | head -10 >> "${REPORT_FILE}"
            echo "" >> "${REPORT_FILE}"
        fi
    fi
done
if [ "${TODO_COUNT}" -eq 0 ]; then
    echo "未发现待办标记" >> "${REPORT_FILE}"
fi
echo "" >> "${REPORT_FILE}"

# 4. 检测硬编码敏感信息
echo "【4. 安全检查 - 敏感信息检测】" >> "${REPORT_FILE}"
SENSITIVE_COUNT=0
for dir in backend frontend; do
    if [ -d "${dir}/src" ]; then
        SENSITIVE_IN_DIR=$(grep -r -l "password\s*=\s*[\"'][^\"']*[\"']\|apiKey\s*=\s*[\"'][^\"']*[\"']\|secret\s*=\s*[\"'][^\"']*[\"']\|token\s*=\s*[\"'][^\"']*[\"']" "${dir}/src" 2>/dev/null | wc -l)
        SENSITIVE_COUNT=$((SENSITIVE_COUNT + SENSITIVE_IN_DIR))
    fi
done
if [ "${SENSITIVE_COUNT}" -gt 0 ]; then
    echo "警告: 检测到 ${SENSITIVE_COUNT} 个文件可能包含硬编码敏感信息!" >> "${REPORT_FILE}"
    for dir in backend frontend; do
        if [ -d "${dir}/src" ]; then
            grep -r -l "password\s*=\s*[\"'][^\"']*[\"']\|apiKey\s*=\s*[\"'][^\"']*[\"']\|secret\s*=\s*[\"'][^\"']*[\"']" "${dir}/src" 2>/dev/null | head -5 >> "${REPORT_FILE}"
        fi
    done
else
    echo "未检测到明显的硬编码敏感信息" >> "${REPORT_FILE}"
fi
echo "" >> "${REPORT_FILE}"

# 5. 检测长方法 (Java)
echo "【5. 代码复杂度 - 长方法检测 (Java)】" >> "${REPORT_FILE}"
if [ -d "backend/src/main/java" ]; then
    LONG_METHODS=$(find backend/src/main/java -name "*.java" -exec awk '/\{/{s++}/\}/{s--}s>50{print FILENAME":"NR": 方法过长"; exit}' {} \; 2>/dev/null | head -5)
    if [ -n "${LONG_METHODS}" ]; then
        echo "检测到可能过长方法:" >> "${REPORT_FILE}"
        echo "${LONG_METHODS}" >> "${REPORT_FILE}"
    else
        echo "未检测到明显过长方法 (>50行)" >> "${REPORT_FILE}"
    fi
fi
echo "" >> "${REPORT_FILE}"

# 6. 空目录检查
echo "【6. 空目录检查】" >> "${REPORT_FILE}"
EMPTY_DIRS=$(find backend frontend -type d -empty 2>/dev/null | head -10)
if [ -n "${EMPTY_DIRS}" ]; then
    echo "发现空目录:" >> "${REPORT_FILE}"
    echo "${EMPTY_DIRS}" >> "${REPORT_FILE}"
else
    echo "未发现空目录" >> "${REPORT_FILE}"
fi
echo "" >> "${REPORT_FILE}"

# 7. 报告文件检查
echo "【7. 报告输出】" >> "${REPORT_FILE}"
echo "审核报告已保存至: ${REPORT_FILE}" >> "${REPORT_FILE}"
echo "" >> "${REPORT_FILE}"

echo "========================================" >> "${REPORT_FILE}"
echo "审核完成" >> "${REPORT_FILE}"
echo "========================================" >> "${REPORT_FILE}"

# 输出到控制台
echo "代码审核完成。报告已生成: ${REPORT_FILE}"
