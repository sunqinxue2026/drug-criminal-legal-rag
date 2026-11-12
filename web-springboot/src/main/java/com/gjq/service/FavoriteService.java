package com.gjq.service;

import com.gjq.vo.FavoriteItemVO;

import java.util.List;

public interface FavoriteService {
    void addFavorite(Long userId, Long itemId);

    void removeFavorite(Long userId, Long itemId);

    List<FavoriteItemVO> listFavorites(Long userId);

    boolean isFavorite(Long userId, Long itemId);
}
