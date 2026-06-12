from fastapi import APIRouter, HTTPException
from app.services.ocr_service import OcrService
from app.services.embedding_service import EmbeddingService

router = APIRouter(prefix="/api/v1", tags=["AI"])
ocr = OcrService()
embed = EmbeddingService()


@router.get("/health")
async def health():
    return {"status": "ok", "service": "huicai-ai"}


@router.post("/ocr")
async def ocr_recognize(image_path: str):
    try:
        result = await ocr.recognize(image_path)
        return {"success": True, "data": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/embedding")
async def generate_embedding(text: str):
    vector = await embed.embed(text)
    return {"success": True, "data": {"vector": vector, "dimensions": len(vector)}}


@router.post("/anomaly/detect")
async def detect_anomaly(data: list[dict]):
    return {"success": True, "data": {"anomalies": [], "total_checked": len(data)}}


@router.post("/qa/ask")
async def ask_question(question: str):
    return {"success": True, "data": {"answer": "AI问答服务未部署", "confidence": 0.0}}