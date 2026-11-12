package com.gjq.vo;

import lombok.Data;

@Data
public class StructuredAnswerVO {
    private String conclusion = "";
    private String basis = "";
    private String risk = "";
    private String advice = "";
    private String followUp = "";
}
