"""
Application startup initialization and validation.
"""
from __future__ import annotations

import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path

from flask import Flask


DEFAULT_SECRET_KEY = "dev-secret-key-change-in-production"
DEFAULT_INTERNAL_SERVICE_TOKEN = "change-me"
LOCAL_HOST_MARKERS = ("localhost", "127.0.0.1")


def initialize_runtime(app: Flask) -> None:
    _ensure_runtime_directories(app)
    _configure_logging(app)
    warnings = _validate_config(app)
    app.config["STARTUP_WARNINGS"] = warnings

    logger = logging.getLogger(__name__)
    for warning in warnings:
        logger.warning(warning)


def _resolve_path(app: Flask, raw_path: str) -> Path:
    path = Path(raw_path).expanduser()
    if path.is_absolute():
        return path
    return (Path(app.root_path) / path).resolve()


def _ensure_runtime_directories(app: Flask) -> None:
    directories = {
        _resolve_path(app, app.config["UPLOAD_FOLDER"]),
        _resolve_path(app, app.config["INDEX_DIR"]),
        _resolve_path(app, app.config["LOG_FILE"]).parent,
    }
    for directory in directories:
        directory.mkdir(parents=True, exist_ok=True)


def _configure_logging(app: Flask) -> None:
    level_name = str(app.config.get("LOG_LEVEL", "INFO")).upper()
    log_level = getattr(logging, level_name, logging.INFO)
    formatter = logging.Formatter("%(asctime)s - %(name)s - %(levelname)s - %(message)s")

    root_logger = logging.getLogger()
    root_logger.handlers.clear()
    root_logger.setLevel(log_level)

    console_handler = logging.StreamHandler()
    console_handler.setLevel(log_level)
    console_handler.setFormatter(formatter)
    root_logger.addHandler(console_handler)

    log_file = _resolve_path(app, app.config["LOG_FILE"])
    file_handler = RotatingFileHandler(
        log_file,
        maxBytes=max(int(app.config.get("LOG_MAX_BYTES", 0)), 1),
        backupCount=max(int(app.config.get("LOG_BACKUP_COUNT", 0)), 1),
        encoding="utf-8",
    )
    file_handler.setLevel(log_level)
    file_handler.setFormatter(formatter)
    root_logger.addHandler(file_handler)


def _validate_config(app: Flask) -> list[str]:
    warnings: list[str] = []
    errors: list[str] = []

    env_name = str(app.config.get("ENV_NAME", "development")).lower()
    is_production = env_name == "production"

    secret_key = str(app.config.get("SECRET_KEY", "")).strip()
    if not secret_key or secret_key == DEFAULT_SECRET_KEY:
        message = "SECRET_KEY 仍在使用默认值，上线前必须替换。"
        (errors if is_production else warnings).append(message)

    if app.config.get("DEBUG") and is_production:
        errors.append("生产环境禁止开启 FLASK_DEBUG。")

    cors_origins = list(app.config.get("CORS_ORIGINS") or [])
    if not cors_origins:
        errors.append("CORS_ORIGINS 不能为空。")
    elif is_production and any(_contains_localhost(origin) for origin in cors_origins):
        warnings.append("生产环境仍允许 localhost 跨域来源，请按实际域名收紧。")

    springboot_base_url = str(app.config.get("SPRINGBOOT_BASE_URL", "")).strip()
    if not springboot_base_url:
        errors.append("SPRINGBOOT_BASE_URL 未配置。")
    elif is_production and _contains_localhost(springboot_base_url):
        warnings.append("生产环境仍指向 localhost 的 Spring Boot 地址，请检查网关或服务发现配置。")

    service_token = str(app.config.get("SPRINGBOOT_SERVICE_TOKEN", "")).strip()
    if not service_token or service_token == DEFAULT_INTERNAL_SERVICE_TOKEN:
        message = "SPRINGBOOT_SERVICE_TOKEN 未配置为安全值，内部数据接口鉴权存在风险。"
        (errors if is_production else warnings).append(message)

    if not app.config.get("LLM_API_KEY"):
        warnings.append("LLM_API_KEY 未配置，问答接口将退化为演示模式。")

    if int(app.config.get("HTTP_TIMEOUT_SECONDS", 0)) <= 0:
        errors.append("HTTP_TIMEOUT_SECONDS 必须大于 0。")

    if int(app.config.get("MAX_CONTENT_LENGTH", 0)) <= 0:
        errors.append("MAX_CONTENT_LENGTH 必须大于 0。")

    bridge_enabled = bool(app.config.get("BGE_ENABLE_BRIDGE"))
    python_exe = str(app.config.get("BGE_PYTHON_EXE", "")).strip()
    if bridge_enabled and python_exe and not Path(python_exe).exists():
        warnings.append("BGE_PYTHON_EXE 指向的解释器不存在，向量索引构建可能失败。")

    if errors:
        raise RuntimeError("启动配置校验失败：" + "；".join(errors))

    return warnings


def _contains_localhost(value: str) -> bool:
    lower_value = value.lower()
    return any(marker in lower_value for marker in LOCAL_HOST_MARKERS)
