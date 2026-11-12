package com.gjq.service.support;

public class LegalDocumentTextSlice {

    public final int start;
    public final int end;
    public final String text;

    public LegalDocumentTextSlice(int start, int end, String text) {
        this.start = start;
        this.end = end;
        this.text = text;
    }
}
