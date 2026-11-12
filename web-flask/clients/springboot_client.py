"""
Spring Boot API client used by the Flask RAG service.
"""
from __future__ import annotations

import logging
from typing import Any

import requests

from config import get_config

logger = logging.getLogger(__name__)


class SpringBootClient:
    def __init__(self) -> None:
        config = get_config()
        self.base_url = config.SPRINGBOOT_BASE_URL.rstrip("/")
        self.timeout = config.HTTP_TIMEOUT_SECONDS
        self.admin_username = config.SPRINGBOOT_ADMIN_USERNAME
        self.admin_password = config.SPRINGBOOT_ADMIN_PASSWORD
        self.session = requests.Session()
        self._service_token = config.SPRINGBOOT_SERVICE_TOKEN
        self._service_header = config.SPRINGBOOT_SERVICE_HEADER
        self._bearer_token: str | None = None
        if self._service_token:
            self.session.headers[self._service_header] = self._service_token
        logger.info("Spring Boot client initialized: %s", self.base_url)

    def get_all_items(self) -> list[dict[str, Any]]:
        if self._service_token:
            return self._get("/internal/items")
        return self._get_paginated_records("/item/page")

    def get_all_legal_document_chunks(self) -> list[dict[str, Any]]:
        if self._service_token:
            return self._get("/internal/legal-document/chunks")
        return self._get_paginated_records("/legal-document/index/chunks")

    def get_all_legal_knowledge_chunks(self) -> list[dict[str, Any]]:
        if self._service_token:
            return self._get("/internal/legal-knowledge/chunks")
        return self._get_paginated_records("/legal-knowledge/index/chunks")

    def _get(self, path: str) -> list[dict[str, Any]]:
        url = f"{self.base_url}{path}"
        try:
            response = self.session.get(url, timeout=self.timeout)
            response.raise_for_status()
            payload = response.json()
            data = payload.get("data", payload)
            if isinstance(data, list):
                return data
            logger.warning("Unexpected Spring Boot payload for %s: %s", path, type(data).__name__)
            return []
        except requests.RequestException as exc:
            logger.warning("Spring Boot request failed for %s: %s", path, exc)
            return []

    def _get_paginated_records(self, path: str, page_size: int = 500) -> list[dict[str, Any]]:
        headers = self._admin_headers()
        records: list[dict[str, Any]] = []
        page_num = 1

        while True:
            url = f"{self.base_url}{path}"
            params = {"pageNum": page_num, "pageSize": page_size}
            try:
                response = self.session.get(url, params=params, headers=headers, timeout=self.timeout)
                response.raise_for_status()
                payload = response.json()
                data = payload.get("data", payload)
                page_records = data.get("records") if isinstance(data, dict) else None
                if not isinstance(page_records, list) or not page_records:
                    break
                records.extend(page_records)

                total_pages = data.get("pages") if isinstance(data, dict) else None
                if not isinstance(total_pages, int) or page_num >= total_pages:
                    break
                page_num += 1
            except requests.RequestException as exc:
                logger.warning("Spring Boot paged request failed for %s page %s: %s", path, page_num, exc)
                break

        return records

    def _admin_headers(self) -> dict[str, str]:
        token = self._ensure_admin_token()
        return {"Authorization": f"Bearer {token}"} if token else {}

    def _ensure_admin_token(self) -> str | None:
        if self._bearer_token:
            return self._bearer_token

        login_url = f"{self.base_url}/user/login"
        payload = {
            "username": self.admin_username,
            "password": self.admin_password,
        }
        try:
            response = self.session.post(login_url, json=payload, timeout=self.timeout)
            response.raise_for_status()
            body = response.json()
            data = body.get("data", body)
            token = data.get("token") if isinstance(data, dict) else None
            if isinstance(token, str) and token.strip():
                self._bearer_token = token.strip()
                return self._bearer_token
            logger.warning("Spring Boot login succeeded but token missing in payload")
            return None
        except requests.RequestException as exc:
            logger.warning("Spring Boot login failed: %s", exc)
            return None
