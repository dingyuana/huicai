# 慧财 AI 服务

Python FastAPI 微服务, 提供 OCR、智能匹配、异常检测、文本嵌入等 AI 能力。

## 能力列表

| 路由 | 能力 | 说明 |
|------|------|------|
| `/api/v1/ocr/invoice` | 发票 OCR | 识别发票图片, 提取金额/日期/对方/发票号 |
| `/api/v1/match/score` | 智能匹配 | 银行对账/往来核销候选评分 |
| `/api/v1/anomaly/voucher` | 异常检测 | 凭证借贷平衡/金额异常/串户检测 |
| `/api/v1/embedding/encode` | 文本嵌入 | 中文文本向量化(768 维) |

## 启动

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

## 消息通信

通过 RabbitMQ 与 Java 后端通信:
- 消费: `huicai.ai.task.queue`
- 发送: `huicai.ai.result.queue`

任务类型:
- `OCR` - 发票/单据识别
- `MATCH` - 银行对账/核销匹配
- `ANOMALY` - 凭证异常检测
- `EMBEDDING` - 文本嵌入
