package com.gjq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gjq.entity.ChatSession;
import java.util.List;

public interface ChatSessionService extends IService<ChatSession> {
    List<ChatSession> getUserSessions(Long userId);
    ChatSession createSession(Long userId, String title);
}
