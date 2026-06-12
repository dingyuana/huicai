from pydantic import BaseModel
from typing import Optional, Any


class AiTaskMessage(BaseModel):
    task_id: int
    task_type: str  # OCR, EMBEDDING, ANOMALY_DETECTION, QA, PREDICTION
    source_id: Optional[int] = None
    payload: dict = {}


class AiTaskResult(BaseModel):
    task_id: int
    success: bool
    result: Any = None
    error: Optional[str] = None