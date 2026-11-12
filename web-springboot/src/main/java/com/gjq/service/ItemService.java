package com.gjq.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gjq.entity.Item;
import com.gjq.entity.ItemAsset;

import java.util.List;

public interface ItemService extends IService<Item> {
    Page<Item> getItemPage(int pageNum, int pageSize, Long categoryId, String keyword, String tags);
    Item getItemDetail(Long id, Long userId);
    Item createItem(Item item);
    Item updateItem(Item item);
    List<ItemAsset> getItemAssets(Long itemId);
    ItemAsset addItemAsset(Long itemId, ItemAsset asset);
    void deleteItemAsset(Long assetId);
}
