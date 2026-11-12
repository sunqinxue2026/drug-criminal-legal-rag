package com.gjq.client;

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
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Map;

@Component
public class FileClient {

    @Value("${external.file-service.base-url}")
    private String fileServiceUrl;

    @Value("${app.security.internal-service-token:}")
    private String internalServiceToken;

    @Value("${app.security.internal-header-name:X-Internal-Service-Token}")
    private String internalHeaderName;

    private final RestTemplate restTemplate;

    public FileClient(@Qualifier("fileServiceRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> uploadFile(MultipartFile file, String bucket) throws Exception {
        String url = fileServiceUrl + "/file/upload";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (StringUtils.hasText(internalServiceToken)) {
            headers.set(internalHeaderName, internalServiceToken);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        });
        body.add("bucket", bucket);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
        return extractData(response.getBody());
    }

    public String getFileUrl(String bucket, String objectKey) {
        return fileServiceUrl + "/file/" + bucket + "/" + objectKey;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return Collections.emptyMap();
        }
        Object data = responseBody.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return (Map<String, Object>) dataMap;
        }
        return (Map<String, Object>) responseBody;
    }
}
