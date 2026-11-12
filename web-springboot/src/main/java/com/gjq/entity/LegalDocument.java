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
@TableName("legal_document")
public class LegalDocument implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceType;
    private String sourceName;
    private String sourceUrl;
    private Integer sourceReliability;
    private String sourceFileName;
    private String sourceFilePath;
    private String fileExt;
    private Long fileSize;
    private String fileSha256;

    private String title;
    private String titleNormalized;
    private String caseNumber;
    private String courtName;
    private String courtLevel;
    private String province;
    private String city;

    private String documentType;
    private String documentTypeRaw;
    private String documentSubtype;
    private String documentSubtypeRaw;

    private String caseType;
    private Long categoryId;
    private String causeRaw;
    private String causeNormalized;
    private String businessDomain;
    private String procedureDomain;
    private String trialStageNormalized;
    private String procedureStageRaw;
    private String specialProcedureType;
    private String applicationType;

    private String decisionResult;
    private String decisionResultNormalized;
    private LocalDate decisionDate;
    private LocalDate effectiveDate;
    private LocalDate filingDate;
    private LocalDate hearingDate;

    private String partiesText;
    private String claimsSummary;
    private String defenseSummary;
    private String disputeSummary;
    private String trialProcessSummary;
    private String factsSummary;
    private String issuesSummary;
    private String issueTags;
    private String reasoningSummary;
    private String resultSummary;
    private String resultTags;
    private String legalBasisText;
    private String relatedCaseNumbersText;
    private String relatedDecisionNumbersText;
    private String keywords;
    private String aliases;

    private String summary;
    private String retrievalText;
    private String caseDetailText;
    private String displayText;
    private String rawText;
    private String cleanText;

    private String parserVersion;
    private Integer parseStatus;
    private Integer classifyStatus;
    private BigDecimal classifyConfidence;
    private BigDecimal extractConfidence;
    private BigDecimal docQualityScore;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
