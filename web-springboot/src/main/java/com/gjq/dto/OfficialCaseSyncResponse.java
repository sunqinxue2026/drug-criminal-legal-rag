package com.gjq.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OfficialCaseSyncResponse {

    private int totalRecords;
    private int createdCount;
    private int updatedCount;
    private int deletedCount;
    private int skippedCount;
    private int cleanedBehaviorCount;
    private String message;
    private List<SyncItemResult> results = new ArrayList<>();

    @Data
    public static class SyncItemResult {
        private String title;
        private String action;
        private String sourceFileName;
        private String categoryName;
    }
}
