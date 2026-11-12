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
import java.time.LocalDateTime;

@Data
@TableName("legal_document_chunk")
public class LegalDocumentChunk implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;
    private Long parentChunkId;
    private String chunkStrategy;
    private String chunkVersion;
    private String sectionType;
    private String sectionTitle;
    private String headerPath;
    private String retrievalRole;
    private BigDecimal sectionWeight;
    private Integer sectionOrder;
    private Integer chunkIndex;
    private Integer charStartOffset;
    private Integer charEndOffset;
    private String chunkHash;
    private String chunkText;
    private String retrievalText;
    private Integer charCount;
    private Integer tokenCount;
    private String metadataJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
