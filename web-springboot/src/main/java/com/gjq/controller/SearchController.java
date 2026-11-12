package com.gjq.controller;

import com.gjq.client.AlgorithmClient;
import com.gjq.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private AlgorithmClient algorithmClient;

    @GetMapping
    public Result<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        try {
            Map<String, Object> result = algorithmClient.search(query, topK);
            Object nestedData = result != null ? result.get("data") : null;
            if (nestedData instanceof Map<?, ?> nestedMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) nestedMap;
                return Result.success(payload);
            }
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("搜索服务调用失败: " + e.getMessage());
        }
    }
}
