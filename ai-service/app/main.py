"""慧财 AI 服务 - 入口"""
import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import health, ocr, match, anomaly, embedding
from app.core.config import settings
from app.workers.task_consumer import TaskConsumer

# 初始化日志
logging.basicConfig(
    level=getattr(logging, settings.log_level),
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# 全局任务消费者
_consumer: TaskConsumer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """启动/关闭钩子: 启动时初始化 RabbitMQ 消费者"""
    global _consumer
    logger.info(f"启动 {settings.service_name} on port {settings.service_port}")
    _consumer = TaskConsumer()
    consumer_task = asyncio.create_task(_consumer.start())
    try:
        yield
    finally:
        logger.info("关闭 AI 服务")
        if _consumer:
            await _consumer.stop()
        consumer_task.cancel()
        try:
            await consumer_task
        except asyncio.CancelledError:
            pass


app = FastAPI(
    title="慧财 AI 服务",
    description="OCR / 智能匹配 / 异常检测 / 文本嵌入",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 路由
app.include_router(health.router, prefix="/api/v1")
app.include_router(ocr.router, prefix="/api/v1")
app.include_router(match.router, prefix="/api/v1")
app.include_router(anomaly.router, prefix="/api/v1")
app.include_router(embedding.router, prefix="/api/v1")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=settings.service_port,
        reload=settings.debug,
    )
