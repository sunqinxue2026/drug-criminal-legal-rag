"""
BGE embedding client with subprocess and deterministic fallbacks.
"""
import hashlib
import json
import logging
import os
import subprocess
import threading
from pathlib import Path

import numpy as np

from config import get_config

logger = logging.getLogger(__name__)


class BGEClient:
    """Generate embeddings for retrieval."""

    _bridge_process = None
    _bridge_lock = threading.Lock()
    _bridge_ready = False

    def __init__(self, model_name='BAAI/bge-small-zh-v1.5'):
        config = get_config()
        self.model_name = model_name or getattr(config, 'BGE_MODEL_NAME', 'BAAI/bge-small-zh-v1.5')
        self.dimension = self._infer_dimension(self.model_name)
        self.model = None
        self._fallback_mode = False
        self._subprocess_mode = False
        self._bridge_python = getattr(config, 'BGE_PYTHON_EXE', None)
        self._enable_bridge = bool(getattr(config, 'BGE_ENABLE_BRIDGE', False))
        self._bridge_script = Path(__file__).resolve().parents[2] / 'scripts' / 'bge_embed_bridge.py'
        logger.info("BGE client initialized: %s", model_name)
        if self._bridge_available():
            try:
                self._ensure_bridge_process()
            except Exception as exc:
                logger.warning("Persistent BGE bridge startup failed, fallback will be used: %s", exc)
                self._fallback_mode = True

    def _infer_dimension(self, model_name):
        lowered = (model_name or '').lower()
        if 'large' in lowered:
            return 1024
        if 'base' in lowered:
            return 768
        return 512

    def _load_model(self):
        if self.model is not None or self._fallback_mode or self._subprocess_mode:
            return

        try:
            from sentence_transformers import SentenceTransformer

            logger.info("Loading BGE model locally: %s", self.model_name)
            self.model = SentenceTransformer(self.model_name)
            logger.info("BGE model loaded successfully in current interpreter")
        except Exception as exc:
            logger.warning("Local BGE load failed: %s", exc)
            if self._bridge_available():
                self._subprocess_mode = True
                logger.info("Using bridge interpreter for embeddings: %s", self._bridge_python)
            else:
                logger.warning("Falling back to hash embeddings for fast local retrieval")
                self._fallback_mode = True

    def _bridge_available(self):
        return self._enable_bridge and bool(self._bridge_python) and self._bridge_script.exists()

    def _ensure_bridge_process(self):
        if not self._bridge_available():
            return False

        process = BGEClient._bridge_process
        if process is not None and process.poll() is None and BGEClient._bridge_ready:
            self._subprocess_mode = True
            return True

        with BGEClient._bridge_lock:
            process = BGEClient._bridge_process
            if process is not None and process.poll() is None and BGEClient._bridge_ready:
                self._subprocess_mode = True
                return True

            logger.info("Starting persistent BGE bridge: %s", self._bridge_python)
            process = subprocess.Popen(
                [self._bridge_python, str(self._bridge_script), "--server"],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                encoding='utf-8',
                bufsize=1,
                env={
                    **os.environ,
                    'PYTHONIOENCODING': 'utf-8',
                    'HF_HUB_DISABLE_SYMLINKS_WARNING': '1',
                },
            )

            ready_line = process.stdout.readline().strip() if process.stdout else ""
            if not ready_line:
                process.terminate()
                raise RuntimeError("Persistent BGE bridge failed to start")

            ready_payload = json.loads(ready_line)
            if ready_payload.get("status") != "ready":
                process.terminate()
                raise RuntimeError(f"Unexpected bridge startup response: {ready_payload}")

            BGEClient._bridge_process = process
            BGEClient._bridge_ready = True
            self._subprocess_mode = True
            logger.info("Persistent BGE bridge is ready")
            return True

    def _encode_via_bridge(self, texts):
        if not self._ensure_bridge_process():
            raise RuntimeError("Persistent BGE bridge is unavailable")

        process = BGEClient._bridge_process
        payload = json.dumps({"texts": texts, "normalize_embeddings": True}, ensure_ascii=False)

        with BGEClient._bridge_lock:
            if process is None or process.poll() is not None or process.stdin is None or process.stdout is None:
                BGEClient._bridge_process = None
                BGEClient._bridge_ready = False
                raise RuntimeError("Persistent BGE bridge exited unexpectedly")

            process.stdin.write(payload + "\n")
            process.stdin.flush()
            raw = process.stdout.readline().strip()

        if not raw:
            raise RuntimeError("Persistent BGE bridge returned an empty response")

        response = json.loads(raw)
        return np.array(response["vectors"], dtype='float32')

    def _fallback_encode_single(self, text: str) -> np.ndarray:
        vector = np.zeros(self.dimension, dtype='float32')
        normalized = (text or "").strip()
        if not normalized:
            return vector

        for token in normalized.split():
            digest = hashlib.sha256(token.encode('utf-8')).digest()
            idx = int.from_bytes(digest[:4], 'little') % self.dimension
            sign = 1.0 if digest[4] % 2 == 0 else -1.0
            weight = 1.0 + (digest[5] / 255.0)
            vector[idx] += sign * weight

        for char in normalized:
            digest = hashlib.md5(char.encode('utf-8')).digest()
            idx = int.from_bytes(digest[:4], 'little') % self.dimension
            vector[idx] += 0.25

        norm = np.linalg.norm(vector)
        if norm > 0:
            vector /= norm
        return vector

    def encode(self, text):
        self._load_model()

        is_single = isinstance(text, str)
        texts = [text] if is_single else list(text)

        logger.info("Encoding %s text item(s)", len(texts))
        if self.model is not None:
            vectors = self.model.encode(texts, normalize_embeddings=True)
        elif self._subprocess_mode:
            vectors = self._encode_via_bridge(texts)
        else:
            vectors = [self._fallback_encode_single(item) for item in texts]

        vectors = np.array(vectors, dtype='float32')
        logger.info("Embedding shape: %s", vectors.shape)
        return vectors[0] if is_single else vectors

    def encode_batch(self, texts, batch_size=32):
        self._load_model()

        logger.info("Batch encoding %s text item(s)", len(texts))
        if self.model is not None:
            vectors = self.model.encode(
                texts,
                batch_size=batch_size,
                normalize_embeddings=True,
                show_progress_bar=True,
            )
        elif self._subprocess_mode:
            vectors = self._encode_via_bridge(list(texts))
        else:
            vectors = [self._fallback_encode_single(text) for text in texts]

        return np.array(vectors, dtype='float32')

    def get_dimension(self):
        return self.dimension

    def get_status(self):
        process = BGEClient._bridge_process
        bridge_alive = process is not None and process.poll() is None
        mode = "local"
        if self._fallback_mode:
            mode = "fallback"
        elif self._subprocess_mode:
            mode = "bridge"

        return {
            "model_name": self.model_name,
            "dimension": self.dimension,
            "mode": mode,
            "bridge_enabled": self._enable_bridge,
            "bridge_ready": bridge_alive and BGEClient._bridge_ready,
            "local_model_loaded": self.model is not None,
            "fallback_mode": self._fallback_mode,
        }
