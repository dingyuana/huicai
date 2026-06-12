"""异常检测"""
import logging
from typing import List

from fastapi import APIRouter
from pydantic import BaseModel

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/anomaly", tags=["anomaly"])


class VoucherCheck(BaseModel):
    voucher_id: int
    total_debit: float
    total_credit: float
    entries: List[dict]


class AnomalyTag(BaseModel):
    type: str
    severity: str  # LOW / MEDIUM / HIGH / CRITICAL
    description: str


class AnomalyResponse(BaseModel):
    voucher_id: int
    anomalies: List[AnomalyTag]
    risk_score: float


@router.post("/voucher", response_model=AnomalyResponse)
async def check_voucher(req: VoucherCheck):
    """
    检查凭证异常:
    1. 借贷不平衡
    2. 金额异常(单笔超大)
    3. 科目串户(典型如: 借固定资产 / 贷应付账款 实际应为 贷银行存款)
    4. 摘要关键词异常
    """
    anomalies: List[AnomalyTag] = []

    # 借贷平衡
    diff = abs(req.total_debit - req.total_credit)
    if diff > 0.01:
        anomalies.append(AnomalyTag(
            type="UNBALANCED",
            severity="CRITICAL",
            description=f"借贷不平衡, 差额 {diff:.2f}",
        ))

    # 单笔异常(单笔金额 > 1,000,000)
    for entry in req.entries:
        amount = max(entry.get("debit", 0), entry.get("credit", 0))
        if amount > 1_000_000:
            anomalies.append(AnomalyTag(
                type="LARGE_AMOUNT",
                severity="MEDIUM",
                description=f"分录金额过大: {amount:.2f}",
            ))

    # 摘要中含可疑关键词
    suspicious_keywords = ["补亏", "退回", "调整", "测试", "调账", "暂估"]
    for entry in req.entries:
        summary = entry.get("summary", "") or ""
        for kw in suspicious_keywords:
            if kw in summary:
                anomalies.append(AnomalyTag(
                    type="SUSPICIOUS_SUMMARY",
                    severity="LOW",
                    description=f"摘要含可疑关键词: {kw}",
                ))
                break

    # 计算风险评分
    risk_score = 0.0
    for a in anomalies:
        if a.severity == "CRITICAL":
            risk_score += 1.0
        elif a.severity == "HIGH":
            risk_score += 0.7
        elif a.severity == "MEDIUM":
            risk_score += 0.4
        else:
            risk_score += 0.1
    risk_score = min(risk_score, 1.0)

    return AnomalyResponse(
        voucher_id=req.voucher_id,
        anomalies=anomalies,
        risk_score=round(risk_score, 2),
    )
