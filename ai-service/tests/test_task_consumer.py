"""SPEC-P139 AT-139-4 + 实施中查出的连带缺陷③。

缺陷③：旧实现经 default 交换机发布结果（routing_key="ai.result"）——default 交换机
按「队列名」路由，而 result 队列名为 huicai.ai.result.queue ⇒ 消息无机路由、
被 RabbitMQ 静默丢弃，Python 全链路从未走通（队列消耗了、结果永不回来）。
修复：须经被动声明的 huicai.ai.exchange（Java AiRabbitConfig 为唯一拓扑事实源）
发布，routing_key 才在 direct 交换机语义下生效。

AT-139-4 另锁：enterpriseId 必须原样透传（D-1 全链路上下文）。
"""
import asyncio
import json

from app.workers.task_consumer import TaskConsumer

AI_EXCHANGE = "huicai.ai.exchange"


class FakeExchange:
    def __init__(self, name):
        self.name = name
        self.published = []

    async def publish(self, message, routing_key=None, **kwargs):
        self.published.append(
            (self.name, routing_key, json.loads(message.body.decode("utf-8")))
        )


def _publish(body, status, output):
    consumer = TaskConsumer()
    consumer._exchange = FakeExchange(AI_EXCHANGE)
    # asyncio.run() 每次新建事件循环，需先把待发布的状态装进 consumer 再 run
    asyncio.run(consumer._publish_result(body, status, output=output))
    return consumer._exchange.published


def test_publish_result_echoes_enterprise_id_via_ai_exchange():
    published = _publish(
        {"taskId": 3, "taskNo": "P139", "taskType": "OCR", "enterpriseId": 42},
        "COMPLETED",
        output={"raw_text": "x"},
    )
    assert published, "必须显式发布一条结果消息"
    exchange_name, routing_key, msg = published[0]
    assert exchange_name == AI_EXCHANGE, (
        "结果必须经 huicai.ai.exchange 发布：default 交换机按队列名路由，"
        "ai.result 不是队列名 ⇒ 消息被静默丢弃（连带缺陷③）"
    )
    assert routing_key == "ai.result"
    assert msg["enterpriseId"] == 42, (
        "结果消息必须透传下发消息携带的 enterpriseId；"
        "缺失时 Java 监听侧无法重建租户上下文 ⇒ 写回被 RLS 静默过滤"
    )


def test_publish_result_missing_enterprise_id_stays_consistent():
    """旧系统路径消息无 enterpriseId 时回传 None（键必须存在，由 Java D-2 兜底）"""
    published = _publish({"taskId": 3, "taskNo": "P139"}, "FAILED", output=None)
    assert published, "即使失败也必须显式发布一条结果消息"
    exchange_name, routing_key, msg = published[0]
    assert exchange_name == AI_EXCHANGE
    assert routing_key == "ai.result"
    assert "enterpriseId" in msg
    assert msg["enterpriseId"] is None
