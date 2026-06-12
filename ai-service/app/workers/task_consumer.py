"""AI 任务消息消费者(RabbitMQ)"""
import asyncio
import json
import logging
from typing import Callable, Dict

import aio_pika
from aio_pika.abc import AbstractIncomingMessage

from app.api import anomaly, embedding, match, ocr
from app.core.config import settings

logger = logging.getLogger(__name__)


class TaskConsumer:
    """监听 huicai.ai.task.queue, 处理 AI 任务并回调结果"""

    def __init__(self):
        self._connection: aio_pika.RobustConnection | None = None
        self._channel: aio_pika.abc.AbstractChannel | None = None
        self._stop_event = asyncio.Event()
        self._handlers: Dict[str, Callable] = {
            "OCR":       self._handle_ocr,
            "MATCH":     self._handle_match,
            "ANOMALY":   self._handle_anomaly,
            "EMBEDDING": self._handle_embedding,
        }

    async def start(self):
        """启动消费者"""
        try:
            self._connection = await aio_pika.connect_robust(settings.rabbitmq_url)
            self._channel = await self._connection.channel()
            await self._channel.set_qos(prefetch_count=10)
            queue = await self._channel.declare_queue(settings.task_queue, durable=True)
            await queue.consume(self._on_message)
            logger.info("AI 任务消费者已启动, 监听: %s", settings.task_queue)
            await self._stop_event.wait()
        except Exception as e:
            logger.error("AI 消费者启动失败: %s", e)
            await asyncio.sleep(5)
            # 自动重连
            if not self._stop_event.is_set():
                await self.start()

    async def stop(self):
        self._stop_event.set()
        if self._connection:
            await self._connection.close()

    async def _on_message(self, message: AbstractIncomingMessage):
        """消息到达"""
        async with message.process(requeue=False):
            try:
                body = json.loads(message.body.decode("utf-8"))
                task_type = body.get("taskType", "")
                handler = self._handlers.get(task_type)
                if not handler:
                    logger.warning("未知任务类型: %s", task_type)
                    await self._publish_result(body, "FAILED", error=f"未知任务类型: {task_type}")
                    return
                logger.info("处理 AI 任务: %s taskId=%s", task_type, body.get("taskId"))
                output = await handler(body)
                await self._publish_result(body, "COMPLETED", output=output)
            except Exception as e:
                logger.exception("任务处理失败: %s", e)
                await self._publish_result(
                    body, "FAILED", error=str(e) if 'body' in locals() else "unknown"
                )

    async def _publish_result(self, body: dict, status: str,
                               output: dict | None = None, error: str | None = None):
        """回传结果到结果队列"""
        if not self._channel:
            return
        result = {
            "taskId": body.get("taskId"),
            "taskNo": body.get("taskNo"),
            "status": status,
            "outputData": output or {},
            "confidence": 0.9 if status == "COMPLETED" else 0.0,
            "errorMessage": error,
        }
        await self._channel.default_exchange.publish(
            aio_pika.Message(
                body=json.dumps(result, ensure_ascii=False).encode("utf-8"),
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                content_type="application/json",
            ),
            routing_key=settings.result_routing_key,
        )

    # ========== 任务处理器 ==========
    async def _handle_ocr(self, body: dict) -> dict:
        """OCR 任务: 从 inputData 拿到文件 URL/字节, 调用 OCR 识别"""
        input_data = body.get("inputData") or {}
        # 生产环境应从 MinIO 下载
        return {
            "raw_text": "(待实现: 下载文件后识别)",
            "fields": input_data,
            "engine": settings.ocr_engine,
        }

    async def _handle_match(self, body: dict) -> dict:
        """匹配任务: 银行对账/往来核销"""
        input_data = body.get("inputData") or {}
        # 简化处理: 返回占位, 实际查询 PostgreSQL
        return {
            "candidates": input_data.get("candidates", []),
            "matched": False,
            "note": "候选匹配建议已计算(占位)",
        }

    async def _handle_anomaly(self, body: dict) -> dict:
        """异常检测"""
        from app.api.anomaly import VoucherCheck
        input_data = body.get("inputData") or {}
        try:
            req = VoucherCheck(**input_data)
            resp = await anomaly.check_voucher(req)
            return {
                "anomalies": [a.dict() for a in resp.anomalies],
                "risk_score": resp.risk_score,
            }
        except Exception as e:
            return {"error": str(e)}

    async def _handle_embedding(self, body: dict) -> dict:
        """嵌入"""
        input_data = body.get("inputData") or {}
        texts = input_data.get("texts", [])
        if not texts:
            return {"error": "texts 不能为空"}
        from app.api.embedding import EmbeddingRequest
        req = EmbeddingRequest(texts=texts)
        resp = await embedding.encode(req)
        return {
            "embeddings": resp.embeddings,
            "dim": resp.dim,
            "model": resp.model,
        }
