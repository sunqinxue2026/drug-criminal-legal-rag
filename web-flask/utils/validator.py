"""
数据验证工具类
"""
import re
from typing import Any, Dict, List, Optional

class Validator:
    """数据验证器"""
    
    @staticmethod
    def is_email(email: str) -> bool:
        """验证邮箱格式"""
        pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
        return bool(re.match(pattern, email))
    
    @staticmethod
    def is_phone(phone: str) -> bool:
        """验证手机号格式（中国）"""
        pattern = r'^1[3-9]\d{9}$'
        return bool(re.match(pattern, phone))
    
    @staticmethod
    def is_url(url: str) -> bool:
        """验证URL格式"""
        pattern = r'^https?://[^\s/$.?#].[^\s]*$'
        return bool(re.match(pattern, url))
    
    @staticmethod
    def is_chinese(text: str) -> bool:
        """验证是否为中文"""
        pattern = r'^[\u4e00-\u9fa5]+$'
        return bool(re.match(pattern, text))
    
    @staticmethod
    def validate_length(text: str, min_len: int = 0, max_len: int = None) -> bool:
        """验证字符串长度"""
        length = len(text)
        if length < min_len:
            return False
        if max_len and length > max_len:
            return False
        return True
    
    @staticmethod
    def validate_range(value: float, min_val: float = None, max_val: float = None) -> bool:
        """验证数值范围"""
        if min_val is not None and value < min_val:
            return False
        if max_val is not None and value > max_val:
            return False
        return True
    
    @staticmethod
    def validate_required(data: Dict, required_fields: List[str]) -> tuple[bool, Optional[str]]:
        """
        验证必填字段
        
        Returns:
            (是否通过, 错误信息)
        """
        for field in required_fields:
            if field not in data or not data[field]:
                return False, f"字段 {field} 不能为空"
        return True, None
    
    @staticmethod
    def sanitize_html(text: str) -> str:
        """清理HTML标签"""
        return re.sub(r'<[^>]+>', '', text)
    
    @staticmethod
    def validate_password(password: str) -> tuple[bool, Optional[str]]:
        """
        验证密码强度
        要求：至少8位，包含大小写字母和数字
        """
        if len(password) < 8:
            return False, "密码长度至少8位"
        
        if not re.search(r'[a-z]', password):
            return False, "密码必须包含小写字母"
        
        if not re.search(r'[A-Z]', password):
            return False, "密码必须包含大写字母"
        
        if not re.search(r'\d', password):
            return False, "密码必须包含数字"
        
        return True, None
