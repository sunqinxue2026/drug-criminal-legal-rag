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
@TableName("legal_document_reference")
public class LegalDocumentReference implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;
    private Integer refOrder;
    private String sectionType;
    private String refType;
    private String refName;
    private String refValue;
    private String normalizedValue;
    private Integer isPrimary;
    private String rawText;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
