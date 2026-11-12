"""
Minimal file storage service with safer defaults for a graduation project.
"""
from __future__ import annotations

import logging
import os
import re
import uuid
from pathlib import Path

from flask import Flask, jsonify, request, send_file
from flask_cors import CORS


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


APP_ROOT = Path(__file__).resolve().parent
STORAGE_ROOT = Path(os.environ.get("FILE_STORAGE_ROOT", APP_ROOT / "storage")).resolve()
PUBLIC_BASE_URL = os.environ.get("FILE_PUBLIC_BASE_URL", "").strip().rstrip("/")
ALLOWED_ORIGINS = [
    item.strip()
    for item in os.environ.get(
        "FILE_ALLOWED_ORIGINS",
        "http://localhost:3000,http://localhost:5173,http://localhost:5174",
    ).split(",")
    if item.strip()
]
INTERNAL_SERVICE_TOKEN = os.environ.get("INTERNAL_SERVICE_TOKEN", "").strip()
INTERNAL_HEADER_NAME = os.environ.get("INTERNAL_SERVICE_HEADER", "X-Internal-Service-Token").strip() or "X-Internal-Service-Token"
MAX_CONTENT_LENGTH = int(os.environ.get("FILE_MAX_CONTENT_LENGTH", 20 * 1024 * 1024))
ALLOWED_EXTENSIONS = {
    item.strip().lower().lstrip(".")
    for item in os.environ.get(
        "FILE_ALLOWED_EXTENSIONS",
        "jpg,jpeg,png,gif,pdf,doc,docx,txt",
    ).split(",")
    if item.strip()
}
DEBUG_MODE = os.environ.get("FILE_DEBUG", "").strip().lower() in {"1", "true", "yes", "on"}
HOST = os.environ.get("FILE_HOST", "0.0.0.0").strip() or "0.0.0.0"
PORT = int(os.environ.get("FILE_PORT", "5001"))

BUCKET_PATTERN = re.compile(r"^[a-z0-9][a-z0-9-]{0,31}$")
OBJECT_KEY_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
LOOPBACK_ADDRESSES = {"127.0.0.1", "::1", "localhost"}


app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = MAX_CONTENT_LENGTH
CORS(app, resources={r"/*": {"origins": ALLOWED_ORIGINS or "*"}})
STORAGE_ROOT.mkdir(parents=True, exist_ok=True)


def success(data=None, message="操作成功", status=200):
    response = jsonify({
        "code": 200,
        "message": message,
        "data": data,
    })
    return response, status


def failure(message, status=400, code=None):
    response = jsonify({
        "code": code or status,
        "message": message,
    })
    return response, status


def _is_loopback_request() -> bool:
    remote_addr = (request.remote_addr or "").strip().lower()
    forwarded_for = (request.headers.get("X-Forwarded-For") or "").split(",")[0].strip().lower()
    return remote_addr in LOOPBACK_ADDRESSES or forwarded_for in LOOPBACK_ADDRESSES


def _require_internal_access():
    provided = (request.headers.get(INTERNAL_HEADER_NAME) or "").strip()
    if INTERNAL_SERVICE_TOKEN:
        if provided == INTERNAL_SERVICE_TOKEN:
            return None
        return failure("内部服务鉴权失败", status=401, code=401)

    if _is_loopback_request():
        logger.warning("INTERNAL_SERVICE_TOKEN 未配置，当前仅允许本机写入请求")
        return None

    return failure("文件服务未完成安全初始化，已拒绝非本机写入请求", status=503, code=503)


def _normalize_bucket(raw_bucket: str) -> str:
    bucket = (raw_bucket or "default").strip().lower()
    if not BUCKET_PATTERN.fullmatch(bucket):
        raise ValueError("bucket 非法，只允许小写字母、数字和连字符")
    return bucket


def _normalize_extension(filename: str) -> str:
    suffix = Path(filename or "").suffix.lower().lstrip(".")
    if not suffix:
        raise ValueError("文件缺少扩展名")
    if suffix not in ALLOWED_EXTENSIONS:
        raise ValueError(f"不支持的文件类型: .{suffix}")
    return suffix


def _normalize_object_key(raw_object_key: str) -> str:
    object_key = (raw_object_key or "").strip()
    if ".." in object_key or not OBJECT_KEY_PATTERN.fullmatch(object_key):
        raise ValueError("object_key 非法")
    return object_key


def _resolve_bucket_dir(bucket: str) -> Path:
    bucket_dir = (STORAGE_ROOT / bucket).resolve()
    if STORAGE_ROOT not in bucket_dir.parents and bucket_dir != STORAGE_ROOT:
        raise ValueError("bucket 路径非法")
    return bucket_dir


def _resolve_file_path(bucket: str, object_key: str) -> Path:
    bucket_dir = _resolve_bucket_dir(bucket)
    file_path = (bucket_dir / object_key).resolve()
    if bucket_dir not in file_path.parents:
        raise ValueError("文件路径非法")
    return file_path


def _build_public_base_url() -> str:
    if PUBLIC_BASE_URL:
        return PUBLIC_BASE_URL

    forwarded_proto = (request.headers.get("X-Forwarded-Proto") or "").strip()
    forwarded_host = (request.headers.get("X-Forwarded-Host") or "").strip()
    if forwarded_host:
        scheme = forwarded_proto or request.scheme or "http"
        return f"{scheme}://{forwarded_host}".rstrip("/")
    return request.host_url.rstrip("/")


def _build_file_url(bucket: str, object_key: str) -> str:
    return f"{_build_public_base_url()}/file/{bucket}/{object_key}"


@app.route("/health", methods=["GET"])
def health_check():
    warnings = []
    if not INTERNAL_SERVICE_TOKEN:
        warnings.append("INTERNAL_SERVICE_TOKEN 未配置，仅允许本机写入")
    if DEBUG_MODE:
        warnings.append("FILE_DEBUG 当前为开启状态")

    return success({
        "status": "healthy",
        "service": "file-storage",
        "storageRoot": str(STORAGE_ROOT),
        "maxContentLength": MAX_CONTENT_LENGTH,
        "tokenConfigured": bool(INTERNAL_SERVICE_TOKEN),
        "allowedOrigins": ALLOWED_ORIGINS,
        "warnings": warnings,
    })


@app.route("/file/upload", methods=["POST"])
@app.route("/upload", methods=["POST"])
def upload_file():
    access_error = _require_internal_access()
    if access_error:
        return access_error

    if "file" not in request.files:
        return failure("没有检测到上传文件")

    file = request.files["file"]
    if not file.filename:
        return failure("文件名不能为空")

    try:
        bucket = _normalize_bucket(request.form.get("bucket", "default"))
        extension = _normalize_extension(file.filename)
        bucket_dir = _resolve_bucket_dir(bucket)
        bucket_dir.mkdir(parents=True, exist_ok=True)

        object_key = f"{uuid.uuid4().hex}.{extension}"
        file_path = _resolve_file_path(bucket, object_key)
        file.save(file_path)

        logger.info("文件上传成功: bucket=%s object_key=%s", bucket, object_key)
        return success({
            "bucket": bucket,
            "objectKey": object_key,
            "fileName": file.filename,
            "url": _build_file_url(bucket, object_key),
        }, message="上传成功")
    except ValueError as exc:
        return failure(str(exc))
    except Exception as exc:  # pragma: no cover
        logger.error("文件上传失败: %s", exc, exc_info=True)
        return failure(f"上传失败: {exc}", status=500, code=500)


@app.route("/file/<bucket>/<object_key>", methods=["GET"])
@app.route("/files/<bucket>/<object_key>", methods=["GET"])
def get_file(bucket: str, object_key: str):
    try:
        normalized_bucket = _normalize_bucket(bucket)
        normalized_object_key = _normalize_object_key(object_key)
        file_path = _resolve_file_path(normalized_bucket, normalized_object_key)
        if not file_path.exists() or not file_path.is_file():
            return failure("文件不存在", status=404, code=404)
        return send_file(file_path)
    except ValueError as exc:
        return failure(str(exc))
    except Exception as exc:  # pragma: no cover
        logger.error("文件读取失败: %s", exc, exc_info=True)
        return failure(f"读取失败: {exc}", status=500, code=500)


@app.route("/file/<bucket>/<object_key>", methods=["DELETE"])
@app.route("/files/<bucket>/<object_key>", methods=["DELETE"])
def delete_file(bucket: str, object_key: str):
    access_error = _require_internal_access()
    if access_error:
        return access_error

    try:
        normalized_bucket = _normalize_bucket(bucket)
        normalized_object_key = _normalize_object_key(object_key)
        file_path = _resolve_file_path(normalized_bucket, normalized_object_key)
        if not file_path.exists():
            return failure("文件不存在", status=404, code=404)

        file_path.unlink()
        logger.info("文件删除成功: bucket=%s object_key=%s", normalized_bucket, normalized_object_key)
        return success(message="删除成功")
    except ValueError as exc:
        return failure(str(exc))
    except Exception as exc:  # pragma: no cover
        logger.error("文件删除失败: %s", exc, exc_info=True)
        return failure(f"删除失败: {exc}", status=500, code=500)


if __name__ == "__main__":
    print("=" * 50)
    print("文件存储服务启动成功")
    print(f"服务地址: http://localhost:{PORT}")
    print(f"存储目录: {STORAGE_ROOT}")
    if not INTERNAL_SERVICE_TOKEN:
        print("警告: 未配置 INTERNAL_SERVICE_TOKEN，仅允许本机写入")
    print("=" * 50)
    app.run(host=HOST, port=PORT, debug=DEBUG_MODE)
