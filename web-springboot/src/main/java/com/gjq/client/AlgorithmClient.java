package com.gjq.client;

import com.alibaba.fastjson2.JSON;
import com.gjq.dto.ChatAttachmentCaseDTO;
import com.gjq.dto.ChatAttachmentDocumentDTO;
import com.gjq.dto.ChatHistoryMessageDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Component
public class AlgorithmClient {

    @Value("${external.algorithm-service.base-url}")
    private String algorithmServiceUrl;

    @Value("${external.algorithm-service.connect-timeout-ms:5000}")
    private long connectTimeoutMs;

    @Value("${external.algorithm-service.read-timeout-ms:60000}")
    private long readTimeoutMs;

    private final RestTemplate restTemplate;

    public AlgorithmClient(@Qualifier("algorithmServiceRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> chat(String question,
                                    Long sessionId,
                                    String imageUrl,
                                    String imageName,
                                    String model,
                                    Boolean smartConsulting,
                                    String profile,
                                    List<ChatHistoryMessageDTO> history,
                                    List<ChatAttachmentCaseDTO> attachedCases,
                                    List<ChatAttachmentDocumentDTO> attachedDocuments) {
        String url = algorithmServiceUrl + "/api/chat";

        Map<String, Object> request = new HashMap<>();
        request.put("question", question);
        request.put("session_id", sessionId);
        request.put("image_url", imageUrl);
        request.put("image_name", imageName);
        request.put("model", model);
        request.put("smart_consulting", smartConsulting);
        request.put("profile", profile);
        request.put("history", history);
        request.put("attached_cases", attachedCases);
        request.put("attached_documents", attachedDocuments);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        return restTemplate.postForObject(url, entity, Map.class);
    }

    public void chatStream(String question,
                           Long sessionId,
                           String imageUrl,
                           String imageName,
                           String model,
                           Boolean smartConsulting,
                           String profile,
                           List<ChatHistoryMessageDTO> history,
                           List<ChatAttachmentCaseDTO> attachedCases,
                           List<ChatAttachmentDocumentDTO> attachedDocuments,
                           BiConsumer<String, Map<String, Object>> eventConsumer) {
        String url = algorithmServiceUrl + "/api/chat/stream";

        Map<String, Object> request = new HashMap<>();
        request.put("question", question);
        request.put("session_id", sessionId);
        request.put("image_url", imageUrl);
        request.put("image_name", imageName);
        request.put("model", model);
        request.put("smart_consulting", smartConsulting);
        request.put("profile", profile);
        request.put("history", history);
        request.put("attached_cases", attachedCases);
        request.put("attached_documents", attachedDocuments);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(readTimeoutMs))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(request), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("聊天流服务调用失败: HTTP " + response.statusCode() + " - " + errorBody);
            }
            consumeSseStream(response.body(), eventConsumer);
        } catch (IOException ex) {
            throw new IllegalStateException("聊天流服务不可用，请先启动 Flask 服务（默认地址 " + algorithmServiceUrl + "）", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("聊天流服务被中断", ex);
        }
    }

    public Map<String, Object> search(String query, int topK) {
        String url = algorithmServiceUrl + "/api/search";

        Map<String, Object> request = new HashMap<>();
        request.put("query", query);
        request.put("top_k", topK);
        request.put("profile", "drug_crime");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        return restTemplate.postForObject(url, entity, Map.class);
    }

    public Map<String, Object> extractDocumentText(MultipartFile file) throws IOException {
        return extractDocumentText(file.getOriginalFilename(), file.getBytes());
    }

    public Map<String, Object> extractDocumentText(String fileName, byte[] payload) {
        String url = algorithmServiceUrl + "/api/document/extract-text";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource(payload) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            return response.getBody();
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException(
                    "\u6587\u6863\u89e3\u6790\u670d\u52a1\u4e0d\u53ef\u7528\uff0c\u8bf7\u5148\u542f\u52a8 Flask \u670d\u52a1\uff08web-flask/app.py\uff0c\u9ed8\u8ba4\u5730\u5740 "
                            + algorithmServiceUrl + "\uff09",
                    ex
            );
        }
    }

    public Map<String, Object> rebuildLegalIndex() {
        String url = algorithmServiceUrl + "/api/vector/rebuild";

        Map<String, Object> request = new HashMap<>();
        request.put("index_name", "legal_cases_drug_crime");
        request.put("profile", "drug_crime");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        return restTemplate.postForObject(url, entity, Map.class);
    }

    public Map<String, Object> getLlmModels() {
        String url = algorithmServiceUrl + "/api/llm/models";
        return restTemplate.getForObject(url, Map.class);
    }

    public Map<String, Object> getVectorStatus() {
        String url = algorithmServiceUrl + "/api/vector/status";
        return restTemplate.getForObject(url, Map.class);
    }

    @SuppressWarnings("unchecked")
    private void consumeSseStream(InputStream stream, BiConsumer<String, Map<String, Object>> eventConsumer) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String eventName = "message";
            StringBuilder dataBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    if (dataBuilder.length() > 0) {
                        Map<String, Object> payload = JSON.parseObject(dataBuilder.toString(), Map.class);
                        eventConsumer.accept(eventName, payload);
                        dataBuilder.setLength(0);
                        eventName = "message";
                    }
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (dataBuilder.length() > 0) {
                        dataBuilder.append('\n');
                    }
                    dataBuilder.append(line.substring(5).trim());
                }
            }
            if (dataBuilder.length() > 0) {
                Map<String, Object> payload = JSON.parseObject(dataBuilder.toString(), Map.class);
                eventConsumer.accept(eventName, payload);
            }
        }
    }
}
