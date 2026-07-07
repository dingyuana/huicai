"""智能匹配(银行对账、往来核销、科目映射)"""
import logging
from typing import Any, List

from fastapi import APIRouter
from pydantic import BaseModel

from app.core.logging import get_logger

logger = get_logger(__name__)
router = APIRouter(prefix="/match", tags=["match"])


class MatchItem(BaseModel):
    id: str
    text: str
    amount: float
    date: str
    counter_account: str | None = None


class MatchRequest(BaseModel):
    candidates: List[MatchItem]
    target: MatchItem
    threshold: float = 0.7


class MatchScore(BaseModel):
    id: str
    score: float
    reason: str | None = None


class MatchResponse(BaseModel):
    best: MatchScore | None = None
    candidates: List[MatchScore]


# ============================================================
# 端点 1: 原有 rule-based 匹配（向后兼容）
# ============================================================


@router.post("/score", response_model=MatchResponse)
async def score(req: MatchRequest):
    """
    银行对账 / 往来核销匹配（规则版）
    简化版: 基于金额一致 + 摘要相似度
    """
    scored: List[MatchScore] = []
    target_text = req.target.text or ""
    for cand in req.candidates:
        score = 0.0
        reason_parts: list = []

        # 金额一致性(0.4)
        if abs(cand.amount - req.target.amount) < 0.01:
            score += 0.4
            reason_parts.append("金额一致")
        elif cand.amount * req.target.amount > 0:
            score += 0.1

        # 摘要文本相似度(0.4)
        text_sim = _text_similarity(target_text, cand.text or "")
        score += 0.4 * text_sim
        if text_sim > 0.5:
            reason_parts.append(f"摘要相似度{text_sim:.2f}")

        # 对方户名匹配(0.2)
        if cand.counter_account and req.target.counter_account:
            if cand.counter_account == req.target.counter_account:
                score += 0.2
                reason_parts.append("对方户名匹配")

        scored.append(MatchScore(
            id=cand.id,
            score=round(score, 4),
            reason="; ".join(reason_parts) if reason_parts else None,
        ))

    scored.sort(key=lambda x: x.score, reverse=True)
    best = scored[0] if scored and scored[0].score >= req.threshold else None
    return MatchResponse(best=best, candidates=scored[:10])


# ============================================================
# 端点 2: 三阶段科目映射 Agent（新增）
# ============================================================


class SubjectMappingRequest(BaseModel):
    """科目映射请求"""
    item_name: str = ""              # 商品名称 / 摘要
    amount: float | None = None      # 金额（可选）
    counterparty: str | None = None  # 对方户名（可选）


class SubjectMappingCandidate(BaseModel):
    """科目映射候选"""
    account_code: str
    account_name: str
    confidence: float
    reasoning: str
    source: str  # "rule" / "vector" / "llm"


class SubjectMappingResponse(BaseModel):
    """科目映射响应"""
    best: SubjectMappingCandidate | None
    candidates: list[SubjectMappingCandidate]
    requires_human: bool


@router.post("/agent/subject-mapping", response_model=SubjectMappingResponse)
async def subject_mapping_agent(req: SubjectMappingRequest):
    """
    三阶段科目映射 Agent。

    阶段 1: 规则匹配 — 基于关键字精确匹配（conf≥0.9 直接返回）
    阶段 2: pgvector 语义检索 — 找历史相似记录（conf≥0.5 返回 top-3）
    阶段 3: LLM 推理 — LangChain 结构化输出（conf<0.5 挂起 HITL）
    """
    candidates: list[SubjectMappingCandidate] = []

    # === 阶段 1: 规则匹配 ===
    rule_result = _rule_match(req.item_name)
    if rule_result:
        candidates.append(SubjectMappingCandidate(
            account_code=rule_result[0],
            account_name=rule_result[1],
            confidence=0.95,
            reasoning=f"规则匹配: 关键字 '{rule_result[2]}' → {rule_result[1]}",
            source="rule",
        ))
        logger.info("阶段1(规则)命中: {} → {}", req.item_name, rule_result[1])
        # conf≥0.9 直接返回
        return SubjectMappingResponse(
            best=candidates[0],
            candidates=candidates,
            requires_human=False,
        )

    # === 阶段 2: pgvector 语义检索 ===
    try:
        vector_result = await _vector_search(req.item_name)
        for vr in vector_result:
            candidates.append(SubjectMappingCandidate(
                account_code=vr["code"],
                account_name=vr["name"],
                confidence=vr["score"],
                reasoning=vr.get("reason", "语义检索"),
                source="vector",
            ))
        if vector_result and vector_result[0]["score"] >= 0.5:
            logger.info("阶段2(向量)命中: {} → {} (conf={})",
                        req.item_name, vector_result[0]["name"], vector_result[0]["score"])
            return SubjectMappingResponse(
                best=candidates[0],
                candidates=candidates[:3],
                requires_human=False,
            )
    except Exception as e:
        logger.warning("阶段2(向量)失败: {}", str(e))

    # === 阶段 3: LLM 推理 ===
    llm_result = await _llm_match(req.item_name, req.amount, req.counterparty)
    if llm_result:
        candidates.append(SubjectMappingCandidate(
            account_code=llm_result["code"],
            account_name=llm_result["name"],
            confidence=llm_result["confidence"],
            reasoning=llm_result["reasoning"],
            source="llm",
        ))
        best = candidates[-1]
        requires_human = best.confidence < 0.5
        logger.info("阶段3(LLM): {} → {} (conf={}, HITL={})",
                    req.item_name, best.account_name, best.confidence, requires_human)
        return SubjectMappingResponse(
            best=best,
            candidates=candidates,
            requires_human=requires_human,
        )

    # 全部阶段都失败了
    logger.warning("科目映射全部阶段失败: {}", req.item_name)
    return SubjectMappingResponse(
        best=None,
        candidates=[],
        requires_human=True,
    )


# ============================================================
# 内部工具函数
# ============================================================

# 规则库: 关键字 → (科目编码, 科目名称)
_RULE_DB: list[tuple[str, str, str]] = [
    ("办公用品",   "6602.01", "办公费"),
    ("办公桌",     "6602.01", "办公费"),
    ("打印纸",     "6602.01", "办公费"),
    ("差旅费",     "6602.02", "差旅费"),
    ("机票",       "6602.02", "差旅费"),
    ("酒店",       "6602.02", "差旅费"),
    ("招待费",     "6602.03", "业务招待费"),
    ("餐饮",       "6602.03", "业务招待费"),
    ("交通费",     "6602.04", "交通费"),
    ("加油",       "6602.04", "交通费"),
    ("维修",       "6602.05", "维修费"),
    ("快递",       "6602.06", "快递费"),
    ("房租",       "6602.07", "租赁费"),
    ("物业",       "6602.07", "租赁费"),
    ("培训",       "6602.08", "培训费"),
    ("咨询",       "6602.09", "咨询费"),
    ("广告",       "6602.10", "广告宣传费"),
    ("招聘",       "6602.11", "招聘费"),
    ("电脑",       "6602.12", "办公设备"),
    ("服务器",     "6602.12", "办公设备"),
    ("打印机",     "6602.12", "办公设备"),
    ("工资",       "6602.13", "工资薪酬"),
    ("社保",       "6602.13", "工资薪酬"),
    ("公积金",     "6602.13", "工资薪酬"),
]


def _rule_match(item_name: str) -> tuple[str, str, str] | None:
    """阶段 1: 规则匹配"""
    if not item_name:
        return None
    for keyword, code, name in _RULE_DB:
        if keyword in item_name:
            return (code, name, keyword)
    return None


async def _vector_search(item_name: str) -> list[dict]:
    """阶段 2: pgvector 语义检索"""
    from app.core.config import settings
    import httpx

    # 先获取 embedding
    embed_url = f"http://localhost:{settings.service_port}/api/v1/embedding/similarity"
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.post(embed_url, json={
            "text": item_name,
            "top_k": 3,
        })
        if resp.status_code == 200:
            data = resp.json()
            if isinstance(data, list):
                return data
            return data.get("results", [])
    return []


async def _llm_match(
    item_name: str,
    amount: float | None = None,
    counterparty: str | None = None,
) -> dict | None:
    """阶段 3: LLM 推理"""
    try:
        from app.core.models import get_llm_client
        llm = get_llm_client()
        result = await llm.structured_match(item_name, amount, counterparty)
        if result:
            return {
                "code": result.account_code,
                "name": result.account_name,
                "confidence": result.confidence,
                "reasoning": result.reasoning,
            }
    except Exception as e:
        logger.warning("LLM 调用失败: {}", str(e))
    return None


def _text_similarity(a: str, b: str) -> float:
    """简化 Jaccard 相似度"""
    if not a or not b:
        return 0.0
    set_a = set(_tokenize(a))
    set_b = set(_tokenize(b))
    if not set_a or not set_b:
        return 0.0
    intersection = set_a & set_b
    union = set_a | set_b
    return len(intersection) / len(union)


def _tokenize(text: str) -> list:
    """中文二元分词 + 英文单词"""
    text = text.lower().strip()
    tokens: list = []
    chars = [c for c in text if not c.isspace()]
    for i in range(len(chars) - 1):
        tokens.append(chars[i] + chars[i + 1])
    for w in text.split():
        if any(c.isalpha() for c in w):
            tokens.append(w)
    return tokens