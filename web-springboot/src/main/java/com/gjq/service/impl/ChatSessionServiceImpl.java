package com.gjq.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gjq.entity.ChatSession;
import com.gjq.mapper.ChatSessionMapper;
import com.gjq.service.ChatSessionService;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;

@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    @Override
    public List<ChatSession> getUserSessions(Long userId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId);
        wrapper.orderByDesc(ChatSession::getUpdateTime);
        return this.list(wrapper);
    }

    @Override
    public ChatSession createSession(Long userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title);
        this.save(session);
        return session;
    }
}
