package com.gjq.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage implements Serializable {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long sessionId;
    
    private String role;
    
    private String content;

    private String imageUrl;

    private String imageName;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableLogic
    private Integer deleted;
}
