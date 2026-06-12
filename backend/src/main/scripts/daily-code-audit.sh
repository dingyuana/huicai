#!/usr/bin/env bash
# =============================================================================
# 慧财财务 · 每日代码审核脚本 (系统级)
# 每天早晨自动执行一次, 对项目代码状态做全面巡检
# =============================================================================

set -euo pipefail

# 自动探测项目根目录 (向上查找 .git)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../.."
while [ "$PROJECT_ROOT" != "/" ] && [ ! -d "$PROJECT_ROOT/.git" ]; do
    PROJECT_ROOT="$(dirname "$PROJECT_ROOT")"
done
if [ "$PROJECT_ROOT" = "/" ]; then
    PROJECT_ROOT="$SCRIPT_DIR/../.."
fi

cd "$PROJECT_ROOT"

REPORT_DIR="$PROJECT_ROOT/docs/reports"
mkdir -p "$REPORT_DIR"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
DATE_STR="$(date +%Y-%m-%d\ %H:%M:%S)"
REPORT_FILE="$REPORT_DIR/code-audit_${TIMESTAMP}.md"

# ========== 开始生成报告 ==========
{
echo "# 慧财财务 · 每日代码审核报告"
echo "生成时间: ${DATE_STR}"
echo ""

echo "## 一、Git 提交与工作区状态"
echo ""
echo "### 最近 5 条提交"
git log --oneline -5 --format="%h | %ai | %s" 2>/dev/null | while read -r line; do
    echo "- ${line}"
done || echo "_暂无提交记录_"
echo ""

echo "### 工作区状态"
STATUS_LINES="$(git status --short 2>/dev/null || echo '')"
if [ -z "$STATUS_LINES" ]; then
    echo "- 工作区 **干净**, 无未提交改动"
else
    COUNT="$(echo "$STATUS_LINES" | grep -c . || true)"
    echo "- ⚠️  工作区有 ${COUNT} 处未提交改动:"
    echo "$STATUS_LINES" | while read -r line; do
        echo "  - ${line}"
    done
fi
echo ""

echo "## 二、后端代码统计 (Java)"
echo ""
BACKEND_SRC="$PROJECT_ROOT/backend/src/main/java/com/huicai"
if [ -d "$BACKEND_SRC" ]; then
    TOTAL_JAVA="$(find "$BACKEND_SRC" -name "*.java" | wc -l | tr -d ' ')"
    CNT_ENTITY="$(find "$BACKEND_SRC" -name "*Entity.java" | wc -l | tr -d ' ')"
    CNT_MAPPER="$(find "$BACKEND_SRC" -name "*Mapper.java" | wc -l | tr -d ' ')"
    CNT_SERVICE="$(find "$BACKEND_SRC" -name "*Service.java" ! -name "*ServiceImpl.java" | wc -l | tr -d ' ')"
    CNT_SERVICE_IMPL="$(find "$BACKEND_SRC" -name "*ServiceImpl.java" | wc -l | tr -d ' ')"
    CNT_CONTROLLER="$(find "$BACKEND_SRC" -name "*Controller.java" | wc -l | tr -d ' ')"

    echo "| 指标 | 数量 |"
    echo "|------|------|"
    echo "| 总 Java 文件 | ${TOTAL_JAVA} |"
    echo "| Entity 实体类 | ${CNT_ENTITY} |"
    echo "| Mapper 接口 | ${CNT_MAPPER} |"
    echo "| Service 接口 | ${CNT_SERVICE} |"
    echo "| ServiceImpl 实现 | ${CNT_SERVICE_IMPL} |"
    echo "| Controller 控制器 | ${CNT_CONTROLLER} |"
    echo ""

    echo "| 模块 | Java 文件数 |"
    echo "|------|------------|"
    for d in "$BACKEND_SRC"/*/; do
        [ -d "$d" ] || continue
        MODULE="$(basename "$d")"
        C="$(find "$d" -name "*.java" | wc -l | tr -d ' ')"
        echo "| ${MODULE} | ${C} |"
    done
    echo ""
else
    echo "_后端源码目录不存在_"
    echo ""
fi

echo "## 三、前端代码统计"
echo ""
FRONTEND_SRC="$PROJECT_ROOT/frontend/src"
if [ -d "$FRONTEND_SRC" ]; then
    CNT_VUE="$(find "$FRONTEND_SRC" -name "*.vue" | wc -l | tr -d ' ')"
    CNT_TS="$(find "$FRONTEND_SRC" -name "*.ts" | wc -l | tr -d ' ')"
    CNT_SCSS="$(find "$FRONTEND_SRC" -name "*.scss" | wc -l | tr -d ' ')"
    echo "| 类型 | 数量 |"
    echo "|------|------|"
    echo "| .vue 组件 | ${CNT_VUE} |"
    echo "| .ts 脚本 | ${CNT_TS} |"
    echo "| .scss 样式 | ${CNT_SCSS} |"
    echo "| **合计** | $((CNT_VUE + CNT_TS + CNT_SCSS)) |"
    echo ""
else
    echo "_前端源码目录不存在_"
    echo ""
fi

echo "## 四、数据库迁移脚本"
echo ""
MIG_DIR="$PROJECT_ROOT/backend/src/main/resources/db/migration"
if [ -d "$MIG_DIR" ]; then
    MIG_COUNT="$(ls -1 "$MIG_DIR"/*.sql 2>/dev/null | wc -l | tr -d ' ')"
    echo "- 总迁移脚本数: **${MIG_COUNT}** 个"
    echo ""
    for f in "$MIG_DIR"/*.sql; do
        [ -f "$f" ] || continue
        echo "  - $(basename "$f")"
    done
else
    echo "_迁移脚本目录不存在_"
fi
echo ""

echo "## 五、开发进度对照 (基于 Phase 计划)"
echo ""
echo "| Phase | 状态 | 依据 |"
echo "|-------|------|------|"
[ -f "$BACKEND_SRC/config/AsyncConfig.java" ] && P0="✅ 已完成" || P0="❌ 未开始"
echo "| Phase 0 · 项目骨架 | ${P0} | AsyncConfig / Docker Compose |"
[ -d "$BACKEND_SRC/config/security" ] && P2="✅ 已完成" || P2="❌ 未开始"
echo "| Phase 2 · RBAC 权限 | ${P2} | config/security + JWT |"
[ -d "$BACKEND_SRC/module/system" ] && P1="✅ 已完成" || P1="❌ 未开始"
echo "| Phase 1 · 基础数据 | ${P1} | module/system 模块 |"
[ -d "$BACKEND_SRC/module/finance" ] && P3="🟨 进行中" || P3="❌ 未开始"
echo "| Phase 3 · 财务核心 | ${P3} | module/finance 模块 |"
echo "| Phase 4+ · 业务单据/出纳/... | ❌ 未开始 | 尚未搭建模块 |"
echo ""

echo "## 六、下一步建议"
echo ""
if [ "$P3" = "🟨 进行中" ]; then
    echo "1. **优先完成 Phase 3 财务核心闭环**: 凭证校验 → 审核 → 记账 → 科目余额更新 → 结账"
    echo "2. **凭证核心状态机**: 草稿 → 提交 → 审核 → 记账 → 红冲 的完整流程"
    echo "3. **借贷平衡硬约束**: 保存凭证时强制校验 SUM(借) = SUM(贷)"
    echo "4. **科目余额实时更新**: 记账操作同步更新 t_subject_balance"
    echo "5. **前端联动**: 补齐凭证审核/记账按钮, 打通操作闭环"
else
    echo "1. 搭建 module/finance 财务核心模块"
    echo "2. 设计凭证表结构与核心业务流程"
fi
echo ""

echo "---"
echo "报告结束 · 自动生成于 ${DATE_STR}"
} > "$REPORT_FILE"

echo "=============================================================="
echo " 慧财财务 · 每日代码审核完成"
echo " 报告文件: ${REPORT_FILE}"
echo "=============================================================="
