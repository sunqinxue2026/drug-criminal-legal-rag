package com.gjq.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gjq.dto.OfficialCaseSyncResponse;
import com.gjq.entity.Category;
import com.gjq.entity.Item;
import com.gjq.entity.UserBehavior;
import com.gjq.mapper.UserBehaviorMapper;
import com.gjq.service.CategoryService;
import com.gjq.service.ItemService;
import com.gjq.service.OfficialCaseSyncService;
import com.gjq.utils.DrugVerticalDomainSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class OfficialCaseSyncServiceImpl implements OfficialCaseSyncService {

    private static final int SUMMARY_LIMIT = 180;
    private static final Set<String> EXCLUDED_CASE_FILES = Set.of(
            "guiding_case_248_illegal_drug_purchase_meritorious_service.json"
    );

    private final ItemService itemService;
    private final CategoryService categoryService;
    private final UserBehaviorMapper userBehaviorMapper;

    public OfficialCaseSyncServiceImpl(
            ItemService itemService,
            CategoryService categoryService,
            UserBehaviorMapper userBehaviorMapper
    ) {
        this.itemService = itemService;
        this.categoryService = categoryService;
        this.userBehaviorMapper = userBehaviorMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OfficialCaseSyncResponse syncOfficialCases() {
        OfficialCaseSyncResponse response = new OfficialCaseSyncResponse();
        List<Category> categories = categoryService.getAllCategories();
        List<OfficialCaseRecord> officialRecords = loadOfficialCaseRecords(categories);
        response.setTotalRecords(officialRecords.size());

        List<Item> existingItems = itemService.list(new LambdaQueryWrapper<Item>()
                .eq(Item::getStatus, 1)
                .orderByAsc(Item::getId));
        Map<String, Item> existingByTitle = existingItems.stream()
                .filter(item -> StringUtils.hasText(item.getTitle()))
                .collect(Collectors.toMap(
                        item -> normalizeTitle(item.getTitle()),
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Set<String> officialTitleKeys = officialRecords.stream()
                .map(record -> normalizeTitle(record.title()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Item> obsoleteItems = existingItems.stream()
                .filter(item -> !officialTitleKeys.contains(normalizeTitle(item.getTitle())))
                .collect(Collectors.toList());
        if (!obsoleteItems.isEmpty()) {
            List<Long> obsoleteIds = obsoleteItems.stream()
                    .map(Item::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!obsoleteIds.isEmpty()) {
                response.setCleanedBehaviorCount(userBehaviorMapper.delete(new LambdaQueryWrapper<UserBehavior>()
                        .eq(UserBehavior::getTargetType, "item")
                        .in(UserBehavior::getTargetId, obsoleteIds)));
                itemService.removeByIds(obsoleteIds);
                response.setDeletedCount(obsoleteIds.size());
                for (Item obsolete : obsoleteItems) {
                    response.getResults().add(buildResult(obsolete.getTitle(), "deleted", null, "已删除非官方案例样本"));
                }
            }
        }

        for (OfficialCaseRecord record : officialRecords) {
            Item current = existingByTitle.get(normalizeTitle(record.title()));
            if (current == null) {
                Item created = buildItem(record, null);
                itemService.save(created);
                response.setCreatedCount(response.getCreatedCount() + 1);
                response.getResults().add(buildResult(record.title(), "created", record.sourceFileName(), record.categoryName()));
                continue;
            }

            Item updated = buildItem(record, current);
            if (isSameItem(current, updated)) {
                response.setSkippedCount(response.getSkippedCount() + 1);
                response.getResults().add(buildResult(record.title(), "kept", record.sourceFileName(), record.categoryName()));
                continue;
            }

            updated.setId(current.getId());
            updated.setViewCount(current.getViewCount());
            updated.setLikeCount(current.getLikeCount());
            updated.setFavoriteCount(current.getFavoriteCount());
            updated.setStatus(current.getStatus() == null ? 1 : current.getStatus());
            itemService.updateById(updated);
            response.setUpdatedCount(response.getUpdatedCount() + 1);
            response.getResults().add(buildResult(record.title(), "updated", record.sourceFileName(), record.categoryName()));
        }

        response.setMessage(String.format(
                Locale.ROOT,
                "官方案例同步完成：新增 %d 条，更新 %d 条，删除 %d 条，保留 %d 条",
                response.getCreatedCount(),
                response.getUpdatedCount(),
                response.getDeletedCount(),
                response.getSkippedCount()
        ));
        return response;
    }

    private OfficialCaseSyncResponse.SyncItemResult buildResult(String title, String action, String sourceFileName, String categoryName) {
        OfficialCaseSyncResponse.SyncItemResult result = new OfficialCaseSyncResponse.SyncItemResult();
        result.setTitle(title);
        result.setAction(action);
        result.setSourceFileName(sourceFileName);
        result.setCategoryName(categoryName);
        return result;
    }

    private List<OfficialCaseRecord> loadOfficialCaseRecords(List<Category> categories) {
        Path directory = resolveProjectRoot()
                .resolve("data")
                .resolve("legal_sources")
                .resolve("drug_vertical")
                .resolve("processed")
                .resolve("guiding_cases");
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("未找到官方案例语料目录: " + directory);
        }

        Map<String, OfficialCaseRecord> deduplicated = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !EXCLUDED_CASE_FILES.contains(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());

            for (Path path : files) {
                JSONObject payload = JSON.parseObject(Files.readString(path, StandardCharsets.UTF_8));
                JSONArray entries = payload.getJSONArray("entries");
                if (entries == null || entries.isEmpty()) {
                    continue;
                }
                for (Object rawEntry : entries) {
                    JSONObject entry = rawEntry instanceof JSONObject
                            ? (JSONObject) rawEntry
                            : JSON.parseObject(JSON.toJSONString(rawEntry));
                    OfficialCaseRecord record = buildOfficialCaseRecord(path.getFileName().toString(), payload, entry, categories);
                    if (record == null) {
                        continue;
                    }
                    deduplicated.putIfAbsent(normalizeTitle(record.title()), record);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取官方案例语料失败: " + ex.getMessage(), ex);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private OfficialCaseRecord buildOfficialCaseRecord(
            String sourceFileName,
            JSONObject payload,
            JSONObject entry,
            List<Category> categories
    ) {
        String title = firstText(entry.getString("articleTitle"), entry.getString("title"));
        String summary = firstText(entry.getString("summary"), entry.getString("content"), title);
        String body = firstText(entry.getString("content"), entry.getString("displayText"), summary, title);
        String topic = firstText(entry.getString("topic"), payload.getString("topic"));
        String businessDomain = firstText(entry.getString("businessDomain"), payload.getString("businessDomain"));
        String articleNumber = firstText(entry.getString("articleNumber"));
        String sourceName = firstText(entry.getString("sourceName"), payload.getString("sourceName"), entry.getString("issuer"), payload.getString("issuer"), "官方公开案例");
        String sourceUrl = firstText(entry.getString("sourceUrl"), payload.getString("sourceUrl"));
        String issuer = firstText(entry.getString("issuer"), payload.getString("issuer"), sourceName);
        String effectiveDate = firstText(entry.getString("effectiveDate"), payload.getString("effectiveDate"));
        String knowledgeType = firstText(entry.getString("knowledgeType"), payload.getString("knowledgeType"), "典型案例");

        String corpus = String.join("\n",
                firstText(title),
                firstText(summary),
                firstText(body),
                firstText(topic),
                firstText(businessDomain));
        if (!StringUtils.hasText(title) || !DrugVerticalDomainSupport.containsTopicalTerm(corpus)) {
            return null;
        }

        Long categoryId = DrugVerticalDomainSupport.matchCategoryId(categories, title, summary, body, topic, businessDomain);
        String categoryName = categories.stream()
                .filter(category -> Objects.equals(category.getId(), categoryId))
                .map(Category::getName)
                .findFirst()
                .orElse(DrugVerticalDomainSupport.DEFAULT_CATEGORY_NAME);

        String content = buildOfficialContent(sourceName, sourceUrl, issuer, effectiveDate, topic, articleNumber, body);
        String tags = buildTags(categoryName, articleNumber, topic, knowledgeType, entry.get("keywords"));

        return new OfficialCaseRecord(
                title,
                ellipsize(summary, SUMMARY_LIMIT),
                content,
                categoryId,
                categoryName,
                tags,
                sourceFileName
        );
    }

    private Item buildItem(OfficialCaseRecord record, Item existing) {
        Item item = new Item();
        item.setTitle(record.title());
        item.setSummary(record.summary());
        item.setContent(record.content());
        item.setCategoryId(record.categoryId());
        item.setTags(record.tags());
        item.setStatus(1);
        if (existing == null) {
            item.setViewCount(0);
            item.setLikeCount(0);
            item.setFavoriteCount(0);
        }
        return item;
    }

    private boolean isSameItem(Item left, Item right) {
        return Objects.equals(left.getTitle(), right.getTitle())
                && Objects.equals(left.getSummary(), right.getSummary())
                && Objects.equals(left.getContent(), right.getContent())
                && Objects.equals(left.getCategoryId(), right.getCategoryId())
                && Objects.equals(left.getTags(), right.getTags())
                && Objects.equals(left.getStatus(), right.getStatus());
    }

    private String buildOfficialContent(
            String sourceName,
            String sourceUrl,
            String issuer,
            String effectiveDate,
            String topic,
            String articleNumber,
            String body
    ) {
        List<String> lines = new ArrayList<>();
        addLine(lines, "官方来源：" + sourceName);
        addLine(lines, StringUtils.hasText(sourceUrl) ? "来源链接：" + sourceUrl : null);
        addLine(lines, StringUtils.hasText(issuer) ? "发布机关：" + issuer : null);
        addLine(lines, StringUtils.hasText(effectiveDate) ? "发布日期：" + effectiveDate : null);
        addLine(lines, StringUtils.hasText(topic) ? "专题主题：" + topic : null);
        addLine(lines, StringUtils.hasText(articleNumber) ? "案例编号：" + articleNumber : null);
        addLine(lines, null);
        addLine(lines, firstText(body, "暂无正文摘要"));
        return String.join("\n", lines).trim();
    }

    private String buildTags(String categoryName, String articleNumber, String topic, String knowledgeType, Object rawKeywords) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addTag(tags, categoryName);
        addTag(tags, knowledgeType);
        addTag(tags, articleNumber);
        addTag(tags, topic);
        if (rawKeywords instanceof JSONArray keywords) {
            for (Object keyword : keywords) {
                addTag(tags, Objects.toString(keyword, null));
                if (tags.size() >= 6) {
                    break;
                }
            }
        } else if (rawKeywords instanceof Collection<?> keywords) {
            for (Object keyword : keywords) {
                addTag(tags, Objects.toString(keyword, null));
                if (tags.size() >= 6) {
                    break;
                }
            }
        } else if (rawKeywords instanceof String keywordString) {
            for (String keyword : keywordString.split("[,，、\\s]+")) {
                addTag(tags, keyword);
                if (tags.size() >= 6) {
                    break;
                }
            }
        }
        return String.join(",", tags);
    }

    private void addTag(Set<String> tags, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String normalized = value.trim();
        if (normalized.length() > 20) {
            return;
        }
        tags.add(normalized);
    }

    private void addLine(List<String> lines, String value) {
        if (value == null) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
            return;
        }
        if (StringUtils.hasText(value)) {
            lines.add(value.trim());
        }
    }

    private String ellipsize(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return firstText(text);
        }
        return text.substring(0, maxLength) + "...";
    }

    private String normalizeTitle(String title) {
        return firstText(title).replaceAll("\\s+", "");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private Path resolveProjectRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("data").resolve("legal_sources"))) {
            return current;
        }
        if (current.getParent() != null && Files.isDirectory(current.getParent().resolve("data").resolve("legal_sources"))) {
            return current.getParent();
        }
        return current;
    }

    private record OfficialCaseRecord(
            String title,
            String summary,
            String content,
            Long categoryId,
            String categoryName,
            String tags,
            String sourceFileName
    ) {
    }
}
