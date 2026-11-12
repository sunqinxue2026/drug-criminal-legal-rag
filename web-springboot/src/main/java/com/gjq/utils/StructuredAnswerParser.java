package com.gjq.utils;

import com.gjq.vo.StructuredAnswerVO;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class StructuredAnswerParser {

    private static final Map<String, String[]> SECTION_LABELS = new LinkedHashMap<>();
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("^```[\\w-]*\\s*");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^>+\\s*");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s*");
    private static final Pattern ORDERED_PREFIX_PATTERN = Pattern.compile("^(?:[\\(\\[]?\\d+[\\)\\.、．])\\s*");
    private static final Pattern SECTION_BOUNDARY_PATTERN = Pattern.compile(
            "(?<!\\n)(\\u521d\\u6b65\\u7ed3\\u8bba|\\u7ed3\\u8bba|\\u6cd5\\u5f8b\\u4f9d\\u636e|\\u6cd5\\u6761\\u4f9d\\u636e|\\u98ce\\u9669\\u63d0\\u793a|\\u6ce8\\u610f\\u4e8b\\u9879|\\u884c\\u52a8\\u5efa\\u8bae|\\u7ef4\\u6743\\u5efa\\u8bae|\\u4e0b\\u4e00\\u6b65\\u5efa\\u8bae|\\u5f8b\\u5e08\\u8ffd\\u95ee|\\u8865\\u5145\\u8ffd\\u95ee|\\u8fdb\\u4e00\\u6b65\\u8ffd\\u95ee)(?:\\uFF1A|:)"
    );

    static {
        SECTION_LABELS.put("conclusion", new String[]{
                "\u521d\u6b65\u7ed3\u8bba",
                "\u7ed3\u8bba"
        });
        SECTION_LABELS.put("basis", new String[]{
                "\u6cd5\u5f8b\u4f9d\u636e",
                "\u6cd5\u6761\u4f9d\u636e"
        });
        SECTION_LABELS.put("risk", new String[]{
                "\u98ce\u9669\u63d0\u793a",
                "\u6ce8\u610f\u4e8b\u9879"
        });
        SECTION_LABELS.put("advice", new String[]{
                "\u884c\u52a8\u5efa\u8bae",
                "\u7ef4\u6743\u5efa\u8bae",
                "\u4e0b\u4e00\u6b65\u5efa\u8bae"
        });
        SECTION_LABELS.put("followUp", new String[]{
                "\u5f8b\u5e08\u8ffd\u95ee",
                "\u8865\u5145\u8ffd\u95ee",
                "\u8fdb\u4e00\u6b65\u8ffd\u95ee"
        });
    }

    private StructuredAnswerParser() {
    }

    public static StructuredAnswerVO parse(String content) {
        StructuredAnswerVO structured = new StructuredAnswerVO();
        String normalized = normalizeBoundaries(content);
        if (normalized.isBlank()) {
            return structured;
        }

        structured.setConclusion("");
        String currentSection = "conclusion";
        boolean explicitSectionFound = false;

        for (String rawLine : normalized.split("\\r?\\n")) {
            String line = sanitize(rawLine);
            if (line.isBlank()) {
                continue;
            }

            SectionMatch match = matchSection(line);
            if (match != null) {
                explicitSectionFound = true;
                currentSection = match.section();
                append(structured, currentSection, sanitize(match.content()));
                continue;
            }

            append(structured, currentSection, line);
        }

        if (!explicitSectionFound) {
            structured.setConclusion(normalized);
        } else if (structured.getConclusion().isBlank()) {
            structured.setConclusion(sanitize(content));
        }
        structured.setFollowUp(normalizeFollowUp(structured.getFollowUp()));
        return structured;
    }

    public static StructuredAnswerVO fromObject(Object payload, String fallbackContent) {
        if (payload instanceof Map<?, ?> map) {
            StructuredAnswerVO structured = new StructuredAnswerVO();
            structured.setConclusion(readString(map.get("conclusion")));
            structured.setBasis(readString(map.get("basis")));
            structured.setRisk(readString(map.get("risk")));
            structured.setAdvice(readString(map.get("advice")));
            structured.setFollowUp(normalizeFollowUp(readFirstString(map, "followUp", "follow_up")));
            if (!structured.getConclusion().isBlank()
                    || !structured.getBasis().isBlank()
                    || !structured.getRisk().isBlank()
                    || !structured.getAdvice().isBlank()
                    || !structured.getFollowUp().isBlank()) {
                return structured;
            }
        }
        return parse(fallbackContent);
    }

    private static void append(StructuredAnswerVO structured, String section, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        switch (section) {
            case "basis" -> structured.setBasis(join(structured.getBasis(), text));
            case "risk" -> structured.setRisk(join(structured.getRisk(), text));
            case "advice" -> structured.setAdvice(join(structured.getAdvice(), text));
            case "followUp" -> structured.setFollowUp(join(structured.getFollowUp(), text));
            default -> structured.setConclusion(join(structured.getConclusion(), text));
        }
    }

    private static String join(String current, String next) {
        if (current == null || current.isBlank()) {
            return next == null ? "" : next.trim();
        }
        if (next == null || next.isBlank()) {
            return current.trim();
        }
        return current.trim() + "\n" + next.trim();
    }

    private static String normalizeBoundaries(String content) {
        String value = readString(content).replace("\r\n", "\n").trim();
        if (value.isBlank()) {
            return "";
        }
        return SECTION_BOUNDARY_PATTERN.matcher(value).replaceAll("\n$1\uFF1A");
    }

    private static String sanitize(String line) {
        String result = readString(line).trim();
        String previous = "";
        while (!result.equals(previous)) {
            previous = result;
            result = CODE_FENCE_PATTERN.matcher(result).replaceFirst("");
            result = QUOTE_PATTERN.matcher(result).replaceFirst("");
            result = HEADING_PATTERN.matcher(result).replaceFirst("");
            result = ORDERED_PREFIX_PATTERN.matcher(result).replaceFirst("");
            result = result.trim();
        }
        return result;
    }

    private static SectionMatch matchSection(String line) {
        for (Map.Entry<String, String[]> entry : SECTION_LABELS.entrySet()) {
            for (String label : entry.getValue()) {
                if (line.startsWith(label + "\uFF1A") || line.startsWith(label + ":")) {
                    return new SectionMatch(entry.getKey(), line.substring(label.length() + 1).trim());
                }
            }
        }
        return null;
    }

    private static String readString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String readFirstString(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            String value = readString(map.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String normalizeFollowUp(String value) {
        String text = sanitize(value).replaceAll("\\s+", " ").trim();
        if (text.isBlank()) {
            return "";
        }
        int cutIndex = firstEndingIndex(text);
        return cutIndex >= 0 ? text.substring(0, cutIndex + 1).trim() : text;
    }

    private static int firstEndingIndex(String value) {
        int result = Integer.MAX_VALUE;
        for (String marker : new String[]{"\uFF1F", "?", "\u3002", "\uFF01", "!", "\uFF1B", ";"}) {
            int index = value.indexOf(marker);
            if (index >= 0 && index < result) {
                result = index;
            }
        }
        return result == Integer.MAX_VALUE ? -1 : result;
    }

    private record SectionMatch(String section, String content) {
    }
}
