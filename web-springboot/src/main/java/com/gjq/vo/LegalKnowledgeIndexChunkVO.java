package com.gjq.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class LegalKnowledgeIndexChunkVO {
    private Long chunkId;
    private Long knowledgeId;
    private Long documentId;
    private Long itemId;
    private Integer chunkIndex;
    private String title;
    private String summary;
    private Long categoryId;
    private String categoryName;
    private String tags;
    private String documentType;
    private String documentSubtype;
    private String caseType;
    private String cause;
    private String businessDomain;
    private String procedureDomain;
    private String courtName;
    private String courtLevel;
    private LocalDate decisionDate;
    private String decisionResult;
    private String keywords;
    private String aliases;
    private String issueTags;
    private String resultTags;
    private Integer sourceReliability;
    private String source;
    private String sourceName;
    private String sourceUrl;
    private String issuer;
    private LocalDate effectiveDate;
    private String currentStatus;
    private String sectionType;
    private String sectionTitle;
    private String headerPath;
    private String retrievalRole;
    private BigDecimal sectionWeight;
    private String chunkText;
    private String retrievalText;
}
