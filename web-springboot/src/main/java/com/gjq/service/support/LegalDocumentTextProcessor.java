package com.gjq.service.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class LegalDocumentTextProcessor {

    private static final Pattern MULTI_SPACE = Pattern.compile("[\\t\\x0B\\f\\r ]+");
    private static final Pattern OFFICIAL_SITE_NOISE_PATTERN = Pattern.compile(
            "^(?:当前位置[:：].*|上一篇.*|下一篇.*|打印本页.*|关闭窗口.*|责任编辑[:：].*|来源[:：].*|发布时间[:：].*|扫一扫在手机打开当前页.*|版权所有.*|ICP备.*|联系我们.*)$"
    );
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(一、|二、|三、|四、|五、|六、|七、|八、|九、|十、|本院认为|法院认为|审理查明|经审理查明|本院查明|"
                    + "审理经过|诉讼经过|程序经过|法律依据|相关法条|一审法院认为|二审法院认为|再审审查认为|"
                    + "裁判结果|判决如下|裁定如下|决定如下)"
    );

    public String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = normalizeRawText(text);
        normalized = stripLeadingSiteBoilerplate(normalized);
        normalized = injectHeadingBreaks(normalized);
        normalized = stripLeadingArticleMetadata(normalized);
        StringBuilder builder = new StringBuilder();
        int blankCount = 0;
        for (String line : normalized.split("\n", -1)) {
            String cleaned = normalizeLineText(line);
            if (!StringUtils.hasText(cleaned) || isPageMarkerLine(cleaned) || isBoilerplateLine(cleaned)) {
                blankCount++;
                if (blankCount <= 1 && builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                    builder.append('\n');
                }
                continue;
            }
            blankCount = 0;
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(cleaned);
        }
        return builder.toString().replaceAll("\\n{3,}", "\n\n").trim();
    }

    public String normalizeRawText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u3000', ' ')
                .replace('\u00A0', ' ')
                .trim();
    }

    public String stripLeadingSiteBoilerplate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        int anchor = normalized.indexOf("无障碍");
        if (anchor >= 0 && anchor <= 320
                && normalized.contains("长者版")
                && normalized.contains("关怀版")
                && normalized.contains("首页")) {
            int lastNavEnd = -1;
            int navHitCount = 0;
            String[] navTokens = {
                    "无障碍", "长者版", "关怀版", "首页", "检务公开", "机构设置",
                    "检察业务", "新闻中心", "检察文化", "队伍建设", "数字检察", "12309"
            };
            for (String token : navTokens) {
                int index = normalized.indexOf(token, anchor);
                if (index >= 0 && index <= 900) {
                    navHitCount++;
                    lastNavEnd = Math.max(lastNavEnd, index + token.length());
                }
            }
            if (navHitCount >= 5 && lastNavEnd > anchor) {
                normalized = normalized.substring(lastNavEnd).trim();
            }
            int fontSizeAnchor = normalized.indexOf("大 中 小");
            if (fontSizeAnchor >= 0 && fontSizeAnchor <= 320) {
                normalized = normalized.substring(fontSizeAnchor + "大 中 小".length()).trim();
            }
        }
        return normalized;
    }

    public String stripLeadingArticleMetadata(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String[] rawLines = text.trim().split("\n+");
        int metadataHits = 0;
        int bodyStartIndex = -1;
        int titleCandidateIndex = -1;
        for (int index = 0; index < rawLines.length && index < 14; index++) {
            String line = normalizeLineText(rawLines[index]);
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (isLeadingArticleMetadataLine(line)) {
                metadataHits++;
                continue;
            }
            if (metadataHits >= 2 && isLikelyArticleBodyStart(line)) {
                bodyStartIndex = index;
                break;
            }
            if (titleCandidateIndex < 0 && line.length() <= 40) {
                titleCandidateIndex = index;
                continue;
            }
            break;
        }
        if (bodyStartIndex <= 0) {
            return text.trim();
        }
        StringBuilder builder = new StringBuilder();
        for (int index = bodyStartIndex; index < rawLines.length; index++) {
            String line = rawLines[index].trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.toString();
    }

    public String collapseWhitespace(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return MULTI_SPACE.matcher(text.replace('\n', ' ')).replaceAll(" ").trim();
    }

    public String cleanChunkSource(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        StringBuilder builder = new StringBuilder();
        int blankCount = 0;
        for (String line : normalized.split("\n", -1)) {
            String cleaned = normalizeLineText(line);
            if (!StringUtils.hasText(cleaned) || isBoilerplateLine(cleaned)) {
                blankCount++;
                if (blankCount <= 1 && builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                    builder.append('\n');
                }
                continue;
            }
            blankCount = 0;
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(cleaned);
        }
        return builder.toString().trim();
    }

    public String cleanDisplayText(String text, String title, String courtName, String documentSubtype) {
        String normalized = normalizeText(text);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }

        String normalizedTitle = collapseWhitespace(title);
        String normalizedCourt = collapseWhitespace(courtName);
        String normalizedSubtype = collapseWhitespace(documentSubtype);

        List<String> cleanedLines = new ArrayList<>();
        String previousLine = null;
        int titleHits = 0;
        int courtHits = 0;
        int subtypeHits = 0;

        for (String rawLine : normalized.split("\n+")) {
            String line = normalizeLineText(rawLine);
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if ("正文".equals(line) || "律师阅读版正文".equals(line)) {
                continue;
            }
            if (line.equals(previousLine)) {
                continue;
            }
            if (cleanedLines.size() < 10) {
                if (StringUtils.hasText(normalizedTitle) && line.equals(normalizedTitle)) {
                    titleHits++;
                    if (titleHits >= 1) {
                        previousLine = line;
                        continue;
                    }
                }
                if (StringUtils.hasText(normalizedCourt) && line.equals(normalizedCourt)) {
                    courtHits++;
                    if (courtHits >= 1) {
                        previousLine = line;
                        continue;
                    }
                }
                if (StringUtils.hasText(normalizedSubtype) && line.equals(normalizedSubtype)) {
                    subtypeHits++;
                    if (subtypeHits >= 1) {
                        previousLine = line;
                        continue;
                    }
                }
            }
            cleanedLines.add(line);
            previousLine = line;
        }

        return String.join("\n", cleanedLines).replaceAll("\\n{3,}", "\n\n").trim();
    }

    public String injectHeadingBreaks(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text;
        String[] headings = {
                "审理经过", "诉讼经过", "程序经过",
                "审理查明", "经审理查明", "本院查明", "一审法院查明", "二审法院查明",
                "本院认为", "法院认为", "一审法院认为", "二审法院认为", "再审审查认为",
                "争议焦点", "法律依据", "相关法条", "裁判结果", "判决如下", "裁定如下", "决定如下"
        };
        for (String heading : headings) {
            normalized = normalized.replace(heading, "\n" + heading);
        }
        normalized = normalized.replaceAll("(?<!\\n)([一二三四五六七八九十]+、)", "\n$1");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        return normalized;
    }

    public String normalizeLineText(String line) {
        if (!StringUtils.hasText(line)) {
            return "";
        }
        String normalized = line.replace('\u3000', ' ').trim();
        normalized = MULTI_SPACE.matcher(normalized).replaceAll(" ");
        normalized = normalized.replaceAll("\\s*([，。；：、])\\s*", "$1");
        normalized = normalized.replaceAll("\\s*([()（）])\\s*", "$1");
        return normalized.trim();
    }

    public boolean isBoilerplateLine(String line) {
        if (!StringUtils.hasText(line)) {
            return true;
        }
        String normalized = collapseWhitespace(line);
        if (!StringUtils.hasText(normalized)) {
            return true;
        }
        if (OFFICIAL_SITE_NOISE_PATTERN.matcher(normalized).matches()) {
            return true;
        }
        int navTokenHits = 0;
        navTokenHits += containsToken(normalized, "无障碍");
        navTokenHits += containsToken(normalized, "长者版");
        navTokenHits += containsToken(normalized, "关怀版");
        navTokenHits += containsToken(normalized, "首页");
        navTokenHits += containsToken(normalized, "检务公开");
        navTokenHits += containsToken(normalized, "政务公开");
        navTokenHits += containsToken(normalized, "案件发布");
        navTokenHits += containsToken(normalized, "工作报告");
        navTokenHits += containsToken(normalized, "检察服务");
        navTokenHits += containsToken(normalized, "12309");
        navTokenHits += containsToken(normalized, "返回顶部");
        navTokenHits += containsToken(normalized, "网站地图");
        navTokenHits += containsToken(normalized, "联系我们");
        navTokenHits += containsToken(normalized, "微信");
        navTokenHits += containsToken(normalized, "微博");
        if (navTokenHits >= 3 && normalized.length() <= 120) {
            return true;
        }
        return normalized.startsWith("无障碍 ")
                || normalized.startsWith("无障碍/")
                || normalized.startsWith("无障碍|")
                || normalized.startsWith("长者版 ")
                || normalized.startsWith("关怀版 ")
                || normalized.startsWith("首页 ")
                || normalized.startsWith("当前位置")
                || (normalized.contains("首页") && normalized.contains("检务公开") && normalized.length() <= 120);
    }

    private int containsToken(String text, String token) {
        return text.contains(token) ? 1 : 0;
    }

    private boolean isLeadingArticleMetadataLine(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        String normalized = collapseWhitespace(line);
        return normalized.equals("外部监督")
                || normalized.equals("以案说法")
                || normalized.equals("律师阅读版正文")
                || normalized.equals("大")
                || normalized.equals("中")
                || normalized.equals("小")
                || normalized.equals("大 中 小")
                || normalized.startsWith("日期")
                || normalized.startsWith("作者")
                || normalized.startsWith("来源")
                || normalized.startsWith("今天")
                || normalized.contains("星期")
                || normalized.contains("来源：")
                || normalized.matches("\\d{4}年\\d{1,2}月\\d{1,2}日.*")
                || normalized.matches(".*\\d{4}年\\d{1,2}月\\d{1,2}日.*星期[一二三四五六日天].*");
    }

    private boolean isLikelyArticleBodyStart(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        String normalized = collapseWhitespace(line);
        return normalized.length() >= 24
                || normalized.contains("，")
                || normalized.contains("。")
                || normalized.contains("；")
                || normalized.contains("？")
                || normalized.contains("！");
    }

    public boolean isPageMarkerLine(String line) {
        if (!StringUtils.hasText(line)) {
            return true;
        }
        String normalized = line.trim();
        return normalized.matches("[-—_]*\\s*\\d+\\s*[-—_]*")
                || normalized.matches("第\\s*\\d+\\s*页(\\s*共\\s*\\d+\\s*页)?")
                || normalized.matches("共\\s*\\d+\\s*页\\s*第\\s*\\d+\\s*页");
    }

    public String ellipsize(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String normalized = collapseWhitespace(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3).trim() + "...";
    }

    public List<String> splitParagraphs(String text) {
        return List.of(text.replace("\r", "\n").split("\\n+"));
    }

    public boolean isHeading(String value) {
        return value.length() <= 30 && HEADING_PATTERN.matcher(value).find();
    }

    public String mapSectionType(String title) {
        if (title.contains("审理经过") || title.contains("诉讼经过") || title.contains("程序经过")) {
            return "process";
        }
        if (title.contains("查明") || title.contains("事实")) {
            return "facts";
        }
        if (title.contains("焦点") || title.contains("争议")) {
            return "issues";
        }
        if (title.contains("法律依据") || title.contains("相关法条")) {
            return "law";
        }
        if (title.contains("认为") || title.contains("审查")) {
            return "reasoning";
        }
        if (title.contains("判决如下") || title.contains("裁定如下") || title.contains("决定如下") || title.contains("结果")) {
            return "result";
        }
        return "body";
    }
}
