# 法律文书库 V2.1 字段说明

这是一版面向真实裁判文书与司法文书的结构草案，目标不是把所有文书强行塞进固定分类，而是同时满足三件事：

- 文书能稳定入库
- 结构化字段能做精确过滤
- 分块文本能最大化服务 RAG 检索

## 1. 表设计总览

使用 6 张表：

- `legal_document`
  存文书主信息、标准化字段、摘要字段和整案检索文本。
- `legal_document_party`
  存当事人、角色、代理人等可精确命中的信息。
- `legal_document_reference`
  存法条、案号、决定号、专利号、仲裁号等“精确召回点”。
- `legal_document_chunk`
  存切块后的文本，是语料层的主工作表。
- `legal_document_chunk_index`
  存 chunk 在不同检索框架、不同 embedding 模型、不同索引配置下的构建结果。
- `legal_document_ext`
  存长尾类型和未知类型文书的扩展 JSON，不让主流程被分类阻塞。

## 2. 主表 `legal_document` 存什么

### 来源与去重字段

- `source_type`
  文书来源类型，如 `court_website`、`json`、`manual`。
- `source_name`
  具体来源名称，如“中国裁判文书网”。
- `source_url`
  文书原始链接。
- `source_reliability`
  来源可信度，方便区分官方文书与不明来源样本。
- `source_file_name`
  导入时的原始文件名。
- `source_file_path`
  原始文件路径。
- `file_ext`
  文件后缀，如 `doc`、`docx`、`pdf`。
- `file_size`
  文件大小。
- `file_sha256`
  文件指纹，用于去重。

### 标题与法院信息

- `title`
  文书标题。
- `title_normalized`
  标准化标题，去掉多余空格和部分噪声。
- `case_number`
  案号。
- `court_name`
  法院名称。
- `court_level`
  法院层级，如基层法院、中级法院、高级法院、最高人民法院。
- `province`
  省份。
- `city`
  城市。

### 文书类型与程序类型

- `document_type`
  大类文书类型，如 `判决书`、`裁定书`、`决定书`、`通知书`。
- `document_type_raw`
  原始文书类型文本，防止标准化时丢失原貌。
- `document_subtype`
  细分类型，如 `执行裁定书`、`驳回申诉通知书`、`司法制裁决定书`。
- `document_subtype_raw`
  原始细分类型文本。
- `case_type`
  大类案件类型，如 `民事`、`刑事`、`行政`、`执行`、`破产`、`国家赔偿`。
- `category_id`
  给现有前端分类兼容使用。
- `cause_raw`
  原始案由。
- `cause_normalized`
  标准化案由，便于统一筛选。
- `business_domain`
  业务领域，如 `知识产权`、`合同`、`执行`、`司法协助`、`司法制裁`。
- `procedure_domain`
  程序领域，如 `审判监督`、`执行监督`、`区际司法协助`、`保全复议`。
- `trial_stage_normalized`
  标准审级或程序阶段，如 `一审`、`二审`、`再审`、`复议`。
- `procedure_stage_raw`
  原始程序表述，如“再审审查与审判监督”。
- `special_procedure_type`
  特殊程序类型，如“承认外国法院判决”“认可与执行仲裁裁决”“指定管辖”“破产清算申请”。
- `application_type`
  申请类型，如“再审申请”“申诉”“执行监督申请”“行为保全复议”。

### 裁判结果与日期

- `decision_result`
  原始裁判结果或决定结果。
- `decision_result_normalized`
  标准化后的结果，如 `驳回申请`、`驳回申诉`、`不予重新审判`、`罚款`。
- `decision_date`
  文书作出日期。
- `effective_date`
  生效日期，能提取则存。
- `filing_date`
  立案日期。
- `hearing_date`
  开庭或询问日期。

### 面向展示与检索的摘要字段

- `parties_text`
  当事人扁平文本，适合快速检索和列表展示。
- `claims_summary`
  诉讼请求、上诉请求、申请请求摘要。
- `defense_summary`
  答辩、抗辩、意见摘要。
- `facts_summary`
  基本事实摘要。
- `issues_summary`
  争议焦点摘要。
- `issue_tags`
  争议焦点标签，便于过滤与 rerank。
- `reasoning_summary`
  法院理由摘要。
- `result_summary`
  裁判结果摘要。
- `result_tags`
  结果标签，如“驳回申请”“罚款”“准许执行”等。
- `legal_basis_text`
  法律依据聚合文本。
- `related_case_numbers_text`
  关联案号扁平化文本。
- `related_decision_numbers_text`
  关联决定号、仲裁号、专利号等扁平化文本。
- `keywords`
  检索关键词。
- `aliases`
  同义表达、口语表达、别名。
- `summary`
  列表页摘要。

### 面向 RAG 的全文字段

- `retrieval_text`
  整案级检索文本。通常由标题、法院、案号、案由、争议焦点、结果摘要等拼接而成。
- `raw_text`
  原始抽取全文。
- `clean_text`
  清洗后的全文。

### 处理状态字段

- `parser_version`
  当前解析器版本。
- `parse_status`
  解析状态。
- `classify_status`
  分类状态。
- `classify_confidence`
  分类置信度。
- `extract_confidence`
  结构化抽取置信度。
- `doc_quality_score`
  文书质量评分，用于在检索排序时对低质量文书降权。
- `status`
  文书业务状态。

## 3. 当事人表 `legal_document_party` 存什么

- `party_role_raw`
  原始角色，如“一审原告”“再审申请人”“被罚款人”。
- `party_role_normalized`
  规范角色，如“原告”“被告”“申请人”“被申请人”。
- `party_name`
  当事人名称。
- `party_name_normalized`
  标准化名称，便于搜索和去重。
- `party_type`
  个人、公司、机关、其他组织。
- `nationality_or_region`
  外国、港澳台或地区信息。
- `address_text`
  住址或住所地。
- `identity_text`
  身份描述，如“专利申请人”“法定代表人”。
- `representative_text`
  代理人、法定代表人、授权代表人等文本。

这张表主要服务：

- 按当事人搜索文书
- 按角色过滤文书
- 在问答里展示“谁和谁打的官司”

## 4. 引用表 `legal_document_reference` 存什么

这张表专门存精确命中字段。

- `ref_type`
  引用类型，如：
  `law_article`
  `case_number`
  `decision_number`
  `patent_number`
  `right_number`
  `arbitration_number`
- `ref_name`
  法律名称、专利名称、仲裁机构名称等。
- `ref_value`
  具体编号或条文。
- `normalized_value`
  标准化后的值。
- `is_primary`
  是否主引用。比如本案案号、涉案专利号、被诉决定号通常应标为主引用。
- `raw_text`
  原始引用文本。

这张表主要服务：

- 搜案号
- 搜法条
- 搜专利号、品种权号、仲裁号
- 做知识点型精确召回

## 5. 分块表 `legal_document_chunk` 存什么

这是最直接服务 RAG 的表。

- `section_type`
  块所属章节类型，如 `facts`、`issues`、`reasoning`、`result`。
- `parent_chunk_id`
  父块 ID，用于支持层级切块。
- `chunk_strategy`
  切块策略，如 `rule`、`semantic`、`hybrid`。
- `chunk_version`
  切块策略版本。
- `section_title`
  原始章节标题。
- `header_path`
  块的标题路径，例如 `本院认为 > 关于争议焦点一`。
- `retrieval_role`
  检索角色，帮助区分 `facts`、`reasoning`、`result`、`law` 等。
- `section_weight`
  章节权重。通常 `result` 和 `reasoning` 应高于 `header` 与 `party`。
- `section_order`
  章节顺序。
- `chunk_index`
  文书内部的块序号。
- `char_start_offset`
  在全文中的起始偏移。
- `char_end_offset`
  在全文中的结束偏移。
- `chunk_hash`
  块文本指纹，便于去重。
- `chunk_text`
  原始块文本。
- `retrieval_text`
  真正送入 embedding 的文本。通常会加元数据前缀。
- `char_count`
  字符数。
- `token_count`
  Token 数。
- `metadata_json`
  块级额外元数据。

推荐的 `retrieval_text` 形式：

```text
法院：最高人民法院
案号：（2025）最高法执监314号
文书类型：执行裁定书
程序领域：执行监督
案由：执行监督
裁判结果：申诉理由不能成立，本院不予支持
正文片段：……
```

这样比直接拿正文切块做 embedding 更稳。

## 6. 分块索引表 `legal_document_chunk_index` 存什么

这张表是为了“把内容和索引实现解耦”。

如果未来你要换：

- `FAISS`
- `Milvus`
- `pgvector`
- `Elasticsearch / OpenSearch`
- `ColBERT`
- 不同 embedding 模型
- 不同 reranker

就不应该把这些信息直接写死在 `chunk` 表里。

字段作用如下：

- `chunk_id`
  指向语料分块。
- `retrieval_profile`
  一套检索配置的标识，比如 `dense_bge_v1`、`hybrid_v2`、`colbert_v1`。
- `backend_type`
  检索后端类型。
- `index_name`
  具体索引名。
- `index_partition`
  分区、集合或命名空间。
- `embedding_provider`
  向量提供方。
- `embedding_model`
  向量模型名。
- `embedding_dim`
  向量维度。
- `vector_id`
  后端向量 ID。
- `reranker_model`
  重排模型名。
- `build_version`
  构建版本。
- `is_active`
  当前是否生效。
- `metadata_json`
  索引级额外信息。

这张表对“最高 RAG 效率”很重要，因为它允许：

- 同一批 chunk 同时跑多套检索方案
- 不改语料表就切换框架和模型
- 做 A/B 对比和回滚

## 7. 扩展表 `legal_document_ext` 存什么

这张表是兜底表，也是长尾类型的缓冲区。

- `ext_type_summary`
  扩展类别摘要，如 `ip`、`enforcement`、`foreign_judgment`、`sanction`、`bankruptcy`。
- `ext_json`
  长尾字段 JSON。
- `schema_version`
  扩展结构版本。

示例：

- 外国判决承认类：
  外国法院名称、外国案号、原判生效时间、认证情况
- 执行监督类：
  执行标的、执行措施、拍卖/查封信息、优先购买权
- 司法制裁类：
  罚款金额、制裁依据、复议期限
- 行为保全复议类：
  保全类型、担保金额、专利号、涉案药品信息
- 破产类：
  债权金额、终本执行信息、申请类型

## 8. 如果来了一个“不在分类里”的新文书，怎么办

处理顺序是：

1. 先完整入库，不因分类未知而失败
2. 原始标签写进 raw 字段
3. 标准字段尽量归到宽类，无法确定时允许为空
4. 长尾专属字段写入 `ext_json`
5. 仍然生成 `retrieval_text` 和 `chunk`

例如一个新类型“司法惩戒决定书”，即使规则里暂时没有：

- `document_type` 可以先存 `决定书`
- `document_subtype_raw` 可以直接存 `司法惩戒决定书`
- `procedure_domain` 可以先存 `司法制裁`
- 细节写入 `ext_json`

这意味着：

- 文书能存进去
- 检索不会中断
- 后续再补标准化规则即可

## 9. 这套表为什么更利于 RAG

比起只存 `title + content`，这套表对 RAG 的帮助主要体现在：

- 可以先按结构化字段过滤，再做向量召回
- 可以用 `reference` 表做案号、法条、编号级精确检索
- 可以用 `party` 表做当事人级检索
- 可以用 `chunk.retrieval_text` 做高质量块检索
- 可以按 `section_weight` 和 `retrieval_role` 对召回结果做更细排序
- 可以通过 `chunk_index` 同时维护多套框架和模型，不把语料结构绑死在某个向量库上
- 对未知文书类型有兜底，不会因为分类不全而丢文书

注意：

表设计本身只能“为高质量 RAG 做准备”，真正要把效果拉满，还需要后续代码配套：

- 导入时做规则抽取和摘要抽取
- 构建 `retrieval_text`
- 生成高质量 chunk
- 走混合检索：结构过滤 + 关键词 + 向量 + rerank
- 允许并行维护多套检索 profile，持续评估最优方案

## 10. 当前阶段建议

建议先按这版字段定稿，再开始代码改造。理由是：

- 主表已经足够覆盖你目前给出的普通与长尾文书
- 对新类型也有兜底空间
- 与你现有 Spring Boot + Flask 架构兼容，同时不锁死后续检索框架

配套 SQL 草案见：

- `docs/legal_document_schema_v2.sql`
