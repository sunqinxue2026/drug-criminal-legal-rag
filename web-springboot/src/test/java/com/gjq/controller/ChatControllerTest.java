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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatSessionService chatSessionService;

    @Mock
    private RequestAuthorizationService requestAuthorizationService;

    @Mock
    private ChatSessionAccessService chatSessionAccessService;

    @Mock
    private ChatWorkflowService chatWorkflowService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private ChatController chatController;

    @Test
    void sendMessageDelegatesToWorkflowAfterSessionAccessCheck() {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setSessionId(12L);
        request.setMessage("请分析这个问题");

        ChatMessageVO messageVO = new ChatMessageVO();
        messageVO.setSessionId(12L);
        messageVO.setContent("分析结果");

        when(chatWorkflowService.sendMessage(request)).thenReturn(messageVO);

        Result<ChatMessageVO> result = chatController.sendMessage(request, httpServletRequest);

        verify(chatSessionAccessService).requireSessionAccess(httpServletRequest, 12L);
        verify(chatWorkflowService).sendMessage(request);
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getSessionId()).isEqualTo(12L);
    }

    @Test
    void getSessionMessagesChecksOwnershipBeforeReturningMessages() {
        ChatMessageVO messageVO = new ChatMessageVO();
        messageVO.setSessionId(12L);
        messageVO.setContent("初步结论：存在风险");

        when(chatWorkflowService.getSessionMessages(12L)).thenReturn(List.of(messageVO));

        Result<List<ChatMessageVO>> result = chatController.getSessionMessages(12L, httpServletRequest);

        verify(chatSessionAccessService).requireSessionAccess(httpServletRequest, 12L);
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getContent()).contains("风险");
    }

    @Test
    void createSessionChecksUserAccessBeforeSaving() {
        ChatSession session = new ChatSession();
        session.setId(9L);
        session.setUserId(5L);

        when(chatSessionService.createSession(5L, "新会话")).thenReturn(session);

        Result<ChatSession> result = chatController.createSession(5L, "新会话", httpServletRequest);

        verify(requestAuthorizationService).requireUserAccess(httpServletRequest, 5L);
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(9L);
    }
}
