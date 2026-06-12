import logging
import uvicorn
from fastapi import FastAPI
from app.api.router import router
from app.config import SERVICE_PORT, LOG_LEVEL

logging.basicConfig(level=getattr(logging, LOG_LEVEL.upper(), logging.INFO),
                    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger(__name__)

app = FastAPI(title="慧财财务 AI 服务", version="0.1.0")
app.include_router(router)


@app.on_event("startup")
async def startup():
    logger.info("AI service starting on port %d", SERVICE_PORT)


@app.on_event("shutdown")
async def shutdown():
    logger.info("AI service shutting down")


if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=SERVICE_PORT, reload=True)