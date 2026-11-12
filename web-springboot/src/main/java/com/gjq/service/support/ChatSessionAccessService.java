package com.gjq.service.support;

import com.gjq.common.exception.BusinessException;
import com.gjq.entity.ChatSession;
import com.gjq.entity.User;
import com.gjq.service.ChatSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class ChatSessionAccessService {

    private final ChatSessionService chatSessionService;
    private final RequestAuthorizationService requestAuthorizationService;

    public ChatSessionAccessService(ChatSessionService chatSessionService,
                                    RequestAuthorizationService requestAuthorizationService) {
        this.chatSessionService = chatSessionService;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    public ChatSession requireSessionAccess(HttpServletRequest request, Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException("会话不存在");
        }

        User currentUser = requestAuthorizationService.requireCurrentUser(request);
        ChatSession session = chatSessionService.getById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!requestAuthorizationService.canAccessUser(currentUser, session.getUserId())) {
            throw new BusinessException(403, "无权访问该会话");
        }
        return session;
    }
}
