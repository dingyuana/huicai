"""慧财 AI 服务 - 入口"""
import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api import health, ocr, match, anomaly, embedding
from app.core.config import settings
from app.core.logging import get_logger, setup_logging
from app.workers.task_consumer import TaskConsumer

# 初始化结构化日志
setup_logging()
logger = get_logger(__name__)

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
    version=settings.service_version,
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 统一异常处理器
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error("未捕获异常: {} {} | {}", request.method, request.url.path, str(exc))
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error", "path": request.url.path},
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
