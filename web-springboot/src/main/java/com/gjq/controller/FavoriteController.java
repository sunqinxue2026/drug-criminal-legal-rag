package com.gjq.controller;

import com.gjq.common.Result;
import com.gjq.service.FavoriteService;
import com.gjq.service.support.RequestAuthorizationService;
import com.gjq.vo.FavoriteItemVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/favorite")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final RequestAuthorizationService requestAuthorizationService;

    public FavoriteController(FavoriteService favoriteService,
                              RequestAuthorizationService requestAuthorizationService) {
        this.favoriteService = favoriteService;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @GetMapping("/list")
    public Result<List<FavoriteItemVO>> listFavorites(@RequestParam Long userId, HttpServletRequest request) {
        requestAuthorizationService.requireUserAccess(request, userId);
        return Result.success(favoriteService.listFavorites(userId));
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> favoriteStatus(@RequestParam Long userId,
                                                      @RequestParam Long itemId,
                                                      HttpServletRequest request) {
        requestAuthorizationService.requireUserAccess(request, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("isFavorite", favoriteService.isFavorite(userId, itemId));
        return Result.success(data);
    }

    @PostMapping
    public Result<String> addFavorite(@RequestParam Long userId,
                                      @RequestParam Long itemId,
                                      HttpServletRequest request) {
        requestAuthorizationService.requireUserAccess(request, userId);
        favoriteService.addFavorite(userId, itemId);
        return Result.success("收藏成功");
    }

    @DeleteMapping("/{itemId}")
    public Result<String> removeFavorite(@PathVariable Long itemId,
                                         @RequestParam Long userId,
                                         HttpServletRequest request) {
        requestAuthorizationService.requireUserAccess(request, userId);
        favoriteService.removeFavorite(userId, itemId);
        return Result.success("取消收藏成功");
    }
}
