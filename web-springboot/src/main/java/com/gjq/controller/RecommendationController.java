package com.gjq.controller;

import com.gjq.common.Result;
import com.gjq.service.RecommendationService;
import com.gjq.service.support.RequestAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/recommend")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final RequestAuthorizationService requestAuthorizationService;

    public RecommendationController(RecommendationService recommendationService,
                                    RequestAuthorizationService requestAuthorizationService) {
        this.recommendationService = recommendationService;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @GetMapping("/cases")
    public Result<Map<String, Object>> recommendCases(@RequestParam(required = false) Long userId,
                                                      @RequestParam(defaultValue = "6") int limit,
                                                      HttpServletRequest request) {
        Long accessibleUserId = requestAuthorizationService.resolveAccessibleUserId(request, userId);
        return Result.success(recommendationService.recommendCases(accessibleUserId, limit));
    }
}
