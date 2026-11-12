package com.gjq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gjq.entity.ItemAsset;
import com.gjq.mapper.ItemAssetMapper;
import com.gjq.service.ItemAssetService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ItemAssetServiceImpl extends ServiceImpl<ItemAssetMapper, ItemAsset> implements ItemAssetService {

    @Override
    public List<ItemAsset> listByItemId(Long itemId) {
        return list(new LambdaQueryWrapper<ItemAsset>()
                .eq(ItemAsset::getItemId, itemId)
                .orderByAsc(ItemAsset::getSort)
                .orderByDesc(ItemAsset::getCreateTime));
    }

    @Override
    public ItemAsset createAsset(Long itemId, ItemAsset asset) {
        asset.setId(null);
        asset.setItemId(itemId);
        asset.setAssetType(StringUtils.hasText(asset.getAssetType()) ? asset.getAssetType().trim() : "document");
        asset.setStatus(asset.getStatus() == null ? 1 : asset.getStatus());
        asset.setSort(asset.getSort() == null ? 0 : asset.getSort());
        save(asset);
        return asset;
    }

    @Override
    public void removeAsset(Long assetId) {
        removeById(assetId);
    }
}
