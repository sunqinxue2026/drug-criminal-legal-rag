package com.gjq.service.impl;

import com.gjq.client.AlgorithmClient;
import com.gjq.dto.ItemImportResponse;
import com.gjq.dto.ItemImportResult;
import com.gjq.dto.ItemPreviewResponse;
import com.gjq.entity.Category;
import com.gjq.entity.Item;
import com.gjq.service.CategoryService;
import com.gjq.service.ItemImportService;
import com.gjq.service.ItemService;
import com.gjq.utils.DrugVerticalDomainSupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ItemImportServiceImpl implements ItemImportService {

    private static final int SUMMARY_LIMIT = 180;
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private final ItemService itemService;
    private final CategoryService categoryService;
    private final AlgorithmClient algorithmClient;

    public ItemImportServiceImpl(
            ItemService itemService,
            CategoryService categoryService,
            AlgorithmClient algorithmClient
    ) {
        this.itemService = itemService;
        this.categoryService = categoryService;
        this.algorithmClient = algorithmClient;
    }

    @Override
    public ItemPreviewResponse previewFile(MultipartFile file, Long categoryId) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> extracted = unwrapData(algorithmClient.extractDocumentText(file));
            boolean supported = Boolean.TRUE.equals(extracted.get("supported"));
            String text = normalizeText(asText(extracted.get("text"), ""));

            ItemPreviewResponse response = new ItemPreviewResponse();
            response.setSupported(supported);
            response.setMessage(asText(extracted.get("message"), supported ? "文档解析成功" : "当前文件格式暂不支持导入"));
            response.setCharCount(text.length());

            if (!supported || !StringUtils.hasText(text)) {
                return response;
            }

            List<Category> categories = categoryService.getAllCategories();
            Category category = resolveCategory(categoryId, categories, text, file.getOriginalFilename());
            response.setItem(buildItem(file.getOriginalFilename(), text, category));
            return response;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public ItemImportResponse importFiles(MultipartFile[] files, Long categoryId, boolean rebuildIndex) {
        ItemImportResponse response = new ItemImportResponse();
        if (files == null || files.length == 0) {
            response.setIndexMessage("未选择任何文件");
            return response;
        }

        List<Category> categories = categoryService.getAllCategories();
        DedupState dedupState = buildDedupState();
        response.setTotalFiles(files.length);

        for (MultipartFile file : files) {
            ItemImportResult result = importSingleFile(file, categoryId, categories, dedupState);
            response.getResults().add(result);
            if (result.isSuccess()) {
                response.setImportedCount(response.getImportedCount() + 1);
            } else if (result.isSkipped()) {
                response.setSkippedCount(response.getSkippedCount() + 1);
            } else {
                response.setFailedCount(response.getFailedCount() + 1);
            }
        }

        if (response.getImportedCount() > 0 && rebuildIndex) {
            Map<String, Object> rebuildInfo = rebuildLegalIndex();
            response.setReindexed(Boolean.TRUE.equals(rebuildInfo.get("success")));
            response.setIndexMessage(asText(
                    rebuildInfo.get("message"),
                    response.isReindexed() ? "RAG 索引已更新" : "RAG 索引更新失败"
            ));
        } else if (response.getImportedCount() > 0) {
            response.setIndexMessage("案件已入库，未执行索引更新");
        } else {
            response.setIndexMessage("没有成功导入的案件，未触发索引更新");
        }

        return response;
    }

    @Override
    public Map<String, Object> rebuildLegalIndex() {
        try {
            Map<String, Object> rebuildResult = algorithmClient.rebuildLegalIndex();
            Map<String, Object> rebuildData = unwrapData(rebuildResult);
            boolean success = Boolean.TRUE.equals(rebuildResult.get("success"))
                    || Integer.valueOf(200).equals(rebuildResult.get("code"))
                    || Boolean.TRUE.equals(rebuildData.get("success"));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", success);
            response.put("message", asText(
                    rebuildData.get("message"),
                    asText(rebuildResult.get("message"), success ? "RAG 索引已更新" : "RAG 索引更新失败")
            ));
            return response;
        } catch (Exception ex) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("message", "RAG 索引更新失败：" + ex.getMessage());
            return response;
        }
    }

    private ItemImportResult importSingleFile(MultipartFile file, Long categoryId, List<Category> categories, DedupState dedupState) {
        ItemImportResult result = new ItemImportResult();
        result.setFileName(file == null ? "unknown" : file.getOriginalFilename());

        if (file == null || file.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("文件为空，无法导入");
            return result;
        }

        try {
            Map<String, Object> extracted = algorithmClient.extractDocumentText(file);
            extracted = unwrapData(extracted);
            boolean supported = Boolean.TRUE.equals(extracted.get("supported"));
            String text = normalizeText(asText(extracted.get("text"), ""));

            if (!supported) {
                result.setSuccess(false);
                result.setMessage(asText(extracted.get("message"), "当前文件格式暂不支持导入"));
                return result;
            }

            if (!StringUtils.hasText(text)) {
                result.setSuccess(false);
                result.setMessage("文档解析成功，但未提取到可用正文");
                return result;
            }

            Category category = resolveCategory(categoryId, categories, text, file.getOriginalFilename());
            Item item = buildItem(file.getOriginalFilename(), text, category);
            DuplicateMatch duplicateMatch = findDuplicate(item, dedupState);
            if (duplicateMatch != null) {
                result.setSuccess(false);
                result.setSkipped(true);
                result.setDuplicate(true);
                result.setExistingItemId(duplicateMatch.itemId());
                result.setExistingTitle(duplicateMatch.title());
                result.setItemId(duplicateMatch.itemId());
                result.setTitle(duplicateMatch.title());
                result.setCategoryId(duplicateMatch.categoryId());
                result.setCategoryName(duplicateMatch.categoryName());
                result.setCharCount(text.length());
                result.setMessage("检测到重复案件，已跳过入库");
                return result;
            }
            itemService.save(item);
            registerImportedItem(item, category, dedupState);

            result.setSuccess(true);
            result.setItemId(item.getId());
            result.setTitle(item.getTitle());
            result.setCategoryId(item.getCategoryId());
            result.setCategoryName(category == null ? "未分类" : category.getName());
            result.setCharCount(text.length());
            result.setMessage("导入成功");
            return result;
        } catch (Exception ex) {
            result.setSuccess(false);
            result.setMessage("导入失败：" + ex.getMessage());
            return result;
        }
    }

    private Item buildItem(String fileName, String text, Category category) {
        Item item = new Item();
        item.setTitle(buildTitle(fileName, text));
        item.setSummary(buildSummary(text));
        item.setContent(text);
        item.setCategoryId(category == null ? null : category.getId());
        item.setTags(buildTags(text, category));
        item.setStatus(1);
        item.setViewCount(0);
        item.setLikeCount(0);
        item.setFavoriteCount(0);
        return item;
    }

    private String buildTitle(String fileName, String text) {
        String fileNameTitle = sanitizeTitleCandidate(stripExtension(fileName));
        if (isUsableTitleCandidate(fileNameTitle) && !isGenericDocumentHeader(fileNameTitle)) {
            return fileNameTitle;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            String cleaned = trimmed.replaceAll("^[#*\\-\\d.、\\s]+", "").trim();
            if (!isUsableTitleCandidate(cleaned) || isGenericDocumentHeader(cleaned)) {
                continue;
            }
            if (cleaned.length() <= 80) {
                return cleaned;
            }
        }
        return StringUtils.hasText(fileNameTitle) ? fileNameTitle : stripExtension(fileName);
    }

    private String sanitizeTitleCandidate(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String cleaned = value.trim().replaceAll("^[#*\\-\\d.、\\s]+", "");
        return collapseWhitespace(cleaned);
    }

    private boolean isUsableTitleCandidate(String value) {
        return StringUtils.hasText(value) && value.length() >= 4 && value.length() <= 120;
    }

    private boolean isGenericDocumentHeader(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String normalized = collapseWhitespace(value);
        return normalized.matches("第.+页")
                || normalized.equalsIgnoreCase("word")
                || normalized.equalsIgnoreCase("document")
                || normalized.startsWith("附件")
                || normalized.startsWith("目录");
    }

    private String buildSummary(String text) {
        for (String paragraph : text.split("\\R\\s*\\R")) {
            String normalized = collapseWhitespace(paragraph);
            if (normalized.length() >= 40) {
                return ellipsize(normalized, SUMMARY_LIMIT);
            }
        }

        String compact = collapseWhitespace(text);
        return ellipsize(compact, SUMMARY_LIMIT);
    }

    private String buildTags(String text, Category category) {
        Set<String> tags = new LinkedHashSet<>();
        if (category != null && StringUtils.hasText(category.getName())) {
            tags.add(category.getName());
        }

        Map<String, List<String>> keywords = buildKeywordDictionary();
        String lowerText = text.toLowerCase(Locale.ROOT);

        for (Map.Entry<String, List<String>> entry : keywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lowerText.contains(keyword.toLowerCase(Locale.ROOT))) {
                    tags.add(entry.getKey());
                    break;
                }
            }
            if (tags.size() >= 5) {
                break;
            }
        }

        if (tags.isEmpty()) {
            tags.add("涉毒专题");
            tags.add("风险分析");
        }

        return String.join(",", tags);
    }

    private Category resolveCategory(Long categoryId, List<Category> categories, String text, String fileName) {
        if (categoryId != null) {
            for (Category category : categories) {
                if (Objects.equals(category.getId(), categoryId)) {
                    return category;
                }
            }
        }

        if (CollectionUtils.isEmpty(categories)) {
            return null;
        }

        String corpus = (text + "\n" + fileName).toLowerCase(Locale.ROOT);
        Category bestCategory = null;
        int bestScore = 0;
        for (Category category : categories) {
            int score = scoreCategory(category, corpus);
            if (score > bestScore) {
                bestScore = score;
                bestCategory = category;
            }
        }

        return bestCategory != null ? bestCategory : categories.get(0);
    }

    private int scoreCategory(Category category, String corpus) {
        int score = 0;
        List<String> candidates = new ArrayList<>();
        if (StringUtils.hasText(category.getName())) {
            candidates.add(category.getName());
        }
        if (StringUtils.hasText(category.getDescription())) {
            candidates.add(category.getDescription());
        }
        candidates.addAll(extraKeywordsForCategory(category.getName()));

        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            String[] words = candidate.toLowerCase(Locale.ROOT).split("[,，/\\s]+");
            for (String word : words) {
                String token = word.trim();
                if (token.length() >= 2 && corpus.contains(token)) {
                    score++;
                }
            }
        }

        return score;
    }

    private List<String> extraKeywordsForCategory(String categoryName) {
        return new ArrayList<>(DrugVerticalDomainSupport.keywordsForCategory(categoryName));
    }

    private Map<String, List<String>> buildKeywordDictionary() {
        return new LinkedHashMap<>(DrugVerticalDomainSupport.tagKeywordDictionary());
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replace("\u0000", "").trim();
    }

    private String collapseWhitespace(String text) {
        return MULTI_SPACE.matcher(text == null ? "" : text).replaceAll(" ").trim();
    }

    private String ellipsize(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String stripExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "导入案件";
        }
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private String asText(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private DedupState buildDedupState() {
        DedupState state = new DedupState();
        List<Item> existingItems = itemService.list(new LambdaQueryWrapper<Item>()
                .eq(Item::getStatus, 1)
                .orderByDesc(Item::getCreateTime));
        Map<Long, String> categoryNames = new LinkedHashMap<>();
        for (Category category : categoryService.getAllCategories()) {
            categoryNames.put(category.getId(), category.getName());
        }

        for (Item item : existingItems) {
            registerStateItem(item, categoryNames.get(item.getCategoryId()), state);
        }
        return state;
    }

    private void registerImportedItem(Item item, Category category, DedupState state) {
        registerStateItem(item, category == null ? null : category.getName(), state);
    }

    private void registerStateItem(Item item, String categoryName, DedupState state) {
        String titleKey = normalizeKey(item.getTitle());
        if (StringUtils.hasText(titleKey) && !state.titleIndex.containsKey(titleKey)) {
            state.titleIndex.put(titleKey, new DuplicateMatch(item.getId(), item.getTitle(), item.getCategoryId(), categoryName));
        }

        String contentKey = fingerprint(normalizeDedupText(item.getContent()));
        if (StringUtils.hasText(contentKey) && !state.contentIndex.containsKey(contentKey)) {
            state.contentIndex.put(contentKey, new DuplicateMatch(item.getId(), item.getTitle(), item.getCategoryId(), categoryName));
        }
    }

    private DuplicateMatch findDuplicate(Item item, DedupState state) {
        String titleKey = normalizeKey(item.getTitle());
        if (StringUtils.hasText(titleKey) && state.titleIndex.containsKey(titleKey)) {
            return state.titleIndex.get(titleKey);
        }

        String contentKey = fingerprint(normalizeDedupText(item.getContent()));
        if (StringUtils.hasText(contentKey) && state.contentIndex.containsKey(contentKey)) {
            return state.contentIndex.get(contentKey);
        }

        return null;
    }

    private String normalizeKey(String value) {
        return collapseWhitespace(value).toLowerCase(Locale.ROOT);
    }

    private String normalizeDedupText(String value) {
        return collapseWhitespace(value)
                .replaceAll("[，。、“”‘’；：,.!?！？;:()（）\\-]", "")
                .toLowerCase(Locale.ROOT);
    }

    private String fingerprint(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception ex) {
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapData(Map<String, Object> payload) {
        if (payload == null) {
            return new LinkedHashMap<>();
        }
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return (Map<String, Object>) dataMap;
        }
        return payload;
    }

    private static class DedupState {
        private final Map<String, DuplicateMatch> titleIndex = new LinkedHashMap<>();
        private final Map<String, DuplicateMatch> contentIndex = new LinkedHashMap<>();
    }

    private record DuplicateMatch(Long itemId, String title, Long categoryId, String categoryName) {
    }
}
