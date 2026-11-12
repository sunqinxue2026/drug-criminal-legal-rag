package com.gjq.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LegalDocumentImportResponse {
    private int totalFiles;
    private int importedCount;
    private int skippedCount;
    private int failedCount;
    private boolean reindexed;
    private String indexMessage;
    private List<LegalDocumentImportResult> results = new ArrayList<>();
}
