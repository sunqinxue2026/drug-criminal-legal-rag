"""
初始化脚本 - 创建默认索引和数据
"""
import logging
from algo.rag_engine import RAGEngine

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def init_indexes():
    """初始化向量索引"""
    try:
        logger.info("开始初始化索引...")
        
        rag_engine = RAGEngine()
        
        # 创建法律案例索引
        rag_engine.index_manager.create_index("legal_cases", dimension=1024)
        
        # 添加示例文档
        sample_docs = [
            {
                "content": "涉毒刑事风险案例：行为人明知他人贩卖毒品仍代为联络交易、收取毒资，可能构成共同犯罪...",
                "metadata": {
                    "title": "涉毒共同犯罪风险案例",
                    "category": "涉毒刑事专题",
                    "tags": "贩卖毒品,共同犯罪"
                }
            },
            {
                "content": "程序普法案例：当事人被查获后，聊天记录、转账记录、毒品实物和指认笔录会共同影响案件认定...",
                "metadata": {
                    "title": "涉毒案件证据链案例",
                    "category": "涉毒刑事专题",
                    "tags": "证据,转账记录,聊天记录"
                }
            },
            {
                "content": "普法预警案例：容留他人吸毒、教唆吸毒或者为娱乐场所涉毒活动提供帮助，可能分别触发独立刑事风险...",
                "metadata": {
                    "title": "容留吸毒风险案例",
                    "category": "涉毒刑事专题",
                    "tags": "容留吸毒,普法"
                }
            }
        ]
        
        rag_engine.add_documents(sample_docs, index_name="legal_cases")
        
        # 保存索引
        rag_engine.index_manager.save_index("legal_cases")
        
        logger.info("索引初始化完成！")
        
    except Exception as e:
        logger.error(f"初始化失败: {str(e)}")
        raise

if __name__ == "__main__":
    init_indexes()
