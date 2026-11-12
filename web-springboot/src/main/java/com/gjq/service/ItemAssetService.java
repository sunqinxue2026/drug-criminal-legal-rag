package com.gjq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gjq.entity.ItemAsset;

import java.util.List;

public interface ItemAssetService extends IService<ItemAsset> {
    List<ItemAsset> listByItemId(Long itemId);
    ItemAsset createAsset(Long itemId, ItemAsset asset);
    void removeAsset(Long assetId);
}
