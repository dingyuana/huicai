"""OCR 智能识别 API"""
import io
import logging
from typing import Optional

from fastapi import APIRouter, HTTPException, UploadFile, File, Form
from pydantic import BaseModel

from app.core.config import settings

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/ocr", tags=["ocr"])


class OCRResult(BaseModel):
    raw_text: str
    invoice_no: Optional[str] = None
    invoice_date: Optional[str] = None
    amount: Optional[float] = None
    tax_amount: Optional[float] = None
    vendor_name: Optional[str] = None
    confidence: float = 0.0
    fields: dict = {}


class HealthCheckResponse(BaseModel):
    engine: str
    available: bool


@router.get("/engine", response_model=HealthCheckResponse)
async def engine_status():
    """OCR 引擎状态"""
    available = False
    try:
        if settings.ocr_engine == "tesseract":
            import pytesseract  # type: ignore
            pytesseract.get_tesseract_version()
            available = True
    except Exception:
        available = False
    return HealthCheckResponse(engine=settings.ocr_engine, available=available)


@router.post("/invoice", response_model=OCRResult)
async def recognize_invoice(
    file: UploadFile = File(..., description="发票/单据图片"),
    hint: str = Form(default="auto", description="识别提示: invoice/bank/receipt"),
):
    """
    识别发票图片, 提取关键字段
    返回结构化结果供前端填充单据
    """
    if file.size and file.size > settings.max_upload_bytes:
        raise HTTPException(413, "文件过大")

    content = await file.read()
    result = await _recognize(content, hint)
    return result


async def _recognize(content: bytes, hint: str) -> OCRResult:
    """实际识别逻辑(可替换为不同引擎)"""
    # 优先使用云端 OCR, 否则本地 Tesseract
    try:
        if settings.ocr_engine == "tesseract":
            from PIL import Image
            import pytesseract
            image = Image.open(io.BytesIO(content))
            raw = pytesseract.image_to_string(image, lang="chi_sim+eng")
        else:
            raw = ""
    except Exception as e:
        logger.warning("OCR 失败, 返回降级结果: %s", e)
        raw = ""

    fields = _parse_invoice_fields(raw)
    confidence = 0.85 if raw else 0.0
    return OCRResult(
        raw_text=raw,
        invoice_no=fields.get("invoice_no"),
        invoice_date=fields.get("invoice_date"),
        amount=fields.get("amount"),
        tax_amount=fields.get("tax_amount"),
        vendor_name=fields.get("vendor_name"),
        confidence=confidence,
        fields=fields,
    )


def _parse_invoice_fields(text: str) -> dict:
    """基于正则的结构化字段提取(轻量级, 生产应换 ML)"""
    import re
    out: dict = {}
    if not text:
        return out
    # 发票号: 8~20 位数字
    m = re.search(r"发票号码[:：\s]*([0-9]{8,20})", text)
    if m:
        out["invoice_no"] = m.group(1)
    # 金额: 含税小写
    m = re.search(r"小写[）)]\s*[¥￥]?\s*([0-9,]+\.?[0-9]*)", text)
    if m:
        try:
            out["amount"] = float(m.group(1).replace(",", ""))
        except ValueError:
            pass
    m = re.search(r"税额[¥￥]?\s*([0-9,]+\.?[0-9]*)", text)
    if m:
        try:
            out["tax_amount"] = float(m.group(1).replace(",", ""))
        except ValueError:
            pass
    m = re.search(r"开票日期[:：\s]*(\d{4}[-/年]\d{1,2}[-/月]\d{1,2})", text)
    if m:
        out["invoice_date"] = m.group(1).replace("年", "-").replace("月", "-").replace("日", "")
    m = re.search(r"名\s*称[:：]\s*([^\n]+)", text)
    if m:
        out["vendor_name"] = m.group(1).strip()
    return out
