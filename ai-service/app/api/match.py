"""智能匹配(银行对账、往来核销)"""
import logging
from typing import List

from fastapi import APIRouter
from pydantic import BaseModel

logger = logging.getLogger(__name__)
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


@router.post("/score", response_model=MatchResponse)
async def score(req: MatchRequest):
    """
    银行对账 / 往来核销匹配
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
            # 符号一致(同向)
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
    # 简单二元中文切分
    chars = [c for c in text if not c.isspace()]
    for i in range(len(chars) - 1):
        tokens.append(chars[i] + chars[i + 1])
    # 英文单词
    for w in text.split():
        if any(c.isalpha() for c in w):
            tokens.append(w)
    return tokens
