package com.gjq.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatRequestDTO {
    private Long sessionId;
    private String message;
    private String model;
    private Boolean smartConsulting;
    private String imageUrl;
    private String imageName;
    private String profile;
    private List<ChatHistoryMessageDTO> history;
    private List<ChatAttachmentCaseDTO> attachedCases;
    private List<ChatAttachmentDocumentDTO> attachedDocuments;
}
