"""
缓存工具类
"""
from typing import Any, Optional
import time
import logging

logger = logging.getLogger(__name__)

class CacheManager:
    """简单的内存缓存管理器"""
    
    def __init__(self, default_ttl: int = 3600):
        """
        初始化缓存管理器
        
        Args:
            default_ttl: 默认过期时间（秒）
        """
        self.cache = {}
        self.default_ttl = default_ttl
        logger.info(f"缓存管理器初始化，默认TTL: {default_ttl}秒")
    
    def set(self, key: str, value: Any, ttl: Optional[int] = None) -> None:
        """
        设置缓存
        
        Args:
            key: 缓存键
            value: 缓存值
            ttl: 过期时间（秒）
        """
        expire_time = time.time() + (ttl or self.default_ttl)
        self.cache[key] = {
            'value': value,
            'expire_time': expire_time
        }
        logger.debug(f"缓存设置: {key}")
    
    def get(self, key: str) -> Optional[Any]:
        """
        获取缓存
        
        Args:
            key: 缓存键
            
        Returns:
            缓存值，如果不存在或已过期则返回 None
        """
        if key not in self.cache:
            return None
        
        item = self.cache[key]
        
        # 检查是否过期
        if time.time() > item['expire_time']:
            del self.cache[key]
            logger.debug(f"缓存过期: {key}")
            return None
        
        logger.debug(f"缓存命中: {key}")
        return item['value']
    
    def delete(self, key: str) -> bool:
        """
        删除缓存
        
        Args:
            key: 缓存键
            
        Returns:
            是否删除成功
        """
        if key in self.cache:
            del self.cache[key]
            logger.debug(f"缓存删除: {key}")
            return True
        return False
    
    def clear(self) -> None:
        """清空所有缓存"""
        self.cache.clear()
        logger.info("缓存已清空")
    
    def cleanup_expired(self) -> int:
        """
        清理过期缓存
        
        Returns:
            清理的缓存数量
        """
        current_time = time.time()
        expired_keys = [
            key for key, item in self.cache.items()
            if current_time > item['expire_time']
        ]
        
        for key in expired_keys:
            del self.cache[key]
        
        if expired_keys:
            logger.info(f"清理过期缓存: {len(expired_keys)} 个")
        
        return len(expired_keys)
    
    def get_stats(self) -> dict:
        """
        获取缓存统计信息
        
        Returns:
            统计信息字典
        """
        total = len(self.cache)
        current_time = time.time()
        expired = sum(1 for item in self.cache.values() if current_time > item['expire_time'])
        
        return {
            'total': total,
            'active': total - expired,
            'expired': expired
        }
