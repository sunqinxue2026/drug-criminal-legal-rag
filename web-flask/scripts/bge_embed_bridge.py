import json
import logging
import sys

import numpy as np
from sentence_transformers import SentenceTransformer


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

MODEL_NAME = 'BAAI/bge-small-zh-v1.5'
_model = None


def get_model():
    global _model
    if _model is None:
        logger.info("Loading bridge model: %s", MODEL_NAME)
        _model = SentenceTransformer(MODEL_NAME)
    return _model


def encode_payload(payload):
    texts = payload.get("texts", [])
    normalize = payload.get("normalize_embeddings", True)
    model = get_model()
    vectors = model.encode(texts, normalize_embeddings=normalize)
    return {"vectors": np.array(vectors, dtype="float32").tolist()}


def run_once():
    payload = json.load(sys.stdin)
    json.dump(encode_payload(payload), sys.stdout, ensure_ascii=False)


def run_server():
    get_model()
    sys.stdout.write(json.dumps({"status": "ready"}, ensure_ascii=False) + "\n")
    sys.stdout.flush()

    for line in sys.stdin:
        raw = line.strip()
        if not raw:
            continue

        payload = json.loads(raw)
        if payload.get("command") == "shutdown":
            sys.stdout.write(json.dumps({"status": "bye"}, ensure_ascii=False) + "\n")
            sys.stdout.flush()
            break

        result = encode_payload(payload)
        sys.stdout.write(json.dumps(result, ensure_ascii=False) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    if "--server" in sys.argv:
        run_server()
    else:
        run_once()
