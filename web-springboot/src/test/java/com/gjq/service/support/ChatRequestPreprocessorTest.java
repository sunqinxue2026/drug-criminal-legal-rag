package com.gjq.service.support;

import com.gjq.common.exception.BusinessException;
import com.gjq.dto.ChatRequestDTO;
import com.gjq.entity.ChatMessage;
import com.gjq.service.ChatMessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRequestPreprocessorTest {

    @Mock
    private ChatMessageService chatMessageService;

    @InjectMocks
    private ChatRequestPreprocessor chatRequestPreprocessor;

    @Test
    void prepareUsesLatestImageWhenCurrentRequestHasNoImage() {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setSessionId(7L);
        request.setSmartConsulting(true);

        ChatMessage latestImage = new ChatMessage();
        latestImage.setImageUrl("http://example.com/evidence.png");
        latestImage.setImageName("evidence.png");
        when(chatMessageService.getLatestImageMessage(7L)).thenReturn(latestImage);

        PreparedChatRequest prepared = chatRequestPreprocessor.prepare(request);

        assertThat(prepared.algorithmQuestion()).isEqualTo(ChatWorkflowDefaults.DEFAULT_IMAGE_PROMPT);
        assertThat(prepared.imageUrl()).isEqualTo("http://example.com/evidence.png");
        assertThat(prepared.imageName()).isEqualTo("evidence.png");
        assertThat(prepared.userMessage()).contains("[图片附件]");
        assertThat(prepared.profile()).isEqualTo(ChatWorkflowDefaults.DEFAULT_PROFILE);
    }

    @Test
    void prepareRejectsEmptyMessageWhenNoCurrentOrHistoricalImageExists() {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setSessionId(8L);
        when(chatMessageService.getLatestImageMessage(8L)).thenReturn(null);

        assertThatThrownBy(() -> chatRequestPreprocessor.prepare(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ChatWorkflowDefaults.DEFAULT_EMPTY_ERROR);
    }
}
