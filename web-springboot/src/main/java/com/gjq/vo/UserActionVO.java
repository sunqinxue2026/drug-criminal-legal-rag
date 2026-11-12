package com.gjq.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserActionVO {
    private String username;
    private String actionType;
    private String actionTarget;
    private String actionDetail;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createTime;
}
