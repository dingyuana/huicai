#!/bin/bash
# OpenCode Plan Phase 脚本
# Hermes 调用此脚本让 OpenCode 做规划（只读不写）

TASK_FILE="$1"
if [ -z "$TASK_FILE" ]; then
  echo "Usage: $0 <task-file>"
  exit 1
fi

echo "=== Loop Engineering: Plan Phase ==="
echo "任务文件: $TASK_FILE"
echo "规范文件: harness/ARCHITECTURE.md, docs/coding-conduct.md"
echo "工作目录: /data/disk/huicai"

# 调用 OpenCode Plan 模式
opencode plan \
  --context /data/disk/huicai \
  --task-file "$TASK_FILE" \
  --prompt "请阅读 $TASK_FILE 和 harness/ARCHITECTURE.md、docs/coding-conduct.md，生成详细的实施方案。只做规划，不修改任何文件。"
