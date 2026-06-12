import logging

logger = logging.getLogger(__name__)


class OcrService:
    """OCR service placeholder - will use PaddleOCR when deployed"""

    async def recognize(self, image_path: str) -> dict:
        logger.info("OCR recognize: %s", image_path)
        return {
            "invoice_no": "",
            "amount": 0,
            "date": "",
            "seller": "",
            "buyer": "",
            "raw_text": []
        }