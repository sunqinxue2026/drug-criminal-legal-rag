package com.gjq.controller;

import com.gjq.client.FileClient;
import com.gjq.common.Result;
import com.gjq.service.support.RequestAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileClient fileClient;

    @Mock
    private RequestAuthorizationService requestAuthorizationService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private FileController fileController;

    @Test
    void uploadFileRequiresAuthenticatedUserAndReturnsUploadPayload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "sample.png", "image/png", "sample".getBytes());
        Map<String, Object> payload = Map.of(
                "bucket", "chat-images",
                "objectKey", "abc.png",
                "url", "http://localhost:5001/file/chat-images/abc.png"
        );
        when(fileClient.uploadFile(file, "chat-images")).thenReturn(payload);

        Result<Map<String, Object>> result = fileController.uploadFile(file, "chat-images", httpServletRequest);

        verify(requestAuthorizationService).requireCurrentUser(httpServletRequest);
        verify(fileClient).uploadFile(file, "chat-images");
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("bucket", "chat-images");
    }

    @Test
    void getFileUrlBuildsUrlFromBucketAndObjectKey() {
        when(fileClient.getFileUrl("chat-images", "abc.png"))
                .thenReturn("http://localhost:5001/file/chat-images/abc.png");

        Result<String> result = fileController.getFileUrl("chat-images", "abc.png");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo("http://localhost:5001/file/chat-images/abc.png");
    }
}
