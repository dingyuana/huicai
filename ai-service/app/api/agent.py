"""
慧财 AI Agent 编排层 - API 路由

提供统一的 Agent 调用入口 /agent/route，
将现有 5 个端点封装为 Agent 工具。
"""
import logging
from typing import Any

from fastapi import APIRouter
from pydantic import BaseModel

from app.core.agent import RouterAgent, BaseAgent, AgentTool
from app.core.graph import create_agent_graph, execute_agent_chain
from app.core.logging import get_logger

logger = get_logger(__name__)
router = APIRouter(prefix="/agent", tags=["agent"])

# 全局 RouterAgent 实例，由 setup_agent_system 初始化
_router_agent: RouterAgent | None = None


class AgentRequest(BaseModel):
    intent: str
    input_data: dict = {}


class AgentResponse(BaseModel):
    success: bool
    intent: str
    result: Any = None
    confidence: float = 0.0
    requires_human: bool = True
    error: str | None = None


def setup_agent_system():
    """
    初始化 Agent 系统。

    注册所有已有的 Agent 和工具。
    在应用启动时调用。
    """
    global _router_agent
    _router_agent = RouterAgent()
    logger.info("Agent 系统初始化完成，可用 Agent: {}", _router_agent.available_agents())
    return _router_agent


def get_router() -> RouterAgent:
    """获取全局 RouterAgent 实例"""
    global _router_agent
    if _router_agent is None:
        _router_agent = setup_agent_system()
    return _router_agent


@router.post("/route", response_model=AgentResponse)
async def agent_route(req: AgentRequest):
    """
    统一的 Agent 路由入口。

    根据 intent 自动选择对应的 Agent 处理请求。
    """
    r = get_router()
    result = await execute_agent_chain(r, req.intent, req.input_data)
    return AgentResponse(
        success=result.get("success", False),
        intent=req.intent,
        result=result.get("result"),
        confidence=result.get("confidence", 0.0),
        requires_human=result.get("requires_human", True),
        error=result.get("error"),
    )


@router.get("/agents")
async def list_agents():
    """列出所有可用的 Agent"""
    r = get_router()
    return {
        "agents": r.available_agents(),
        "graph": create_agent_graph(),
    }


@router.get("/health")
async def agent_health():
    """Agent 系统健康检查"""
    r = get_router()
    return {
        "status": "ok",
        "agent_count": len(r.agents),
        "agents": [name for name in r.agents],
    }