"""慧财 AI 服务 - 结构化日志配置 (loguru)

提供:
    setup_logging()  — 根据 debug / JSON 模式配置全局日志
    get_logger()     — 获取以当前模块命名的 logger
"""

import sys

from loguru import logger

from app.core.config import settings


def setup_logging() -> None:
    """配置结构化日志。

    开发环境: 彩色可读格式，输出到 stderr
    生产环境: JSON 格式，便于日志收集系统消费
    """
    logger.remove()

    if settings.debug:
        fmt = (
            "<green>{time:YYYY-MM-DD HH:mm:ss.SSS}</green> | "
            "<level>{level: <8}</level> | "
            "<cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> | "
            "<level>{message}</level>"
        )
        logger.add(sys.stderr, format=fmt, level=settings.log_level, colorize=True)
    else:
        fmt = (
            '{"time":"{time:YYYY-MM-DDTHH:mm:ss.SSSZ}",'
            '"level":"{level}",'
            '"name":"{name}",'
            '"function":"{function}",'
            '"line":{line},'
            '"message":"{message}"}'
        )
        logger.add(sys.stderr, format=fmt, level=settings.log_level, serialize=True)


def get_logger(name: str | None = None) -> "logger":  # type: ignore[type-arg]
    """获取 loguru logger 实例。

    不传 name 时自动以调用模块的 __name__ 命名，方便追溯来源。
    """
    return logger.bind(name=name or __name__)