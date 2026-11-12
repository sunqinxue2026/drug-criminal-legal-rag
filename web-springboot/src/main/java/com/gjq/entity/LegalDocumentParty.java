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
@TableName("legal_document_party")
public class LegalDocumentParty implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;
    private Integer partySeq;
    private String partyRoleRaw;
    private String partyRoleNormalized;
    private String partyName;
    private String partyNameNormalized;
    private String partyType;
    private String nationalityOrRegion;
    private String addressText;
    private String identityText;
    private String representativeText;
    private Integer isAnonymized;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
