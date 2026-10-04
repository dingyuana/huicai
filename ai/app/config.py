import os

# RabbitMQ
RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "huicai")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "huicai123")
RABBITMQ_QUEUE = os.getenv("RABBITMQ_QUEUE", "ai_task_queue")
RABBITMQ_RESULT_QUEUE = os.getenv("RABBITMQ_RESULT_QUEUE", "ai_result_queue")

# DB (read-only access for AI)
# 目标态同后端：使用非超级用户角色（超级用户会绕过 RLS）。
# huicai_app 已建好但暂不启用 —— TenantRlsInitializer 未生效前切过去读不到租户表。
DB_URL = os.getenv("DB_URL", "jdbc:postgresql://localhost:5432/huicai")
DB_USER = os.getenv("DB_USER", "huicai")
DB_PASS = os.getenv("DB_PASS", "huicai123")

# MinIO
MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "localhost:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "huicai")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "huicai123")

# Service
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8001"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")