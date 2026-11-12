package com.gjq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gjq.entity.Item;
import com.gjq.entity.ItemAsset;
import com.gjq.entity.UserBehavior;
import com.gjq.mapper.ItemMapper;
import com.gjq.mapper.UserBehaviorMapper;
import com.gjq.service.ItemAssetService;
import com.gjq.service.ItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements ItemService {

    private final UserBehaviorMapper userBehaviorMapper;
    private final ItemAssetService itemAssetService;

    public ItemServiceImpl(UserBehaviorMapper userBehaviorMapper,
                           ItemAssetService itemAssetService) {
        this.userBehaviorMapper = userBehaviorMapper;
        this.itemAssetService = itemAssetService;
    }

    @Override
    public Page<Item> getItemPage(int pageNum, int pageSize, Long categoryId, String keyword, String tags) {
        Page<Item> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq(Item::getCategoryId, categoryId);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String trimmedKeyword = keyword.trim();
            wrapper.and(query -> query
                    .like(Item::getTitle, trimmedKeyword)
                    .or()
                    .like(Item::getSummary, trimmedKeyword)
                    .or()
                    .like(Item::getContent, trimmedKeyword));
        }
        if (tags != null && !tags.trim().isEmpty()) {
            wrapper.like(Item::getTags, tags.trim());
        }
        wrapper.eq(Item::getStatus, 1);
        wrapper.orderByDesc(Item::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Item getItemDetail(Long id, Long userId) {
        Item item = this.getById(id);
        if (item != null) {
            item.setViewCount((item.getViewCount() == null ? 0 : item.getViewCount()) + 1);
            this.updateById(item);
            item.setAssets(itemAssetService.listByItemId(item.getId()));

            if (userId != null) {
                UserBehavior behavior = new UserBehavior();
                behavior.setUserId(userId);
                behavior.setBehaviorType("view");
                behavior.setTargetType("item");
                behavior.setTargetId(item.getId());
                behavior.setDetails(item.getTitle());
                behavior.setUserAgent("案例详情页浏览");
                userBehaviorMapper.insert(behavior);
            }
        }
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Item createItem(Item item) {
        save(item);
        return loadItemWithAssets(item.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Item updateItem(Item item) {
        updateById(item);
        return loadItemWithAssets(item.getId());
    }

    @Override
    public List<ItemAsset> getItemAssets(Long itemId) {
        return itemAssetService.listByItemId(itemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ItemAsset addItemAsset(Long itemId, ItemAsset asset) {
        return itemAssetService.createAsset(itemId, asset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItemAsset(Long assetId) {
        itemAssetService.removeAsset(assetId);
    }

    private Item loadItemWithAssets(Long itemId) {
        Item item = getById(itemId);
        if (item != null) {
            item.setAssets(itemAssetService.listByItemId(itemId));
        }
        return item;
    }
}
