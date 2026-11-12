package com.gjq.dto;

import lombok.Data;

@Data
public class ItemImportResult {
    private String fileName;
    private boolean success;
    private boolean skipped;
    private boolean duplicate;
    private Long itemId;
    private Long existingItemId;
    private String existingTitle;
    private String title;
    private Long categoryId;
    private String categoryName;
    private Integer charCount;
    private String message;
}
