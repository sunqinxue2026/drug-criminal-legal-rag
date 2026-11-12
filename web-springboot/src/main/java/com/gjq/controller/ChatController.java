package com.gjq.controller;

import com.gjq.common.Result;
import com.gjq.dto.ChatRequestDTO;
import com.gjq.entity.ChatSession;
import com.gjq.service.ChatSessionService;
import com.gjq.service.support.ChatSessionAccessService;
import com.gjq.service.support.ChatWorkflowService;
import com.gjq.service.support.RequestAuthorizationService;
import com.gjq.vo.ChatMessageVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final ExecutorService STREAM_EXECUTOR = Executors.newCachedThreadPool();

    private final ChatSessionService chatSessionService;
    private final RequestAuthorizationService requestAuthorizationService;
    private final ChatSessionAccessService chatSessionAccessService;
    private final ChatWorkflowService chatWorkflowService;

    public ChatController(ChatSessionService chatSessionService,
                          RequestAuthorizationService requestAuthorizationService,
                          ChatSessionAccessService chatSessionAccessService,
                          ChatWorkflowService chatWorkflowService) {
        this.chatSessionService = chatSessionService;
        this.requestAuthorizationService = requestAuthorizationService;
        this.chatSessionAccessService = chatSessionAccessService;
        this.chatWorkflowService = chatWorkflowService;
    }

    @GetMapping("/sessions/{userId}")
    public Result<List<ChatSession>> getUserSessions(@PathVariable Long userId, HttpServletRequest request) {
        requestAuthorizationService.requireUserAccess(request, userId);
        return Result.success(chatSessionService.getUserSessions(userId));
    }

    @PostMapping("/session")
    public Result<ChatSession> createSession(@RequestParam Long userId,
                                             @RequestParam String title,
                                             HttpServletRequest request) {
        requestAuthorizationService.requireUserAccess(request, userId);
        return Result.success(chatSessionService.createSession(userId, title));
    }

    @GetMapping("/messages/{sessionId}")
    public Result<List<ChatMessageVO>> getSessionMessages(@PathVariable Long sessionId, HttpServletRequest request) {
        chatSessionAccessService.requireSessionAccess(request, sessionId);
        return Result.success(chatWorkflowService.getSessionMessages(sessionId));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getChatStats(HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success(chatWorkflowService.getChatStats());
    }

    @PostMapping("/send")
    public Result<ChatMessageVO> sendMessage(@RequestBody ChatRequestDTO request, HttpServletRequest servletRequest) {
        chatSessionAccessService.requireSessionAccess(servletRequest, request.getSessionId());
        return Result.success(chatWorkflowService.sendMessage(request));
    }

    @PostMapping(value = "/send-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(@RequestBody ChatRequestDTO request, HttpServletRequest servletRequest) {
        chatSessionAccessService.requireSessionAccess(servletRequest, request.getSessionId());
        SseEmitter emitter = new SseEmitter(0L);
        STREAM_EXECUTOR.execute(() -> {
            try {
                ChatMessageVO finalMessage = chatWorkflowService.streamMessage(
                        request,
                        (eventName, payload) -> sendEvent(emitter, eventName, payload)
                );
                sendEvent(emitter, "done", finalMessage);
                emitter.complete();
            } catch (Exception ex) {
                sendEvent(emitter, "error", Map.of("message", ex.getMessage()));
                emitter.complete();
            }
        });
        return emitter;
    }

    @DeleteMapping("/session/{id}")
    public Result<String> deleteSession(@PathVariable Long id, HttpServletRequest request) {
        chatSessionAccessService.requireSessionAccess(request, id);
        chatSessionService.removeById(id);
        return Result.success("鍒犻櫎鎴愬姛");
    }
    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("流式响应发送失败", ex);
        }
    }
}
