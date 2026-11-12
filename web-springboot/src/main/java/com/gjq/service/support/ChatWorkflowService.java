package com.gjq.service.support;

import com.gjq.client.AlgorithmClient;
import com.gjq.common.exception.BusinessException;
import com.gjq.dto.ChatRequestDTO;
import com.gjq.entity.ChatMessage;
import com.gjq.entity.ChatSession;
import com.gjq.service.ChatMessageService;
import com.gjq.service.ChatSessionService;
import com.gjq.vo.ChatMessageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

@Service
public class ChatWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(ChatWorkflowService.class);

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final AlgorithmClient algorithmClient;
    private final ChatRequestPreprocessor chatRequestPreprocessor;
    private final ChatMessageViewFactory chatMessageViewFactory;
    private final ChatStatisticsService chatStatisticsService;

    public ChatWorkflowService(ChatSessionService chatSessionService,
                               ChatMessageService chatMessageService,
                               AlgorithmClient algorithmClient,
                               ChatRequestPreprocessor chatRequestPreprocessor,
                               ChatMessageViewFactory chatMessageViewFactory,
                               ChatStatisticsService chatStatisticsService) {
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
        this.algorithmClient = algorithmClient;
        this.chatRequestPreprocessor = chatRequestPreprocessor;
        this.chatMessageViewFactory = chatMessageViewFactory;
        this.chatStatisticsService = chatStatisticsService;
    }

    public List<ChatMessageVO> getSessionMessages(Long sessionId) {
        return chatMessageService.getSessionMessages(sessionId).stream()
                .map(chatMessageViewFactory::toChatMessageVO)
                .toList();
    }

    public Map<String, Object> getChatStats() {
        return chatStatisticsService.getChatStats();
    }

    @SuppressWarnings("unchecked")
    public ChatMessageVO sendMessage(ChatRequestDTO request) {
        PreparedChatRequest preparedRequest = chatRequestPreprocessor.prepare(request);

        chatMessageService.addMessage(
                preparedRequest.sessionId(),
                "user",
                preparedRequest.userMessage(),
                preparedRequest.imageUrl(),
                preparedRequest.imageName()
        );
        updateSessionOnUserMessage(preparedRequest.sessionId(), preparedRequest.userMessage());

        try {
            Map<String, Object> response = algorithmClient.chat(
                    preparedRequest.algorithmQuestion(),
                    preparedRequest.sessionId(),
                    preparedRequest.imageUrl(),
                    preparedRequest.imageName(),
                    preparedRequest.model(),
                    preparedRequest.smartConsulting(),
                    preparedRequest.profile(),
                    preparedRequest.history(),
                    preparedRequest.attachedCases(),
                    preparedRequest.attachedDocuments()
            );

            if (response == null) {
                throw new BusinessException(ChatWorkflowDefaults.DEFAULT_AI_EMPTY_ERROR);
            }

            Map<String, Object> payload = unwrapPayload(response);
            String answer = readString(payload.get("answer"));
            if (answer.isBlank()) {
                answer = ChatWorkflowDefaults.DEFAULT_FALLBACK_ANSWER;
            }

            ChatMessage aiMessage = chatMessageService.addMessage(preparedRequest.sessionId(), "assistant", answer);
            updateSessionPreview(preparedRequest.sessionId(), answer);

            return chatMessageViewFactory.toAssistantView(
                    aiMessage,
                    payload,
                    preparedRequest.imageUrl(),
                    preparedRequest.imageName()
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Chat workflow failed for session {}", preparedRequest.sessionId(), e);
            throw new BusinessException(ChatWorkflowDefaults.DEFAULT_AI_FAILURE_PREFIX + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public ChatMessageVO streamMessage(ChatRequestDTO request, BiConsumer<String, Map<String, Object>> eventConsumer) {
        PreparedChatRequest preparedRequest = chatRequestPreprocessor.prepare(request);

        chatMessageService.addMessage(
                preparedRequest.sessionId(),
                "user",
                preparedRequest.userMessage(),
                preparedRequest.imageUrl(),
                preparedRequest.imageName()
        );
        updateSessionOnUserMessage(preparedRequest.sessionId(), preparedRequest.userMessage());

        try {
            AtomicReference<Map<String, Object>> finalPayloadRef = new AtomicReference<>();
            algorithmClient.chatStream(
                    preparedRequest.algorithmQuestion(),
                    preparedRequest.sessionId(),
                    preparedRequest.imageUrl(),
                    preparedRequest.imageName(),
                    preparedRequest.model(),
                    preparedRequest.smartConsulting(),
                    preparedRequest.profile(),
                    preparedRequest.history(),
                    preparedRequest.attachedCases(),
                    preparedRequest.attachedDocuments(),
                    (eventName, payload) -> {
                        if ("done".equals(eventName)) {
                            finalPayloadRef.set(payload);
                            return;
                        }
                        if ("error".equals(eventName)) {
                            throw new BusinessException(readString(payload.get("message")));
                        }
                        eventConsumer.accept(eventName, payload);
                    }
            );

            Map<String, Object> payload = finalPayloadRef.get();
            if (payload == null) {
                throw new BusinessException(ChatWorkflowDefaults.DEFAULT_AI_EMPTY_ERROR);
            }

            String answer = readString(payload.get("content"));
            if (answer.isBlank()) {
                answer = ChatWorkflowDefaults.DEFAULT_FALLBACK_ANSWER;
            }

            ChatMessage aiMessage = chatMessageService.addMessage(preparedRequest.sessionId(), "assistant", answer);
            updateSessionPreview(preparedRequest.sessionId(), answer);

            return chatMessageViewFactory.toAssistantView(
                    aiMessage,
                    normalizeStreamPayload(payload),
                    preparedRequest.imageUrl(),
                    preparedRequest.imageName()
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Streaming chat workflow failed for session {}", preparedRequest.sessionId(), e);
            throw new BusinessException(ChatWorkflowDefaults.DEFAULT_AI_FAILURE_PREFIX + e.getMessage());
        }
    }

    private void updateSessionPreview(Long sessionId, String answer) {
        ChatSession session = chatSessionService.getById(sessionId);
        if (session == null) {
            return;
        }
        session.setLastMessage(answer.length() > 50 ? answer.substring(0, 50) + "..." : answer);
        chatSessionService.updateById(session);
    }

    private void updateSessionOnUserMessage(Long sessionId, String userMessage) {
        ChatSession session = chatSessionService.getById(sessionId);
        if (session == null) {
            return;
        }
        String preview = ellipsize(userMessage, 50);
        session.setLastMessage(preview);
        if (shouldReplaceSessionTitle(session.getTitle())) {
            session.setTitle(buildSessionTitle(userMessage));
        }
        chatSessionService.updateById(session);
    }

    private boolean shouldReplaceSessionTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return true;
        }
        String normalized = title.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("涉毒咨询") || normalized.startsWith("新的咨询");
    }

    private String buildSessionTitle(String userMessage) {
        String normalized = StringUtils.hasText(userMessage)
                ? userMessage.replaceAll("\\s+", " ").trim()
                : "";
        if (!StringUtils.hasText(normalized)) {
            return "涉毒咨询";
        }
        return ellipsize(normalized, 24);
    }

    private String ellipsize(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > limit ? value.substring(0, limit) + "..." : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapPayload(Map<String, Object> response) {
        Object nested = response.get("data");
        if (nested instanceof Map<?, ?> dataMap) {
            return (Map<String, Object>) dataMap;
        }
        return response;
    }

    private String readString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> normalizeStreamPayload(Map<String, Object> payload) {
        payload.putIfAbsent("answer", payload.get("content"));
        payload.putIfAbsent("image_analysis", payload.get("imageAnalysis"));
        payload.putIfAbsent("query_analysis", payload.get("queryAnalysis"));
        payload.putIfAbsent("response_mode", payload.get("responseMode"));
        payload.putIfAbsent("structured_answer", payload.get("structuredAnswer"));
        return payload;
    }
}
