package com.gjq.controller;

import com.gjq.common.Result;
import com.gjq.dto.DocumentGenerateDTO;
import com.gjq.entity.ChatSession;
import com.gjq.entity.User;
import com.gjq.service.ChatSessionService;
import com.gjq.service.DocumentGenerationService;
import com.gjq.service.support.RequestAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/document-generation")
public class DocumentGenerationController {

    private final DocumentGenerationService documentGenerationService;
    private final ChatSessionService chatSessionService;
    private final RequestAuthorizationService requestAuthorizationService;

    public DocumentGenerationController(DocumentGenerationService documentGenerationService,
                                        ChatSessionService chatSessionService,
                                        RequestAuthorizationService requestAuthorizationService) {
        this.documentGenerationService = documentGenerationService;
        this.chatSessionService = chatSessionService;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody DocumentGenerateDTO request,
                                                HttpServletRequest servletRequest) {
        User currentUser = requestAuthorizationService.requireCurrentUser(servletRequest);
        ChatSession session = chatSessionService.getById(request.getSessionId());
        if (session == null) {
            return Result.error("咨询会话不存在");
        }
        requestAuthorizationService.requireUserAccess(servletRequest, session.getUserId());
        if (request.getUserId() == null) {
            request.setUserId(currentUser.getId());
        }
        return Result.success("文档生成成功", documentGenerationService.generateDocument(request));
    }
}
