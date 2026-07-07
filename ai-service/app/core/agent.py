"""
慧财 AI Agent 编排层 - 基类与工具定义

将现有 5 个独立端点整合为 LangGraph Agent 体系：
- match_agent: 智能匹配（银行对账/核销 + 科目映射）
- anomaly_agent: 异常检测
- ocr_agent: OCR 识别
- embedding_agent: 文本嵌入
"""
import logging
from typing import Any

from app.api import match as match_api
from app.api import anomaly as anomaly_api
from app.core.logging import get_logger

logger = get_logger(__name__)


class AgentTool:
    """Agent 可调用的工具（对应一个已有 API 端点）"""

    def __init__(self, name: str, description: str, handler: callable):
        self.name = name
        self.description = description
        self.handler = handler

    async def run(self, **kwargs) -> Any:
        logger.info("工具调用: {} args={}", self.name, kwargs)
        try:
            result = await self.handler(**kwargs)
            return result
        except Exception as e:
            logger.error("工具执行失败: {} error={}", self.name, str(e))
            return {"error": str(e)}


class BaseAgent:
    """Agent 基类"""

    def __init__(self, name: str, description: str):
        self.name = name
        self.description = description
        self.tools: dict[str, AgentTool] = {}

    def register_tool(self, tool: AgentTool):
        self.tools[tool.name] = tool
        logger.debug("Agent {} 注册工具: {}", self.name, tool.name)

    async def process(self, input_data: dict) -> dict:
        """子类实现具体的处理逻辑"""
        raise NotImplementedError


class RouterAgent:
    """
    路由 Agent：根据用户输入选择对应的子 Agent 处理。

    当前版本基于关键词路由，后续可升级为 LLM 意图识别。
    """

    def __init__(self):
        self.agents: dict[str, BaseAgent] = {}
        logger.info("RouterAgent 初始化")

    def register_agent(self, agent: BaseAgent):
        self.agents[agent.name] = agent
        logger.info("注册 Agent: {} - {}", agent.name, agent.description)

    async def route(self, intent: str, input_data: dict) -> dict:
        """
        路由请求到对应 Agent。

        策略：
        1. 匹配 intent 名称（精确匹配 agent name）
        2. 匹配 intent 关键词
        3. 兜底返回 unknown
        """
        # 1. 精确匹配
        if intent in self.agents:
            logger.info("路由: {} → Agent {}", intent, intent)
            return await self.agents[intent].process(input_data)

        # 2. 关键词匹配
        for name, agent in self.agents.items():
            if name in intent or intent in name:
                logger.info("路由: {} → Agent {} (关键词匹配)", intent, name)
                return await agent.process(input_data)

        # 3. 兜底
        logger.warning("无法路由: {}，可用 Agent: {}", intent, list(self.agents.keys()))
        return {
            "error": f"unknown_intent: {intent}",
            "available_agents": list(self.agents.keys()),
        }

    def available_agents(self) -> list[dict]:
        return [
            {"name": name, "description": agent.description}
            for name, agent in self.agents.items()
        ]


class MatchAgent(BaseAgent):
    """科目映射 Agent"""

    def __init__(self):
        super().__init__("match", "科目映射与智能匹配（规则→向量→LLM 三阶段）")

    async def process(self, input_data: dict) -> dict:
        item_name = input_data.get("item_name", "")
        amount = input_data.get("amount")
        counterparty = input_data.get("counterparty")
        req = match_api.SubjectMappingRequest(
            item_name=item_name,
            amount=amount,
            counterparty=counterparty,
        )
        result = await match_api.subject_mapping_agent(req)
        return {
            "best": result.best.model_dump() if result.best else None,
            "candidates": [c.model_dump() for c in result.candidates],
            "requires_human": result.requires_human,
        }


class AnomalyAgent(BaseAgent):
    """多维异常检测 Agent"""

    def __init__(self):
        super().__init__("anomaly", "多维异常检测（品名背离/时间异常/金额波动/对方重复）")

    async def process(self, input_data: dict) -> dict:
        req = anomaly_api.InvoiceDimCheck(
            invoice_no=input_data.get("invoice_no", ""),
            item_name=input_data.get("item_name", ""),
            amount=input_data.get("amount", 0),
            invoice_date=input_data.get("invoice_date", ""),
            counterparty=input_data.get("counterparty", ""),
            invoice_type=input_data.get("invoice_type", "OUTPUT"),
            recent_invoices=input_data.get("recent_invoices"),
        )
        result = await anomaly_api.check_invoice(req)
        return {
            "anomalies": [a.model_dump() for a in result.anomalies],
            "risk_score": result.risk_score,
            "requires_human": result.requires_human,
        }


def create_default_agents() -> RouterAgent:
    """创建并注册所有默认 Agent"""
    router = RouterAgent()
    router.register_agent(MatchAgent())
    router.register_agent(AnomalyAgent())
    logger.info("已注册默认 Agent: match, anomaly")
    return router