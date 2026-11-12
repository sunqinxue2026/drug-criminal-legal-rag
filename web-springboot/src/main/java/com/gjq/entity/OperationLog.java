package com.gjq.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志实体
 */
@Data
@TableName("operation_log")
public class OperationLog implements Serializable {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 用户ID */
    private Long userId;
    
    /** 用户名 */
    private String username;
    
    /** 操作模块 */
    private String module;
    
    /** 操作类型 */
    private String operationType;
    
    /** 操作描述 */
    private String description;
    
    /** 请求方法 */
    private String method;
    
    /** 请求参数 */
    private String params;
    
    /** 返回结果 */
    private String result;
    
    /** IP地址 */
    private String ipAddress;
    
    /** 执行时长（毫秒） */
    private Long duration;
    
    /** 状态：0-失败 1-成功 */
    private Integer status;
    
    /** 错误信息 */
    private String errorMsg;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
