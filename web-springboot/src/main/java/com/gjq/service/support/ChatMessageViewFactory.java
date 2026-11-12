package com.gjq.service.support;

import com.gjq.entity.ChatMessage;
import com.gjq.utils.StructuredAnswerParser;
import com.gjq.vo.ChatMessageVO;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChatMessageViewFactory {

    public ChatMessageVO toChatMessageVO(ChatMessage message) {
        ChatMessageVO view = new ChatMessageVO();
        view.setId(message.getId());
        view.setSessionId(message.getSessionId());
        view.setRole(message.getRole());
        view.setContent(message.getContent());
        view.setCreateTime(message.getCreateTime());
        view.setImageUrl(message.getImageUrl());
        view.setImageName(message.getImageName());
        view.setDisclaimer(ChatWorkflowDefaults.DEFAULT_DISCLAIMER);
        view.setScopeStatement(ChatWorkflowDefaults.DEFAULT_SCOPE_STATEMENT);
        view.setPrivacyNotice(ChatWorkflowDefaults.DEFAULT_PRIVACY_NOTICE);
        if ("assistant".equalsIgnoreCase(message.getRole())) {
            view.setStructuredAnswer(StructuredAnswerParser.parse(message.getContent()));
        }
        return view;
    }

    public ChatMessageVO toAssistantView(ChatMessage message,
                                         Map<String, Object> payload,
                                         String imageUrl,
                                         String imageName) {
        ChatMessageVO result = toChatMessageVO(message);
        result.setReferences(payload.get("references"));
        result.setModel(readString(payload.get("model")));
        result.setImageUrl(imageUrl);
        result.setImageName(imageName);
        result.setImageAnalysis(readString(payload.get("image_analysis")));
        result.setQueryAnalysis(payload.get("query_analysis"));
        result.setResponseMode(readString(payload.get("response_mode")));
        result.setStructuredAnswer(StructuredAnswerParser.fromObject(payload.get("structured_answer"), message.getContent()));
        result.setDisclaimer(readStringOrDefault(payload.get("disclaimer"), ChatWorkflowDefaults.DEFAULT_DISCLAIMER));
        result.setScopeStatement(readStringOrDefault(payload.get("scope_statement"), ChatWorkflowDefaults.DEFAULT_SCOPE_STATEMENT));
        result.setPrivacyNotice(readStringOrDefault(payload.get("privacy_notice"), ChatWorkflowDefaults.DEFAULT_PRIVACY_NOTICE));
        return result;
    }

    private String readString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String readStringOrDefault(Object value, String defaultValue) {
        String text = readString(value);
        return text.isBlank() ? defaultValue : text;
    }
}
