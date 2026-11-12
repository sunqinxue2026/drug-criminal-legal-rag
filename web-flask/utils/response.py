"""
Unified response helpers.
"""
from __future__ import annotations


def success(data=None, message: str = "操作成功"):
    return {
        "code": 200,
        "message": message,
        "data": data,
    }


def error(message: str = "操作失败", code: int = 500, data=None):
    return {
        "code": code,
        "message": message,
        "data": data,
    }


def paginate(items, total: int, page: int, size: int):
    return success({
        "items": items,
        "total": total,
        "page": page,
        "size": size,
        "pages": (total + size - 1) // size if size else 0,
    })
