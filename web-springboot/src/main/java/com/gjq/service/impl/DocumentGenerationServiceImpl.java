package com.gjq.service.impl;

import com.gjq.common.exception.BusinessException;
import com.gjq.dto.DocumentGenerateDTO;
import com.gjq.entity.ChatMessage;
import com.gjq.entity.ChatSession;
import com.gjq.service.ChatMessageService;
import com.gjq.service.ChatSessionService;
import com.gjq.service.DocumentGenerationService;
import com.gjq.utils.StructuredAnswerParser;
import com.gjq.vo.StructuredAnswerVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class DocumentGenerationServiceImpl implements DocumentGenerationService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    public DocumentGenerationServiceImpl(ChatSessionService chatSessionService,
                                         ChatMessageService chatMessageService) {
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
    }

    @Override
    public Map<String, Object> generateDocument(DocumentGenerateDTO request) {
        if (request == null || request.getSessionId() == null) {
            throw new BusinessException("请先选择要生成文档的咨询会话");
        }

        ChatSession session = chatSessionService.getById(request.getSessionId());
        if (session == null) {
            throw new BusinessException("咨询会话不存在");
        }

        List<ChatMessage> messages = chatMessageService.getSessionMessages(session.getId());
        if (messages.isEmpty()) {
            throw new BusinessException("当前会话还没有可用于生成文档的内容");
        }

        String documentType = normalizeDocumentType(request.getDocumentType());
        String template = normalizeTemplate(request.getTemplate());
        String format = normalizeFormat(request.getFormat());
        StructuredAnswerVO structuredAnswer = resolveStructuredAnswer(messages);
        String title = resolveTitle(request, session, documentType);

        String markdown = switch (documentType) {
            case "summary" -> buildSummaryMarkdown(title, session, messages, template, request.getCustomContent());
            default -> buildConsultationReportMarkdown(title, session, messages, structuredAnswer, template, request.getCustomContent());
        };

        String exportedContent = switch (format) {
            case "html" -> toHtml(markdown);
            case "text" -> toPlainText(markdown);
            default -> markdown;
        };

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("title", title);
        response.put("documentType", documentType);
        response.put("template", template);
        response.put("format", format);
        response.put("content", exportedContent);
        response.put("previewMarkdown", markdown);
        response.put("summary", buildExecutiveSummary(messages, structuredAnswer));
        response.put("suggestedFilename", buildSuggestedFilename(title, format));
        response.put("messageCount", messages.size());
        response.put("generatedAt", TIME_FORMATTER.format(LocalDateTime.now()));
        response.put("sessionTitle", session.getTitle());
        response.put("sessionId", session.getId());
        return response;
    }

    private String buildSummaryMarkdown(String title,
                                        ChatSession session,
                                        List<ChatMessage> messages,
                                        String template,
                                        String customContent) {
        List<ChatMessage> userMessages = filterMessages(messages, "user");
        List<ChatMessage> assistantMessages = filterMessages(messages, "assistant");

        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(title).append("\n\n");
        builder.append("## 会话信息\n");
        builder.append("- 会话标题：").append(readString(session.getTitle(), "未命名会话")).append("\n");
        builder.append("- 生成时间：").append(TIME_FORMATTER.format(LocalDateTime.now())).append("\n");
        builder.append("- 对话轮次：").append(messages.size()).append(" 条消息\n");
        builder.append("- 模板：").append(template).append("\n\n");

        builder.append("## 主要诉求摘要\n");
        if (userMessages.isEmpty()) {
            builder.append("- 暂无用户问题记录\n\n");
        } else {
            for (int i = 0; i < Math.min(userMessages.size(), "brief".equals(template) ? 2 : 4); i += 1) {
                builder.append(i + 1).append(". ").append(crop(userMessages.get(i).getContent(), 120)).append("\n");
            }
            builder.append("\n");
        }

        builder.append("## AI 回复概览\n");
        if (assistantMessages.isEmpty()) {
            builder.append("暂无 AI 回复内容。\n\n");
        } else {
            for (int i = 0; i < Math.min(assistantMessages.size(), "brief".equals(template) ? 2 : 3); i += 1) {
                builder.append("### 回复 ").append(i + 1).append("\n");
                builder.append(crop(assistantMessages.get(i).getContent(), "detailed".equals(template) ? 420 : 220)).append("\n\n");
            }
        }

        builder.append("## 对话摘录\n");
        for (ChatMessage message : messages.stream().limit("brief".equals(template) ? 6 : 10).toList()) {
            builder.append("- ")
                    .append("user".equalsIgnoreCase(message.getRole()) ? "用户" : "AI")
                    .append("：")
                    .append(crop(message.getContent(), "detailed".equals(template) ? 180 : 90))
                    .append("\n");
        }
        builder.append("\n");

        appendCustomContent(builder, customContent);
        return builder.toString().trim();
    }

    private String buildConsultationReportMarkdown(String title,
                                                   ChatSession session,
                                                   List<ChatMessage> messages,
                                                   StructuredAnswerVO structuredAnswer,
                                                   String template,
                                                   String customContent) {
        List<ChatMessage> userMessages = filterMessages(messages, "user");

        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(title).append("\n\n");
        builder.append("## 一、咨询基本信息\n");
        builder.append("- 会话标题：").append(readString(session.getTitle(), "未命名会话")).append("\n");
        builder.append("- 会话编号：").append(session.getId()).append("\n");
        builder.append("- 生成时间：").append(TIME_FORMATTER.format(LocalDateTime.now())).append("\n");
        builder.append("- 模板：").append(template).append("\n\n");

        builder.append("## 二、用户核心诉求\n");
        if (userMessages.isEmpty()) {
            builder.append("暂无用户输入内容。\n\n");
        } else {
            for (int i = 0; i < Math.min(userMessages.size(), "brief".equals(template) ? 2 : 5); i += 1) {
                builder.append(i + 1).append(". ").append(crop(userMessages.get(i).getContent(), 160)).append("\n");
            }
            builder.append("\n");
        }

        builder.append("## 三、初步结论\n");
        builder.append(readString(structuredAnswer.getConclusion(), "暂无结构化结论，可结合原始对话继续补充。")).append("\n\n");

        builder.append("## 四、法律依据\n");
        builder.append(readString(structuredAnswer.getBasis(), "暂无明确法条依据摘录。")).append("\n\n");

        builder.append("## 五、风险提示\n");
        builder.append(readString(structuredAnswer.getRisk(), "暂无明确风险提示。")).append("\n\n");

        builder.append("## 六、行动建议\n");
        builder.append(readString(structuredAnswer.getAdvice(), "暂无明确行动建议。")).append("\n\n");

        builder.append("## 七、律师追问\n");
        builder.append(readString(structuredAnswer.getFollowUp(), "暂无律师追问，可在下一轮对话中继续补充关键事实。")).append("\n\n");

        builder.append("## 八、材料与证据核对建议\n");
        for (String suggestion : buildEvidenceChecklist(messages, template)) {
            builder.append("- ").append(suggestion).append("\n");
        }
        builder.append("\n");

        if (!"brief".equals(template)) {
            builder.append("## 九、对话时间线\n");
            for (ChatMessage message : messages.stream().limit("standard".equals(template) ? 10 : 16).toList()) {
                builder.append("- [")
                        .append(formatMessageTime(message.getCreateTime()))
                        .append("] ")
                        .append("user".equalsIgnoreCase(message.getRole()) ? "用户" : "AI")
                        .append("：")
                        .append(crop(message.getContent(), "detailed".equals(template) ? 180 : 110))
                        .append("\n");
            }
            builder.append("\n");
        }

        appendCustomContent(builder, customContent);
        return builder.toString().trim();
    }

    private void appendCustomContent(StringBuilder builder, String customContent) {
        if (!StringUtils.hasText(customContent)) {
            return;
        }
        builder.append("## 补充说明\n");
        builder.append(customContent.trim()).append("\n\n");
    }

    private StructuredAnswerVO resolveStructuredAnswer(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i -= 1) {
            ChatMessage message = messages.get(i);
            if ("assistant".equalsIgnoreCase(message.getRole()) && StringUtils.hasText(message.getContent())) {
                return StructuredAnswerParser.parse(message.getContent());
            }
        }
        return new StructuredAnswerVO();
    }

    private List<ChatMessage> filterMessages(List<ChatMessage> messages, String role) {
        return messages.stream()
                .filter(message -> Objects.equals(role, message.getRole()))
                .toList();
    }

    private List<String> buildEvidenceChecklist(List<ChatMessage> messages, String template) {
        String corpus = messages.stream()
                .map(ChatMessage::getContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));

        List<String> checklist = new ArrayList<>();
        checklist.add("核对当事人身份、涉案时间、当前程序阶段是否已经明确。");
        if (corpus.contains("聊天") || corpus.contains("微信") || corpus.contains("截图")) {
            checklist.add("保留聊天记录、截图原图、导出文件及时间信息，避免只留二次截图。");
        }
        if (corpus.contains("转账") || corpus.contains("银行卡") || corpus.contains("流水")) {
            checklist.add("整理转账流水、交易凭证、收付款说明，标注每笔款项用途。");
        }
        if (corpus.contains("拘留") || corpus.contains("通知书") || corpus.contains("传唤")) {
            checklist.add("留存拘留通知书、传唤通知、搜查笔录、扣押清单等程序性材料。");
        }
        if (corpus.contains("检测") || corpus.contains("尿检") || corpus.contains("鉴定")) {
            checklist.add("核对检测报告、鉴定意见和送检流程，确认出具机构与结论时间。");
        }
        checklist.add("与律师沟通前，可先整理一页案情时间线，减少信息遗漏。");
        return "brief".equals(template) ? checklist.stream().limit(4).toList() : checklist;
    }

    private String buildExecutiveSummary(List<ChatMessage> messages, StructuredAnswerVO structuredAnswer) {
        if (StringUtils.hasText(structuredAnswer.getConclusion())) {
            return crop(structuredAnswer.getConclusion(), 140);
        }
        return messages.stream()
                .filter(message -> "assistant".equalsIgnoreCase(message.getRole()))
                .map(ChatMessage::getContent)
                .filter(StringUtils::hasText)
                .findFirst()
                .map(content -> crop(content, 140))
                .orElse("已根据当前咨询会话生成文档。");
    }

    private String normalizeDocumentType(String rawValue) {
        String value = readString(rawValue, "report").toLowerCase(Locale.ROOT);
        return switch (value) {
            case "summary", "session_summary", "dialog_summary" -> "summary";
            default -> "report";
        };
    }

    private String normalizeTemplate(String rawValue) {
        String value = readString(rawValue, "standard").toLowerCase(Locale.ROOT);
        return switch (value) {
            case "brief", "standard", "detailed" -> value;
            default -> "standard";
        };
    }

    private String normalizeFormat(String rawValue) {
        String value = readString(rawValue, "markdown").toLowerCase(Locale.ROOT);
        return switch (value) {
            case "markdown", "text", "html" -> value;
            default -> "markdown";
        };
    }

    private String resolveTitle(DocumentGenerateDTO request, ChatSession session, String documentType) {
        if (StringUtils.hasText(request.getTitle())) {
            return request.getTitle().trim();
        }
        String sessionTitle = readString(session.getTitle(), "咨询会话");
        return "summary".equals(documentType)
                ? sessionTitle + " 对话总结"
                : sessionTitle + " 法律咨询报告";
    }

    private String buildSuggestedFilename(String title, String format) {
        String extension = switch (format) {
            case "html" -> "html";
            case "text" -> "txt";
            default -> "md";
        };
        return title.replaceAll("[\\\\/:*?\"<>|]+", "_") + "." + extension;
    }

    private String formatMessageTime(LocalDateTime value) {
        return value == null ? "--" : TIME_FORMATTER.format(value);
    }

    private String toPlainText(String markdown) {
        return markdown
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replace("**", "")
                .replace("##", "")
                .replace("#", "")
                .trim();
    }

    private String toHtml(String markdown) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">");
        html.append("<title>法律咨询文档</title>");
        html.append("<style>body{font-family:'Microsoft YaHei',sans-serif;padding:32px;line-height:1.8;color:#1f2937;}");
        html.append("h1,h2{color:#0f172a;}pre{white-space:pre-wrap;}ul{padding-left:20px;}</style></head><body>");

        for (String line : markdown.split("\\r?\\n")) {
            if (line.startsWith("# ")) {
                html.append("<h1>").append(escapeHtml(line.substring(2))).append("</h1>");
            } else if (line.startsWith("## ")) {
                html.append("<h2>").append(escapeHtml(line.substring(3))).append("</h2>");
            } else if (line.isBlank()) {
                html.append("<br/>");
            } else {
                html.append("<p>").append(escapeHtml(line)).append("</p>");
            }
        }

        html.append("</body></html>");
        return html.toString();
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String crop(String value, int limit) {
        String text = readString(value, "");
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }

    private String readString(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
