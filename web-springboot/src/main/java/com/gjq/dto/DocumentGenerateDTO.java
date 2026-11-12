package com.gjq.dto;

import lombok.Data;

@Data
public class DocumentGenerateDTO {

    private Long userId;

    private Long sessionId;

    private String documentType;

    private String template;

    private String title;

    private String customContent;

    private String format;
}
