package com.gjq.service.support;

import com.gjq.entity.LegalDocument;
import com.gjq.entity.LegalDocumentChunk;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegalDocumentChunkServiceTest {

    private final LegalDocumentTextProcessor textProcessor = new LegalDocumentTextProcessor();
    private final LegalDocumentChunkService chunkService = new LegalDocumentChunkService(textProcessor);

    @Test
    void splitSectionsSeparatesKnownHeadings() {
        List<LegalDocumentSection> sections = chunkService.splitSections(
                "审理经过\n第一段\n本院认为\n第二段\n判决如下\n第三段"
        );

        assertThat(sections).hasSize(3);
        assertThat(sections.get(0).type).isEqualTo("process");
        assertThat(sections.get(1).type).isEqualTo("reasoning");
        assertThat(sections.get(2).type).isEqualTo("result");
    }

    @Test
    void buildChunksAddsAtomicSummaryChunks() {
        LegalDocument document = new LegalDocument();
        document.setTitle("示例判决书");
        document.setCaseNumber("(2026)京01刑终1号");
        document.setCourtName("北京市第一中级人民法院");
        document.setDocumentSubtype("刑事判决书");
        document.setResultSummary("驳回上诉，维持原判。");
        document.setDecisionResult("驳回上诉，维持原判。");
        document.setLegalBasisText("《刑法》第三百四十七条");
        document.setCaseDetailText("这是案件概览。");
        document.setSummary("这是摘要。");
        document.setCleanText("完整正文");

        List<LegalDocumentChunk> chunks = chunkService.buildChunks(
                document,
                List.of(new LegalDocumentSection("reasoning", "本院认为", "本院认为", "这里是法院的理由分析。")),
                new StubChunkSupport()
        );

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getSectionType()).isEqualTo("overview");
        assertThat(chunks).anyMatch(chunk -> "result".equals(chunk.getSectionType()));
        assertThat(chunks).anyMatch(chunk -> "law".equals(chunk.getSectionType()));
    }

    private static class StubChunkSupport implements LegalDocumentChunkService.ChunkSupport {

        @Override
        public String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
            return null;
        }

        @Override
        public String buildChunkRetrievalText(LegalDocument document, LegalDocumentSection section, String chunkText) {
            return chunkText;
        }

        @Override
        public int estimateTokenCount(String text) {
            return Math.max(1, text.length());
        }

        @Override
        public String sha256Hex(byte[] bytes) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (Exception ex) {
                throw new IllegalStateException("Hash failed", ex);
            }
        }
    }
}
