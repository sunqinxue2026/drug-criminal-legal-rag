"""
文档处理工具类
"""
import logging
import re
from typing import List, Dict

logger = logging.getLogger(__name__)

class DocumentProcessor:
    """文档处理器"""
    
    def __init__(self):
        """初始化文档处理器"""
        logger.info("文档处理器初始化")
    
    def clean_text(self, text: str) -> str:
        """
        清理文本
        
        Args:
            text: 原始文本
            
        Returns:
            清理后的文本
        """
        # 移除多余空白
        text = re.sub(r'\s+', ' ', text)
        # 移除特殊字符
        text = re.sub(r'[^\w\s\u4e00-\u9fff，。！？；：""''（）【】《》、]', '', text)
        return text.strip()
    
    def split_text(self, text: str, max_length: int = 500) -> List[str]:
        """
        分割长文本
        
        Args:
            text: 原始文本
            max_length: 最大长度
            
        Returns:
            文本片段列表
        """
        chunks = []
        sentences = re.split(r'[。！？]', text)
        
        current_chunk = ""
        for sentence in sentences:
            if len(current_chunk) + len(sentence) <= max_length:
                current_chunk += sentence + "。"
            else:
                if current_chunk:
                    chunks.append(current_chunk)
                current_chunk = sentence + "。"
        
        if current_chunk:
            chunks.append(current_chunk)
        
        return chunks
    
    def extract_keywords(self, text: str, top_k: int = 10) -> List[str]:
        """
        提取关键词
        
        Args:
            text: 文本
            top_k: 返回关键词数量
            
        Returns:
            关键词列表
        """
        # 简单的关键词提取（实际项目中应使用 jieba 等工具）
        words = re.findall(r'[\u4e00-\u9fff]+', text)
        word_freq = {}
        
        for word in words:
            if len(word) >= 2:  # 只统计2个字以上的词
                word_freq[word] = word_freq.get(word, 0) + 1
        
        # 按频率排序
        sorted_words = sorted(word_freq.items(), key=lambda x: x[1], reverse=True)
        return [word for word, freq in sorted_words[:top_k]]
    
    def process_document(self, content: str, metadata: Dict = None) -> Dict:
        """
        处理文档
        
        Args:
            content: 文档内容
            metadata: 元数据
            
        Returns:
            处理后的文档
        """
        # 清理文本
        cleaned_content = self.clean_text(content)
        
        # 提取关键词
        keywords = self.extract_keywords(cleaned_content)
        
        # 分割文本
        chunks = self.split_text(cleaned_content)
        
        result = {
            "original_content": content,
            "cleaned_content": cleaned_content,
            "keywords": keywords,
            "chunks": chunks,
            "metadata": metadata or {}
        }
        
        logger.info(f"文档处理完成，生成 {len(chunks)} 个片段")
        return result
