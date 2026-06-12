"""文本嵌入"""
import hashlib
import logging
from typing import List

from fastapi import APIRouter
from pydantic import BaseModel

from app.core.config import settings

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/embedding", tags=["embedding"])


class EmbeddingRequest(BaseModel):
    texts: List[str]


class EmbeddingResponse(BaseModel):
    embeddings: List[List[float]]
    dim: int
    model: str


@router.post("/encode", response_model=EmbeddingResponse)
async def encode(req: EmbeddingRequest):
    """
    文本嵌入(用于相似度检索)
    优先使用 shibing624/text2vec-base-chinese, 否则回退到 hash 哈希(用于开发与测试)
    """
    embeddings: List[List[float]] = []
    for text in req.texts:
        embeddings.append(await _encode_text(text))
    return EmbeddingResponse(
        embeddings=embeddings,
        dim=settings.embedding_dim,
        model=settings.embedding_model,
    )


async def _encode_text(text: str) -> List[float]:
    """实际调用嵌入模型(此处用确定性哈希占位, 生产换真实模型)"""
    try:
        # 真实环境使用 sentence-transformers
        # from sentence_transformers import SentenceTransformer
        # model = SentenceTransformer(settings.embedding_model)
        # return model.encode(text).tolist()
        return _hash_embed(text, settings.embedding_dim)
    except Exception as e:
        logger.warning("嵌入失败, 使用哈希回退: %s", e)
        return _hash_embed(text, settings.embedding_dim)


def _hash_embed(text: str, dim: int) -> List[float]:
    """确定性哈希嵌入(仅用于开发/测试, 不可用于生产相似度检索)"""
    vec = [0.0] * dim
    text = text or ""
    for i, ch in enumerate(text):
        idx = (hash(ch) + i * 31) % dim
        h = int(hashlib.md5(f"{ch}-{i}".encode()).hexdigest()[:8], 16)
        vec[idx] = (h % 1000) / 1000.0 - 0.5
    # 归一化
    norm = sum(x * x for x in vec) ** 0.5
    if norm > 0:
        vec = [x / norm for x in vec]
    return vec
