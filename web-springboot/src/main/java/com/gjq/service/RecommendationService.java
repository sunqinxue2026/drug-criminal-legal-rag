package com.gjq.service;

import java.util.Map;

public interface RecommendationService {
    Map<String, Object> recommendCases(Long userId, int limit);
}
