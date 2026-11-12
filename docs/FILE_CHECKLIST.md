# 项目文件检查清单

## 说明

旧版本的文件清单曾包含大量已经不存在的脚本、说明文档和目录结构，不能再作为当前仓库依据。

本文件改为维护“当前仓库实际存在的核心结构”，用于快速核对项目是否完整。

## 根目录核心内容

| 路径 | 说明 |
| --- | --- |
| `README.md` | 项目总说明 |
| `docs/` | 论文、接口、数据库、导入格式等文档 |
| `data/` | 评测集、专题语料、处理结果 |
| `scripts/` | 抓取、导入、评测、重建索引脚本 |
| `web-vue/` | Vue 前端 |
| `web-springboot/` | Spring Boot 业务后端 |
| `web-flask/` | Flask 算法服务 |
| `web-file/` | 文件服务 |

## 当前重点文档

| 文件 | 用途 |
| --- | --- |
| `docs/API文档.md` | 当前接口说明 |
| `docs/数据库设计文档.md` | 数据库与检索设计 |
| `docs/legal_document_schema_v2.md` | 法律文书字段设计 |
| `docs/legal_knowledge_import_format.md` | 规范知识导入格式 |
| `docs/RAG_EVALUATION.md` | 评测说明 |
| `docs/THESIS_COMPLETION_TRACKER.md` | 论文补强清单 |

## 当前前端结构

前端目录中当前实际存在的关键部分：

- `src/views/user/`
- `src/views/item/`
- `src/views/chat/`
- `src/views/admin/`
- `src/api/`
- `src/stores/`
- `src/router/`
- `src/components/`

## 当前后端结构

后端目录中当前实际存在的关键部分：

- `src/main/java/com/gjq/controller/`
- `src/main/java/com/gjq/service/`
- `src/main/java/com/gjq/mapper/`
- `src/main/java/com/gjq/entity/`
- `src/main/java/com/gjq/dto/`
- `src/main/java/com/gjq/vo/`
- `src/main/resources/application.yml`
- `mvnw.cmd`
- `start-dev.bat`
- `start-v2.bat`
- `fix-compile.bat`

## 当前算法服务结构

算法服务目录中当前实际存在的关键部分：

- `algo/`
- `routes/`
- `clients/`
- `utils/`
- `tests/`
- `config.py`
- `app.py`
- `requirements.txt`

## 当前项目完整性检查

- [x] 前端目录存在
- [x] 后端目录存在
- [x] 算法服务目录存在
- [x] 文件服务目录存在
- [x] 论文相关文档存在
- [x] 评测脚本存在
- [x] 语料导入脚本存在
- [x] 法律知识与法律文书管理代码存在

## 使用建议

如果你需要做毕业论文、答辩或阶段汇报，优先参考以下文件，而不要再引用旧版“完整清单式”文档：

1. [README.md](/D:/CODE/RAG/README.md)
2. [API文档.md](/D:/CODE/RAG/docs/API文档.md)
3. [数据库设计文档.md](/D:/CODE/RAG/docs/数据库设计文档.md)
4. [THESIS_COMPLETION_TRACKER.md](/D:/CODE/RAG/docs/THESIS_COMPLETION_TRACKER.md)
5. [RAG_EVALUATION.md](/D:/CODE/RAG/docs/RAG_EVALUATION.md)
