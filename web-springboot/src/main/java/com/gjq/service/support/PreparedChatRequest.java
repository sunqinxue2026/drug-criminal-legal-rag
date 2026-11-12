package com.gjq.service.support;

import com.gjq.dto.ChatAttachmentCaseDTO;
import com.gjq.dto.ChatAttachmentDocumentDTO;
import com.gjq.dto.ChatHistoryMessageDTO;

import java.util.List;

public record PreparedChatRequest(
        Long sessionId,
        String userMessage,
        String algorithmQuestion,
        String imageUrl,
        String imageName,
        String model,
        Boolean smartConsulting,
        String profile,
        List<ChatHistoryMessageDTO> history,
        List<ChatAttachmentCaseDTO> attachedCases,
        List<ChatAttachmentDocumentDTO> attachedDocuments
) {
}
