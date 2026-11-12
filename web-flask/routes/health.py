"""
Health check routes.
"""
from __future__ import annotations

from flask import Blueprint, current_app, jsonify

from utils.response import success

health_bp = Blueprint("health", __name__)


@health_bp.route("/health", methods=["GET"])
def health_check():
    return jsonify(success({
        "status": "healthy",
        "service": "legal-rag-algorithm",
        "env": current_app.config.get("ENV_NAME", "development"),
        "startup_warnings": current_app.config.get("STARTUP_WARNINGS", []),
    }))


@health_bp.route("/ping", methods=["GET"])
def ping():
    return jsonify({"message": "pong"})
