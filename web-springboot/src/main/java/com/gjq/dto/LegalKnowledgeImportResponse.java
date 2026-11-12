package com.gjq.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LegalKnowledgeImportResponse {
    private int totalRecords;
    private int importedCount;
    private int skippedCount;
    private int failedCount;
    private boolean reindexed;
    private String indexMessage;
    private List<LegalKnowledgeImportResult> results = new ArrayList<>();
}
