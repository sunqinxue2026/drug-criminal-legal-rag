package com.gjq.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 案例详情VO
 */
@Data
public class ItemVO {
    
    private Long id;
    
    private String title;
    
    private String content;
    
    private String summary;
    
    private Long categoryId;
    
    private String categoryName;
    
    private String tags;
    
    private Integer viewCount;
    
    private Integer likeCount;
    
    private Integer favoriteCount;
    
    private Integer status;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    private Boolean isFavorite; // 当前用户是否收藏
    
    private Boolean isLiked; // 当前用户是否点赞
}
