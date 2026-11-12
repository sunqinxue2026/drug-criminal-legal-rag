package com.gjq.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Test
    void uploadFileUsesInjectedRestTemplateAndMultipartPayload() throws Exception {
        FileClient fileClient = new FileClient(restTemplate);
        ReflectionTestUtils.setField(fileClient, "fileServiceUrl", "http://localhost:5001");
        ReflectionTestUtils.setField(fileClient, "internalServiceToken", "inner-token");
        ReflectionTestUtils.setField(fileClient, "internalHeaderName", "X-Internal-Service-Token");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "sample".getBytes()
        );
        Map<String, Object> responseBody = Map.of(
                "code", 200,
                "data", Map.of(
                        "bucket", "chat-documents",
                        "objectKey", "sample.txt",
                        "url", "http://localhost:5001/file/chat-documents/sample.txt"
                )
        );
        when(restTemplate.postForEntity(
                eq("http://localhost:5001/file/upload"),
                ArgumentMatchers.<HttpEntity<MultiValueMap<String, Object>>>any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(responseBody));

        Map<String, Object> result = fileClient.uploadFile(file, "chat-documents");

        ArgumentCaptor<HttpEntity<MultiValueMap<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("http://localhost:5001/file/upload"), captor.capture(), eq(Map.class));
        HttpEntity<MultiValueMap<String, Object>> requestEntity = captor.getValue();

        assertThat(result).containsEntry("bucket", "chat-documents");
        assertThat(requestEntity.getHeaders().getContentType()).isEqualTo(MediaType.MULTIPART_FORM_DATA);
        assertThat(requestEntity.getHeaders().getFirst("X-Internal-Service-Token")).isEqualTo("inner-token");
        assertThat(requestEntity.getBody()).isNotNull();
        assertThat(requestEntity.getBody()).containsKey("file");
        assertThat(requestEntity.getBody()).containsKey("bucket");
    }

    @Test
    void getFileUrlBuildsPublicFilePath() {
        FileClient fileClient = new FileClient(restTemplate);
        ReflectionTestUtils.setField(fileClient, "fileServiceUrl", "http://localhost:5001");

        assertThat(fileClient.getFileUrl("chat-images", "demo.png"))
                .isEqualTo("http://localhost:5001/file/chat-images/demo.png");
    }
}
