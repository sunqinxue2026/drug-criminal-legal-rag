package com.gjq.dto;

import lombok.Data;

@Data
public class LegalDocumentImportResult {
    private String fileName;
    private boolean success;
    private boolean skipped;
    private boolean duplicate;
    private Long documentId;
    private Long existingDocumentId;
    private String existingTitle;
    private String title;
    private String caseNumber;
    private Long categoryId;
    private String categoryName;
    private Integer charCount;
    private Integer chunkCount;
    private String message;
}
