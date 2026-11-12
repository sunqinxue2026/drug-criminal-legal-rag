package com.gjq.dto;

import lombok.Data;

@Data
public class LegalKnowledgeImportResult {
    private String sourceName;
    private boolean success;
    private boolean skipped;
    private boolean duplicate;
    private Long knowledgeId;
    private Long existingKnowledgeId;
    private String title;
    private String articleNumber;
    private Long categoryId;
    private String categoryName;
    private Integer charCount;
    private Integer chunkCount;
    private String message;
}
