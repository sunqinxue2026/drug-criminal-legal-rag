package com.gjq.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("legal_document_chunk_index")
public class LegalDocumentChunkIndex implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long chunkId;
    private String retrievalProfile;
    private String backendType;
    private String indexName;
    private String indexPartition;
    private String embeddingProvider;
    private String embeddingModel;
    private Integer embeddingDim;
    private String vectorId;
    private String rerankerModel;
    private String buildVersion;
    private Integer isActive;
    private String metadataJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
