"""健康检查"""
from fastapi import APIRouter

router = APIRouter(tags=["health"])


@router.get("/health")
async def health():
    return {"status": "UP", "service": "huicai-ai-service"}


@router.get("/health/ready")
async def ready():
    return {"ready": True}
