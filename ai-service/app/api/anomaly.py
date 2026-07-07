"""异常检测（凭证 + 多维跨表分析）"""
import logging
from typing import Any, List

from fastapi import APIRouter
from pydantic import BaseModel

from app.core.logging import get_logger

logger = get_logger(__name__)
router = APIRouter(prefix="/anomaly", tags=["anomaly"])


# ============================================================
# 通用模型
# ============================================================

class AnomalyTag(BaseModel):
    type: str
    severity: str  # LOW / MEDIUM / HIGH / CRITICAL
    description: str
    dimension: str = "voucher"  # voucher / invoice / time / counterparty


# ============================================================
# 端点 1: 凭证异常检测（已有，保持向后兼容）
# ============================================================

class VoucherCheck(BaseModel):
    voucher_id: int
    total_debit: float
    total_credit: float
    entries: List[dict]


class AnomalyResponse(BaseModel):
    voucher_id: int
    anomalies: List[AnomalyTag]
    risk_score: float


@router.post("/voucher", response_model=AnomalyResponse)
async def check_voucher(req: VoucherCheck):
    """检查凭证异常：借贷平衡、金额异常、可疑摘要"""
    anomalies: List[AnomalyTag] = []

    # 借贷平衡
    diff = abs(req.total_debit - req.total_credit)
    if diff > 0.01:
        anomalies.append(AnomalyTag(
            type="UNBALANCED", severity="CRITICAL",
            description=f"借贷不平衡, 差额 {diff:.2f}",
        ))

    # 单笔异常
    for entry in req.entries:
        amount = max(entry.get("debit", 0), entry.get("credit", 0))
        if amount > 1_000_000:
            anomalies.append(AnomalyTag(
                type="LARGE_AMOUNT", severity="MEDIUM",
                description=f"分录金额过大: {amount:.2f}",
            ))

    # 可疑关键词
    suspicious_keywords = ["补亏", "退回", "调整", "测试", "调账", "暂估"]
    for entry in req.entries:
        summary = entry.get("summary", "") or ""
        for kw in suspicious_keywords:
            if kw in summary:
                anomalies.append(AnomalyTag(
                    type="SUSPICIOUS_SUMMARY", severity="LOW",
                    description=f"摘要含可疑关键词: {kw}",
                ))
                break

    risk_score = _calc_risk_score(anomalies)
    return AnomalyResponse(voucher_id=req.voucher_id, anomalies=anomalies, risk_score=risk_score)


# ============================================================
# 端点 2: 多维异常检测 Agent（新增）
# ============================================================

class InvoiceDimCheck(BaseModel):
    """发票维度检测请求"""
    invoice_no: str = ""
    item_name: str = ""           # 商品名称
    amount: float = 0
    invoice_date: str = ""        # YYYY-MM-DD
    counterparty: str = ""        # 客户/供应商
    partner_tax_id: str = ""      # 对方税号
    invoice_type: str = ""        # INPUT / OUTPUT
    period: str = ""              # YYYYMM
    recent_invoices: list[dict] | None = None  # [{item_name, amount, date, counterparty, type}]


class AnomalyAgentResponse(BaseModel):
    """多维异常检测响应"""
    anomalies: list[AnomalyTag]
    risk_score: float
    requires_human: bool


@router.post("/invoice", response_model=AnomalyAgentResponse)
async def check_invoice(req: InvoiceDimCheck):
    """
    多维发票异常检测 Agent。

    检测维度：
    1. 品名背离 — 进项"电子产品" vs 销项"餐饮"
    2. 时间异常 — 凌晨/节假日开票
    3. 对方重复 — 短时间内多次开票
    4. 金额波动 — 与历史对比
    """
    anomalies: list[AnomalyTag] = []

    # === 维度 1: 品名背离 ===
    item_anomaly = _check_item_mismatch(req.item_name, req.invoice_type, req.recent_invoices)
    if item_anomaly:
        anomalies.append(item_anomaly)

    # === 维度 2: 时间异常 ===
    time_anomaly = _check_time_anomaly(req.invoice_date)
    if time_anomaly:
        anomalies.append(time_anomaly)

    # === 维度 3: 对方重复 ===
    dup_anomaly = _check_counterparty_frequency(req.counterparty, req.invoice_date, req.recent_invoices)
    if dup_anomaly:
        anomalies.append(dup_anomaly)

    # === 维度 4: 金额波动 ===
    amount_anomaly = _check_amount_volatility(req.amount, req.recent_invoices)
    if amount_anomaly:
        anomalies.append(amount_anomaly)

    risk_score = _calc_risk_score(anomalies)
    requires_human = risk_score > 0.3  # 总分 > 0.3 挂起人工

    return AnomalyAgentResponse(
        anomalies=anomalies,
        risk_score=round(risk_score, 2),
        requires_human=requires_human,
    )


# ============================================================
# 内部检测函数
# ============================================================

# 已知品名分类（用于检测进销项背离）
_ITEM_CATEGORIES: dict[str, str] = {
    # 电子产品
    "电脑": "ELECTRONICS", "服务器": "ELECTRONICS", "打印机": "ELECTRONICS",
    "手机": "ELECTRONICS", "显示器": "ELECTRONICS",
    # 办公用品
    "办公桌": "OFFICE", "办公椅": "OFFICE", "打印纸": "OFFICE",
    "文具": "OFFICE", "文件夹": "OFFICE",
    # 餐饮
    "餐饮": "FOOD", "餐费": "FOOD", "食品": "FOOD",
    "招待费": "FOOD", "酒水": "FOOD", "大米": "FOOD",
    # 建材
    "水泥": "CONSTRUCTION", "钢材": "CONSTRUCTION", "木材": "CONSTRUCTION",
    "瓷砖": "CONSTRUCTION", "水管": "CONSTRUCTION",
    # 医疗
    "药品": "MEDICAL", "口罩": "MEDICAL", "医疗器械": "MEDICAL",
    "试剂": "MEDICAL", "消毒液": "MEDICAL",
    # 服装
    "服装": "CLOTHING", "布料": "CLOTHING", "鞋": "CLOTHING",
    "纺织": "CLOTHING",
}
# 正常业务组合（进项→销项）
_VALID_COMBINATIONS: set[tuple[str, str]] = {
    ("ELECTRONICS", "ELECTRONICS"),  # 电子→电子
    ("OFFICE", "OFFICE"),            # 办公→办公
    ("CONSTRUCTION", "CONSTRUCTION"),
    ("MEDICAL", "MEDICAL"),
    ("CLOTHING", "CLOTHING"),
    ("FOOD", "FOOD"),
    ("", ""),                        # 未知品名
    ("ELECTRONICS", ""),
    ("OFFICE", ""),
    ("FOOD", ""),
    ("CONSTRUCTION", ""),
    ("MEDICAL", ""),
    ("CLOTHING", ""),
}


def _check_item_mismatch(
    item_name: str,
    invoice_type: str,
    recent_invoices: list[dict] | None,
) -> AnomalyTag | None:
    """检查品名背离：进项和销项商品类别不匹配"""
    if not item_name or not recent_invoices:
        return None

    my_cat = _categorize(item_name)
    if not my_cat:
        return None

    # 查最近的相反类型发票的品名
    opposite_type = "INPUT" if invoice_type == "OUTPUT" else "OUTPUT"
    for inv in recent_invoices:
        if inv.get("type") != opposite_type:
            continue
        opp_name = inv.get("item_name", "")
        if not opp_name:
            continue
        opp_cat = _categorize(opp_name)
        if opp_cat and (my_cat, opp_cat) not in _VALID_COMBINATIONS:
            return AnomalyTag(
                type="ITEM_MISMATCH",
                severity="HIGH",
                dimension="invoice",
                description=f"品名背离: 进项'{opp_name}'({opp_cat}) → 销项'{item_name}'({my_cat})",
            )
    return None


def _check_time_anomaly(invoice_date: str) -> AnomalyTag | None:
    """检查时间异常：周末/节假日/凌晨开票"""
    if not invoice_date:
        return None

    try:
        from datetime import datetime
        dt = datetime.strptime(invoice_date, "%Y-%m-%d")
        # 周末检查
        if dt.weekday() >= 5:  # 5=周六, 6=周日
            return AnomalyTag(
                type="WEEKEND_INVOICE",
                severity="MEDIUM",
                dimension="time",
                description=f"周末开票: {invoice_date}",
            )
        # 凌晨检查（如果 date 包含时间）
        if len(invoice_date) > 10:
            hour = dt.hour
            if 0 <= hour < 6:
                return AnomalyTag(
                    type="OFF_HOURS",
                    severity="MEDIUM",
                    dimension="time",
                    description=f"凌晨开票 ({hour}:00): {invoice_date}",
                )
    except (ValueError, ImportError):
        pass
    return None


def _check_counterparty_frequency(
    counterparty: str,
    invoice_date: str,
    recent_invoices: list[dict] | None,
) -> AnomalyTag | None:
    """检查对方重复：同一天/同一客户多次开票"""
    if not counterparty or not invoice_date or not recent_invoices:
        return None

    same_date_count = sum(
        1 for inv in recent_invoices
        if inv.get("counterparty") == counterparty
        and inv.get("date", "")[:10] == invoice_date[:10]
    )
    if same_date_count >= 3:
        return AnomalyTag(
            type="HIGH_FREQUENCY",
            severity="LOW",
            dimension="counterparty",
            description=f"同一客户/供应商同日开票 {same_date_count + 1} 次: {counterparty}",
        )
    return None


def _check_amount_volatility(
    amount: float,
    recent_invoices: list[dict] | None,
) -> AnomalyTag | None:
    """检查金额波动：与历史均值差异过大"""
    if amount <= 0 or not recent_invoices:
        return None

    amounts = [abs(inv.get("amount", 0)) for inv in recent_invoices if inv.get("amount")]
    if not amounts:
        return None

    avg = sum(amounts) / len(amounts)
    if avg > 0 and amount > avg * 5:
        return AnomalyTag(
            type="AMOUNT_SPIKE",
            severity="MEDIUM",
            dimension="invoice",
            description=f"金额异常: {amount:.2f} 超过历史均值 {avg:.2f} 的 5 倍",
        )
    return None


def _categorize(item_name: str) -> str:
    """根据商品名称返回分类"""
    if not item_name:
        return ""
    for keyword, cat in _ITEM_CATEGORIES.items():
        if keyword in item_name:
            return cat
    return ""


def _calc_risk_score(anomalies: list[AnomalyTag]) -> float:
    """计算综合风险评分"""
    score = 0.0
    for a in anomalies:
        if a.severity == "CRITICAL": score += 1.0
        elif a.severity == "HIGH": score += 0.7
        elif a.severity == "MEDIUM": score += 0.4
        else: score += 0.1
    return min(score, 1.0)