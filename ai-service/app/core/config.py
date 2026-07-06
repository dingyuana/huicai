"""慧财 AI 服务 - 配置"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_prefix="HUICAI_", case_sensitive=False)

    # 服务
    service_name: str = "huicai-ai-service"
    service_version: str = "1.0"
    service_port: int = 8000
    debug: bool = True
    log_level: str = "INFO"
    cors_origins: list[str] = ["http://localhost:3000", "http://localhost:3001"]

    # RabbitMQ
    rabbitmq_url: str = "amqp://huicai:huicai123@localhost:5672/"
    task_queue: str = "huicai.ai.task.queue"
    result_queue: str = "huicai.ai.result.queue"
    task_exchange: str = "huicai.ai.exchange"
    task_routing_key: str = "ai.task"
    result_routing_key: str = "ai.result"

    # PostgreSQL (读取 AI 任务与附件)
    db_host: str = "localhost"
    db_port: int = 5432
    db_user: str = "huicai"
    db_password: str = "huicai123"
    db_name: str = "huicai"

    # MinIO (下载附件)
    minio_endpoint: str = "http://localhost:9000"
    minio_access_key: str = "huicai"
    minio_secret_key: str = "huicai123"
    minio_bucket: str = "huicai-files"
    minio_secure: bool = False

    # 嵌入模型
    embedding_dim: int = 768
    embedding_model: str = "shibing624/text2vec-base-chinese"

    # OCR
    ocr_engine: str = "tesseract"  # tesseract / paddle / cloud

    # 上传 / 接收的最大字节
    max_upload_bytes: int = 20 * 1024 * 1024


settings = Settings()
