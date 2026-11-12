package com.gjq.service.support;

import com.alibaba.fastjson2.JSON;
import com.gjq.entity.LegalDocument;
import com.gjq.entity.LegalDocumentChunk;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LegalDocumentChunkService {

    private static final int CHUNK_SIZE = 700;
    private static final int CHUNK_OVERLAP = 120;

    private final LegalDocumentTextProcessor textProcessor;

    public LegalDocumentChunkService(LegalDocumentTextProcessor textProcessor) {
        this.textProcessor = textProcessor;
    }

    public List<LegalDocumentSection> splitSections(String text) {
        List<String> paragraphs = textProcessor.splitParagraphs(textProcessor.injectHeadingBreaks(text));
        List<LegalDocumentSection> sections = new ArrayList<>();
        String currentTitle = "全文";
        String currentType = "full_text";
        StringBuilder buffer = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmed = textProcessor.normalizeLineText(paragraph);
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (textProcessor.isHeading(trimmed)) {
                if (buffer.length() > 0) {
                    sections.add(new LegalDocumentSection(currentType, currentTitle, currentTitle, buffer.toString().trim()));
                    buffer.setLength(0);
                }
                currentTitle = trimmed;
                currentType = textProcessor.mapSectionType(trimmed);
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(trimmed);
        }
        if (buffer.length() > 0) {
            sections.add(new LegalDocumentSection(currentType, currentTitle, currentTitle, buffer.toString().trim()));
        }
        if (sections.isEmpty()) {
            sections.add(new LegalDocumentSection("full_text", "全文", "全文", text));
        }
        return sections;
    }

    public List<LegalDocumentChunk> buildChunks(LegalDocument document,
                                                List<LegalDocumentSection> sections,
                                                ChunkSupport support) {
        List<LegalDocumentChunk> chunks = new ArrayList<>();
        Set<String> seenHashes = new HashSet<>();
        int sectionOrder = 0;

        sectionOrder = appendAtomicChunk(
                chunks, seenHashes, document,
                new LegalDocumentSection("overview", "案件概览", "案件概览", buildOverviewChunkText(document, support)),
                "overview_atomic", sectionOrder, support
        );
        sectionOrder = appendAtomicChunk(
                chunks, seenHashes, document,
                new LegalDocumentSection(
                        "result",
                        "裁判结果摘要",
                        "裁判结果摘要",
                        support.firstNonBlank(document.getResultSummary(), document.getDecisionResult())
                ),
                "result_atomic", sectionOrder, support
        );
        sectionOrder = appendAtomicChunk(
                chunks, seenHashes, document,
                new LegalDocumentSection("law", "法律依据摘要", "法律依据摘要", document.getLegalBasisText()),
                "law_basis", sectionOrder, support
        );

        for (LegalDocumentSection section : sections) {
            String text = textProcessor.cleanChunkSource(section.content);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            int chunkSize = chunkSizeForSection(section.type);
            int overlap = overlapForSection(section.type);
            List<LegalDocumentTextSlice> slices = "result".equals(section.type)
                    ? sliceResultSection(text)
                    : sliceText(text, chunkSize, overlap);
            int localIndex = 0;
            for (LegalDocumentTextSlice slice : slices) {
                sectionOrder = appendChunk(
                        chunks,
                        seenHashes,
                        document,
                        section,
                        "result".equals(section.type) ? "result_items" : "section_window",
                        sectionOrder,
                        localIndex++,
                        slice.start,
                        slice.end,
                        slice.text,
                        support
                );
            }
        }
        if (chunks.isEmpty()) {
            LegalDocumentSection fallback = new LegalDocumentSection("full_text", "全文", "全文", document.getCleanText());
            if (StringUtils.hasText(fallback.content) && !Objects.equals(fallback.content, sections.isEmpty() ? null : sections.get(0).content)) {
                return buildChunks(document, List.of(fallback), support);
            }
        }
        return chunks;
    }

    private int appendAtomicChunk(List<LegalDocumentChunk> chunks,
                                  Set<String> seenHashes,
                                  LegalDocument document,
                                  LegalDocumentSection section,
                                  String strategy,
                                  int sectionOrder,
                                  ChunkSupport support) {
        String text = textProcessor.cleanChunkSource(section.content);
        if (!StringUtils.hasText(text)) {
            return sectionOrder;
        }
        return appendChunk(chunks, seenHashes, document, section, strategy, sectionOrder, 0, 0, text.length(), text, support);
    }

    private int appendChunk(List<LegalDocumentChunk> chunks,
                            Set<String> seenHashes,
                            LegalDocument document,
                            LegalDocumentSection section,
                            String strategy,
                            int sectionOrder,
                            int chunkIndex,
                            int charStart,
                            int charEnd,
                            String text,
                            ChunkSupport support) {
        String normalized = textProcessor.cleanChunkSource(text);
        if (!StringUtils.hasText(normalized)) {
            return sectionOrder;
        }
        String chunkHash = support.sha256Hex(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!seenHashes.add(chunkHash)) {
            return sectionOrder;
        }
        LegalDocumentChunk chunk = new LegalDocumentChunk();
        chunk.setChunkStrategy(strategy);
        chunk.setChunkVersion("v2.2");
        chunk.setSectionType(section.type);
        chunk.setSectionTitle(section.title);
        chunk.setHeaderPath(section.headerPath);
        chunk.setRetrievalRole(mapRetrievalRole(section.type));
        chunk.setSectionWeight(sectionWeight(section.type));
        chunk.setSectionOrder(sectionOrder);
        chunk.setChunkIndex(chunkIndex);
        chunk.setCharStartOffset(charStart);
        chunk.setCharEndOffset(charEnd);
        chunk.setChunkHash(chunkHash);
        chunk.setChunkText(normalized);
        chunk.setRetrievalText(support.buildChunkRetrievalText(document, section, normalized));
        chunk.setCharCount(normalized.length());
        chunk.setTokenCount(support.estimateTokenCount(normalized));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sectionType", section.type);
        metadata.put("sectionTitle", section.title);
        metadata.put("headerPath", section.headerPath);
        metadata.put("weight", sectionWeight(section.type));
        metadata.put("strategy", strategy);
        chunk.setMetadataJson(JSON.toJSONString(metadata));
        chunk.setDeleted(0);
        chunks.add(chunk);
        return sectionOrder + 1;
    }

    private String buildOverviewChunkText(LegalDocument document, ChunkSupport support) {
        return support.firstNonBlank(
                document.getCaseDetailText(),
                document.getDisputeSummary(),
                document.getSummary(),
                document.getResultSummary()
        );
    }

    private int chunkSizeForSection(String sectionType) {
        return switch (sectionType) {
            case "issues", "reasoning" -> 520;
            case "facts" -> 680;
            case "overview", "result", "law" -> 420;
            default -> CHUNK_SIZE;
        };
    }

    private int overlapForSection(String sectionType) {
        return switch (sectionType) {
            case "issues", "reasoning" -> 80;
            case "facts" -> 100;
            case "overview", "result", "law" -> 40;
            default -> CHUNK_OVERLAP;
        };
    }

    private List<LegalDocumentTextSlice> sliceResultSection(String text) {
        List<LegalDocumentTextSlice> items = splitOrderedItems(text);
        if (!items.isEmpty()) {
            return items;
        }
        return sliceText(text, chunkSizeForSection("result"), overlapForSection("result"));
    }

    private String mapRetrievalRole(String sectionType) {
        return switch (sectionType) {
            case "overview" -> "case_overview";
            case "process" -> "trial_process";
            case "issues" -> "issue_focus";
            case "reasoning" -> "court_reasoning";
            case "result" -> "decision_result";
            case "law" -> "law_basis";
            case "facts" -> "facts";
            default -> "body";
        };
    }

    private BigDecimal sectionWeight(String sectionType) {
        return switch (sectionType) {
            case "overview" -> BigDecimal.valueOf(1.45);
            case "result" -> BigDecimal.valueOf(1.35);
            case "reasoning" -> BigDecimal.valueOf(1.25);
            case "issues" -> BigDecimal.valueOf(1.15);
            case "process" -> BigDecimal.valueOf(1.10);
            case "law" -> BigDecimal.valueOf(1.10);
            case "facts" -> BigDecimal.valueOf(1.00);
            default -> BigDecimal.valueOf(0.90);
        };
    }

    private List<LegalDocumentTextSlice> sliceText(String text, int chunkSize, int overlap) {
        List<LegalDocumentTextSlice> slices = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return slices;
        }
        if (text.length() <= chunkSize) {
            slices.add(new LegalDocumentTextSlice(0, text.length(), text));
            return slices;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkSize);
            if (end < text.length()) {
                int split = findSplitPoint(text, start, end);
                if (split > start + 120) {
                    end = split;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (StringUtils.hasText(chunk)) {
                slices.add(new LegalDocumentTextSlice(start, end, chunk));
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return slices;
    }

    private int findSplitPoint(String text, int start, int end) {
        for (int i = end; i > start + 80; i--) {
            char ch = text.charAt(i - 1);
            if (ch == '\n' || ch == '。' || ch == '；' || ch == '！' || ch == '？') {
                return i;
            }
        }
        return end;
    }

    private List<LegalDocumentTextSlice> splitOrderedItems(String text) {
        List<LegalDocumentTextSlice> slices = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return slices;
        }
        Matcher matcher = Pattern.compile("([一二三四五六七八九十]+[、.．]|\\d+[、.．])").matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
        }
        if (starts.size() <= 1) {
            return slices;
        }
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
            String item = text.substring(start, end).trim();
            if (StringUtils.hasText(item)) {
                slices.add(new LegalDocumentTextSlice(start, end, item));
            }
        }
        return slices;
    }

    public interface ChunkSupport {
        String firstNonBlank(String... values);

        String buildChunkRetrievalText(LegalDocument document, LegalDocumentSection section, String chunkText);

        int estimateTokenCount(String text);

        String sha256Hex(byte[] bytes);
    }
}
