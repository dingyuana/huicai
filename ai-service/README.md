"""慧财 AI 服务 (Python FastAPI)

提供能力:
- OCR 智能识别 (发票/单据)
- 银行对账/往来核销匹配
- 凭证异常检测
- 文本嵌入 (pgvector 相似度检索)

部署:
    docker compose up -d ai-service
    # 或本地开发
    cd ai-service && pip install -r requirements.txt
    uvicorn app.main:app --reload --port 8000

与 Java 后端通过 RabbitMQ 异步通信:
- 接收: huicai.ai.task.queue
- 回传: huicai.ai.result.queue
"""
