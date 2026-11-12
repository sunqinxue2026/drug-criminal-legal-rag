# 规范知识导入格式

当前系统支持通过 `POST /api/legal-knowledge/import/file` 导入 JSON 文件。

## 支持的两种结构

### 1. 扁平数组

```json
[
  {
    "knowledgeType": "法律法规",
    "documentName": "中华人民共和国禁毒法",
    "articleNumber": "第六十一条",
    "articleTitle": "吸毒成瘾认定与处置",
    "content": "国家采取多种措施帮助吸毒人员戒除毒瘾。"
  }
]
```

### 2. 带 `articles / entries / items` 的嵌套结构

```json
{
  "documentName": "中华人民共和国刑事诉讼法（涉毒程序权利节录）",
  "knowledgeType": "法律法规",
  "issuer": "全国人民代表大会常务委员会",
  "businessDomain": "刑事",
  "topic": "涉毒程序权利",
  "articles": [
    {
      "articleNumber": "第三十九条",
      "articleTitle": "辩护律师会见权",
      "content": "辩护律师持律师执业证书、律师事务所证明和委托书或者法律援助公函，有权会见在押的犯罪嫌疑人、被告人。"
    }
  ]
}
```

## 常用字段

- `knowledgeType`：法律法规 / 司法解释 / 指导案例 / 规范知识
- `documentName`：规范名称
- `issuer`：发布机关
- `businessDomain`：业务领域，如刑事、涉毒、普法、刑事程序
- `topic`：主题
- `chapterTitle` / `sectionTitle`
- `articleNumber`
- `articleTitle`
- `content`
- `keywords`
- `aliases`
- `effectiveDate`
- `currentStatus`
- `sourceName`
- `sourceUrl`

## 导入后的效果

- 会为每条规范生成 `retrievalText`
- 会生成 `law_basis` 类型的检索块
- 在 Flask 重建索引后，会与法律文书分块、专题案例入口共同进入 `legal_cases` 检索库

## 样例文件

- [legal_knowledge_import_example.json](D:/CODE/RAG/docs/legal_knowledge_import_example.json)
