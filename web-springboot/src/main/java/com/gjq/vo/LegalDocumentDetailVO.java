package com.gjq.vo;

import com.gjq.entity.LegalDocument;
import com.gjq.entity.LegalDocumentChunk;
import com.gjq.entity.LegalDocumentParty;
import com.gjq.entity.LegalDocumentReference;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LegalDocumentDetailVO {
    private LegalDocument document;
    private List<LegalDocumentParty> parties = new ArrayList<>();
    private List<LegalDocumentReference> references = new ArrayList<>();
    private List<LegalDocumentChunk> chunks = new ArrayList<>();
}
