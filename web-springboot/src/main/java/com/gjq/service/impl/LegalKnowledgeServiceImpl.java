package com.gjq.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gjq.client.AlgorithmClient;
import com.gjq.dto.LegalKnowledgeImportResponse;
import com.gjq.dto.LegalKnowledgeImportResult;
import com.gjq.entity.Category;
import com.gjq.entity.LegalKnowledge;
import com.gjq.entity.LegalKnowledgeChunk;
import com.gjq.mapper.LegalKnowledgeChunkMapper;
import com.gjq.mapper.LegalKnowledgeMapper;
import com.gjq.service.CategoryService;
import com.gjq.service.LegalKnowledgeService;
import com.gjq.service.support.LegalDocumentTextProcessor;
import com.gjq.utils.DrugVerticalDomainSupport;
import com.gjq.vo.LegalKnowledgeDetailVO;
import com.gjq.vo.LegalKnowledgeIndexChunkVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LegalKnowledgeServiceImpl extends ServiceImpl<LegalKnowledgeMapper, LegalKnowledge> implements LegalKnowledgeService {

    private static final Pattern MULTI_SPACE = Pattern.compile("[\\t\\x0B\\f\\r ]+");
    private static final int SUMMARY_LIMIT = 200;
    private static final int CHUNK_SIZE = 420;
    private static final int CHUNK_OVERLAP = 40;
    private static final Set<String> OFFICIAL_KNOWLEDGE_EXCLUDED_FILES = Set.of(
            "guiding_case_248_illegal_drug_purchase_meritorious_service.html"
    );

    private final LegalKnowledgeChunkMapper chunkMapper;
    private final CategoryService categoryService;
    private final AlgorithmClient algorithmClient;
    private final LegalDocumentTextProcessor legalDocumentTextProcessor;

    public LegalKnowledgeServiceImpl(LegalKnowledgeChunkMapper chunkMapper,
                                     CategoryService categoryService,
                                     AlgorithmClient algorithmClient,
                                     LegalDocumentTextProcessor legalDocumentTextProcessor) {
        this.chunkMapper = chunkMapper;
        this.categoryService = categoryService;
        this.algorithmClient = algorithmClient;
        this.legalDocumentTextProcessor = legalDocumentTextProcessor;
    }

    @Override
    public Page<LegalKnowledge> getKnowledgePage(int pageNum, int pageSize, Long categoryId, String keyword,
                                                 String knowledgeType, String businessDomain) {
        Page<LegalKnowledge> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<LegalKnowledge> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq(LegalKnowledge::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(query -> query
                    .like(LegalKnowledge::getTitle, trimmed)
                    .or().like(LegalKnowledge::getDocumentName, trimmed)
                    .or().like(LegalKnowledge::getArticleNumber, trimmed)
                    .or().like(LegalKnowledge::getKeywords, trimmed)
                    .or().like(LegalKnowledge::getRetrievalText, trimmed));
        }
        if (StringUtils.hasText(knowledgeType)) {
            wrapper.eq(LegalKnowledge::getKnowledgeType, knowledgeType.trim());
        }
        if (StringUtils.hasText(businessDomain)) {
            wrapper.like(LegalKnowledge::getBusinessDomain, businessDomain.trim());
        }
        wrapper.eq(LegalKnowledge::getStatus, 1)
                .orderByDesc(LegalKnowledge::getEffectiveDate)
                .orderByDesc(LegalKnowledge::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    public LegalKnowledgeDetailVO getKnowledgeDetail(Long id) {
        LegalKnowledge knowledge = this.getById(id);
        if (knowledge == null) {
            return null;
        }
        List<LegalKnowledgeChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<LegalKnowledgeChunk>()
                .eq(LegalKnowledgeChunk::getKnowledgeId, id)
                .orderByAsc(LegalKnowledgeChunk::getSectionOrder)
                .orderByAsc(LegalKnowledgeChunk::getChunkIndex));
        return buildDetailVO(knowledge, chunks);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createKnowledge(LegalKnowledge knowledge, boolean rebuildIndex) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (knowledge == null) {
            response.put("success", false);
            response.put("message", "法律知识记录不能为空");
            return response;
        }

        prepareManualKnowledge(knowledge);
        this.save(knowledge);
        replaceKnowledgeChunks(knowledge);
        return buildKnowledgeWriteResponse(knowledge.getId(), true, rebuildIndex);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateKnowledge(LegalKnowledge knowledge, boolean rebuildIndex) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (knowledge == null || knowledge.getId() == null) {
            response.put("success", false);
            response.put("message", "缺少要更新的法律知识 ID");
            return response;
        }

        LegalKnowledge existing = this.getById(knowledge.getId());
        if (existing == null) {
            response.put("success", false);
            response.put("message", "法律知识不存在或已被删除");
            return response;
        }

        mergeManualKnowledge(existing, knowledge);
        prepareManualKnowledge(existing);
        this.updateById(existing);
        replaceKnowledgeChunks(existing);
        return buildKnowledgeWriteResponse(existing.getId(), false, rebuildIndex);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteKnowledge(Long id, boolean rebuildIndex) {
        Map<String, Object> response = new LinkedHashMap<>();
        LegalKnowledge knowledge = this.getById(id);
        if (knowledge == null) {
            response.put("success", false);
            response.put("message", "\u89c4\u8303\u77e5\u8bc6\u4e0d\u5b58\u5728\u6216\u5df2\u88ab\u5220\u9664");
            return response;
        }

        chunkMapper.delete(new LambdaQueryWrapper<LegalKnowledgeChunk>()
                .eq(LegalKnowledgeChunk::getKnowledgeId, id));
        boolean removed = this.removeById(id);
        if (!removed) {
            response.put("success", false);
            response.put("message", "\u89c4\u8303\u77e5\u8bc6\u5220\u9664\u5931\u8d25");
            return response;
        }

        response.put("success", true);
        response.put("knowledgeId", id);
        response.put("reindexed", false);
        response.put("message", "\u89c4\u8303\u77e5\u8bc6\u5df2\u5220\u9664");

        if (rebuildIndex) {
            Map<String, Object> rebuildInfo = rebuildLegalIndex();
            boolean reindexed = Boolean.TRUE.equals(rebuildInfo.get("success"));
            response.put("reindexed", reindexed);
            response.put("indexMessage", rebuildInfo.get("message"));
            response.put("message", reindexed
                    ? "\u89c4\u8303\u77e5\u8bc6\u5df2\u5220\u9664\uff0c\u4e13\u9898\u7d22\u5f15\u5df2\u5237\u65b0"
                    : "\u89c4\u8303\u77e5\u8bc6\u5df2\u5220\u9664\uff0c\u4f46\u4e13\u9898\u7d22\u5f15\u5237\u65b0\u5931\u8d25");
        }

        return response;
    }

    private LegalKnowledgeDetailVO buildDetailVO(LegalKnowledge knowledge, List<LegalKnowledgeChunk> chunks) {
        LegalKnowledgeDetailVO detailVO = new LegalKnowledgeDetailVO();
        detailVO.setKnowledge(knowledge);
        if (!CollectionUtils.isEmpty(chunks)) {
            detailVO.setChunks(chunks);
        }
        return detailVO;
    }

    @Override
    public LegalKnowledgeImportResponse importJsonFile(MultipartFile file, Long categoryId, boolean rebuildIndex) {
        LegalKnowledgeImportResponse response = new LegalKnowledgeImportResponse();
        if (file == null || file.isEmpty()) {
            response.setIndexMessage("未选择任何规范知识文件");
            return response;
        }

        try {
            String jsonText = decodeUtf8(file.getBytes());
            List<JSONObject> records = flattenRecords(jsonText);
            response.setTotalRecords(records.size());
            List<Category> categories = categoryService.getAllCategories();

            for (JSONObject record : records) {
                LegalKnowledgeImportResult result = importSingleRecord(record, file.getOriginalFilename(), categoryId, categories);
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
                        response.isReindexed() ? "规范知识已入库，检索索引已更新" : "规范知识已入库，但索引刷新失败"
                ));
            } else if (response.getImportedCount() > 0) {
                response.setIndexMessage("规范知识已入库，未执行索引刷新");
            } else {
                response.setIndexMessage("没有成功导入的规范知识，未触发索引刷新");
            }
        } catch (Exception ex) {
            response.setIndexMessage("规范知识导入失败: " + ex.getMessage());
        }
        return response;
    }

    @Override
    public LegalKnowledgeImportResponse importOfficialKnowledge(Long categoryId, boolean rebuildIndex) {
        LegalKnowledgeImportResponse response = new LegalKnowledgeImportResponse();
        List<Path> files = resolveOfficialKnowledgeFiles();
        response.setTotalRecords(files.size());
        if (files.isEmpty()) {
            response.setIndexMessage("未发现可同步的官方知识内容");
            return response;
        }

        List<Category> categories = categoryService.getAllCategories();
        for (Path filePath : files) {
            LegalKnowledgeImportResult result = importOfficialKnowledgeSource(filePath, categoryId, categories);
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
                    response.isReindexed() ? "官方知识内容已同步，索引已刷新" : "官方知识内容已同步，但索引刷新失败"
            ));
        } else if (response.getImportedCount() > 0) {
            response.setIndexMessage("官方知识内容已同步，本次未执行索引刷新");
        } else if (response.getSkippedCount() > 0) {
            response.setIndexMessage("官方知识内容已存在，本次仅跳过重复记录");
        } else {
            response.setIndexMessage("未成功同步任何官方知识内容");
        }
        return response;
    }

    private Map<String, Object> buildKnowledgeWriteResponse(Long knowledgeId, boolean created, boolean rebuildIndex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("knowledgeId", knowledgeId);
        response.put("reindexed", false);
        response.put("message", created ? "法律知识创建成功" : "法律知识更新成功");

        if (rebuildIndex) {
            Map<String, Object> rebuildInfo = rebuildLegalIndex();
            boolean reindexed = Boolean.TRUE.equals(rebuildInfo.get("success"));
            response.put("reindexed", reindexed);
            response.put("indexMessage", rebuildInfo.get("message"));
            response.put("message", reindexed
                    ? (created ? "法律知识创建成功，专题索引已刷新" : "法律知识更新成功，专题索引已刷新")
                    : (created ? "法律知识创建成功，但专题索引刷新失败" : "法律知识更新成功，但专题索引刷新失败"));
        }

        return response;
    }

    private void mergeManualKnowledge(LegalKnowledge target, LegalKnowledge source) {
        target.setSourceType(source.getSourceType());
        target.setSourceName(source.getSourceName());
        target.setSourceUrl(source.getSourceUrl());
        target.setSourceReliability(source.getSourceReliability());
        target.setSourceFileName(source.getSourceFileName());
        target.setTitle(source.getTitle());
        target.setKnowledgeType(source.getKnowledgeType());
        target.setKnowledgeSubtype(source.getKnowledgeSubtype());
        target.setDocumentName(source.getDocumentName());
        target.setDocumentLevel(source.getDocumentLevel());
        target.setIssuer(source.getIssuer());
        target.setCategoryId(source.getCategoryId());
        target.setBusinessDomain(source.getBusinessDomain());
        target.setTopic(source.getTopic());
        target.setChapterTitle(source.getChapterTitle());
        target.setSectionTitle(source.getSectionTitle());
        target.setArticleNumber(source.getArticleNumber());
        target.setArticleTitle(source.getArticleTitle());
        target.setKeywords(source.getKeywords());
        target.setAliases(source.getAliases());
        target.setSummary(source.getSummary());
        target.setDisplayText(source.getDisplayText());
        target.setRetrievalText(source.getRetrievalText());
        target.setEffectiveDate(source.getEffectiveDate());
        target.setExpiryDate(source.getExpiryDate());
        target.setCurrentStatus(source.getCurrentStatus());
        target.setParserVersion(source.getParserVersion());
        target.setParseStatus(source.getParseStatus());
        target.setExtractConfidence(source.getExtractConfidence());
        target.setStatus(source.getStatus());
    }

    private void prepareManualKnowledge(LegalKnowledge knowledge) {
        knowledge.setSourceType(normalizeOptionalText(firstNonBlank(knowledge.getSourceType(), "manual_entry")));
        knowledge.setSourceName(normalizeOptionalText(firstNonBlank(knowledge.getSourceName(), "手工录入")));
        knowledge.setSourceUrl(normalizeOptionalText(knowledge.getSourceUrl()));
        knowledge.setSourceFileName(normalizeOptionalText(knowledge.getSourceFileName()));
        knowledge.setDocumentName(normalizeOptionalText(knowledge.getDocumentName()));
        knowledge.setDocumentLevel(normalizeOptionalText(knowledge.getDocumentLevel()));
        knowledge.setIssuer(normalizeOptionalText(knowledge.getIssuer()));
        knowledge.setKnowledgeType(normalizeOptionalText(firstNonBlank(knowledge.getKnowledgeType(), "规范知识")));
        knowledge.setKnowledgeSubtype(normalizeOptionalText(knowledge.getKnowledgeSubtype()));
        knowledge.setBusinessDomain(normalizeOptionalText(knowledge.getBusinessDomain()));
        knowledge.setTopic(normalizeOptionalText(knowledge.getTopic()));
        knowledge.setChapterTitle(normalizeOptionalText(knowledge.getChapterTitle()));
        knowledge.setSectionTitle(normalizeOptionalText(knowledge.getSectionTitle()));
        knowledge.setArticleNumber(normalizeOptionalText(knowledge.getArticleNumber()));
        knowledge.setArticleTitle(normalizeOptionalText(knowledge.getArticleTitle()));
        knowledge.setKeywords(normalizeOptionalTags(knowledge.getKeywords()));
        knowledge.setAliases(normalizeOptionalTags(knowledge.getAliases()));
        knowledge.setCurrentStatus(normalizeOptionalText(firstNonBlank(knowledge.getCurrentStatus(), "现行有效")));

        String displayText = normalizeText(firstNonBlank(
                knowledge.getDisplayText(),
                knowledge.getRetrievalText(),
                knowledge.getSummary()
        ));
        if (!StringUtils.hasText(displayText)) {
            throw new IllegalArgumentException("请至少填写正文内容或摘要");
        }
        knowledge.setDisplayText(displayText);

        String title = normalizeOptionalText(firstNonBlank(
                knowledge.getTitle(),
                buildTitle(knowledge.getDocumentName(), knowledge.getArticleNumber(), knowledge.getArticleTitle())
        ));
        knowledge.setTitle(title);
        knowledge.setTitleNormalized(normalizeTitle(title));
        knowledge.setSummary(normalizeOptionalText(firstNonBlank(
                knowledge.getSummary(),
                buildSummary(knowledge.getArticleTitle(), displayText)
        )));
        knowledge.setRetrievalText(buildRetrievalText(knowledge, displayText));
        knowledge.setContentSha256(sha256Hex(String.join("\n",
                firstNonBlank(knowledge.getTitle(), ""),
                firstNonBlank(knowledge.getArticleNumber(), ""),
                displayText
        )));

        if (knowledge.getSourceReliability() == null) {
            knowledge.setSourceReliability(95);
        }
        if (!StringUtils.hasText(knowledge.getParserVersion())) {
            knowledge.setParserVersion("legal-knowledge-manual-v1");
        }
        if (knowledge.getParseStatus() == null) {
            knowledge.setParseStatus(1);
        }
        if (knowledge.getExtractConfidence() == null) {
            knowledge.setExtractConfidence(BigDecimal.valueOf(0.95));
        }
        if (knowledge.getStatus() == null) {
            knowledge.setStatus(1);
        }
    }

    private void replaceKnowledgeChunks(LegalKnowledge knowledge) {
        replaceKnowledgeChunks(knowledge, buildChunks(knowledge));
    }

    private void replaceKnowledgeChunks(LegalKnowledge knowledge, List<LegalKnowledgeChunk> chunks) {
        chunkMapper.delete(new LambdaQueryWrapper<LegalKnowledgeChunk>()
                .eq(LegalKnowledgeChunk::getKnowledgeId, knowledge.getId()));

        for (LegalKnowledgeChunk chunk : chunks) {
            chunk.setKnowledgeId(knowledge.getId());
            chunkMapper.insert(chunk);
        }
    }

    @Override
    public LegalKnowledgeDetailVO previewRecord(Map<String, Object> record, String sourceFileName, Long categoryId) {
        try {
            JSONObject jsonObject = asJsonObject(record);
            if (jsonObject == null) {
                return null;
            }
            List<Category> categories = categoryService.getAllCategories();
            LegalKnowledge knowledge = buildKnowledge(jsonObject, firstNonBlank(sourceFileName, "规范知识预览"), categoryId, categories);
            if (knowledge == null) {
                return null;
            }
            return buildDetailVO(knowledge, buildChunks(knowledge));
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public LegalKnowledgeImportResult importRecord(Map<String, Object> record, String sourceFileName, Long categoryId) {
        JSONObject jsonObject = asJsonObject(record);
        if (jsonObject == null) {
            LegalKnowledgeImportResult result = new LegalKnowledgeImportResult();
            result.setSuccess(false);
            result.setSourceName(sourceFileName);
            result.setMessage("记录格式无效");
            return result;
        }
        return importSingleRecord(jsonObject, firstNonBlank(sourceFileName, "规范知识导入"), categoryId, categoryService.getAllCategories());
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
                    asText(rebuildResult.get("message"), success ? "规范知识索引已更新" : "规范知识索引刷新失败")
            ));
            return response;
        } catch (Exception ex) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("message", "规范知识索引刷新失败: " + ex.getMessage());
            return response;
        }
    }

    @Override
    public Page<LegalKnowledgeIndexChunkVO> getIndexChunkPage(int pageNum, int pageSize) {
        Page<LegalKnowledgeChunk> chunkPage = new Page<>(pageNum, pageSize);
        chunkMapper.selectPage(chunkPage, new LambdaQueryWrapper<LegalKnowledgeChunk>()
                .eq(LegalKnowledgeChunk::getDeleted, 0)
                .orderByAsc(LegalKnowledgeChunk::getKnowledgeId)
                .orderByAsc(LegalKnowledgeChunk::getSectionOrder)
                .orderByAsc(LegalKnowledgeChunk::getChunkIndex));

        Set<Long> knowledgeIds = chunkPage.getRecords().stream()
                .map(LegalKnowledgeChunk::getKnowledgeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, LegalKnowledge> knowledgeMap = loadKnowledgeMap(knowledgeIds);
        Map<Long, String> categoryMap = loadCategoryNameMap(knowledgeMap.values());

        Page<LegalKnowledgeIndexChunkVO> resultPage = new Page<>(pageNum, pageSize, chunkPage.getTotal());
        List<LegalKnowledgeIndexChunkVO> records = new ArrayList<>();
        for (LegalKnowledgeChunk chunk : chunkPage.getRecords()) {
            LegalKnowledge knowledge = knowledgeMap.get(chunk.getKnowledgeId());
            if (knowledge == null) {
                continue;
            }
            LegalKnowledgeIndexChunkVO vo = new LegalKnowledgeIndexChunkVO();
            vo.setChunkId(chunk.getId());
            vo.setKnowledgeId(knowledge.getId());
            vo.setDocumentId(knowledge.getId());
            vo.setItemId(knowledge.getId());
            vo.setChunkIndex(chunk.getChunkIndex());
            vo.setTitle(knowledge.getTitle());
            vo.setSummary(firstNonBlank(knowledge.getSummary(), knowledge.getArticleTitle()));
            vo.setCategoryId(knowledge.getCategoryId());
            vo.setCategoryName(knowledge.getCategoryId() == null ? null : categoryMap.get(knowledge.getCategoryId()));
            vo.setTags(buildIndexTags(knowledge));
            vo.setDocumentType(knowledge.getKnowledgeType());
            vo.setDocumentSubtype(firstNonBlank(knowledge.getArticleNumber(), knowledge.getKnowledgeSubtype()));
            vo.setCaseType("规范");
            vo.setCause(firstNonBlank(knowledge.getTopic(), knowledge.getArticleTitle()));
            vo.setBusinessDomain(knowledge.getBusinessDomain());
            vo.setProcedureDomain("");
            vo.setCourtName(knowledge.getIssuer());
            vo.setCourtLevel("");
            vo.setDecisionDate(knowledge.getEffectiveDate());
            vo.setDecisionResult(knowledge.getCurrentStatus());
            vo.setKeywords(knowledge.getKeywords());
            vo.setAliases(knowledge.getAliases());
            vo.setIssueTags("");
            vo.setResultTags("");
            vo.setSourceReliability(knowledge.getSourceReliability());
            vo.setSource("legal_knowledge_db");
            vo.setSourceName(knowledge.getSourceName());
            vo.setSourceUrl(knowledge.getSourceUrl());
            vo.setIssuer(knowledge.getIssuer());
            vo.setEffectiveDate(knowledge.getEffectiveDate());
            vo.setCurrentStatus(knowledge.getCurrentStatus());
            vo.setSectionType(chunk.getSectionType());
            vo.setSectionTitle(chunk.getSectionTitle());
            vo.setHeaderPath(chunk.getHeaderPath());
            vo.setRetrievalRole(chunk.getRetrievalRole());
            vo.setSectionWeight(chunk.getSectionWeight());
            vo.setChunkText(chunk.getChunkText());
            vo.setRetrievalText(chunk.getRetrievalText());
            records.add(vo);
        }
        resultPage.setRecords(records);
        resultPage.setPages(chunkPage.getPages());
        return resultPage;
    }

    @Transactional(rollbackFor = Exception.class)
    protected LegalKnowledgeImportResult importSingleRecord(JSONObject record, String sourceFileName,
                                                            Long categoryId, List<Category> categories) {
        LegalKnowledgeImportResult result = new LegalKnowledgeImportResult();
        result.setSourceName(sourceFileName);

        try {
            LegalKnowledge knowledge = buildKnowledge(record, sourceFileName, categoryId, categories);
            if (knowledge == null || !StringUtils.hasText(knowledge.getDisplayText())) {
                result.setSuccess(false);
                result.setMessage("记录缺少有效正文，已跳过");
                return result;
            }

            LegalKnowledge duplicate = findDuplicateKnowledge(knowledge, List.of());

            if (duplicate != null) {
                result.setSuccess(false);
                result.setSkipped(true);
                result.setDuplicate(true);
                result.setExistingKnowledgeId(duplicate.getId());
                result.setKnowledgeId(duplicate.getId());
                result.setTitle(duplicate.getTitle());
                result.setArticleNumber(duplicate.getArticleNumber());
                result.setMessage("检测到重复规范条文，已跳过");
                return result;
            }

            this.save(knowledge);
            List<LegalKnowledgeChunk> chunks = buildChunks(knowledge);
            replaceKnowledgeChunks(knowledge, chunks);

            result.setSuccess(true);
            result.setKnowledgeId(knowledge.getId());
            result.setTitle(knowledge.getTitle());
            result.setArticleNumber(knowledge.getArticleNumber());
            result.setCategoryId(knowledge.getCategoryId());
            result.setCategoryName(resolveCategoryName(knowledge.getCategoryId(), categories));
            result.setCharCount(knowledge.getDisplayText() == null ? 0 : knowledge.getDisplayText().length());
            result.setChunkCount(chunks.size());
            result.setMessage("导入成功");
            return result;
        } catch (Exception ex) {
            result.setSuccess(false);
            result.setMessage("导入失败: " + ex.getMessage());
            return result;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    protected LegalKnowledgeImportResult importOrReplaceOfficialKnowledgeRecord(JSONObject record, String sourceFileName,
                                                                               Long categoryId, List<Category> categories) {
        LegalKnowledgeImportResult result = new LegalKnowledgeImportResult();
        result.setSourceName(sourceFileName);

        try {
            LegalKnowledge knowledge = buildKnowledge(record, sourceFileName, categoryId, categories);
            if (knowledge == null || !StringUtils.hasText(knowledge.getDisplayText())) {
                result.setSuccess(false);
                result.setMessage("未提取到可用正文，已跳过");
                return result;
            }

            List<LegalKnowledge> existingSameSource = this.list(new LambdaQueryWrapper<LegalKnowledge>()
                    .eq(LegalKnowledge::getSourceType, "official_crawl")
                    .eq(LegalKnowledge::getSourceFileName, sourceFileName)
                    .orderByAsc(LegalKnowledge::getId));
            boolean replacingExisting = !CollectionUtils.isEmpty(existingSameSource);

            if (!replacingExisting) {
                LegalKnowledge duplicate = findDuplicateKnowledge(knowledge, List.of());
                if (duplicate != null) {
                    markDuplicateKnowledgeResult(result, duplicate);
                    return result;
                }
                this.save(knowledge);
            } else {
                LegalKnowledge preserved = existingSameSource.get(0);
                List<Long> redundantIds = existingSameSource.stream()
                        .skip(1)
                        .map(LegalKnowledge::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                deleteKnowledgeRecords(redundantIds);
                knowledge.setId(preserved.getId());
                this.updateById(knowledge);
            }

            List<LegalKnowledgeChunk> chunks = buildChunks(knowledge);
            replaceKnowledgeChunks(knowledge, chunks);

            result.setSuccess(true);
            result.setKnowledgeId(knowledge.getId());
            result.setTitle(knowledge.getTitle());
            result.setArticleNumber(knowledge.getArticleNumber());
            result.setCategoryId(knowledge.getCategoryId());
            result.setCategoryName(resolveCategoryName(knowledge.getCategoryId(), categories));
            result.setCharCount(knowledge.getDisplayText() == null ? 0 : knowledge.getDisplayText().length());
            result.setChunkCount(chunks.size());
            result.setMessage(replacingExisting ? "官方知识已按来源更新" : "导入成功");
            return result;
        } catch (Exception ex) {
            result.setSuccess(false);
            result.setMessage("导入失败: " + ex.getMessage());
            return result;
        }
    }

    private LegalKnowledge findDuplicateKnowledge(LegalKnowledge knowledge, Collection<Long> excludedIds) {
        LambdaQueryWrapper<LegalKnowledge> shaWrapper = new LambdaQueryWrapper<LegalKnowledge>()
                .eq(LegalKnowledge::getContentSha256, knowledge.getContentSha256());
        if (!CollectionUtils.isEmpty(excludedIds)) {
            shaWrapper.notIn(LegalKnowledge::getId, excludedIds);
        }
        LegalKnowledge duplicate = this.getOne(shaWrapper.last("LIMIT 1"));
        if (duplicate != null) {
            return duplicate;
        }

        if (StringUtils.hasText(knowledge.getTitleNormalized()) && StringUtils.hasText(knowledge.getArticleNumber())) {
            LambdaQueryWrapper<LegalKnowledge> titleWrapper = new LambdaQueryWrapper<LegalKnowledge>()
                    .eq(LegalKnowledge::getTitleNormalized, knowledge.getTitleNormalized())
                    .eq(LegalKnowledge::getArticleNumber, knowledge.getArticleNumber());
            if (!CollectionUtils.isEmpty(excludedIds)) {
                titleWrapper.notIn(LegalKnowledge::getId, excludedIds);
            }
            return this.getOne(titleWrapper.last("LIMIT 1"));
        }
        return null;
    }

    private void markDuplicateKnowledgeResult(LegalKnowledgeImportResult result, LegalKnowledge duplicate) {
        result.setSuccess(false);
        result.setSkipped(true);
        result.setDuplicate(true);
        result.setExistingKnowledgeId(duplicate.getId());
        result.setKnowledgeId(duplicate.getId());
        result.setTitle(duplicate.getTitle());
        result.setArticleNumber(duplicate.getArticleNumber());
        result.setMessage("检测到重复规范知识，已跳过");
    }

    private void deleteKnowledgeRecords(Collection<Long> knowledgeIds) {
        if (CollectionUtils.isEmpty(knowledgeIds)) {
            return;
        }
        chunkMapper.delete(new LambdaQueryWrapper<LegalKnowledgeChunk>()
                .in(LegalKnowledgeChunk::getKnowledgeId, knowledgeIds));
        this.removeByIds(knowledgeIds);
    }

    private LegalKnowledge buildKnowledge(JSONObject record, String sourceFileName,
                                          Long categoryId, List<Category> categories) {
        String documentName = firstNonBlank(
                asText(record.get("documentName"), ""),
                asText(record.get("lawName"), ""),
                asText(record.get("title"), ""),
                asText(record.get("name"), "")
        );
        String articleNumber = firstNonBlank(
                asText(record.get("articleNumber"), ""),
                asText(record.get("articleNo"), ""),
                asText(record.get("clauseNo"), ""),
                asText(record.get("条号"), "")
        );
        String articleTitle = firstNonBlank(
                asText(record.get("articleTitle"), ""),
                asText(record.get("clauseTitle"), ""),
                asText(record.get("小标题"), "")
        );
        String content = normalizeText(firstNonBlank(
                asText(record.get("content"), ""),
                asText(record.get("text"), ""),
                asText(record.get("displayText"), ""),
                asText(record.get("正文"), "")
        ));
        if (!StringUtils.hasText(content)) {
            return null;
        }

        String knowledgeType = detectKnowledgeType(record, documentName, articleTitle, content);
        String businessDomain = firstNonBlank(
                asText(record.get("businessDomain"), ""),
                asText(record.get("domain"), ""),
                asText(record.get("topic"), "")
        );
        String inferredDomain = DrugVerticalDomainSupport.inferCategoryName(documentName, articleTitle, content, businessDomain);
        if (!StringUtils.hasText(businessDomain)) {
            businessDomain = inferredDomain;
        }
        String topic = firstNonBlank(
                asText(record.get("topic"), ""),
                asText(record.get("subject"), ""),
                businessDomain,
                inferredDomain
        );
        Long resolvedCategoryId = categoryId != null ? categoryId : resolveCategoryId(categories, documentName, topic, businessDomain, articleTitle, content);

        LegalKnowledge knowledge = new LegalKnowledge();
        knowledge.setSourceType(firstNonBlank(asText(record.get("sourceType"), ""), "json_import"));
        knowledge.setSourceName(firstNonBlank(asText(record.get("sourceName"), ""), "规范知识导入"));
        knowledge.setSourceUrl(asText(record.get("sourceUrl"), ""));
        knowledge.setSourceReliability(parseInteger(record.get("sourceReliability"), 1));
        knowledge.setSourceFileName(sourceFileName);
        knowledge.setKnowledgeType(knowledgeType);
        knowledge.setKnowledgeSubtype(firstNonBlank(asText(record.get("knowledgeSubtype"), ""), asText(record.get("documentLevel"), ""), articleNumber));
        knowledge.setDocumentName(documentName);
        knowledge.setDocumentLevel(firstNonBlank(asText(record.get("documentLevel"), ""), knowledgeType));
        knowledge.setIssuer(firstNonBlank(asText(record.get("issuer"), ""), asText(record.get("publishOrg"), ""), asText(record.get("发布机关"), "")));
        knowledge.setCategoryId(resolvedCategoryId);
        knowledge.setBusinessDomain(businessDomain);
        knowledge.setTopic(topic);
        knowledge.setChapterTitle(firstNonBlank(asText(record.get("chapterTitle"), ""), asText(record.get("chapter"), "")));
        knowledge.setSectionTitle(firstNonBlank(asText(record.get("sectionTitle"), ""), asText(record.get("section"), "")));
        knowledge.setArticleNumber(articleNumber);
        knowledge.setArticleTitle(articleTitle);

        String title = buildTitle(documentName, articleNumber, articleTitle);
        knowledge.setTitle(title);
        knowledge.setTitleNormalized(normalizeTitle(title));

        List<String> keywordList = new ArrayList<>();
        addIfHasText(keywordList, businessDomain);
        addIfHasText(keywordList, topic);
        addIfHasText(keywordList, documentName);
        addIfHasText(keywordList, articleNumber);
        addIfHasText(keywordList, articleTitle);
        addAll(keywordList, splitTags(record.get("keywords")));
        knowledge.setKeywords(joinDistinct(keywordList));

        List<String> aliasList = new ArrayList<>(splitTags(record.get("aliases")));
        if (content.contains("麻醉药品") || content.contains("精神药品") || content.contains("麻精药品")
                || content.contains("依托咪酯") || content.contains("右美沙芬") || content.contains("笑气")) {
            addIfHasText(aliasList, "麻精药品");
        }
        if (content.contains("贩卖毒品") || content.contains("走私") || content.contains("运输毒品")
                || content.contains("制造毒品") || content.contains("容留他人吸毒")) {
            addIfHasText(aliasList, "毒品犯罪");
        }
        if (content.contains("家属") || content.contains("律师") || content.contains("取保候审")
                || content.contains("会见") || content.contains("证据")) {
            addIfHasText(aliasList, "程序应对");
        }
        knowledge.setAliases(joinDistinct(aliasList));

        knowledge.setSummary(firstNonBlank(asText(record.get("summary"), ""), buildSummary(articleTitle, content)));
        knowledge.setDisplayText(content);
        knowledge.setEffectiveDate(parseDate(firstNonBlank(asText(record.get("effectiveDate"), ""), asText(record.get("publishDate"), ""))));
        knowledge.setExpiryDate(parseDate(asText(record.get("expiryDate"), "")));
        knowledge.setCurrentStatus(firstNonBlank(asText(record.get("currentStatus"), ""), "现行有效"));
        knowledge.setParserVersion("legal-knowledge-v1");
        knowledge.setParseStatus(1);
        knowledge.setExtractConfidence(BigDecimal.valueOf(0.96));
        knowledge.setStatus(1);
        knowledge.setRetrievalText(buildRetrievalText(knowledge, content));
        knowledge.setContentSha256(sha256Hex(String.join("\n", title, articleNumber == null ? "" : articleNumber, content)));
        return knowledge;
    }

    private List<LegalKnowledgeChunk> buildChunks(LegalKnowledge knowledge) {
        List<LegalKnowledgeChunk> chunks = new ArrayList<>();
        List<TextSlice> slices = sliceText(knowledge.getDisplayText(), CHUNK_SIZE, CHUNK_OVERLAP);
        int index = 0;
        for (TextSlice slice : slices) {
            LegalKnowledgeChunk chunk = new LegalKnowledgeChunk();
            chunk.setChunkStrategy("article_semantic");
            chunk.setChunkVersion("v1");
            chunk.setSectionType("law");
            chunk.setSectionTitle(firstNonBlank(knowledge.getArticleNumber(), knowledge.getArticleTitle(), "条文"));
            chunk.setHeaderPath(firstNonBlank(knowledge.getDocumentName(), knowledge.getTitle())
                    + (StringUtils.hasText(knowledge.getArticleNumber()) ? " > " + knowledge.getArticleNumber() : ""));
            chunk.setRetrievalRole("law_basis");
            chunk.setSectionWeight(sectionWeight(knowledge.getKnowledgeType()));
            chunk.setSectionOrder(0);
            chunk.setChunkIndex(index++);
            chunk.setCharStartOffset(slice.start);
            chunk.setCharEndOffset(slice.end);
            chunk.setChunkHash(sha256Hex(knowledge.getTitle() + "|" + slice.text));
            chunk.setChunkText(slice.text);
            chunk.setRetrievalText(buildChunkRetrievalText(knowledge, slice.text));
            chunk.setCharCount(slice.text.length());
            chunk.setTokenCount(null);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("knowledgeType", knowledge.getKnowledgeType());
            metadata.put("documentName", knowledge.getDocumentName());
            metadata.put("articleNumber", knowledge.getArticleNumber());
            metadata.put("businessDomain", knowledge.getBusinessDomain());
            chunk.setMetadataJson(JSON.toJSONString(metadata));
            chunks.add(chunk);
        }
        return chunks;
    }

    private List<TextSlice> sliceText(String text, int chunkSize, int overlap) {
        List<TextSlice> slices = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return slices;
        }
        if (text.length() <= chunkSize) {
            slices.add(new TextSlice(0, text.length(), text));
            return slices;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkSize);
            if (end < text.length()) {
                int split = findSplitPoint(text, start, end);
                if (split > start + 80) {
                    end = split;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (StringUtils.hasText(chunk)) {
                slices.add(new TextSlice(start, end, chunk));
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return slices;
    }

    private int findSplitPoint(String text, int start, int end) {
        int split = text.lastIndexOf("\n\n", end);
        if (split > start) {
            return split + 2;
        }
        split = text.lastIndexOf('\n', end);
        if (split > start) {
            return split + 1;
        }
        split = Math.max(text.lastIndexOf('。', end), text.lastIndexOf('；', end));
        if (split > start) {
            return split + 1;
        }
        split = text.lastIndexOf('，', end);
        if (split > start) {
            return split + 1;
        }
        return end;
    }

    private BigDecimal sectionWeight(String knowledgeType) {
        if (knowledgeType != null && knowledgeType.contains("司法解释")) {
            return BigDecimal.valueOf(1.55);
        }
        if (knowledgeType != null && knowledgeType.contains("法律")) {
            return BigDecimal.valueOf(1.48);
        }
        if (knowledgeType != null && knowledgeType.contains("指导")) {
            return BigDecimal.valueOf(1.30);
        }
        return BigDecimal.valueOf(1.15);
    }

    private String buildRetrievalText(LegalKnowledge knowledge, String content) {
        List<String> parts = new ArrayList<>();
        addLabelled(parts, "标题", knowledge.getTitle());
        addLabelled(parts, "知识类型", knowledge.getKnowledgeType());
        addLabelled(parts, "规范名称", knowledge.getDocumentName());
        addLabelled(parts, "条号", knowledge.getArticleNumber());
        addLabelled(parts, "条文标题", knowledge.getArticleTitle());
        addLabelled(parts, "业务领域", knowledge.getBusinessDomain());
        addLabelled(parts, "主题", knowledge.getTopic());
        addLabelled(parts, "发布机关", knowledge.getIssuer());
        addLabelled(parts, "生效日期", knowledge.getEffectiveDate() == null ? null : knowledge.getEffectiveDate().toString());
        addLabelled(parts, "摘要", knowledge.getSummary());
        addLabelled(parts, "关键词", knowledge.getKeywords());
        parts.add(content);
        return String.join("\n", parts);
    }

    private String buildChunkRetrievalText(LegalKnowledge knowledge, String chunkText) {
        List<String> parts = new ArrayList<>();
        addLabelled(parts, "标题", knowledge.getTitle());
        addLabelled(parts, "知识类型", knowledge.getKnowledgeType());
        addLabelled(parts, "规范名称", knowledge.getDocumentName());
        addLabelled(parts, "条号", knowledge.getArticleNumber());
        addLabelled(parts, "业务领域", knowledge.getBusinessDomain());
        addLabelled(parts, "主题", knowledge.getTopic());
        addLabelled(parts, "关键词", knowledge.getKeywords());
        parts.add(chunkText);
        return String.join("\n", parts);
    }

    private String buildTitle(String documentName, String articleNumber, String articleTitle) {
        List<String> parts = new ArrayList<>();
        addIfHasText(parts, documentName);
        addIfHasText(parts, articleNumber);
        addIfHasText(parts, articleTitle);
        if (parts.isEmpty()) {
            return "规范条文";
        }
        return String.join(" ", parts);
    }

    private String buildSummary(String articleTitle, String content) {
        if (StringUtils.hasText(articleTitle)) {
            return articleTitle;
        }
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.length() <= SUMMARY_LIMIT ? content : content.substring(0, SUMMARY_LIMIT) + "...";
    }

    private List<JSONObject> flattenRecords(String jsonText) {
        Object parsed = JSON.parse(jsonText);
        List<JSONObject> records = new ArrayList<>();
        if (parsed instanceof JSONArray array) {
            for (Object item : array) {
                flattenNode(asJsonObject(item), records);
            }
        } else if (parsed instanceof JSONObject object) {
            JSONArray nested = firstArray(object, "data", "records", "items", "entries", "articles");
            if (nested != null) {
                for (Object item : nested) {
                    flattenNode(asJsonObject(item), records);
                }
            } else {
                flattenNode(object, records);
            }
        }
        return records;
    }

    private void flattenNode(JSONObject source, List<JSONObject> output) {
        if (source == null || source.isEmpty()) {
            return;
        }
        JSONArray nested = firstArray(source, "entries", "articles", "clauses", "items", "records");
        String content = firstNonBlank(
                asText(source.get("content"), ""),
                asText(source.get("text"), ""),
                asText(source.get("displayText"), ""),
                asText(source.get("正文"), "")
        );
        if (nested != null && !nested.isEmpty() && !StringUtils.hasText(content)) {
            for (Object item : nested) {
                JSONObject child = asJsonObject(item);
                if (child == null) {
                    continue;
                }
                JSONObject merged = new JSONObject(new LinkedHashMap<>(source));
                merged.remove("entries");
                merged.remove("articles");
                merged.remove("clauses");
                merged.remove("items");
                merged.remove("records");
                merged.putAll(child);
                flattenNode(merged, output);
            }
            return;
        }
        output.add(source);
    }

    private JSONArray firstArray(JSONObject object, String... keys) {
        for (String key : keys) {
            Object value = object.get(key);
            if (value instanceof JSONArray array) {
                return array;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private JSONObject asJsonObject(Object value) {
        if (value instanceof JSONObject object) {
            return object;
        }
        if (value instanceof Map<?, ?> map) {
            return new JSONObject((Map<String, Object>) map);
        }
        return null;
    }

    private String detectKnowledgeType(JSONObject record, String documentName, String articleTitle, String content) {
        String explicit = firstNonBlank(asText(record.get("knowledgeType"), ""), asText(record.get("type"), ""));
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        String joined = String.join(" ", firstNonBlank(documentName, ""), firstNonBlank(articleTitle, ""), firstNonBlank(content, ""));
        if (joined.contains("解释") || joined.contains("规定") || joined.contains("批复") || joined.contains("答复")) {
            return "司法解释";
        }
        if (joined.contains("指导案例") || joined.contains("典型案例")) {
            return "指导案例";
        }
        if (joined.contains("办法") || joined.contains("条例") || joined.contains("法")) {
            return "法律法规";
        }
        return "规范知识";
    }

    private Long resolveCategoryId(List<Category> categories, String... texts) {
        return DrugVerticalDomainSupport.matchCategoryId(categories, texts);
    }

    private String resolveCategoryName(Long categoryId, List<Category> categories) {
        if (categoryId == null || CollectionUtils.isEmpty(categories)) {
            return "未分类";
        }
        return categories.stream()
                .filter(item -> Objects.equals(item.getId(), categoryId))
                .map(Category::getName)
                .findFirst()
                .orElse("未分类");
    }

    private Map<Long, LegalKnowledge> loadKnowledgeMap(Collection<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Map.of();
        }
        return this.listByIds(ids).stream().collect(Collectors.toMap(LegalKnowledge::getId, item -> item));
    }

    private Map<Long, String> loadCategoryNameMap(Collection<LegalKnowledge> items) {
        Set<Long> categoryIds = items.stream()
                .map(LegalKnowledge::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryService.getAllCategories().stream()
                .filter(category -> categoryIds.contains(category.getId()))
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a, LinkedHashMap::new));
    }

    private String buildIndexTags(LegalKnowledge knowledge) {
        List<String> tags = new ArrayList<>();
        addIfHasText(tags, knowledge.getKnowledgeType());
        addIfHasText(tags, knowledge.getBusinessDomain());
        addIfHasText(tags, knowledge.getTopic());
        addIfHasText(tags, knowledge.getArticleNumber());
        addIfHasText(tags, knowledge.getKeywords());
        return joinDistinct(tags);
    }

    private void addLabelled(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + "：" + value.trim());
        }
    }

    private void addAll(List<String> target, List<String> values) {
        if (!CollectionUtils.isEmpty(values)) {
            target.addAll(values);
        }
    }

    private void addIfHasText(List<String> list, String value) {
        if (StringUtils.hasText(value)) {
            list.add(value.trim());
        }
    }

    private String joinDistinct(List<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .flatMap(value -> splitTags(value).stream())
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private List<String> splitTags(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof JSONArray array) {
            List<String> tags = new ArrayList<>();
            for (Object item : array) {
                if (item != null) {
                    tags.add(item.toString());
                }
            }
            return tags;
        }
        String text = value.toString();
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return List.of(text.split("[,，;；、\\n]+"));
    }

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return normalizeText(value).replace('\n', ' ').trim();
    }

    private String normalizeOptionalTags(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return joinDistinct(new ArrayList<>(splitTags(value)));
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replace('\uFEFF', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ');
        normalized = MULTI_SPACE.matcher(normalized).replaceAll(" ");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        return normalized.trim();
    }

    private String normalizeTitle(String title) {
        return normalizeText(title).toLowerCase(Locale.ROOT);
    }

    private String decodeUtf8(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return text;
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim()
                .replace("年", "-")
                .replace("月", "-")
                .replace("日", "")
                .replace("/", "-")
                .replace(".", "-");
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private List<Path> resolveOfficialKnowledgeFiles() {
        Path rawDirectory = resolveProjectRoot()
                .resolve("data")
                .resolve("legal_sources")
                .resolve("drug_vertical")
                .resolve("raw");
        if (!Files.isDirectory(rawDirectory)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(rawDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return (fileName.endsWith(".html") || fileName.endsWith(".htm"))
                                && !fileName.contains("_test")
                                && !OFFICIAL_KNOWLEDGE_EXCLUDED_FILES.contains(path.getFileName().toString());
                    })
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new IllegalStateException("读取官方知识目录失败: " + ex.getMessage(), ex);
        }
    }

    private LegalKnowledgeImportResult importOfficialKnowledgeSource(Path filePath, Long categoryId, List<Category> categories) {
        LegalKnowledgeImportResult result = new LegalKnowledgeImportResult();
        result.setSourceName(filePath == null ? "unknown" : filePath.getFileName().toString());
        if (filePath == null || !Files.isRegularFile(filePath)) {
            result.setSuccess(false);
            result.setMessage("官方知识文件不存在，无法导入");
            return result;
        }

        try {
            OfficialSourceMeta meta = resolveOfficialSourceMeta(filePath);
            byte[] bytes = Files.readAllBytes(filePath);
            MultipartFile virtualFile = new InMemoryMultipartFile(
                    buildOfficialImportFileName(filePath, meta),
                    detectOfficialContentType(filePath),
                    bytes
            );
            Map<String, Object> extracted = unwrapData(algorithmClient.extractDocumentText(virtualFile));
            boolean supported = Boolean.TRUE.equals(extracted.get("supported"));
            String rawText = legalDocumentTextProcessor.normalizeRawText(asText(extracted.get("text"), ""));
            String cleanText = cleanupOfficialKnowledgeContent(legalDocumentTextProcessor.normalizeText(rawText), meta);
            if (!supported) {
                result.setSuccess(false);
                result.setMessage(asText(extracted.get("message"), "当前文件类型暂不支持导入"));
                return result;
            }
            if (!StringUtils.hasText(cleanText)) {
                result.setSuccess(false);
                result.setMessage("未提取到可用正文，已跳过");
                return result;
            }

            JSONObject record = buildOfficialKnowledgeRecord(meta, filePath, cleanText);
            LegalKnowledgeImportResult imported = importOrReplaceOfficialKnowledgeRecord(
                    record,
                    filePath.getFileName().toString(),
                    categoryId,
                    categories
            );
            if (imported.getSourceName() == null) {
                imported.setSourceName(filePath.getFileName().toString());
            }
            return imported;
        } catch (Exception ex) {
            result.setSuccess(false);
            result.setMessage("官方知识导入失败: " + ex.getMessage());
            return result;
        }
    }

    private JSONObject buildOfficialKnowledgeRecord(OfficialSourceMeta meta, Path filePath, String cleanText) {
        JSONObject record = new JSONObject();
        record.put("sourceType", "official_crawl");
        record.put("sourceName", firstNonBlank(meta.sourceName(), meta.issuer(), "官方来源"));
        record.put("sourceUrl", meta.url());
        record.put("sourceReliability", 1);
        record.put("documentName", firstNonBlank(meta.documentName(), stripExtension(filePath.getFileName().toString())));
        record.put("title", firstNonBlank(meta.documentName(), stripExtension(filePath.getFileName().toString())));
        record.put("knowledgeType", mapOfficialKnowledgeType(meta));
        record.put("knowledgeSubtype", firstNonBlank(meta.knowledgeSubtype(), meta.knowledgeType(), meta.documentLevel(), meta.rawGroup()));
        record.put("documentLevel", firstNonBlank(meta.documentLevel(), meta.knowledgeSubtype(), meta.rawGroup()));
        record.put("issuer", firstNonBlank(meta.issuer(), meta.sourceName()));
        record.put("businessDomain", meta.businessDomain());
        record.put("topic", firstNonBlank(meta.topic(), meta.businessDomain(), meta.documentName()));
        record.put("effectiveDate", meta.effectiveDate());
        record.put("currentStatus", firstNonBlank(meta.currentStatus(), "公开发布"));
        record.put("content", cleanText);
        record.put("summary", legalDocumentTextProcessor.ellipsize(cleanText, SUMMARY_LIMIT));
        return record;
    }

    private String cleanupOfficialKnowledgeContent(String cleanText, OfficialSourceMeta meta) {
        if (!StringUtils.hasText(cleanText)) {
            return "";
        }
        String title = meta == null ? "" : firstNonBlank(meta.documentName(), "");
        String[] rawLines = cleanText.trim().split("\n+");
        int metadataHits = 0;
        int bodyStartIndex = -1;
        for (int index = 0; index < rawLines.length && index < 14; index++) {
            String line = normalizeText(rawLines[index]).replace('\n', ' ').trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            boolean titleLine = StringUtils.hasText(title) && title.equals(line);
            if (titleLine || isOfficialMetadataLine(line)) {
                metadataHits++;
                continue;
            }
            if (metadataHits >= 2) {
                bodyStartIndex = index;
            }
            break;
        }
        if (bodyStartIndex <= 0) {
            return cleanText.trim();
        }
        List<String> remainingLines = new ArrayList<>();
        for (int index = bodyStartIndex; index < rawLines.length; index++) {
            String line = rawLines[index].trim();
            if (StringUtils.hasText(line)) {
                remainingLines.add(line);
            }
        }
        return String.join("\n", remainingLines);
    }

    private boolean isOfficialMetadataLine(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        return "外部监督".equals(line)
                || "以案说法".equals(line)
                || "大".equals(line)
                || "中".equals(line)
                || "小".equals(line)
                || "大 中 小".equals(line)
                || line.startsWith("日期")
                || line.startsWith("作者")
                || line.startsWith("来源")
                || line.contains("来源：")
                || line.contains("星期")
                || line.matches("\\d{4}年\\d{1,2}月\\d{1,2}日.*");
    }

    private String mapOfficialKnowledgeType(OfficialSourceMeta meta) {
        if ("laws".equals(meta.rawGroup())) {
            return "法律法规";
        }
        if ("judicial_interpretations".equals(meta.rawGroup())) {
            return "司法解释";
        }
        if ("guiding_cases".equals(meta.rawGroup())) {
            return "案例规则";
        }
        if ("guides".equals(meta.rawGroup())) {
            return "规范知识";
        }
        return firstNonBlank(meta.knowledgeType(), "规范知识");
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

    private OfficialSourceMeta resolveOfficialSourceMeta(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString();
        String extension = getFileExtension(fileName);
        String baseName = extension == null ? fileName : fileName.substring(0, fileName.length() - extension.length() - 1);
        Path metaPath = filePath.resolveSibling(baseName + ".meta.json");
        if (!Files.isRegularFile(metaPath)) {
            return new OfficialSourceMeta(
                    baseName,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    filePath.getParent() == null ? null : filePath.getParent().getFileName().toString()
            );
        }

        JSONObject payload = JSON.parseObject(Files.readString(metaPath, StandardCharsets.UTF_8));
        return new OfficialSourceMeta(
                firstNonBlank(payload.getString("documentName"), stripExtension(fileName)),
                payload.getString("url"),
                payload.getString("sourceName"),
                payload.getString("issuer"),
                payload.getString("knowledgeType"),
                payload.getString("knowledgeSubtype"),
                payload.getString("documentLevel"),
                payload.getString("effectiveDate"),
                payload.getString("currentStatus"),
                payload.getString("businessDomain"),
                payload.getString("topic"),
                payload.getString("rawGroup")
        );
    }

    private String buildOfficialImportFileName(Path filePath, OfficialSourceMeta meta) {
        String extension = firstNonBlank(getFileExtension(filePath.getFileName().toString()), "html");
        String preferredName = firstNonBlank(meta.documentName(), stripExtension(filePath.getFileName().toString()));
        return sanitizeVirtualFileName(preferredName) + "." + extension;
    }

    private String detectOfficialContentType(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html";
        }
        if (fileName.endsWith(".json")) {
            return "application/json";
        }
        if (fileName.endsWith(".md")) {
            return "text/markdown";
        }
        return "application/octet-stream";
    }

    private String sanitizeVirtualFileName(String value) {
        String sanitized = firstNonBlank(value, "official-knowledge")
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        return StringUtils.hasText(sanitized) ? sanitized : "official-knowledge";
    }

    private String stripExtension(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        int dotIndex = value.lastIndexOf('.');
        return dotIndex > 0 ? value.substring(0, dotIndex) : value;
    }

    private String getFileExtension(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        int dotIndex = value.lastIndexOf('.');
        return dotIndex > -1 && dotIndex < value.length() - 1 ? value.substring(dotIndex + 1) : null;
    }

    private Integer parseInteger(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String asText(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return StringUtils.hasText(text) ? text.trim() : fallback;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate sha256", ex);
        }
    }

    private Map<String, Object> unwrapData(Map<String, Object> result) {
        if (result == null) {
            return Map.of();
        }
        Object data = result.get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new HashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        return result;
    }

    private record OfficialSourceMeta(
            String documentName,
            String url,
            String sourceName,
            String issuer,
            String knowledgeType,
            String knowledgeSubtype,
            String documentLevel,
            String effectiveDate,
            String currentStatus,
            String businessDomain,
            String topic,
            String rawGroup
    ) {
    }

    private static class InMemoryMultipartFile implements MultipartFile {
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        private InMemoryMultipartFile(String originalFilename, String contentType, byte[] content) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content == null ? new byte[0] : content;
        }

        @Override
        public String getName() {
            return originalFilename;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }

    private record TextSlice(int start, int end, String text) {
    }
}
