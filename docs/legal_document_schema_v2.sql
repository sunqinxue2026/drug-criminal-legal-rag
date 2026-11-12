-- =============================================
-- 法律文书库 V2.1 结构草案
-- 目标：
-- 1. 兼容判决书、裁定书、决定书、通知书等常见与长尾司法文书
-- 2. 同时支持结构化过滤、精确检索、向量分块检索
-- 3. 对未知文书类型保留原始标签，避免因分类不全导致入库失败
-- =============================================

CREATE DATABASE IF NOT EXISTS `legal_rag`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `legal_rag`;

-- =============================================
-- 1. 法律文书主表
-- =============================================
DROP TABLE IF EXISTS `legal_document`;
CREATE TABLE `legal_document` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_type` VARCHAR(50) NOT NULL DEFAULT 'court_website' COMMENT '来源类型：court_website/json/manual/import',
  `source_name` VARCHAR(100) DEFAULT NULL COMMENT '来源名称：如中国裁判文书网',
  `source_url` VARCHAR(1024) DEFAULT NULL COMMENT '原始来源链接',
  `source_reliability` TINYINT NOT NULL DEFAULT 90 COMMENT '来源可信度：0-100',
  `source_file_name` VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
  `source_file_path` VARCHAR(1024) DEFAULT NULL COMMENT '原始文件路径',
  `file_ext` VARCHAR(20) DEFAULT NULL COMMENT '文件扩展名',
  `file_size` BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
  `file_sha256` CHAR(64) DEFAULT NULL COMMENT '文件SHA256，用于去重',

  `title` VARCHAR(500) NOT NULL COMMENT '文书标题',
  `title_normalized` VARCHAR(500) DEFAULT NULL COMMENT '标准化标题，用于去噪和检索',
  `case_number` VARCHAR(100) DEFAULT NULL COMMENT '案号',
  `court_name` VARCHAR(255) DEFAULT NULL COMMENT '法院名称',
  `court_level` VARCHAR(50) DEFAULT NULL COMMENT '法院层级：基层/中院/高院/最高法等',
  `province` VARCHAR(50) DEFAULT NULL COMMENT '省份',
  `city` VARCHAR(50) DEFAULT NULL COMMENT '城市',

  `document_type` VARCHAR(50) DEFAULT NULL COMMENT '标准文书类型：判决书/裁定书/决定书/通知书',
  `document_type_raw` VARCHAR(100) DEFAULT NULL COMMENT '原始文书类型文本',
  `document_subtype` VARCHAR(100) DEFAULT NULL COMMENT '标准细分类型：执行裁定书/驳回申诉通知书/司法制裁决定书等',
  `document_subtype_raw` VARCHAR(255) DEFAULT NULL COMMENT '原始细分类型文本',

  `case_type` VARCHAR(50) DEFAULT NULL COMMENT '案件大类：民事/刑事/行政/执行/破产/国家赔偿等',
  `category_id` BIGINT DEFAULT NULL COMMENT '前端兼容分类ID',
  `cause_raw` VARCHAR(255) DEFAULT NULL COMMENT '原始案由',
  `cause_normalized` VARCHAR(255) DEFAULT NULL COMMENT '规范化案由',
  `business_domain` VARCHAR(100) DEFAULT NULL COMMENT '业务领域：知识产权/合同/执行/司法协助/司法制裁等',
  `procedure_domain` VARCHAR(100) DEFAULT NULL COMMENT '程序领域：审判监督/执行监督/区际司法协助/保全复议等',
  `trial_stage_normalized` VARCHAR(50) DEFAULT NULL COMMENT '标准审级：一审/二审/再审/复议等',
  `procedure_stage_raw` VARCHAR(255) DEFAULT NULL COMMENT '原始程序阶段文本',
  `special_procedure_type` VARCHAR(100) DEFAULT NULL COMMENT '特殊程序：承认外国判决/认可仲裁裁决/指定管辖/破产清算申请等',
  `application_type` VARCHAR(100) DEFAULT NULL COMMENT '申请类型：再审申请/申诉/执行监督申请/行为保全复议等',

  `decision_result` VARCHAR(255) DEFAULT NULL COMMENT '原始裁判结果/决定结果',
  `decision_result_normalized` VARCHAR(100) DEFAULT NULL COMMENT '标准化结果：驳回申请/驳回申诉/不予重新审判/罚款等',
  `decision_date` DATE DEFAULT NULL COMMENT '文书作出日期',
  `effective_date` DATE DEFAULT NULL COMMENT '生效日期，能提取则存',
  `filing_date` DATE DEFAULT NULL COMMENT '立案日期',
  `hearing_date` DATE DEFAULT NULL COMMENT '开庭或询问日期',

  `parties_text` MEDIUMTEXT COMMENT '当事人扁平化文本，便于全文检索与快速展示',
  `claims_summary` MEDIUMTEXT COMMENT '诉讼请求/上诉请求/申请请求摘要',
  `defense_summary` MEDIUMTEXT COMMENT '答辩/抗辩/意见摘要',
  `facts_summary` LONGTEXT COMMENT '基本事实摘要',
  `issues_summary` TEXT COMMENT '争议焦点摘要',
  `issue_tags` VARCHAR(1000) DEFAULT NULL COMMENT '争议焦点标签，逗号分隔',
  `reasoning_summary` LONGTEXT COMMENT '法院认为/审查意见摘要',
  `result_summary` TEXT COMMENT '裁判结果摘要',
  `result_tags` VARCHAR(1000) DEFAULT NULL COMMENT '结果标签，逗号分隔',
  `legal_basis_text` MEDIUMTEXT COMMENT '法律依据聚合文本',
  `related_case_numbers_text` TEXT COMMENT '关联案号扁平化文本',
  `related_decision_numbers_text` TEXT COMMENT '关联决定号/仲裁号/专利号等扁平化文本',
  `keywords` VARCHAR(2000) DEFAULT NULL COMMENT '规则或模型生成的关键词，逗号分隔',
  `aliases` VARCHAR(2000) DEFAULT NULL COMMENT '同义词/口语表达/别名，逗号分隔',

  `summary` TEXT COMMENT '列表展示摘要',
  `retrieval_text` LONGTEXT COMMENT '整案级检索文本，供混合检索和整文召回使用',
  `raw_text` LONGTEXT COMMENT '原始抽取全文',
  `clean_text` LONGTEXT COMMENT '清洗后的全文',

  `parser_version` VARCHAR(50) DEFAULT NULL COMMENT '解析器版本',
  `parse_status` TINYINT NOT NULL DEFAULT 0 COMMENT '解析状态：0待处理 1成功 2部分成功 3失败',
  `classify_status` TINYINT NOT NULL DEFAULT 0 COMMENT '分类状态：0待处理 1成功 2低置信度 3失败',
  `classify_confidence` DECIMAL(5,4) DEFAULT NULL COMMENT '分类置信度',
  `extract_confidence` DECIMAL(5,4) DEFAULT NULL COMMENT '结构化抽取置信度',
  `doc_quality_score` DECIMAL(5,2) DEFAULT NULL COMMENT '文书质量评分：0-100，用于检索降噪',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '业务状态：0草稿 1可检索 2停用',

  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_legal_document_file_sha256` (`file_sha256`),
  KEY `idx_legal_document_case_number` (`case_number`),
  KEY `idx_legal_document_court_date` (`court_name`, `decision_date`),
  KEY `idx_legal_document_doc_type` (`document_type`, `document_subtype`),
  KEY `idx_legal_document_case_proc` (`case_type`, `procedure_domain`, `trial_stage_normalized`),
  KEY `idx_legal_document_business` (`business_domain`, `cause_normalized`),
  KEY `idx_legal_document_result` (`decision_result_normalized`, `decision_date`),
  KEY `idx_legal_document_quality` (`doc_quality_score`, `extract_confidence`),
  KEY `idx_legal_document_category` (`category_id`),
  KEY `idx_legal_document_status` (`status`, `deleted`, `decision_date`),
  FULLTEXT KEY `ft_legal_document_retrieval`
    (`title`, `title_normalized`, `summary`, `keywords`, `aliases`, `retrieval_text`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法律文书主表';


-- =============================================
-- 2. 当事人表
-- =============================================
DROP TABLE IF EXISTS `legal_document_party`;
CREATE TABLE `legal_document_party` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `document_id` BIGINT NOT NULL COMMENT '文书ID',
  `party_seq` INT NOT NULL DEFAULT 0 COMMENT '当事人顺序',
  `party_role_raw` VARCHAR(100) DEFAULT NULL COMMENT '原始角色：一审原告/被申请人/被罚款人等',
  `party_role_normalized` VARCHAR(100) DEFAULT NULL COMMENT '规范化角色：原告/被告/申请人/被申请人等',
  `party_name` VARCHAR(255) DEFAULT NULL COMMENT '当事人名称',
  `party_name_normalized` VARCHAR(255) DEFAULT NULL COMMENT '标准化当事人名称',
  `party_type` VARCHAR(50) DEFAULT NULL COMMENT '个人/公司/机关/其他组织',
  `nationality_or_region` VARCHAR(100) DEFAULT NULL COMMENT '国籍或地区信息',
  `address_text` TEXT COMMENT '住址/住所地',
  `identity_text` TEXT COMMENT '身份描述，如专利申请人/法定代表人/股东',
  `representative_text` TEXT COMMENT '代理人/法定代表人等聚合文本',
  `is_anonymized` TINYINT NOT NULL DEFAULT 0 COMMENT '是否匿名化处理',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`),
  KEY `idx_legal_document_party_doc` (`document_id`, `party_seq`),
  KEY `idx_legal_document_party_role` (`party_role_normalized`),
  KEY `idx_legal_document_party_name` (`party_name_normalized`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法律文书当事人表';


-- =============================================
-- 3. 引用表
-- =============================================
DROP TABLE IF EXISTS `legal_document_reference`;
CREATE TABLE `legal_document_reference` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `document_id` BIGINT NOT NULL COMMENT '文书ID',
  `ref_order` INT NOT NULL DEFAULT 0 COMMENT '引用顺序',
  `section_type` VARCHAR(50) DEFAULT NULL COMMENT '引用所在章节：facts/reasoning/result/law等',
  `ref_type` VARCHAR(50) NOT NULL COMMENT '引用类型：law_article/case_number/decision_number/patent_number/arbitration_number等',
  `ref_name` VARCHAR(255) DEFAULT NULL COMMENT '引用名称：法律名称/专利名称/机构名称',
  `ref_value` VARCHAR(255) DEFAULT NULL COMMENT '引用值：条文号/案号/专利号/决定号',
  `normalized_value` VARCHAR(255) DEFAULT NULL COMMENT '标准化后的引用值',
  `is_primary` TINYINT NOT NULL DEFAULT 0 COMMENT '是否主引用：1是 0否',
  `raw_text` TEXT COMMENT '原始引用文本',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`),
  KEY `idx_legal_document_reference_doc` (`document_id`, `ref_type`),
  KEY `idx_legal_document_reference_lookup` (`ref_type`, `normalized_value`),
  KEY `idx_legal_document_reference_primary` (`is_primary`, `ref_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法律文书引用表';


-- =============================================
-- 4. 分块表
-- =============================================
DROP TABLE IF EXISTS `legal_document_chunk`;
CREATE TABLE `legal_document_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `document_id` BIGINT NOT NULL COMMENT '文书ID',
  `parent_chunk_id` BIGINT DEFAULT NULL COMMENT '父块ID，用于层级分块',
  `chunk_strategy` VARCHAR(50) DEFAULT NULL COMMENT '切块策略：rule/semantic/hybrid等',
  `chunk_version` VARCHAR(50) DEFAULT NULL COMMENT '切块策略版本',
  `section_type` VARCHAR(50) DEFAULT NULL COMMENT '章节类型：header/party/facts/issues/reasoning/result/law等',
  `section_title` VARCHAR(255) DEFAULT NULL COMMENT '章节标题',
  `header_path` VARCHAR(1000) DEFAULT NULL COMMENT '标题路径，如 本院认为>关于争议焦点一',
  `retrieval_role` VARCHAR(50) DEFAULT NULL COMMENT '检索角色：facts/reasoning/result/law/header',
  `section_weight` DECIMAL(6,3) DEFAULT NULL COMMENT '章节权重，用于排序与重排',
  `section_order` INT NOT NULL DEFAULT 0 COMMENT '章节顺序',
  `chunk_index` INT NOT NULL DEFAULT 0 COMMENT '文书内分块序号',
  `char_start_offset` INT DEFAULT NULL COMMENT '在全文中的起始字符偏移',
  `char_end_offset` INT DEFAULT NULL COMMENT '在全文中的结束字符偏移',
  `chunk_hash` CHAR(64) DEFAULT NULL COMMENT '块文本指纹，便于去重',
  `chunk_text` MEDIUMTEXT COMMENT '原始块文本',
  `retrieval_text` LONGTEXT COMMENT '带元数据前缀的块文本，用于向量检索',
  `char_count` INT DEFAULT NULL COMMENT '字符数',
  `token_count` INT DEFAULT NULL COMMENT 'Token数，可后续回填',
  `metadata_json` JSON DEFAULT NULL COMMENT '块级额外元数据',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_legal_document_chunk_hash` (`document_id`, `chunk_hash`),
  KEY `idx_legal_document_chunk_doc` (`document_id`, `section_order`, `chunk_index`),
  KEY `idx_legal_document_chunk_role` (`retrieval_role`, `section_weight`),
  KEY `idx_legal_document_chunk_offsets` (`document_id`, `char_start_offset`, `char_end_offset`),
  FULLTEXT KEY `ft_legal_document_chunk_retrieval`
    (`section_title`, `header_path`, `chunk_text`, `retrieval_text`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法律文书分块表';


-- =============================================
-- 5. 分块索引表
-- 说明：
-- 1. 让 chunk 内容与具体向量/检索框架解耦
-- 2. 支持同一 chunk 同时被多个 embedding 模型、多个向量库或多套检索策略使用
-- =============================================
DROP TABLE IF EXISTS `legal_document_chunk_index`;
CREATE TABLE `legal_document_chunk_index` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `chunk_id` BIGINT NOT NULL COMMENT '分块ID',
  `retrieval_profile` VARCHAR(100) NOT NULL COMMENT '检索配置标识，如 dense_v1/hybrid_v2/colbert_v1',
  `backend_type` VARCHAR(50) DEFAULT NULL COMMENT '后端类型：faiss/milvus/pgvector/es/opensearch/colbert等',
  `index_name` VARCHAR(100) DEFAULT NULL COMMENT '索引名称',
  `index_partition` VARCHAR(100) DEFAULT NULL COMMENT '索引分区或集合',
  `embedding_provider` VARCHAR(100) DEFAULT NULL COMMENT '向量提供方',
  `embedding_model` VARCHAR(100) DEFAULT NULL COMMENT '向量模型名称',
  `embedding_dim` INT DEFAULT NULL COMMENT '向量维度',
  `vector_id` VARCHAR(100) DEFAULT NULL COMMENT '向量ID',
  `reranker_model` VARCHAR(100) DEFAULT NULL COMMENT '重排模型，可为空',
  `build_version` VARCHAR(50) DEFAULT NULL COMMENT '索引构建版本',
  `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否当前生效',
  `metadata_json` JSON DEFAULT NULL COMMENT '索引级附加元数据',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_legal_document_chunk_index_unique` (`chunk_id`, `retrieval_profile`, `vector_id`),
  KEY `idx_legal_document_chunk_index_lookup` (`retrieval_profile`, `backend_type`, `is_active`),
  KEY `idx_legal_document_chunk_index_model` (`embedding_model`, `reranker_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法律文书分块索引表';


-- =============================================
-- 6. 通用扩展表
-- 说明：
-- 1. 存放长尾文书类型的特殊字段
-- 2. 未知文书类型也可以先落这里，不阻塞主流程
-- =============================================
DROP TABLE IF EXISTS `legal_document_ext`;
CREATE TABLE `legal_document_ext` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `document_id` BIGINT NOT NULL COMMENT '文书ID',
  `ext_type_summary` VARCHAR(255) DEFAULT NULL COMMENT '扩展类别摘要：ip/enforcement/foreign_judgment/sanction/bankruptcy等',
  `ext_json` JSON NOT NULL COMMENT '扩展字段JSON',
  `schema_version` VARCHAR(50) DEFAULT NULL COMMENT '扩展结构版本',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_legal_document_ext_doc` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法律文书通用扩展表';


-- =============================================
-- 设计提示
-- 1. 未知类型文书：先写入 legal_document，保留 raw 字段，并在 ext_json 中存特有信息
-- 2. 内容与索引构建解耦：legal_document_chunk 只存文本，legal_document_chunk_index 存多套检索索引信息
-- 3. 检索优先走 legal_document_chunk.retrieval_text 做召回，按 section_weight / retrieval_role 做重排
-- 4. 精确查询优先走 legal_document_reference 与 legal_document_party
-- =============================================
