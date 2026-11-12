package com.gjq.vo;

import com.gjq.entity.LegalKnowledge;
import com.gjq.entity.LegalKnowledgeChunk;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LegalKnowledgeDetailVO {
    private LegalKnowledge knowledge;
    private List<LegalKnowledgeChunk> chunks = new ArrayList<>();
}
