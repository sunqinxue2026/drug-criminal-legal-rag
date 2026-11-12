"""
性能监控工具
"""
import time
import logging
from functools import wraps
from typing import Callable

logger = logging.getLogger(__name__)

def timer(func: Callable) -> Callable:
    """
    计时装饰器
    用于测量函数执行时间
    """
    @wraps(func)
    def wrapper(*args, **kwargs):
        start_time = time.time()
        result = func(*args, **kwargs)
        end_time = time.time()
        
        elapsed_time = end_time - start_time
        logger.info(f"{func.__name__} 执行时间: {elapsed_time:.4f}秒")
        
        return result
    return wrapper

def retry(max_attempts: int = 3, delay: float = 1.0):
    """
    重试装饰器
    
    Args:
        max_attempts: 最大重试次数
        delay: 重试延迟（秒）
    """
    def decorator(func: Callable) -> Callable:
        @wraps(func)
        def wrapper(*args, **kwargs):
            attempts = 0
            while attempts < max_attempts:
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    attempts += 1
                    if attempts >= max_attempts:
                        logger.error(f"{func.__name__} 重试{max_attempts}次后仍然失败: {str(e)}")
                        raise
                    
                    logger.warning(f"{func.__name__} 第{attempts}次尝试失败，{delay}秒后重试...")
                    time.sleep(delay)
            
            return None
        return wrapper
    return decorator

class PerformanceMonitor:
    """性能监控器"""
    
    def __init__(self):
        """初始化性能监控器"""
        self.metrics = {}
    
    def record(self, name: str, value: float):
        """
        记录性能指标
        
        Args:
            name: 指标名称
            value: 指标值
        """
        if name not in self.metrics:
            self.metrics[name] = []
        
        self.metrics[name].append({
            'value': value,
            'timestamp': time.time()
        })
    
    def get_average(self, name: str) -> float:
        """
        获取平均值
        
        Args:
            name: 指标名称
            
        Returns:
            平均值
        """
        if name not in self.metrics or not self.metrics[name]:
            return 0.0
        
        values = [m['value'] for m in self.metrics[name]]
        return sum(values) / len(values)
    
    def get_stats(self, name: str) -> dict:
        """
        获取统计信息
        
        Args:
            name: 指标名称
            
        Returns:
            统计信息字典
        """
        if name not in self.metrics or not self.metrics[name]:
            return {
                'count': 0,
                'average': 0.0,
                'min': 0.0,
                'max': 0.0
            }
        
        values = [m['value'] for m in self.metrics[name]]
        
        return {
            'count': len(values),
            'average': sum(values) / len(values),
            'min': min(values),
            'max': max(values)
        }
    
    def clear(self, name: str = None):
        """
        清除指标
        
        Args:
            name: 指标名称，如果为None则清除所有
        """
        if name:
            if name in self.metrics:
                del self.metrics[name]
        else:
            self.metrics.clear()
    
    def get_all_stats(self) -> dict:
        """
        获取所有指标的统计信息
        
        Returns:
            所有统计信息
        """
        return {
            name: self.get_stats(name)
            for name in self.metrics.keys()
        }

# 全局性能监控器实例
performance_monitor = PerformanceMonitor()
