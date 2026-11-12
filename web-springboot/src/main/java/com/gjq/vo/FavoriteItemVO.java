package com.gjq.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FavoriteItemVO {
    private Long id;
    private Long itemId;
    private String title;
    private String categoryName;
    private String summary;
    private LocalDateTime createTime;
}
