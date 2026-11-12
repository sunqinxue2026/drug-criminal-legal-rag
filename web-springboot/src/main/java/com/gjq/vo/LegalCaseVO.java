package com.gjq.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 法律案例VO
 */
@Data
public class LegalCaseVO {
    
    private Long id;
    
    private String title;
    
    private String caseNumber;
    
    private String caseType;
    
    private String cause;
    
    private String summary;
    
    private String verdict;
    
    private String tags;
    
    private String court;
    
    private LocalDateTime trialDate;
    
    private Integer viewCount;
    
    private Integer referenceCount;
    
    private Integer status;
    
    private LocalDateTime createTime;
}
