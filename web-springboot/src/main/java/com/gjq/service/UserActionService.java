package com.gjq.service;

import com.gjq.vo.UserActionVO;

import java.util.List;
import java.util.Map;

public interface UserActionService {
    Map<String, Object> getStats();
    Map<String, Object> getDashboardInsights();
    Map<String, Object> getActionPage(int page, int size, String username, String actionType, String startDate, String endDate);
    List<UserActionVO> listAllActions();
}
