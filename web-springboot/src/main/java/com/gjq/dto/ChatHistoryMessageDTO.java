package com.gjq.dto;

import lombok.Data;

@Data
public class ChatHistoryMessageDTO {
    private String role;
    private String content;
    private String createTime;
    private String imageUrl;
    private String imageName;
}
