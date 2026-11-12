package com.gjq.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageVO {

    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private String model;

    private LocalDateTime createTime;

    private Object references;

    private String imageUrl;

    private String imageName;

    private String imageAnalysis;

    private Object queryAnalysis;

    private String responseMode;

    private StructuredAnswerVO structuredAnswer;

    private String disclaimer;

    private String scopeStatement;

    private String privacyNotice;
}
