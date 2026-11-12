"""
LLM service routes.
"""
from __future__ import annotations

from flask import Blueprint, jsonify, request

from algo.llm.llm_client import LLMClient
from config import get_config
from utils.response import error, success

llm_bp = Blueprint("llm", __name__, url_prefix="/api/llm")
config = get_config()
llm_client = LLMClient(
    api_url=config.LLM_API_URL,
    api_key=config.LLM_API_KEY,
    model=config.LLM_MODEL,
    allowed_models=config.LLM_ALLOWED_MODELS,
)


@llm_bp.route("/test", methods=["GET"])
def llm_test():
    return jsonify(success({"message": "LLM service is running"}))


@llm_bp.route("/models", methods=["GET"])
def get_models():
    return jsonify(success(llm_client.get_model_info()))


@llm_bp.route("/resolve-model", methods=["POST"])
def resolve_model():
    data = request.get_json() or {}
    requested_model = data.get("model")
    if not requested_model:
        return jsonify(error("model 不能为空", 400)), 400
    return jsonify(success({
        "requested_model": requested_model,
        "resolved_model": llm_client.resolve_model(requested_model),
    }))
