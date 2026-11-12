package com.gjq.controller;

import com.gjq.common.Result;
import com.gjq.entity.UserBehavior;
import com.gjq.mapper.UserBehaviorMapper;
import com.gjq.service.UserActionService;
import com.gjq.service.support.RequestAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/user-action")
public class UserActionController {

    private final UserActionService userActionService;
    private final UserBehaviorMapper userBehaviorMapper;
    private final RequestAuthorizationService requestAuthorizationService;

    public UserActionController(UserActionService userActionService,
                                UserBehaviorMapper userBehaviorMapper,
                                RequestAuthorizationService requestAuthorizationService) {
        this.userActionService = userActionService;
        this.userBehaviorMapper = userBehaviorMapper;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success(userActionService.getStats());
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboard(HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success(userActionService.getDashboardInsights());
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> getList(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String username,
                                               @RequestParam(required = false) String actionType,
                                               @RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate,
                                               HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success(userActionService.getActionPage(page, size, username, actionType, startDate, endDate));
    }

    @PostMapping("/search-log")
    public Result<String> logSearch(@RequestParam Long userId,
                                    @RequestParam String query,
                                    @RequestParam(defaultValue = "案例检索") String source,
                                    HttpServletRequest request) {
        requestAuthorizationService.requireUserAccess(request, userId);
        if (query == null || query.trim().isEmpty()) {
            return Result.success("空查询已忽略");
        }

        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setBehaviorType("search");
        behavior.setTargetType(source);
        behavior.setDetails(query.trim());
        behavior.setUserAgent(source);
        userBehaviorMapper.insert(behavior);
        return Result.success("搜索记录已写入");
    }
}
