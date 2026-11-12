package com.gjq.service.support;

public class LegalDocumentSection {

    public final String type;
    public final String title;
    public final String headerPath;
    public final String content;

    public LegalDocumentSection(String type, String title, String headerPath, String content) {
        this.type = type;
        this.title = title;
        this.headerPath = headerPath;
        this.content = content;
    }
}
