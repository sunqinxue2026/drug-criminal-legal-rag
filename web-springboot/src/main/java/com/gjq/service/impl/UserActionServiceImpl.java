package com.gjq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gjq.entity.Category;
import com.gjq.entity.ChatMessage;
import com.gjq.entity.ChatSession;
import com.gjq.entity.Item;
import com.gjq.entity.User;
import com.gjq.entity.UserBehavior;
import com.gjq.mapper.UserBehaviorMapper;
import com.gjq.service.CategoryService;
import com.gjq.service.ChatMessageService;
import com.gjq.service.ChatSessionService;
import com.gjq.service.ItemService;
import com.gjq.service.UserActionService;
import com.gjq.service.UserService;
import com.gjq.vo.UserActionVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserActionServiceImpl implements UserActionService {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ItemService itemService;
    private final UserService userService;
    private final CategoryService categoryService;
    private final UserBehaviorMapper userBehaviorMapper;

    public UserActionServiceImpl(ChatSessionService chatSessionService,
                                 ChatMessageService chatMessageService,
                                 ItemService itemService,
                                 UserService userService,
                                 CategoryService categoryService,
                                 UserBehaviorMapper userBehaviorMapper) {
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
        this.itemService = itemService;
        this.userService = userService;
        this.categoryService = categoryService;
        this.userBehaviorMapper = userBehaviorMapper;
    }

    @Override
    public Map<String, Object> getStats() {
        long totalViews = userBehaviorMapper.selectCount(
                new LambdaQueryWrapper<UserBehavior>().eq(UserBehavior::getBehaviorType, "view")
        );
        long totalChats = chatSessionService.count();
        long totalFavorites = userBehaviorMapper.selectCount(
                new LambdaQueryWrapper<UserBehavior>().eq(UserBehavior::getBehaviorType, "favorite")
        );
        long totalSearches = userBehaviorMapper.selectCount(
                new LambdaQueryWrapper<UserBehavior>().eq(UserBehavior::getBehaviorType, "search")
        );
        return Map.of(
                "totalViews", totalViews,
                "totalChats", totalChats,
                "totalSearches", totalSearches,
                "totalFavorites", totalFavorites
        );
    }

    @Override
    public Map<String, Object> getDashboardInsights() {
        Map<Long, User> userMap = userService.list().stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        Map<Long, Item> itemMap = itemService.list().stream()
                .collect(Collectors.toMap(Item::getId, Function.identity(), (left, right) -> left));
        Map<Long, Category> categoryMap = categoryService.getAllCategories().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity(), (left, right) -> left));

        List<UserBehavior> behaviors = userBehaviorMapper.selectList(new LambdaQueryWrapper<UserBehavior>()
                .orderByDesc(UserBehavior::getCreateTime));
        List<ChatSession> sessions = chatSessionService.list(new LambdaQueryWrapper<ChatSession>()
                .orderByDesc(ChatSession::getCreateTime));
        List<ChatMessage> userMessages = chatMessageService.list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRole, "user")
                .orderByDesc(ChatMessage::getCreateTime));

        Map<String, Long> hotQuestionCounter = userMessages.stream()
                .map(ChatMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .map(content -> normalizePhrase(content, 36))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Map<String, Long> keywordCounter = behaviors.stream()
                .filter(behavior -> "search".equalsIgnoreCase(behavior.getBehaviorType()))
                .map(UserBehavior::getDetails)
                .filter(detail -> detail != null && !detail.isBlank())
                .flatMap(detail -> extractKeywords(detail).stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Map<Long, Long> categoryCounter = behaviors.stream()
                .filter(behavior -> "view".equalsIgnoreCase(behavior.getBehaviorType())
                        || "favorite".equalsIgnoreCase(behavior.getBehaviorType()))
                .map(behavior -> itemMap.get(behavior.getTargetId()))
                .filter(item -> item != null && item.getCategoryId() != null)
                .collect(Collectors.groupingBy(Item::getCategoryId, Collectors.counting()));

        Map<Long, Long> sessionUserMap = sessions.stream()
                .collect(Collectors.toMap(ChatSession::getId, ChatSession::getUserId, (left, right) -> left));

        long totalUsers = userMap.size();
        long sessionUsers = sessions.stream().map(ChatSession::getUserId).distinct().count();
        long messageUsers = userMessages.stream()
                .map(message -> sessionUserMap.get(message.getSessionId()))
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long searchUsers = distinctUsersByAction(behaviors, "search");
        long viewUsers = distinctUsersByAction(behaviors, "view");
        long favoriteUsers = distinctUsersByAction(behaviors, "favorite");

        return Map.of(
                "hotQuestions", hotQuestionCounter.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(6)
                        .map(entry -> Map.of("question", entry.getKey(), "count", entry.getValue()))
                        .toList(),
                "searchKeywords", keywordCounter.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(8)
                        .map(entry -> Map.of("keyword", entry.getKey(), "count", entry.getValue()))
                        .toList(),
                "categoryPreference", categoryCounter.entrySet().stream()
                        .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                        .limit(8)
                        .map(entry -> {
                            Category category = categoryMap.get(entry.getKey());
                            Map<String, Object> row = new java.util.LinkedHashMap<>();
                            row.put("categoryId", entry.getKey());
                            row.put("categoryName", category != null ? category.getName() : "未分类");
                            row.put("themeColor", category != null ? category.getThemeColor() : "");
                            row.put("count", entry.getValue());
                            return row;
                        })
                        .toList(),
                "journeyFunnel", List.of(
                        Map.of("stage", "登录用户", "count", totalUsers),
                        Map.of("stage", "发起会话", "count", sessionUsers),
                        Map.of("stage", "发送问题", "count", messageUsers),
                        Map.of("stage", "检索案例", "count", searchUsers),
                        Map.of("stage", "查看案例", "count", viewUsers),
                        Map.of("stage", "收藏案例", "count", favoriteUsers)
                )
        );
    }

    @Override
    public Map<String, Object> getActionPage(int page, int size, String username, String actionType, String startDate, String endDate) {
        List<UserActionVO> filtered = listAllActions().stream()
                .filter(action -> username == null || username.isBlank() || containsIgnoreCase(action.getUsername(), username))
                .filter(action -> actionType == null || actionType.isBlank() || actionType.equals(action.getActionType()))
                .filter(action -> inDateRange(action.getCreateTime(), startDate, endDate))
                .sorted(Comparator.comparing(UserActionVO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int fromIndex = Math.max((page - 1) * size, 0);
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<UserActionVO> records = fromIndex >= filtered.size() ? List.of() : filtered.subList(fromIndex, toIndex);

        return Map.of(
                "records", records,
                "total", filtered.size(),
                "current", page,
                "size", size
        );
    }

    @Override
    public List<UserActionVO> listAllActions() {
        Map<Long, User> userMap = userService.list().stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        Map<Long, Item> itemMap = itemService.list().stream()
                .collect(Collectors.toMap(Item::getId, Function.identity(), (left, right) -> left));

        List<UserActionVO> actions = new ArrayList<>();

        List<ChatSession> sessions = chatSessionService.list(new LambdaQueryWrapper<ChatSession>().orderByDesc(ChatSession::getCreateTime));
        for (ChatSession session : sessions) {
            UserActionVO vo = new UserActionVO();
            User user = userMap.get(session.getUserId());
            vo.setUsername(user != null ? user.getUsername() : "未知用户");
            vo.setActionType("chat");
            vo.setActionTarget(session.getTitle());
            vo.setActionDetail(session.getLastMessage());
            vo.setIpAddress("--");
            vo.setUserAgent("系统会话记录");
            vo.setCreateTime(session.getCreateTime());
            actions.add(vo);
        }

        List<UserBehavior> favorites = userBehaviorMapper.selectList(
                new LambdaQueryWrapper<UserBehavior>()
                        .in(UserBehavior::getBehaviorType, "favorite", "unfavorite")
                        .orderByDesc(UserBehavior::getCreateTime)
        );
        for (UserBehavior favorite : favorites) {
            UserActionVO vo = new UserActionVO();
            User user = userMap.get(favorite.getUserId());
            Item item = itemMap.get(favorite.getTargetId());
            vo.setUsername(user != null ? user.getUsername() : "未知用户");
            vo.setActionType(favorite.getBehaviorType());
            vo.setActionTarget(item != null ? item.getTitle() : "已删除案例");
            vo.setActionDetail(favorite.getDetails() == null || favorite.getDetails().isBlank()
                    ? ("unfavorite".equals(favorite.getBehaviorType()) ? "取消收藏法律案例" : "收藏法律案例")
                    : favorite.getDetails());
            vo.setIpAddress(favorite.getIpAddress() == null || favorite.getIpAddress().isBlank() ? "--" : favorite.getIpAddress());
            vo.setUserAgent(favorite.getUserAgent() == null || favorite.getUserAgent().isBlank()
                    ? ("unfavorite".equals(favorite.getBehaviorType()) ? "系统取消收藏记录" : "系统收藏记录")
                    : favorite.getUserAgent());
            vo.setCreateTime(favorite.getCreateTime());
            actions.add(vo);
        }

        List<UserBehavior> searches = userBehaviorMapper.selectList(
                new LambdaQueryWrapper<UserBehavior>()
                        .eq(UserBehavior::getBehaviorType, "search")
                        .orderByDesc(UserBehavior::getCreateTime)
        );
        for (UserBehavior search : searches) {
            UserActionVO vo = new UserActionVO();
            User user = userMap.get(search.getUserId());
            vo.setUsername(user != null ? user.getUsername() : "未知用户");
            vo.setActionType("search");
            vo.setActionTarget(search.getTargetType() == null || search.getTargetType().isBlank() ? "案例检索" : search.getTargetType());
            vo.setActionDetail(search.getDetails());
            vo.setIpAddress(search.getIpAddress() == null || search.getIpAddress().isBlank() ? "--" : search.getIpAddress());
            vo.setUserAgent(search.getUserAgent() == null || search.getUserAgent().isBlank() ? "搜索行为记录" : search.getUserAgent());
            vo.setCreateTime(search.getCreateTime());
            actions.add(vo);
        }

        List<UserBehavior> views = userBehaviorMapper.selectList(
                new LambdaQueryWrapper<UserBehavior>()
                        .eq(UserBehavior::getBehaviorType, "view")
                        .orderByDesc(UserBehavior::getCreateTime)
        );
        for (UserBehavior view : views) {
            UserActionVO vo = new UserActionVO();
            User user = userMap.get(view.getUserId());
            Item item = itemMap.get(view.getTargetId());
            vo.setUsername(user != null ? user.getUsername() : "未知用户");
            vo.setActionType("view");
            vo.setActionTarget(item != null ? item.getTitle() : "已删除案例");
            vo.setActionDetail(view.getDetails() == null || view.getDetails().isBlank() ? "浏览案例详情" : view.getDetails());
            vo.setIpAddress(view.getIpAddress() == null || view.getIpAddress().isBlank() ? "--" : view.getIpAddress());
            vo.setUserAgent(view.getUserAgent() == null || view.getUserAgent().isBlank() ? "案例详情页浏览" : view.getUserAgent());
            vo.setCreateTime(view.getCreateTime());
            actions.add(vo);
        }

        List<ChatMessage> userMessages = chatMessageService.list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRole, "user")
                .orderByDesc(ChatMessage::getCreateTime));
        Map<Long, ChatSession> sessionMap = sessions.stream()
                .collect(Collectors.toMap(ChatSession::getId, Function.identity(), (left, right) -> left));
        for (ChatMessage message : userMessages) {
            ChatSession session = sessionMap.get(message.getSessionId());
            if (session == null) {
                continue;
            }
            UserActionVO vo = new UserActionVO();
            User user = userMap.get(session.getUserId());
            vo.setUsername(user != null ? user.getUsername() : "未知用户");
            vo.setActionType("message");
            vo.setActionTarget(session.getTitle());
            vo.setActionDetail(message.getContent());
            vo.setIpAddress("--");
            vo.setUserAgent("用户咨询消息");
            vo.setCreateTime(message.getCreateTime());
            actions.add(vo);
        }

        return actions;
    }

    private long distinctUsersByAction(List<UserBehavior> behaviors, String actionType) {
        return behaviors.stream()
                .filter(behavior -> actionType.equalsIgnoreCase(behavior.getBehaviorType()))
                .map(UserBehavior::getUserId)
                .distinct()
                .count();
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String normalizePhrase(String value, int limit) {
        String compact = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (compact.length() > limit) {
            return compact.substring(0, limit) + "...";
        }
        return compact;
    }

    private List<String> extractKeywords(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{IsHan}a-z0-9]+", " ")
                        .trim()
                        .split("\\s+"))
                .map(String::trim)
                .filter(keyword -> keyword.length() >= 2)
                .limit(12)
                .toList();
    }

    private boolean inDateRange(LocalDateTime time, String startDate, String endDate) {
        if (time == null) {
            return false;
        }
        LocalDate current = time.toLocalDate();
        if (startDate != null && !startDate.isBlank() && current.isBefore(LocalDate.parse(startDate))) {
            return false;
        }
        if (endDate != null && !endDate.isBlank() && current.isAfter(LocalDate.parse(endDate))) {
            return false;
        }
        return true;
    }
}
