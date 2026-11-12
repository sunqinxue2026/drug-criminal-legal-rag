package com.gjq.dto;

import lombok.Data;

import java.util.Map;

@Data
public class LegalKnowledgeRecordRequest {
    private Map<String, Object> record;
    private String sourceFileName;
    private Long categoryId;
}
