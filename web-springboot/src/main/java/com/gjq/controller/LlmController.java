package com.gjq.controller;

import com.gjq.client.AlgorithmClient;
import com.gjq.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping
public class LlmController {

    private final AlgorithmClient algorithmClient;

    public LlmController(AlgorithmClient algorithmClient) {
        this.algorithmClient = algorithmClient;
    }

    @GetMapping("/llm/models")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> getLlmModels() {
        Map<String, Object> response = algorithmClient.getLlmModels();
        return Result.success(unwrapData(response));
    }

    @GetMapping("/vector/status")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> getVectorStatus() {
        Map<String, Object> response = algorithmClient.getVectorStatus();
        return Result.success(unwrapData(response));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapData(Map<String, Object> payload) {
        if (payload == null) {
            return new LinkedHashMap<>();
        }
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return (Map<String, Object>) dataMap;
        }
        return payload;
    }
}
