package com.gjq.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName("item")
public class Item implements Serializable {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String title;
    
    private String content;
    
    private String summary;
    
    private Long categoryId;
    
    private String tags;
    
    private Integer viewCount;
    
    private Integer likeCount;
    
    private Integer favoriteCount;
    
    private Integer status;

    @TableField(exist = false)
    private List<ItemAsset> assets = new ArrayList<>();

    @TableField(exist = false)
    private String recommendReason;

    @TableField(exist = false)
    private Double recommendScore;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    @TableLogic
    private Integer deleted;
}
