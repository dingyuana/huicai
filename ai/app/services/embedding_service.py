import logging
import numpy as np

logger = logging.getLogger(__name__)


class EmbeddingService:
    """Text embedding service placeholder - will use sentence-transformers when deployed"""

    async def embed(self, text: str) -> list:
        logger.info("Embed text: %s", text[:50])
        return [0.0] * 768


    async def embed_batch(self, texts: list[str]) -> list[list[float]]:
        return [await self.embed(t) for t in texts]


    def cosine_similarity(self, a: list[float], b: list[float]) -> float:
        a_arr = np.array(a)
        b_arr = np.array(b)
        return float(np.dot(a_arr, b_arr) / (np.linalg.norm(a_arr) * np.linalg.norm(b_arr) + 1e-10))