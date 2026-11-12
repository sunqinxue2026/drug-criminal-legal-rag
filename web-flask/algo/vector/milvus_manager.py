"""
Milvus 向量数据库管理器
"""
import logging
from typing import List, Dict, Any, Optional
import numpy as np

logger = logging.getLogger(__name__)

try:
    from pymilvus import (
        connections,
        Collection,
        CollectionSchema,
        FieldSchema,
        DataType,
        utility
    )
    MILVUS_AVAILABLE = True
except ImportError:
    MILVUS_AVAILABLE = False
    logger.warning("Milvus 未安装，将使用 FAISS 作为替代")

class MilvusManager:
    """Milvus 向量数据库管理器"""
    
    def __init__(self, host: str = "localhost", port: int = 19530):
        """
        初始化 Milvus 管理器
        
        Args:
            host: Milvus 服务器地址
            port: Milvus 服务器端口
        """
        self.host = host
        self.port = port
        self.connected = False
        
        if MILVUS_AVAILABLE:
            try:
                connections.connect(
                    alias="default",
                    host=host,
                    port=port
                )
                self.connected = True
                logger.info(f"连接到 Milvus: {host}:{port}")
            except Exception as e:
                logger.error(f"连接 Milvus 失败: {str(e)}")
        else:
            logger.warning("Milvus 不可用")
    
    def create_collection(
        self,
        name: str,
        dimension: int = 1024,
        description: str = ""
    ) -> Optional[Collection]:
        """
        创建集合
        
        Args:
            name: 集合名称
            dimension: 向量维度
            description: 集合描述
            
        Returns:
            Collection 对象
        """
        if not self.connected:
            logger.error("未连接到 Milvus")
            return None
        
        # 检查集合是否存在
        if utility.has_collection(name):
            logger.info(f"集合已存在: {name}")
            return Collection(name)
        
        # 定义字段
        fields = [
            FieldSchema(
                name="id",
                dtype=DataType.INT64,
                is_primary=True,
                auto_id=False,
                description="案例ID"
            ),
            FieldSchema(
                name="title",
                dtype=DataType.VARCHAR,
                max_length=255,
                description="标题"
            ),
            FieldSchema(
                name="content",
                dtype=DataType.VARCHAR,
                max_length=5000,
                description="内容摘要"
            ),
            FieldSchema(
                name="category_id",
                dtype=DataType.INT64,
                description="分类ID"
            ),
            FieldSchema(
                name="tags",
                dtype=DataType.VARCHAR,
                max_length=255,
                description="标签"
            ),
            FieldSchema(
                name="create_time",
                dtype=DataType.INT64,
                description="创建时间戳"
            ),
            FieldSchema(
                name="vector",
                dtype=DataType.FLOAT_VECTOR,
                dim=dimension,
                description="向量"
            )
        ]
        
        # 创建集合
        schema = CollectionSchema(
            fields=fields,
            description=description or f"{name} 向量集合"
        )
        
        collection = Collection(
            name=name,
            schema=schema
        )
        
        logger.info(f"创建集合: {name}, 维度: {dimension}")
        return collection
    
    def create_index(
        self,
        collection_name: str,
        index_type: str = "IVF_FLAT",
        metric_type: str = "L2",
        nlist: int = 1024
    ) -> bool:
        """
        创建索引
        
        Args:
            collection_name: 集合名称
            index_type: 索引类型 (IVF_FLAT, IVF_SQ8, IVF_PQ, HNSW)
            metric_type: 距离度量 (L2, IP, COSINE)
            nlist: IVF 参数
            
        Returns:
            是否成功
        """
        if not self.connected:
            logger.error("未连接到 Milvus")
            return False
        
        try:
            collection = Collection(collection_name)
            
            # 索引参数
            index_params = {
                "metric_type": metric_type,
                "index_type": index_type,
                "params": {"nlist": nlist}
            }
            
            # 创建索引
            collection.create_index(
                field_name="vector",
                index_params=index_params
            )
            
            logger.info(f"创建索引: {collection_name}, 类型: {index_type}")
            return True
            
        except Exception as e:
            logger.error(f"创建索引失败: {str(e)}")
            return False
    
    def insert(
        self,
        collection_name: str,
        data: List[Dict[str, Any]]
    ) -> bool:
        """
        插入数据
        
        Args:
            collection_name: 集合名称
            data: 数据列表
            
        Returns:
            是否成功
        """
        if not self.connected:
            logger.error("未连接到 Milvus")
            return False
        
        try:
            collection = Collection(collection_name)
            
            # 准备数据
            entities = [
                [item['id'] for item in data],
                [item['title'] for item in data],
                [item['content'] for item in data],
                [item['category_id'] for item in data],
                [item['tags'] for item in data],
                [item['create_time'] for item in data],
                [item['vector'] for item in data]
            ]
            
            # 插入数据
            collection.insert(entities)
            collection.flush()
            
            logger.info(f"插入 {len(data)} 条数据到: {collection_name}")
            return True
            
        except Exception as e:
            logger.error(f"插入数据失败: {str(e)}")
            return False
    
    def search(
        self,
        collection_name: str,
        query_vector: np.ndarray,
        top_k: int = 5,
        nprobe: int = 10
    ) -> List[Dict]:
        """
        搜索相似向量
        
        Args:
            collection_name: 集合名称
            query_vector: 查询向量
            top_k: 返回结果数量
            nprobe: 搜索参数
            
        Returns:
            搜索结果列表
        """
        if not self.connected:
            logger.error("未连接到 Milvus")
            return []
        
        try:
            collection = Collection(collection_name)
            collection.load()
            
            # 搜索参数
            search_params = {
                "metric_type": "L2",
                "params": {"nprobe": nprobe}
            }
            
            # 执行搜索
            results = collection.search(
                data=[query_vector.tolist()],
                anns_field="vector",
                param=search_params,
                limit=top_k,
                output_fields=["id", "title", "content", "category_id", "tags"]
            )
            
            # 格式化结果
            formatted_results = []
            for hits in results:
                for hit in hits:
                    formatted_results.append({
                        'id': hit.entity.get('id'),
                        'title': hit.entity.get('title'),
                        'content': hit.entity.get('content'),
                        'category_id': hit.entity.get('category_id'),
                        'tags': hit.entity.get('tags'),
                        'distance': hit.distance,
                        'score': 1 / (1 + hit.distance)  # 转换为相似度分数
                    })
            
            logger.info(f"搜索完成: {collection_name}, 返回 {len(formatted_results)} 个结果")
            return formatted_results
            
        except Exception as e:
            logger.error(f"搜索失败: {str(e)}")
            return []
    
    def delete(
        self,
        collection_name: str,
        ids: List[int]
    ) -> bool:
        """
        删除数据
        
        Args:
            collection_name: 集合名称
            ids: ID 列表
            
        Returns:
            是否成功
        """
        if not self.connected:
            logger.error("未连接到 Milvus")
            return False
        
        try:
            collection = Collection(collection_name)
            
            # 删除数据
            expr = f"id in {ids}"
            collection.delete(expr)
            
            logger.info(f"删除 {len(ids)} 条数据: {collection_name}")
            return True
            
        except Exception as e:
            logger.error(f"删除数据失败: {str(e)}")
            return False
    
    def get_collection_stats(self, collection_name: str) -> Dict:
        """
        获取集合统计信息
        
        Args:
            collection_name: 集合名称
            
        Returns:
            统计信息字典
        """
        if not self.connected:
            logger.error("未连接到 Milvus")
            return {}
        
        try:
            collection = Collection(collection_name)
            
            stats = {
                'name': collection_name,
                'num_entities': collection.num_entities,
                'schema': str(collection.schema),
                'indexes': [str(index) for index in collection.indexes]
            }
            
            return stats
            
        except Exception as e:
            logger.error(f"获取统计信息失败: {str(e)}")
            return {}
    
    def drop_collection(self, collection_name: str) -> bool:
        """
        删除集合
        
        Args:
            collection_name: 集合名称
            
        Returns:
            是否成功
        """
        if not self.connected:
            logger.error("未连接到 Milvus")
            return False
        
        try:
            if utility.has_collection(collection_name):
                utility.drop_collection(collection_name)
                logger.info(f"删除集合: {collection_name}")
                return True
            else:
                logger.warning(f"集合不存在: {collection_name}")
                return False
                
        except Exception as e:
            logger.error(f"删除集合失败: {str(e)}")
            return False
    
    def disconnect(self):
        """断开连接"""
        if self.connected:
            connections.disconnect("default")
            self.connected = False
            logger.info("断开 Milvus 连接")
