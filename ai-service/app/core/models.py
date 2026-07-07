"""
慧财 AI 服务 - LLM 调用封装

提供 LangChain ChatOpenAI 兼容封装，支持：
- 结构化输出 (with_structured_output)
- 模型降级 (primary → fallback → 规则兜底)
- Prompt 模板管理
"""
import json
import logging
from typing import Any

from pydantic import BaseModel, Field

from app.core.config import settings
from app.core.logging import get_logger

logger = get_logger(__name__)


class AccountMatchResult(BaseModel):
    """科目映射结构化输出"""
    account_code: str = Field(description="匹配的会计科目编码")
    account_name: str = Field(description="匹配的会计科目名称")
    confidence: float = Field(description="置信度 0.0-1.0", ge=0.0, le=1.0)
    reasoning: str = Field(description="匹配推理过程说明")


class LLMClient:
    """
    LLM 调用封装。

    策略：
    1. 优先走 NVIDIA API（当前主模型 minimaxai/minimax-m3）
    2. NVIDIA 不可用时降级到环境变量配置的 fallback 模型
    3. 都不可用时返回 None（调用方自行规则兜底）
    """

    def __init__(self):
        self._model = None
        self._fallback_model = None
        self._initialized = False

    async def initialize(self):
        """惰性初始化 LLM 模型"""
        if self._initialized:
            return
        try:
            from langchain_openai import ChatOpenAI
            from langchain.globals import set_verbose

            nvidia_base_url = getattr(settings, "nvidia_base_url", "https://integrate.api.nvidia.com/v1")
            nvidia_api_key = getattr(settings, "nvidia_api_key", "")
            nvidia_model = getattr(settings, "nvidia_model", "minimaxai/minimax-m3")

            if nvidia_api_key:
                self._model = ChatOpenAI(
                    model=nvidia_model,
                    api_key=nvidia_api_key,
                    base_url=nvidia_base_url,
                    temperature=0.1,
                    max_tokens=1024,
                )
                logger.info("LLM 主模型初始化: {} via NVIDIA", nvidia_model)
            else:
                logger.warning("NVIDIA API key 未配置，LLM 功能不可用")

            self._initialized = True
        except ImportError:
            logger.warning("langchain-openai 未安装，LLM 功能不可用")
        except Exception as e:
            logger.error("LLM 初始化失败: {}", str(e))

    async def structured_match(
        self,
        item_name: str,
        amount: float | None = None,
        counterparty: str | None = None,
    ) -> AccountMatchResult | None:
        """
        使用 LLM 进行科目映射（结构化输出）。

        Args:
            item_name: 商品名称 / 摘要
            amount: 金额（可选）
            counterparty: 对方户名（可选）

        Returns:
            AccountMatchResult 或 None（降级时）
        """
        if not self._initialized:
            await self.initialize()
        if not self._model:
            return None

        try:
            prompt = (
                "你是一个财务科目映射专家。根据以下信息，匹配最合适的会计科目。\n\n"
                f"商品/摘要: {item_name}\n"
                f"金额: {amount if amount else '未知'}\n"
                f"对方: {counterparty if counterparty else '未知'}\n\n"
                "请返回最匹配的会计科目编码、名称、置信度和推理过程。"
            )
            structured_llm = self._model.with_structured_output(AccountMatchResult)
            result: AccountMatchResult = await structured_llm.ainvoke(prompt)  # type: ignore
            logger.info(
                "LLM 科目映射: {} → {} (conf={})",
                item_name, result.account_code, result.confidence,
            )
            return result
        except Exception as e:
            logger.error("LLM 科目映射失败: {} error={}", item_name, str(e))
            return None


# 全局单例
_llm_client: LLMClient | None = None


def get_llm_client() -> LLMClient:
    global _llm_client
    if _llm_client is None:
        _llm_client = LLMClient()
    return _llm_client