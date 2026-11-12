package com.gjq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gjq.entity.Category;
import com.gjq.entity.ChatMessage;
import com.gjq.entity.ChatSession;
import com.gjq.entity.Item;
import com.gjq.entity.UserBehavior;
import com.gjq.mapper.UserBehaviorMapper;
import com.gjq.service.CategoryService;
import com.gjq.service.ChatMessageService;
import com.gjq.service.ChatSessionService;
import com.gjq.service.ItemAssetService;
import com.gjq.service.ItemService;
import com.gjq.service.RecommendationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private final ItemService itemService;
    private final ItemAssetService itemAssetService;
    private final UserBehaviorMapper userBehaviorMapper;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final CategoryService categoryService;

    public RecommendationServiceImpl(ItemService itemService,
                                     ItemAssetService itemAssetService,
                                     UserBehaviorMapper userBehaviorMapper,
                                     ChatSessionService chatSessionService,
                                     ChatMessageService chatMessageService,
                                     CategoryService categoryService) {
        this.itemService = itemService;
        this.itemAssetService = itemAssetService;
        this.userBehaviorMapper = userBehaviorMapper;
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
        this.categoryService = categoryService;
    }

    @Override
    public Map<String, Object> recommendCases(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 12));
        Map<Long, Double> categoryScores = new LinkedHashMap<>();
        Map<String, Double> keywordScores = new LinkedHashMap<>();
        Set<Long> interactedItemIds = new LinkedHashSet<>();

        List<Item> activeItems = itemService.list(new LambdaQueryWrapper<Item>()
                .eq(Item::getStatus, 1)
                .orderByDesc(Item::getUpdateTime));
        Map<Long, Item> itemMap = activeItems.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(Item::getId, Function.identity(), (left, right) -> left));
        Map<Long, Category> categoryMap = categoryService.getAllCategories().stream()
                .filter(category -> category.getId() != null)
                .collect(Collectors.toMap(Category::getId, Function.identity(), (left, right) -> left));

        List<UserBehavior> behaviors = userBehaviorMapper.selectList(new LambdaQueryWrapper<UserBehavior>()
                .eq(UserBehavior::getUserId, userId)
                .orderByDesc(UserBehavior::getCreateTime));

        for (UserBehavior behavior : behaviors) {
            double weight = switch (readString(behavior.getBehaviorType())) {
                case "favorite" -> 4.0;
                case "view" -> 2.0;
                case "search" -> 1.5;
                case "unfavorite" -> -1.5;
                default -> 1.0;
            };

            if (behavior.getTargetId() != null) {
                interactedItemIds.add(behavior.getTargetId());
                Item relatedItem = itemMap.get(behavior.getTargetId());
                if (relatedItem != null) {
                    addCategoryScore(categoryScores, relatedItem.getCategoryId(), weight);
                    for (String keyword : extractKeywords(relatedItem.getTags() + " " + relatedItem.getTitle())) {
                        keywordScores.merge(keyword, weight, Double::sum);
                    }
                }
            }

            if (StringUtils.hasText(behavior.getDetails())) {
                for (String keyword : extractKeywords(behavior.getDetails())) {
                    keywordScores.merge(keyword, Math.max(1.0, weight), Double::sum);
                }
            }
        }

        List<ChatSession> sessions = chatSessionService.getUserSessions(userId);
        for (ChatSession session : sessions.stream().limit(5).toList()) {
            List<ChatMessage> messages = chatMessageService.getSessionMessages(session.getId());
            for (ChatMessage message : messages) {
                if (!"user".equalsIgnoreCase(message.getRole())) {
                    continue;
                }
                for (String keyword : extractKeywords(message.getContent())) {
                    keywordScores.merge(keyword, 1.2, Double::sum);
                }
            }
        }

        List<Item> ranked = activeItems.stream()
                .map(item -> scoreItem(item, categoryScores, keywordScores, interactedItemIds))
                .sorted(Comparator.comparing(Item::getRecommendScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<Item> recommended = ranked.stream()
                .filter(item -> item.getRecommendScore() != null && item.getRecommendScore() > 0)
                .filter(item -> !interactedItemIds.contains(item.getId()) || ranked.size() <= safeLimit)
                .limit(safeLimit)
                .map(item -> {
                    item.setAssets(itemAssetService.listByItemId(item.getId()).stream().limit(3).toList());
                    return item;
                })
                .toList();

        if (recommended.isEmpty()) {
            recommended = activeItems.stream()
                    .sorted(Comparator.comparing(Item::getFavoriteCount, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(Item::getViewCount, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(safeLimit)
                    .map(item -> {
                        item.setRecommendReason("基于当前热门程度推荐");
                        item.setRecommendScore(1.0);
                        item.setAssets(itemAssetService.listByItemId(item.getId()).stream().limit(3).toList());
                        return item;
                    })
                    .toList();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("interestCategories", categoryScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Category category = categoryMap.get(entry.getKey());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("categoryId", entry.getKey());
                    row.put("name", category != null ? category.getName() : "未分类");
                    row.put("score", round(entry.getValue()));
                    row.put("iconUrl", category != null ? category.getIconUrl() : null);
                    row.put("themeColor", category != null ? category.getThemeColor() : null);
                    return row;
                })
                .toList());
        response.put("interestKeywords", keywordScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(8)
                .map(entry -> Map.of("keyword", entry.getKey(), "score", round(entry.getValue())))
                .toList());
        response.put("items", recommended);
        return response;
    }

    private Item scoreItem(Item item,
                           Map<Long, Double> categoryScores,
                           Map<String, Double> keywordScores,
                           Set<Long> interactedItemIds) {
        double score = 0.2;
        if (item.getCategoryId() != null) {
            score += categoryScores.getOrDefault(item.getCategoryId(), 0.0) * 1.8;
        }

        String corpus = String.join(" ",
                readString(item.getTitle()),
                readString(item.getSummary()),
                readString(item.getTags()),
                crop(readString(item.getContent()), 400)
        ).toLowerCase(Locale.ROOT);

        List<String> matchedKeywords = new ArrayList<>();
        for (Map.Entry<String, Double> entry : keywordScores.entrySet()) {
            if (corpus.contains(entry.getKey())) {
                score += Math.min(entry.getValue(), 3.0);
                if (matchedKeywords.size() < 3) {
                    matchedKeywords.add(entry.getKey());
                }
            }
        }

        score += Math.min(readNumber(item.getFavoriteCount()) * 0.08, 2.0);
        score += Math.min(readNumber(item.getViewCount()) * 0.03, 1.5);

        if (interactedItemIds.contains(item.getId())) {
            score -= 0.8;
        }

        item.setRecommendScore(round(score));
        item.setRecommendReason(buildReason(item.getCategoryId(), categoryScores, matchedKeywords, interactedItemIds.contains(item.getId())));
        return item;
    }

    private String buildReason(Long categoryId,
                               Map<Long, Double> categoryScores,
                               List<String> matchedKeywords,
                               boolean alreadyInteracted) {
        List<String> reasons = new ArrayList<>();
        if (categoryId != null && categoryScores.containsKey(categoryId)) {
            reasons.add("贴合你近期关注的专题方向");
        }
        if (!matchedKeywords.isEmpty()) {
            reasons.add("匹配关键词：" + String.join("、", matchedKeywords));
        }
        if (alreadyInteracted) {
            reasons.add("结合你看过的相关案例延伸推荐");
        }
        if (reasons.isEmpty()) {
            reasons.add("基于案例热度和专题相似度推荐");
        }
        return String.join("；", reasons);
    }

    private void addCategoryScore(Map<Long, Double> categoryScores, Long categoryId, double weight) {
        if (categoryId == null) {
            return;
        }
        categoryScores.merge(categoryId, weight, Double::sum);
    }

    private List<String> extractKeywords(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return List.of();
        }
        return rawText.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}a-z0-9]+", " ")
                .trim()
                .lines()
                .flatMap(line -> List.of(line.split("\\s+")).stream())
                .map(String::trim)
                .filter(keyword -> keyword.length() >= 2)
                .limit(24)
                .toList();
    }

    private String crop(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > limit ? value.substring(0, limit) : value;
    }

    private int readNumber(Integer value) {
        return value == null ? 0 : value;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String readString(String value) {
        return value == null ? "" : value;
    }
}
