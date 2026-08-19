#!/bin/bash
# =============================================================================
# SPEC 合规检查脚本
# 扫描 /data/disk/huicai/docs/specs/ 下所有 .md 文件，检查合规项
# 只读操作，不修改任何文件
# =============================================================================

SPECS_DIR="/data/disk/huicai/docs/specs"

echo "========================================================================
  SPEC 合规检查 - $(date '+%Y-%m-%d %H:%M:%S')
  扫描目录: $SPECS_DIR
========================================================================"
echo ""

# 收集所有 .md 文件
FILES=()
while IFS= read -r -d '' f; do
    FILES+=("$f")
done < <(find "$SPECS_DIR" -maxdepth 1 -name '*.md' -type f -print0 | sort -z)

TOTAL=${#FILES[@]}

[[ $TOTAL -eq 0 ]] && { echo "错误：在 $SPECS_DIR 下未找到任何 .md 文件"; exit 1; }

# 统计变量
PASS_HEADER=0; PASS_SDD=0; PASS_BDD=0; PASS_YAML=0; PASS_ALL=0

echo "+-----------------------------------------------------+--------+--------+--------+--------+-------+"
echo "| 文件                                                | 编号   | 四段   | BDD    | YAML   | 总分  |"
echo "+-----------------------------------------------------+--------+--------+--------+--------+-------+"

for file in "${FILES[@]}"; do
    filename=$(basename "$file")
    content=$(<"$file")

    # 检查 1: HUICAI-SPC 编号头部
    if grep -q '^> \*\*编号\*\*：HUICAI-SPC-' <<< "$content"; then
        hdr="PASS"; ((PASS_HEADER++))
    else
        hdr="FAIL"
    fi

    # 检查 2: SDD 四段结构（匹配 ## 或 ### 级别的标题）
    sdd_count=0
    while IFS= read -r line; do
        case "$line" in
            '## 1. 输入契约'|'## 1. 输入契约'*|'### 1. 输入契约'|'### 1. 输入契约'*) ((sdd_count++)) ;;
            '## 2. 输出契约'|'## 2. 输出契约'*|'### 2. 输出契约'|'### 2. 输出契约'*) ((sdd_count++)) ;;
            '## 3. 状态流转'|'## 3. 状态流转'*|'### 3. 状态流转'|'### 3. 状态流转'*) ((sdd_count++)) ;;
            '## 4. 异常处理'|'## 4. 异常处理'*|'### 4. 异常处理'|'### 4. 异常处理'*) ((sdd_count++)) ;;
        esac
    done <<< "$content"

    if [[ $sdd_count -ge 4 ]]; then
        sdd="PASS"; ((PASS_SDD++))
    else
        sdd="FAIL"
    fi

    # 检查 3: BDD 验收标准
    if grep -qE '^#{1,2}.*验收标准' <<< "$content"; then
        bdd="PASS"; ((PASS_BDD++))
    else
        bdd="FAIL"
    fi

    # 检查 4: YAML 契约块
    yaml_flag=false
    grep -q 'contract_version:' <<< "$content" && yaml_flag=true
    if ! $yaml_flag; then
        in_yaml=false
        while IFS= read -r line; do
            if [[ "$line" == "---" ]]; then
                $in_yaml && in_yaml=false || in_yaml=true
                continue
            fi
            if $in_yaml && [[ -n "$line" && ! "$line" =~ ^# ]]; then
                yaml_flag=true; break
            fi
        done <<< "$content"
    fi
    if $yaml_flag; then
        yaml="PASS"; ((PASS_YAML++))
    else
        yaml="FAIL"
    fi

    # 总分
    score=0
    [[ $hdr == "PASS" ]] && ((score++))
    [[ $sdd == "PASS" ]] && ((score++))
    [[ $bdd == "PASS" ]] && ((score++))
    [[ $yaml == "PASS" ]] && ((score++))
    [[ $score -eq 4 ]] && ((PASS_ALL++))

    # 图标
    hdr_icon=$([ "$hdr" = "PASS" ] && echo "✅" || echo "❌")
    sdd_icon=$([ "$sdd" = "PASS" ] && echo "✅" || echo "❌")
    bdd_icon=$([ "$bdd" = "PASS" ] && echo "✅" || echo "❌")
    yaml_icon=$([ "$yaml" = "PASS" ] && echo "✅" || echo "❌")

    printf "| %-51s | %-6s | %-6s | %-6s | %-6s | %d/4  |\n" \
        "$filename" "$hdr_icon" "$sdd_icon" "$bdd_icon" "$yaml_icon" "$score"
done

echo "+-----------------------------------------------------+--------+--------+--------+--------+-------+"
echo ""
echo "========================================================================
  汇总统计
========================================================================"
echo ""
echo "  总文件数        : $TOTAL"
echo "  完全合规 (4/4)  : $PASS_ALL"
echo ""

calc_pct() {
    local n=$1 total=$2
    [[ $total -eq 0 ]] && echo "0.0" || echo "scale=1; $n * 100 / $total" | bc 2>/dev/null || echo "$(( n * 100 / total )).0"
}

echo "  各检查项合规率:"
echo "  +---------------------+------------+----------+"
printf "  | %-19s | %3d/%-4d | %5.1f%% |\n" "HUICAI-SPC 编号" "$PASS_HEADER" "$TOTAL" "$(calc_pct $PASS_HEADER $TOTAL)"
printf "  | %-19s | %3d/%-4d | %5.1f%% |\n" "SDD 四段结构" "$PASS_SDD" "$TOTAL" "$(calc_pct $PASS_SDD $TOTAL)"
printf "  | %-19s | %3d/%-4d | %5.1f%% |\n" "BDD 验收标准" "$PASS_BDD" "$TOTAL" "$(calc_pct $PASS_BDD $TOTAL)"
printf "  | %-19s | %3d/%-4d | %5.1f%% |\n" "YAML 契约块" "$PASS_YAML" "$TOTAL" "$(calc_pct $PASS_YAML $TOTAL)"
echo "  +---------------------+------------+----------+"
echo ""
echo "========================================================================"
exit 0