"""
Flask application configuration.
"""
from __future__ import annotations

import os
from pathlib import Path

DEFAULT_SECRET_KEY = "dev-secret-key-change-in-production"
APP_DIR = Path(__file__).resolve().parent


def _as_bool(value: str | None, default: bool = False) -> bool:
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _as_int(value: str | None, default: int) -> int:
    if value is None:
        return default
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _split_csv(value: str | None, fallback: list[str]) -> list[str]:
    if not value:
        return fallback
    parts = [item.strip() for item in value.split(",")]
    return [item for item in parts if item]


class Config:
    ENV_NAME = os.environ.get("FLASK_ENV", "development").strip().lower()
    SECRET_KEY = os.environ.get("SECRET_KEY", "").strip()
    DEBUG = _as_bool(os.environ.get("FLASK_DEBUG"), False)
    TESTING = False

    CORS_ORIGINS = _split_csv(
        os.environ.get("CORS_ORIGINS"),
        ["http://localhost:3000", "http://localhost:5173", "http://localhost:5174"],
    )
    MAX_CONTENT_LENGTH = _as_int(os.environ.get("MAX_CONTENT_LENGTH"), 16 * 1024 * 1024)
    UPLOAD_FOLDER = os.environ.get("UPLOAD_FOLDER", "./uploads")

    LOG_LEVEL = os.environ.get("LOG_LEVEL", "INFO")
    LOG_FILE = os.environ.get("LOG_FILE", "./logs/app.log")
    LOG_MAX_BYTES = _as_int(os.environ.get("LOG_MAX_BYTES"), 5 * 1024 * 1024)
    LOG_BACKUP_COUNT = _as_int(os.environ.get("LOG_BACKUP_COUNT"), 5)

    BGE_MODEL_NAME = os.environ.get("BGE_MODEL_NAME", "BAAI/bge-small-zh-v1.5")
    BGE_PYTHON_EXE = os.environ.get("BGE_PYTHON_EXE", r"D:\Program Files\Python\python.exe")
    BGE_ENABLE_BRIDGE = _as_bool(os.environ.get("BGE_ENABLE_BRIDGE"), True)

    LLM_API_URL = os.environ.get(
        "LLM_API_URL",
        "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
    )
    LLM_COMPATIBLE_API_URL = os.environ.get(
        "LLM_COMPATIBLE_API_URL",
        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
    )
    LLM_API_KEY = os.environ.get("LLM_API_KEY")
    LLM_MODEL = os.environ.get("LLM_MODEL", "qwen-turbo")
    VISION_API_URL = os.environ.get(
        "VISION_API_URL",
        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
    )
    VISION_MODEL = os.environ.get("VISION_MODEL", "qwen-vl-plus")
    LLM_ALLOWED_MODELS = _split_csv(
        os.environ.get("LLM_ALLOWED_MODELS"),
        ["qwen-turbo", "qwen-plus", "qwen-max"],
    )
    HTTP_TIMEOUT_SECONDS = _as_int(os.environ.get("HTTP_TIMEOUT_SECONDS"), 30)
    LLM_REQUEST_TIMEOUT_SECONDS = _as_int(os.environ.get("LLM_REQUEST_TIMEOUT_SECONDS"), HTTP_TIMEOUT_SECONDS)
    LLM_MAX_TOKENS = _as_int(os.environ.get("LLM_MAX_TOKENS"), 900)
    LLM_MAX_RETRIES = _as_int(os.environ.get("LLM_MAX_RETRIES"), 2)
    LLM_RETRY_BACKOFF_SECONDS = _as_int(os.environ.get("LLM_RETRY_BACKOFF_SECONDS"), 1)
    LLM_FAIL_FAST = _as_bool(os.environ.get("LLM_FAIL_FAST"), ENV_NAME == "production")
    IMAGE_ANALYSIS_CACHE_SIZE = _as_int(os.environ.get("IMAGE_ANALYSIS_CACHE_SIZE"), 32)

    INDEX_DIR = os.environ.get("INDEX_DIR", str(APP_DIR / "data" / "indexes"))
    VECTOR_DIMENSION = _as_int(os.environ.get("VECTOR_DIMENSION"), 512)

    SPRINGBOOT_BASE_URL = os.environ.get("SPRINGBOOT_BASE_URL", "http://localhost:8080/api")
    SPRINGBOOT_SERVICE_TOKEN = os.environ.get("SPRINGBOOT_SERVICE_TOKEN", "")
    SPRINGBOOT_SERVICE_HEADER = os.environ.get("SPRINGBOOT_SERVICE_HEADER", "X-Internal-Service-Token")
    SPRINGBOOT_ADMIN_USERNAME = os.environ.get("SPRINGBOOT_ADMIN_USERNAME", "admin")
    SPRINGBOOT_ADMIN_PASSWORD = os.environ.get("SPRINGBOOT_ADMIN_PASSWORD", "admin123")


class DevelopmentConfig(Config):
    SECRET_KEY = Config.SECRET_KEY or DEFAULT_SECRET_KEY
    DEBUG = True
    LOG_LEVEL = "DEBUG"


class ProductionConfig(Config):
    SECRET_KEY = Config.SECRET_KEY
    DEBUG = False
    LOG_LEVEL = "WARNING"


class TestingConfig(Config):
    SECRET_KEY = Config.SECRET_KEY or "test-secret-key"
    TESTING = True
    DEBUG = True


config = {
    "development": DevelopmentConfig,
    "production": ProductionConfig,
    "testing": TestingConfig,
    "default": DevelopmentConfig,
}


def get_config(env: str | None = None):
    runtime_env = env or os.environ.get("FLASK_ENV", "development")
    return config.get(runtime_env, config["default"])
