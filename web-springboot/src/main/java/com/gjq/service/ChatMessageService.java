package com.gjq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gjq.entity.ChatMessage;
import java.util.List;

public interface ChatMessageService extends IService<ChatMessage> {
    List<ChatMessage> getSessionMessages(Long sessionId);
    ChatMessage addMessage(Long sessionId, String role, String content);
    ChatMessage addMessage(Long sessionId, String role, String content, String imageUrl, String imageName);
    ChatMessage getLatestImageMessage(Long sessionId);
}
