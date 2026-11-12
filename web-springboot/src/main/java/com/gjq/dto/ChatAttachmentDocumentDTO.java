package com.gjq.dto;

import lombok.Data;

@Data
public class ChatAttachmentDocumentDTO {
    private String name;
    private String url;
    private String preview;
    private String extractedText;
    private Integer charCount;
    private String statusLabel;
}
