import pika
import json
import logging
from app.config import (
    RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USER,
    RABBITMQ_PASS, RABBITMQ_QUEUE, RABBITMQ_RESULT_QUEUE
)

logger = logging.getLogger(__name__)


def get_connection():
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
    return pika.BlockingConnection(
        pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT, credentials=credentials)
    )


def publish_result(result: dict):
    try:
        conn = get_connection()
        channel = conn.channel()
        channel.queue_declare(queue=RABBITMQ_RESULT_QUEUE, durable=True)
        channel.basic_publish(
            exchange='',
            routing_key=RABBITMQ_RESULT_QUEUE,
            body=json.dumps(result, ensure_ascii=False),
            properties=pika.BasicProperties(delivery_mode=2)
        )
        conn.close()
        logger.info("Result published: task_id=%s", result.get("task_id"))
    except Exception as e:
        logger.error("Failed to publish result: %s", e)


def start_consumer(callback):
    conn = get_connection()
    channel = conn.channel()
    channel.queue_declare(queue=RABBITMQ_QUEUE, durable=True)
    channel.basic_qos(prefetch_count=1)
    channel.basic_consume(queue=RABBITMQ_QUEUE, on_message_callback=callback, auto_ack=False)
    logger.info("RabbitMQ consumer started. Waiting for tasks...")
    channel.start_consuming()