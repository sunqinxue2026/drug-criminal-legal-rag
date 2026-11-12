package com.gjq.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gjq.entity.ChatMessage;
import com.gjq.mapper.ChatMessageMapper;
import com.gjq.service.ChatMessageService;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {

    @Override
    public List<ChatMessage> getSessionMessages(Long sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId);
        wrapper.orderByAsc(ChatMessage::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public ChatMessage addMessage(Long sessionId, String role, String content) {
        return addMessage(sessionId, role, content, null, null);
    }

    @Override
    public ChatMessage addMessage(Long sessionId, String role, String content, String imageUrl, String imageName) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setImageUrl(imageUrl);
        message.setImageName(imageName);
        this.save(message);
        return message;
    }

    @Override
    public ChatMessage getLatestImageMessage(Long sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
                .isNotNull(ChatMessage::getImageUrl)
                .ne(ChatMessage::getImageUrl, "")
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1");
        return this.getOne(wrapper, false);
    }
}
