package com.gjq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gjq.entity.Category;
import com.gjq.entity.Item;
import com.gjq.entity.UserBehavior;
import com.gjq.mapper.UserBehaviorMapper;
import com.gjq.service.CategoryService;
import com.gjq.service.FavoriteService;
import com.gjq.service.ItemService;
import com.gjq.vo.FavoriteItemVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    private static final String FAVORITE = "favorite";
    private static final String UNFAVORITE = "unfavorite";
    private static final String TARGET_TYPE = "item";

    private final UserBehaviorMapper userBehaviorMapper;
    private final ItemService itemService;
    private final CategoryService categoryService;

    public FavoriteServiceImpl(
            UserBehaviorMapper userBehaviorMapper,
            ItemService itemService,
            CategoryService categoryService
    ) {
        this.userBehaviorMapper = userBehaviorMapper;
        this.itemService = itemService;
        this.categoryService = categoryService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addFavorite(Long userId, Long itemId) {
        if (isFavorite(userId, itemId)) {
            return;
        }

        Item item = itemService.getById(itemId);
        if (item == null) {
            throw new IllegalArgumentException("案例不存在");
        }

        insertBehavior(userId, itemId, FAVORITE, item.getTitle());
        item.setFavoriteCount((item.getFavoriteCount() == null ? 0 : item.getFavoriteCount()) + 1);
        itemService.updateById(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(Long userId, Long itemId) {
        if (!isFavorite(userId, itemId)) {
            return;
        }

        Item item = itemService.getById(itemId);
        if (item == null) {
            return;
        }

        insertBehavior(userId, itemId, UNFAVORITE, item.getTitle());

        int current = item.getFavoriteCount() == null ? 0 : item.getFavoriteCount();
        item.setFavoriteCount(Math.max(0, current - 1));
        itemService.updateById(item);
    }

    @Override
    public List<FavoriteItemVO> listFavorites(Long userId) {
        List<UserBehavior> latestBehaviors = getLatestItemBehaviors(userId);
        if (latestBehaviors.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> itemIds = latestBehaviors.stream()
                .filter(behavior -> FAVORITE.equals(behavior.getBehaviorType()))
                .map(UserBehavior::getTargetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (itemIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Item> itemMap = itemService.listByIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, item -> item));

        Map<Long, String> categoryNameMap = new HashMap<>();
        for (Category category : categoryService.list()) {
            categoryNameMap.put(category.getId(), category.getName());
        }

        return latestBehaviors.stream()
                .filter(behavior -> FAVORITE.equals(behavior.getBehaviorType()))
                .map(behavior -> {
                    Item item = itemMap.get(behavior.getTargetId());
                    if (item == null) {
                        return null;
                    }

                    FavoriteItemVO vo = new FavoriteItemVO();
                    vo.setId(item.getId());
                    vo.setItemId(item.getId());
                    vo.setTitle(item.getTitle());
                    vo.setCategoryName(categoryNameMap.getOrDefault(item.getCategoryId(), "未分类"));
                    vo.setSummary(item.getSummary());
                    vo.setCreateTime(behavior.getCreateTime());
                    return vo;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isFavorite(Long userId, Long itemId) {
        UserBehavior latest = getLatestItemBehavior(userId, itemId);
        return latest != null && FAVORITE.equals(latest.getBehaviorType());
    }

    private void insertBehavior(Long userId, Long itemId, String behaviorType, String details) {
        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setBehaviorType(behaviorType);
        behavior.setTargetType(TARGET_TYPE);
        behavior.setTargetId(itemId);
        behavior.setDetails(details);
        userBehaviorMapper.insert(behavior);
    }

    private UserBehavior getLatestItemBehavior(Long userId, Long itemId) {
        List<UserBehavior> behaviors = userBehaviorMapper.selectList(
                new LambdaQueryWrapper<UserBehavior>()
                        .eq(UserBehavior::getUserId, userId)
                        .eq(UserBehavior::getTargetType, TARGET_TYPE)
                        .eq(UserBehavior::getTargetId, itemId)
                        .in(UserBehavior::getBehaviorType, FAVORITE, UNFAVORITE)
                        .orderByDesc(UserBehavior::getCreateTime)
                        .orderByDesc(UserBehavior::getId)
                        .last("limit 1")
        );
        return behaviors.isEmpty() ? null : behaviors.get(0);
    }

    private List<UserBehavior> getLatestItemBehaviors(Long userId) {
        List<UserBehavior> behaviors = userBehaviorMapper.selectList(
                new LambdaQueryWrapper<UserBehavior>()
                        .eq(UserBehavior::getUserId, userId)
                        .eq(UserBehavior::getTargetType, TARGET_TYPE)
                        .in(UserBehavior::getBehaviorType, FAVORITE, UNFAVORITE)
                        .orderByDesc(UserBehavior::getCreateTime)
                        .orderByDesc(UserBehavior::getId)
        );

        Map<Long, UserBehavior> latestByItem = new HashMap<>();
        for (UserBehavior behavior : behaviors) {
            if (behavior.getTargetId() == null || latestByItem.containsKey(behavior.getTargetId())) {
                continue;
            }
            latestByItem.put(behavior.getTargetId(), behavior);
        }

        return latestByItem.values().stream()
                .sorted(Comparator.comparing(UserBehavior::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }
}
