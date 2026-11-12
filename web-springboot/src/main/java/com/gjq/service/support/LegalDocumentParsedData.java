package com.gjq.service.support;

import com.gjq.entity.LegalDocument;
import com.gjq.entity.LegalDocumentChunk;
import com.gjq.entity.LegalDocumentExt;
import com.gjq.entity.LegalDocumentParty;
import com.gjq.entity.LegalDocumentReference;

import java.util.ArrayList;
import java.util.List;

public class LegalDocumentParsedData {

    public LegalDocument document;
    public List<LegalDocumentParty> parties = new ArrayList<>();
    public List<LegalDocumentReference> references = new ArrayList<>();
    public List<LegalDocumentChunk> chunks = new ArrayList<>();
    public LegalDocumentExt ext;
    public List<String> keywords = new ArrayList<>();
    public List<String> aliases = new ArrayList<>();
    public List<String> issueTags = new ArrayList<>();
    public List<String> resultTags = new ArrayList<>();
}
