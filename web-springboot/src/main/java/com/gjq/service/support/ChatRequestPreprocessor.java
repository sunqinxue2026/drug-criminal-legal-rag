package com.gjq.service.support;

import com.gjq.common.exception.BusinessException;
import com.gjq.dto.ChatRequestDTO;
import com.gjq.entity.ChatMessage;
import com.gjq.service.ChatMessageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatRequestPreprocessor {

    private final ChatMessageService chatMessageService;

    public ChatRequestPreprocessor(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    public PreparedChatRequest prepare(ChatRequestDTO request) {
        if (request.getSessionId() == null) {
            throw new BusinessException("会话不存在");
        }

        boolean hasText = StringUtils.hasText(request.getMessage());
        boolean hasImage = StringUtils.hasText(request.getImageUrl());

        String effectiveImageUrl = hasImage ? request.getImageUrl().trim() : null;
        String effectiveImageName = request.getImageName();

        if (!hasImage) {
            ChatMessage latestImageMessage = chatMessageService.getLatestImageMessage(request.getSessionId());
            if (latestImageMessage != null && StringUtils.hasText(latestImageMessage.getImageUrl())) {
                effectiveImageUrl = latestImageMessage.getImageUrl().trim();
                effectiveImageName = latestImageMessage.getImageName();
                hasImage = true;
            }
        }

        if (!hasText && !hasImage) {
            throw new BusinessException(ChatWorkflowDefaults.DEFAULT_EMPTY_ERROR);
        }

        String algorithmQuestion = hasText
                ? request.getMessage().trim()
                : ChatWorkflowDefaults.DEFAULT_IMAGE_PROMPT;

        String userMessage = buildUserMessage(request.getMessage(), hasText, hasImage, effectiveImageName);
        String profile = StringUtils.hasText(request.getProfile())
                ? request.getProfile().trim()
                : ChatWorkflowDefaults.DEFAULT_PROFILE;

        return new PreparedChatRequest(
                request.getSessionId(),
                userMessage,
                algorithmQuestion,
                effectiveImageUrl,
                effectiveImageName,
                request.getModel(),
                request.getSmartConsulting(),
                profile,
                request.getHistory(),
                request.getAttachedCases(),
                request.getAttachedDocuments()
        );
    }

    private String buildUserMessage(String message, boolean hasText, boolean hasImage, String imageName) {
        StringBuilder userMessage = new StringBuilder();
        if (hasImage) {
            String displayImageName = StringUtils.hasText(imageName) ? imageName : "已上传图片";
            userMessage.append("[图片附件] ").append(displayImageName);
            if (hasText) {
                userMessage.append("\n");
            }
        }
        if (hasText) {
            userMessage.append(message.trim());
        }
        return userMessage.toString();
    }
}
