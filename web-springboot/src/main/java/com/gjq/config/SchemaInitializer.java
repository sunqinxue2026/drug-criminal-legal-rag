package com.gjq.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public SchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        ensureColumnExists("chat_message", "image_url", "ALTER TABLE chat_message ADD COLUMN image_url VARCHAR(512) NULL");
        ensureColumnExists("chat_message", "image_name", "ALTER TABLE chat_message ADD COLUMN image_name VARCHAR(255) NULL");
        ensureColumnExists("category", "icon_url", "ALTER TABLE category ADD COLUMN icon_url VARCHAR(512) NULL");
        ensureColumnExists("category", "theme_color", "ALTER TABLE category ADD COLUMN theme_color VARCHAR(32) NULL");
        ensureColumnExists("legal_document", "dispute_summary", "ALTER TABLE legal_document ADD COLUMN dispute_summary TEXT NULL");
        ensureColumnExists("legal_document", "trial_process_summary", "ALTER TABLE legal_document ADD COLUMN trial_process_summary TEXT NULL");
        ensureColumnExists("legal_document", "case_detail_text", "ALTER TABLE legal_document ADD COLUMN case_detail_text LONGTEXT NULL");
        ensureColumnExists("legal_document", "display_text", "ALTER TABLE legal_document ADD COLUMN display_text LONGTEXT NULL");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_behavior (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  behavior_type VARCHAR(64) NOT NULL,
                  target_type VARCHAR(64),
                  target_id BIGINT,
                  details VARCHAR(512),
                  ip_address VARCHAR(64),
                  user_agent VARCHAR(255),
                  session_id VARCHAR(128),
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  KEY idx_user_behavior_user (user_id),
                  KEY idx_user_behavior_type (behavior_type),
                  KEY idx_user_behavior_target (target_type, target_id)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS item_asset (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  item_id BIGINT NOT NULL,
                  asset_type VARCHAR(64) NOT NULL,
                  file_name VARCHAR(255) NOT NULL,
                  file_url VARCHAR(1024) NOT NULL,
                  bucket VARCHAR(64),
                  object_key VARCHAR(255),
                  file_ext VARCHAR(32),
                  file_size BIGINT,
                  summary VARCHAR(512),
                  sort INT DEFAULT 0,
                  status TINYINT DEFAULT 1,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  deleted TINYINT DEFAULT 0,
                  KEY idx_item_asset_item (item_id),
                  KEY idx_item_asset_type (asset_type),
                  KEY idx_item_asset_bucket (bucket, object_key)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS legal_document (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  source_type VARCHAR(64),
                  source_name VARCHAR(128),
                  source_url VARCHAR(1024),
                  source_reliability TINYINT,
                  source_file_name VARCHAR(255),
                  source_file_path VARCHAR(1024),
                  file_ext VARCHAR(32),
                  file_size BIGINT,
                  file_sha256 CHAR(64),
                  title VARCHAR(255),
                  title_normalized VARCHAR(255),
                  case_number VARCHAR(128),
                  court_name VARCHAR(255),
                  court_level VARCHAR(64),
                  province VARCHAR(64),
                  city VARCHAR(64),
                  document_type VARCHAR(64),
                  document_type_raw VARCHAR(128),
                  document_subtype VARCHAR(128),
                  document_subtype_raw VARCHAR(128),
                  case_type VARCHAR(64),
                  category_id BIGINT,
                  cause_raw VARCHAR(255),
                  cause_normalized VARCHAR(255),
                  business_domain VARCHAR(64),
                  procedure_domain VARCHAR(64),
                  trial_stage_normalized VARCHAR(64),
                  procedure_stage_raw VARCHAR(128),
                  special_procedure_type VARCHAR(128),
                  application_type VARCHAR(128),
                  decision_result VARCHAR(512),
                  decision_result_normalized VARCHAR(128),
                  decision_date DATE,
                  effective_date DATE,
                  filing_date DATE,
                  hearing_date DATE,
                  parties_text TEXT,
                  claims_summary TEXT,
                  defense_summary TEXT,
                  dispute_summary TEXT,
                  trial_process_summary TEXT,
                  facts_summary TEXT,
                  issues_summary TEXT,
                  issue_tags VARCHAR(512),
                  reasoning_summary TEXT,
                  result_summary TEXT,
                  result_tags VARCHAR(512),
                  legal_basis_text TEXT,
                  related_case_numbers_text TEXT,
                  related_decision_numbers_text TEXT,
                  keywords VARCHAR(1024),
                  aliases VARCHAR(1024),
                  summary TEXT,
                  retrieval_text LONGTEXT,
                  case_detail_text LONGTEXT,
                  display_text LONGTEXT,
                  raw_text LONGTEXT,
                  clean_text LONGTEXT,
                  parser_version VARCHAR(64),
                  parse_status TINYINT DEFAULT 0,
                  classify_status TINYINT DEFAULT 0,
                  classify_confidence DECIMAL(5,4),
                  extract_confidence DECIMAL(5,4),
                  doc_quality_score DECIMAL(5,4),
                  status TINYINT DEFAULT 1,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  deleted TINYINT DEFAULT 0,
                  KEY idx_legal_document_category (category_id),
                  KEY idx_legal_document_case_number (case_number),
                  KEY idx_legal_document_court (court_name),
                  KEY idx_legal_document_type (document_type, document_subtype),
                  KEY idx_legal_document_business (business_domain, procedure_domain),
                  KEY idx_legal_document_sha (file_sha256)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS legal_document_party (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  document_id BIGINT NOT NULL,
                  party_seq INT,
                  party_role_raw VARCHAR(128),
                  party_role_normalized VARCHAR(128),
                  party_name VARCHAR(255),
                  party_name_normalized VARCHAR(255),
                  party_type VARCHAR(64),
                  nationality_or_region VARCHAR(64),
                  address_text VARCHAR(512),
                  identity_text TEXT,
                  representative_text VARCHAR(512),
                  is_anonymized TINYINT DEFAULT 0,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  KEY idx_legal_document_party_doc (document_id),
                  KEY idx_legal_document_party_name (party_name_normalized)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS legal_document_reference (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  document_id BIGINT NOT NULL,
                  ref_order INT,
                  section_type VARCHAR(64),
                  ref_type VARCHAR(64),
                  ref_name VARCHAR(255),
                  ref_value VARCHAR(255),
                  normalized_value VARCHAR(255),
                  is_primary TINYINT DEFAULT 0,
                  raw_text TEXT,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  KEY idx_legal_document_ref_doc (document_id),
                  KEY idx_legal_document_ref_type (ref_type),
                  KEY idx_legal_document_ref_value (normalized_value)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS legal_document_chunk (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  document_id BIGINT NOT NULL,
                  parent_chunk_id BIGINT,
                  chunk_strategy VARCHAR(64),
                  chunk_version VARCHAR(64),
                  section_type VARCHAR(64),
                  section_title VARCHAR(255),
                  header_path VARCHAR(512),
                  retrieval_role VARCHAR(64),
                  section_weight DECIMAL(5,2),
                  section_order INT,
                  chunk_index INT,
                  char_start_offset INT,
                  char_end_offset INT,
                  chunk_hash CHAR(64),
                  chunk_text LONGTEXT,
                  retrieval_text LONGTEXT,
                  char_count INT,
                  token_count INT,
                  metadata_json JSON,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  deleted TINYINT DEFAULT 0,
                  KEY idx_legal_document_chunk_doc (document_id),
                  KEY idx_legal_document_chunk_section (section_type),
                  KEY idx_legal_document_chunk_hash (chunk_hash)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS legal_document_chunk_index (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  chunk_id BIGINT NOT NULL,
                  retrieval_profile VARCHAR(64),
                  backend_type VARCHAR(64),
                  index_name VARCHAR(128),
                  index_partition VARCHAR(128),
                  embedding_provider VARCHAR(64),
                  embedding_model VARCHAR(128),
                  embedding_dim INT,
                  vector_id VARCHAR(128),
                  reranker_model VARCHAR(128),
                  build_version VARCHAR(64),
                  is_active TINYINT DEFAULT 1,
                  metadata_json JSON,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  KEY idx_legal_document_chunk_index_chunk (chunk_id),
                  KEY idx_legal_document_chunk_index_profile (retrieval_profile, index_name),
                  KEY idx_legal_document_chunk_index_active (is_active)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS legal_document_ext (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  document_id BIGINT NOT NULL,
                  ext_type_summary VARCHAR(255),
                  ext_json JSON,
                  schema_version VARCHAR(32),
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  KEY idx_legal_document_ext_doc (document_id)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS legal_knowledge (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  source_type VARCHAR(64),
                  source_name VARCHAR(128),
                  source_url VARCHAR(1024),
                  source_reliability TINYINT,
                  source_file_name VARCHAR(255),
                  content_sha256 CHAR(64),
                  title VARCHAR(255),
                  title_normalized VARCHAR(255),
                  knowledge_type VARCHAR(64),
                  knowledge_subtype VARCHAR(128),
                  document_name VARCHAR(255),
                  document_level VARCHAR(128),
                  issuer VARCHAR(255),
                  category_id BIGINT,
                  business_domain VARCHAR(128),
                  topic VARCHAR(255),
                  chapter_title VARCHAR(255),
                  section_title VARCHAR(255),
                  article_number VARCHAR(128),
                  article_title VARCHAR(255),
                  keywords VARCHAR(1024),
                  aliases VARCHAR(1024),
                  summary TEXT,
                  display_text LONGTEXT,
                  retrieval_text LONGTEXT,
                  effective_date DATE,
                  expiry_date DATE,
                  current_status VARCHAR(64),
                  parser_version VARCHAR(64),
                  parse_status TINYINT DEFAULT 0,
                  extract_confidence DECIMAL(5,4),
                  status TINYINT DEFAULT 1,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  deleted TINYINT DEFAULT 0,
                  KEY idx_legal_knowledge_category (category_id),
                  KEY idx_legal_knowledge_type (knowledge_type, business_domain),
                  KEY idx_legal_knowledge_article (article_number),
                  KEY idx_legal_knowledge_sha (content_sha256)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS legal_knowledge_chunk (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  knowledge_id BIGINT NOT NULL,
                  chunk_strategy VARCHAR(64),
                  chunk_version VARCHAR(64),
                  section_type VARCHAR(64),
                  section_title VARCHAR(255),
                  header_path VARCHAR(512),
                  retrieval_role VARCHAR(64),
                  section_weight DECIMAL(5,2),
                  section_order INT,
                  chunk_index INT,
                  char_start_offset INT,
                  char_end_offset INT,
                  chunk_hash CHAR(64),
                  chunk_text LONGTEXT,
                  retrieval_text LONGTEXT,
                  char_count INT,
                  token_count INT,
                  metadata_json JSON,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  deleted TINYINT DEFAULT 0,
                  KEY idx_legal_knowledge_chunk_doc (knowledge_id),
                  KEY idx_legal_knowledge_chunk_role (retrieval_role),
                  KEY idx_legal_knowledge_chunk_hash (chunk_hash)
                )
                """);
    }

    private void ensureColumnExists(String tableName, String columnName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName
        );

        if (count == null || count == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }
}
