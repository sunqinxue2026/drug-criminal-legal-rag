package com.gjq.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天会话VO
 */
@Data
public class ChatSessionVO {
    
    private Long id;
    
    private Long userId;
    
    private String title;
    
    private String lastMessage;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    private Integer messageCount; // 消息数量
    
    private List<ChatMessageVO> recentMessages; // 最近的几条消息
}
