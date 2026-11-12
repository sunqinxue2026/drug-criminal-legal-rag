"""
Utilities for safely fetching remote images.
"""
from __future__ import annotations

from dataclasses import dataclass
import ipaddress
import socket
from urllib.parse import urlparse

import requests


MAX_IMAGE_BYTES = 10 * 1024 * 1024
_ALLOWED_SCHEMES = {"http", "https"}
_LOCAL_HOSTNAMES = {"localhost", "localhost.localdomain"}


class ImageFetchError(ValueError):
    """Raised when a remote image request is rejected or invalid."""


@dataclass(frozen=True)
class ImageFetchResult:
    content: bytes
    content_type: str


def _is_public_ip(value: str) -> bool:
    try:
        return ipaddress.ip_address(value).is_global
    except ValueError:
        return False


def _resolve_public_addresses(hostname: str) -> list[str]:
    try:
        address_info = socket.getaddrinfo(hostname, None, proto=socket.IPPROTO_TCP)
    except socket.gaierror as exc:
        raise ImageFetchError("图片地址无法解析") from exc

    addresses: list[str] = []
    for family, _, _, _, sockaddr in address_info:
        if family == socket.AF_INET:
            addresses.append(sockaddr[0])
        elif family == socket.AF_INET6:
            addresses.append(sockaddr[0])

    if not addresses:
        raise ImageFetchError("图片地址无法解析")

    for address in addresses:
        try:
            if not ipaddress.ip_address(address).is_global:
                raise ImageFetchError("图片地址必须是公网地址")
        except ValueError as exc:
            raise ImageFetchError("图片地址解析结果无效") from exc

    return addresses


def validate_public_image_url(image_url: str) -> str:
    parsed = urlparse((image_url or "").strip())
    if parsed.scheme not in _ALLOWED_SCHEMES:
        raise ImageFetchError("仅支持 http 或 https 图片地址")

    hostname = (parsed.hostname or "").strip().lower()
    if not hostname:
        raise ImageFetchError("图片地址缺少主机名")
    if hostname in _LOCAL_HOSTNAMES:
        raise ImageFetchError("不允许访问本地或内网图片地址")

    if _is_public_ip(hostname):
        return image_url

    try:
        ipaddress.ip_address(hostname)
    except ValueError:
        _resolve_public_addresses(hostname)
        return image_url

    raise ImageFetchError("不允许访问本地或内网图片地址")


def fetch_public_image(image_url: str, timeout: int = 30, max_bytes: int = MAX_IMAGE_BYTES) -> ImageFetchResult:
    validated_url = validate_public_image_url(image_url)

    try:
        response = requests.get(validated_url, timeout=timeout, stream=True, allow_redirects=False)
        response.raise_for_status()
    except requests.RequestException as exc:
        raise ImageFetchError("图片下载失败") from exc

    content_type = (response.headers.get("Content-Type") or "").split(";", 1)[0].strip().lower()
    if not content_type.startswith("image/"):
        raise ImageFetchError("图片地址返回的不是图片内容")

    content = bytearray()
    for chunk in response.iter_content(chunk_size=8192):
        if not chunk:
            continue
        content.extend(chunk)
        if len(content) > max_bytes:
            raise ImageFetchError("图片大小超过限制")

    if not content:
        raise ImageFetchError("图片内容为空")

    return ImageFetchResult(content=bytes(content), content_type=content_type)
