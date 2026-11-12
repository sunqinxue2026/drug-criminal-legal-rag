"""
Flask application entrypoint.
"""
from __future__ import annotations

import sys
from pathlib import Path

from flask import Flask

APP_DIR = Path(__file__).resolve().parent
if str(APP_DIR) not in sys.path:
    sys.path.insert(0, str(APP_DIR))

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover - optional dependency for local developer convenience
    def load_dotenv(*_args, **_kwargs):
        return False

try:
    from flask_cors import CORS
except ImportError:  # pragma: no cover - lightweight fallback for local runtime
    from flask import request

    def CORS(app, resources=None):
        raw_origins = app.config.get("CORS_ORIGINS", "*")
        allowed_origins = raw_origins if isinstance(raw_origins, (list, tuple, set)) else [raw_origins]
        normalized_origins = {str(origin).strip() for origin in allowed_origins if str(origin).strip()}

        @app.after_request
        def add_cors_headers(response):
            origin = request.headers.get("Origin", "")
            allow_any = "*" in normalized_origins or not normalized_origins
            if allow_any:
                response.headers["Access-Control-Allow-Origin"] = origin or "*"
            elif origin in normalized_origins:
                response.headers["Access-Control-Allow-Origin"] = origin

            response.headers["Vary"] = "Origin"
            response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
            response.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
            response.headers["Access-Control-Allow-Credentials"] = "true"
            return response

        return app


load_dotenv(APP_DIR / ".env")
load_dotenv(APP_DIR / ".env.local", override=True)

from config import get_config
from startup import initialize_runtime


def create_app() -> Flask:
    config_cls = get_config()
    app = Flask(__name__)
    app.config.from_object(config_cls)
    initialize_runtime(app)
    CORS(app, resources={r"/*": {"origins": app.config["CORS_ORIGINS"]}})

    from routes.chat import chat_bp
    from routes.document import document_bp
    from routes.health import health_bp
    from routes.llm import llm_bp
    from routes.vector import vector_bp

    app.register_blueprint(health_bp)
    app.register_blueprint(chat_bp)
    app.register_blueprint(llm_bp)
    app.register_blueprint(vector_bp)
    app.register_blueprint(document_bp)

    return app


if __name__ == "__main__":
    app = create_app()
    startup_warnings = app.config.get("STARTUP_WARNINGS", [])
    print("=" * 50)
    print("法律咨询智能问答系统 - 算法服务启动成功")
    print("服务地址: http://localhost:5000")
    print("已自动加载 .env / .env.local 配置文件（如果存在）")
    if startup_warnings:
        print(f"启动警告: {len(startup_warnings)} 项，请在上线前处理")
    print("=" * 50)
    app.run(host="0.0.0.0", port=5000, debug=bool(app.config.get("DEBUG")))
