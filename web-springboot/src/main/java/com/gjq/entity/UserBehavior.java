package com.gjq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("user_behavior")
public class UserBehavior implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String behaviorType;

    private String targetType;

    private Long targetId;

    private String details;

    private String ipAddress;

    private String userAgent;

    private String sessionId;

    private LocalDateTime createTime;
}
