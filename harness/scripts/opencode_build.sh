#!/bin/bash
# OpenCode Build Phase 脚本
# Hermes 调用此脚本让 OpenCode 真正写代码

TASK_FILE="$1"
if [ -z "$TASK_FILE" ]; then
  echo "Usage: $0 <task-file>"
  exit 1
fi

echo "=== Loop Engineering: Build Phase ==="
echo "任务文件: $TASK_FILE"
echo "工作目录: /data/disk/huicai"

# 调用 OpenCode Build 模式
opencode build \
  --context /data/disk/huicai \
  --task-file "$TASK_FILE" \
  --prompt "方案已确认。请严格按照计划创建代码文件，并编写单元测试。完成后提交 Git Commit。"

# 保存 trace
cp /tmp/opencode_trace.log harness/trace/build_$(date +%Y%m%d_%H%M%S).log
