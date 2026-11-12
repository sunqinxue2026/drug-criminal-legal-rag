package com.gjq.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("legal_knowledge")
public class LegalKnowledge implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceType;
    private String sourceName;
    private String sourceUrl;
    private Integer sourceReliability;
    private String sourceFileName;
    private String contentSha256;

    private String title;
    private String titleNormalized;
    private String knowledgeType;
    private String knowledgeSubtype;
    private String documentName;
    private String documentLevel;
    private String issuer;

    private Long categoryId;
    private String businessDomain;
    private String topic;
    private String chapterTitle;
    private String sectionTitle;
    private String articleNumber;
    private String articleTitle;

    private String keywords;
    private String aliases;
    private String summary;
    private String displayText;
    private String retrievalText;

    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String currentStatus;

    private String parserVersion;
    private Integer parseStatus;
    private BigDecimal extractConfidence;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
