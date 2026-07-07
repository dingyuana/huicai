"""
慧财 AI Agent 编排层 - LangGraph 状态图

定义 Agent 间协作的 DAG 图结构。
当前版本：单 Agent 直连模式（1:1 路由），
后续版本可扩展为多 Agent 链式编排。
"""
import logging
from typing import Any, TypedDict

from app.core.logging import get_logger

logger = get_logger(__name__)


# 状态定义
class AgentState(TypedDict):
    """LangGraph 图的状态"""
    intent: str                      # 用户意图
    input_data: dict                 # 输入数据
    result: dict | None              # 处理结果
    error: str | None                # 错误信息
    confidence: float                # 置信度
    requires_human: bool             # 是否需要人工介入
    intermediate_results: list[dict] # 中间结果（用于多步推理）


def create_agent_graph():
    """
    创建 Agent 编排图。

    当前拓扑：
    ┌────────────┐
    │  Router    │ ← 意图识别 + 路由
    └─────┬──────┘
          │
    ┌─────▼──────┐
    │  Sub-Agent │ ← 科目映射 / 异常检测 / OCR / 嵌入
    └─────┬──────┘
          │
    ┌─────▼──────┐
    │  Output    │ ← 返回结果 + 置信度
    └────────────┘

    返回:
        dict: 包含 node 函数字典
    """
    # 当前版本为简化实现，直接返回 Router 调用
    # 后续可替换为真正的 StateGraph 构建
    return {
        "nodes": [
            "router",
            "agent_execute",
            "output",
        ],
        "description": "单层 Agent 路由图（1:1 模式，可扩展为多 Agent 链式）",
    }


async def execute_agent_chain(router, intent: str, input_data: dict) -> dict:
    """
    执行 Agent 编排链。

    1. 路由 → 选择 Agent
    2. 执行 → 调用 Agent 处理
    3. 输出 → 返回结果 + 置信度

    Args:
        router: RouterAgent 实例
        intent: 用户意图
        input_data: 输入数据

    Returns:
        dict: 处理结果，含 confidence 和 requires_human 字段
    """
    logger.info("Agent 编排开始: intent={}", intent)

    result = await router.route(intent, input_data)

    # 如果路由失败，返回错误
    if "error" in result:
        logger.warning("Agent 编排失败: {}", result["error"])
        return {
            "success": False,
            "error": result["error"],
            "available_agents": result.get("available_agents", []),
        }

    # 包装结果
    return {
        "success": True,
        "intent": intent,
        "result": result,
        "confidence": result.get("confidence", 0.0),
        "requires_human": result.get("confidence", 1.0) < 0.5,
    }