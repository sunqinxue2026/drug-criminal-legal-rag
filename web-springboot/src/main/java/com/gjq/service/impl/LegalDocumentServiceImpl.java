package com.gjq.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gjq.client.AlgorithmClient;
import com.gjq.common.exception.BusinessException;
import com.gjq.dto.LegalDocumentImportResponse;
import com.gjq.dto.LegalDocumentImportResult;
import com.gjq.dto.LegalKnowledgeImportResult;
import com.gjq.entity.Category;
import com.gjq.entity.LegalDocument;
import com.gjq.entity.LegalDocumentChunk;
import com.gjq.entity.LegalDocumentChunkIndex;
import com.gjq.entity.LegalDocumentExt;
import com.gjq.entity.LegalDocumentParty;
import com.gjq.entity.LegalDocumentReference;
import com.gjq.mapper.LegalDocumentChunkIndexMapper;
import com.gjq.mapper.LegalDocumentChunkMapper;
import com.gjq.mapper.LegalDocumentExtMapper;
import com.gjq.mapper.LegalDocumentMapper;
import com.gjq.mapper.LegalDocumentPartyMapper;
import com.gjq.mapper.LegalDocumentReferenceMapper;
import com.gjq.service.CategoryService;
import com.gjq.service.LegalDocumentService;
import com.gjq.service.LegalKnowledgeService;
import com.gjq.service.support.LegalDocumentChunkService;
import com.gjq.service.support.LegalDocumentParser;
import com.gjq.service.support.LegalDocumentParsedData;
import com.gjq.service.support.LegalDocumentRelationExtractor;
import com.gjq.service.support.LegalDocumentSection;
import com.gjq.service.support.LegalDocumentTextProcessor;
import com.gjq.service.support.LegalDocumentTextSlice;
import com.gjq.utils.DrugVerticalDomainSupport;
import com.gjq.vo.LegalDocumentDetailVO;
import com.gjq.vo.LegalDocumentIndexChunkVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LegalDocumentServiceImpl extends ServiceImpl<LegalDocumentMapper, LegalDocument> implements LegalDocumentService {

    private static final Logger log = LoggerFactory.getLogger(LegalDocumentServiceImpl.class);

    private static final Pattern MULTI_SPACE = Pattern.compile("[\\t\\x0B\\f\\r ]+");
    private static final Pattern CASE_NUMBER_PATTERN = Pattern.compile(
            "([（(]\\d{4}[)）][^\\s]{1,40}?号|[\\u4e00-\\u9fa5A-Za-z]{1,30}[〔\\[]\\d{4}[〕\\]][0-9一二三四五六七八九十百千万第\\-—]{1,30}号)"
    );
    private static final Pattern NUMERIC_DATE_PATTERN = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern PARTY_LINE_PATTERN = Pattern.compile(
            "^(原告|被告|上诉人|被上诉人|申请再审人|再审申请人|被申请人|申请人|申诉人|被申诉人|申请执行人|被执行人|被申请执行人|赔偿请求人|赔偿义务机关|罚款被处罚人|被处罚人|复议申请人|复议被申请人|当事人|一审原告|一审被告|二审上诉人|二审被上诉人)[：:，,]?\\s*(.+)$"
    );
    private static final Pattern INSTITUTION_LABEL_PATTERN = Pattern.compile("(审理法院|法院|检察机关|检察院|承办机关|发布机构)[：:]\\s*([^\\n]{2,80})");
    private static final Pattern CAUSE_LABEL_PATTERN = Pattern.compile("(案由|案件类型)[：:]\\s*([^\\n]{2,80})");
    private static final Pattern LEGAL_BASIS_LABEL_PATTERN = Pattern.compile("(法律依据|相关法条|依据)[：:]\\s*([^\\n]{2,200})");
    private static final Pattern LAW_PATTERN = Pattern.compile("《[^》]{1,50}》[^。；\\n]{0,80}");
    private static final Pattern DECISION_NUMBER_PATTERN = Pattern.compile("第[0-9A-Za-z一二三四五六七八九十百千万〇零]{2,40}号");
    private static final Pattern RIGHT_NUMBER_PATTERN = Pattern.compile("(专利号|申请号|专利申请号|品种权号|登记号|注册号|案号|决定号)[：: ]*([A-Za-z0-9\\-\\.]+)");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(一、|二、|三、|四、|五、|六、|七、|八、|九、|十、|本院认为|法院认为|审理查明|经审理查明|本院查明|审理经过|诉讼经过|程序经过|法律依据|相关法条|一审法院认为|二审法院认为|再审审查认为|裁判结果|判决如下|裁定如下|决定如下)");

    private static final int SUMMARY_LIMIT = 220;
    private static final int CHUNK_SIZE = 700;
    private static final int CHUNK_OVERLAP = 120;
    private static final String DEFAULT_RETRIEVAL_PROFILE = "dense_v1";
    private static final String DEFAULT_INDEX_NAME = "legal_cases";
    private static final Set<String> OFFICIAL_DOCUMENT_EXCLUDED_FILES = Set.of(
            "guiding_case_248_illegal_drug_purchase_meritorious_service.html"
    );
    private static final Set<String> OFFICIAL_JUDICIAL_DOCUMENT_GROUPS = Set.of(
            "prosecution_documents"
    );
    private static final Set<String> FORMAL_JUDICIAL_DOCUMENT_KEYWORDS = Set.of(
            "起诉书",
            "判决书",
            "裁定书",
            "调解书",
            "裁判文书"
    );
    private static final Set<String> KNOWLEDGE_ONLY_OFFICIAL_KEYWORDS = Set.of(
            "普法",
            "典型案例",
            "指导案例",
            "案例解读",
            "以案说法",
            "法律法规",
            "司法解释",
            "司法解释理解与适用",
            "会议纪要",
            "批复",
            "答复",
            "指导意见",
            "通知",
            "规定",
            "办法",
            "意见",
            "白皮书",
            "工作要点"
    );
    private static final Set<String> JUDICIAL_AUTHORITY_KEYWORDS = Set.of(
            "人民法院",
            "人民检察院",
            "法院",
            "检察院"
    );
    private static final List<String> FORMAL_DOCUMENT_TYPES = List.of(
            "刑事附带民事公益诉讼起诉书", "行政公益诉讼起诉书", "民事公益诉讼起诉书",
            "刑事起诉书", "起诉书", "执行裁定书", "行政判决书", "民事判决书", "刑事判决书",
            "民事裁定书", "刑事裁定书", "行政裁定书", "司法制裁决定书", "司法罚款决定书",
            "通知书", "裁定书", "判决书", "决定书", "调解书"
    );

    private final LegalDocumentPartyMapper partyMapper;
    private final LegalDocumentReferenceMapper referenceMapper;
    private final LegalDocumentChunkMapper chunkMapper;
    private final LegalDocumentChunkIndexMapper chunkIndexMapper;
    private final LegalDocumentExtMapper extMapper;
    private final CategoryService categoryService;
    private final AlgorithmClient algorithmClient;
    private final LegalKnowledgeService legalKnowledgeService;
    private final LegalDocumentChunkService legalDocumentChunkService;
    private final LegalDocumentParser legalDocumentParser;
    private final LegalDocumentRelationExtractor legalDocumentRelationExtractor;
    private final LegalDocumentTextProcessor legalDocumentTextProcessor;

    public LegalDocumentServiceImpl(
            LegalDocumentPartyMapper partyMapper,
            LegalDocumentReferenceMapper referenceMapper,
            LegalDocumentChunkMapper chunkMapper,
            LegalDocumentChunkIndexMapper chunkIndexMapper,
            LegalDocumentExtMapper extMapper,
            CategoryService categoryService,
            AlgorithmClient algorithmClient,
            LegalKnowledgeService legalKnowledgeService,
            LegalDocumentChunkService legalDocumentChunkService,
            LegalDocumentParser legalDocumentParser,
            LegalDocumentRelationExtractor legalDocumentRelationExtractor,
            LegalDocumentTextProcessor legalDocumentTextProcessor
    ) {
        this.partyMapper = partyMapper;
        this.referenceMapper = referenceMapper;
        this.chunkMapper = chunkMapper;
        this.chunkIndexMapper = chunkIndexMapper;
        this.extMapper = extMapper;
        this.categoryService = categoryService;
        this.algorithmClient = algorithmClient;
        this.legalKnowledgeService = legalKnowledgeService;
        this.legalDocumentChunkService = legalDocumentChunkService;
        this.legalDocumentParser = legalDocumentParser;
        this.legalDocumentRelationExtractor = legalDocumentRelationExtractor;
        this.legalDocumentTextProcessor = legalDocumentTextProcessor;
    }

    @Override
    public Page<LegalDocument> getDocumentPage(int pageNum, int pageSize, Long categoryId, String keyword,
                                               String documentType, String caseType, String procedureDomain) {
        Page<LegalDocument> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<LegalDocument> wrapper = new LambdaQueryWrapper<>();
        Set<String> excludedPageFields = Set.of(
                "retrievalText",
                "rawText",
                "cleanText",
                "caseDetailText",
                "displayText"
        );
        wrapper.select(LegalDocument.class, info -> !excludedPageFields.contains(info.getProperty()));
        if (categoryId != null) {
            wrapper.eq(LegalDocument::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(query -> query
                    .like(LegalDocument::getTitle, trimmed)
                    .or().like(LegalDocument::getCaseNumber, trimmed)
                    .or().like(LegalDocument::getCourtName, trimmed)
                    .or().like(LegalDocument::getSummary, trimmed)
                    .or().like(LegalDocument::getRetrievalText, trimmed));
        }
        if (StringUtils.hasText(documentType)) {
            wrapper.eq(LegalDocument::getDocumentType, documentType.trim());
        }
        if (StringUtils.hasText(caseType)) {
            wrapper.eq(LegalDocument::getCaseType, caseType.trim());
        }
        if (StringUtils.hasText(procedureDomain)) {
            wrapper.eq(LegalDocument::getProcedureDomain, procedureDomain.trim());
        }
        wrapper.eq(LegalDocument::getStatus, 1)
                .orderByDesc(LegalDocument::getDecisionDate)
                .orderByDesc(LegalDocument::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    public LegalDocumentDetailVO getDocumentDetail(Long id) {
        LegalDocument document = this.getById(id);
        if (document == null) {
            return null;
        }
        List<LegalDocumentParty> parties = partyMapper.selectList(new LambdaQueryWrapper<LegalDocumentParty>()
                .eq(LegalDocumentParty::getDocumentId, id)
                .orderByAsc(LegalDocumentParty::getPartySeq));
        List<LegalDocumentReference> references = referenceMapper.selectList(new LambdaQueryWrapper<LegalDocumentReference>()
                .eq(LegalDocumentReference::getDocumentId, id)
                .orderByAsc(LegalDocumentReference::getRefOrder));
        List<LegalDocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<LegalDocumentChunk>()
                .eq(LegalDocumentChunk::getDocumentId, id)
                .orderByAsc(LegalDocumentChunk::getSectionOrder)
                .orderByAsc(LegalDocumentChunk::getChunkIndex));
        return buildDetailVO(document, parties, references, chunks);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createDocument(LegalDocument document, boolean rebuildIndex) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (document == null) {
            response.put("success", false);
            response.put("message", "专题文书记录不能为空");
            return response;
        }

        LegalDocumentParsedData parsed = buildManualParsedDocument(document);
        this.save(parsed.document);
        persistRelatedRecords(parsed);
        return buildDocumentWriteResponse(parsed.document.getId(), true, rebuildIndex);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateDocument(LegalDocument document, boolean rebuildIndex) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (document == null || document.getId() == null) {
            response.put("success", false);
            response.put("message", "缺少要更新的法律文书 ID");
            return response;
        }

        LegalDocument existing = this.getById(document.getId());
        if (existing == null) {
            response.put("success", false);
            response.put("message", "专题文书不存在或已被删除");
            return response;
        }

        mergeManualDocument(existing, document);
        LegalDocumentParsedData parsed = buildManualParsedDocument(existing);
        parsed.document.setId(existing.getId());
        this.updateById(parsed.document);
        clearRelatedRecords(existing.getId());
        persistRelatedRecords(parsed);
        return buildDocumentWriteResponse(existing.getId(), false, rebuildIndex);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteDocument(Long id, boolean rebuildIndex) {
        Map<String, Object> response = new LinkedHashMap<>();
        LegalDocument document = this.getById(id);
        if (document == null) {
            response.put("success", false);
            response.put("message", "\u4e13\u9898\u6587\u4e66\u4e0d\u5b58\u5728\u6216\u5df2\u88ab\u5220\u9664");
            return response;
        }

        List<Long> chunkIds = chunkMapper.selectList(new LambdaQueryWrapper<LegalDocumentChunk>()
                        .select(LegalDocumentChunk::getId)
                        .eq(LegalDocumentChunk::getDocumentId, id))
                .stream()
                .map(LegalDocumentChunk::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!chunkIds.isEmpty()) {
            chunkIndexMapper.delete(new LambdaQueryWrapper<LegalDocumentChunkIndex>()
                    .in(LegalDocumentChunkIndex::getChunkId, chunkIds));
        }
        chunkMapper.delete(new LambdaQueryWrapper<LegalDocumentChunk>()
                .eq(LegalDocumentChunk::getDocumentId, id));
        partyMapper.delete(new LambdaQueryWrapper<LegalDocumentParty>()
                .eq(LegalDocumentParty::getDocumentId, id));
        referenceMapper.delete(new LambdaQueryWrapper<LegalDocumentReference>()
                .eq(LegalDocumentReference::getDocumentId, id));
        extMapper.delete(new LambdaQueryWrapper<LegalDocumentExt>()
                .eq(LegalDocumentExt::getDocumentId, id));

        boolean removed = this.removeById(id);
        if (!removed) {
            response.put("success", false);
            response.put("message", "\u4e13\u9898\u6587\u4e66\u5220\u9664\u5931\u8d25");
            return response;
        }

        response.put("success", true);
        response.put("documentId", id);
        response.put("reindexed", false);
        response.put("message", "\u4e13\u9898\u6587\u4e66\u5df2\u5220\u9664");

        if (rebuildIndex) {
            Map<String, Object> rebuildInfo = rebuildLegalIndex();
            boolean reindexed = Boolean.TRUE.equals(rebuildInfo.get("success"));
            response.put("reindexed", reindexed);
            response.put("indexMessage", rebuildInfo.get("message"));
            response.put("message", reindexed
                    ? "\u4e13\u9898\u6587\u4e66\u5df2\u5220\u9664\uff0c\u4e13\u9898\u7d22\u5f15\u5df2\u5237\u65b0"
                    : "\u4e13\u9898\u6587\u4e66\u5df2\u5220\u9664\uff0c\u4f46\u4e13\u9898\u7d22\u5f15\u5237\u65b0\u5931\u8d25");
        }

        return response;
    }

    @Override
    public LegalDocumentDetailVO previewFile(MultipartFile file, Long categoryId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "\u6587\u4e66\u9884\u89c8\u5931\u8d25\uff1a\u8bf7\u9009\u62e9\u8981\u4e0a\u4f20\u7684\u6587\u4ef6");
        }
        try {
            byte[] bytes = file.getBytes();
            String sha256 = sha256Hex(bytes);
            Map<String, Object> extracted = unwrapData(algorithmClient.extractDocumentText(file));
            boolean supported = Boolean.TRUE.equals(extracted.get("supported"));
            String rawText = normalizeRawText(asText(extracted.get("text"), ""));
            String cleanText = normalizeText(rawText);
            if (!supported) {
                throw new BusinessException(400, "\u6587\u4e66\u9884\u89c8\u5931\u8d25\uff1a\u5f53\u524d\u6587\u4ef6\u7c7b\u578b\u6682\u4e0d\u652f\u6301\u89e3\u6790");
            }
            if (!StringUtils.hasText(cleanText)) {
                throw new BusinessException(422, "\u6587\u4e66\u9884\u89c8\u5931\u8d25\uff1a\u672a\u63d0\u53d6\u5230\u6709\u6548\u6587\u672c");
            }
            LegalDocumentParsedData parsed = parseDocument(file, rawText, cleanText, sha256);
            Category category = resolveCategory(categoryId, categoryService.getAllCategories(), parsed);
            parsed.document.setCategoryId(category == null ? null : category.getId());
            parsed.document.setSummary(buildSummary(parsed.document));
            parsed.document.setKeywords(joinDistinct(parsed.keywords));
            parsed.document.setAliases(joinDistinct(parsed.aliases));
            parsed.document.setIssueTags(joinDistinct(parsed.issueTags));
            parsed.document.setResultTags(joinDistinct(parsed.resultTags));
            parsed.document.setRetrievalText(buildDocumentRetrievalText(parsed.document));
            return buildDetailVO(parsed.document, parsed.parties, parsed.references, parsed.chunks);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IllegalStateException ex) {
            log.warn("Document preview dependency failed for file {}", file.getOriginalFilename(), ex);
            throw new BusinessException(503, ex.getMessage());
        } catch (Exception ex) {
            log.error("Document preview failed for file {}", file.getOriginalFilename(), ex);
            throw new BusinessException(500, "\u6587\u4e66\u9884\u89c8\u5931\u8d25\uff1a\u89e3\u6790\u8fc7\u7a0b\u4e2d\u53d1\u751f\u5f02\u5e38");
        }
    }

    private LegalDocumentDetailVO buildDetailVO(LegalDocument document,
                                                List<LegalDocumentParty> parties,
                                                List<LegalDocumentReference> references,
                                                List<LegalDocumentChunk> chunks) {
        normalizeDocumentForDetail(document);
        if (!CollectionUtils.isEmpty(chunks)) {
            chunks.forEach(chunk -> chunk.setChunkText(legalDocumentTextProcessor.cleanChunkSource(chunk.getChunkText())));
        }
        LegalDocumentDetailVO detailVO = new LegalDocumentDetailVO();
        detailVO.setDocument(document);
        if (!CollectionUtils.isEmpty(parties)) {
            detailVO.setParties(parties);
        }
        if (!CollectionUtils.isEmpty(references)) {
            detailVO.setReferences(references);
        }
        if (!CollectionUtils.isEmpty(chunks)) {
            detailVO.setChunks(chunks);
        }
        return detailVO;
    }

    private void normalizeDocumentForDetail(LegalDocument document) {
        if (document == null) {
            return;
        }

        String cleanedBodyText = legalDocumentTextProcessor.cleanDisplayText(
                firstNonBlank(document.getCleanText(), document.getDisplayText(), document.getRawText()),
                document.getTitle(),
                document.getCourtName(),
                firstNonBlank(document.getDocumentSubtype(), document.getDocumentType())
        );
        if (StringUtils.hasText(cleanedBodyText)) {
            document.setDisplayText(cleanedBodyText);
            if (!StringUtils.hasText(document.getCleanText())) {
                document.setCleanText(cleanedBodyText);
            }
        }

        String corpus = firstNonBlank(document.getCleanText(), document.getDisplayText(), document.getRawText(), "");
        String currentCause = firstNonBlank(document.getCauseNormalized(), document.getCauseRaw());
        if (isSuspiciousCauseValue(currentCause)) {
            String detectedCause = normalizeOptionalText(normalizeCause(detectCauseForDisplay(document, corpus)));
            if (StringUtils.hasText(detectedCause)) {
                document.setCauseNormalized(detectedCause);
                document.setCauseRaw(detectedCause);
            }
        }

        if (!StringUtils.hasText(document.getProcedureDomain()) || "普通程序".equals(document.getProcedureDomain())) {
            String detectedProcedureDomain = normalizeOptionalText(detectProcedureDomainForDisplay(document, corpus));
            if (StringUtils.hasText(detectedProcedureDomain)) {
                document.setProcedureDomain(detectedProcedureDomain);
            }
        }

        String rebuiltCaseDetail = normalizeOptionalText(buildCaseDetailText(document));
        if (!StringUtils.hasText(document.getCaseDetailText())
                || document.getCaseDetailText().length() < 24
                || document.getCaseDetailText().contains("侦查终结")) {
            document.setCaseDetailText(rebuiltCaseDetail);
        }

        if (!StringUtils.hasText(document.getSummary())) {
            document.setSummary(normalizeOptionalText(buildSummary(document)));
        }
    }

    private String detectCauseForDisplay(LegalDocument document, String corpus) {
        String title = firstNonBlank(document.getTitle(), "");

        Matcher matcher = Pattern.compile("案由[:：]?\\s*([^\\n，。；]{2,40})").matcher(firstNonBlank(corpus, ""));
        while (matcher.find()) {
            String candidate = normalizeDetectedCause(matcher.group(1));
            if (isReasonableCause(candidate)) {
                return candidate;
            }
        }

        String titleCause = detectCauseFromTitleForDisplay(title);
        if (isReasonableCause(titleCause)) {
            return titleCause;
        }

        String knownCause = detectKnownCause(title + "\n" + firstNonBlank(corpus, ""));
        if (StringUtils.hasText(knownCause)) {
            return knownCause;
        }

        return firstNonBlank(document.getCauseNormalized(), document.getCauseRaw());
    }

    private String detectCauseFromTitleForDisplay(String title) {
        Matcher bracketMatcher = Pattern.compile("[（(]([^）)]{2,40})[）)]").matcher(firstNonBlank(title, ""));
        while (bracketMatcher.find()) {
            String candidate = normalizeDetectedCause(bracketMatcher.group(1));
            String knownCause = detectKnownCause(candidate);
            if (StringUtils.hasText(knownCause)) {
                return knownCause;
            }
            if (isReasonableCause(candidate)) {
                return candidate;
            }
        }

        String knownCause = detectKnownCause(title);
        if (StringUtils.hasText(knownCause)) {
            return knownCause;
        }

        return null;
    }

    private String detectKnownCause(String corpus) {
        if (!StringUtils.hasText(corpus)) {
            return null;
        }
        List<String> candidates = List.of(
                "走私、贩卖、运输、制造毒品",
                "贩卖毒品",
                "运输毒品",
                "制造毒品",
                "非法持有毒品",
                "容留他人吸毒",
                "引诱、教唆、欺骗他人吸毒",
                "洗钱"
        );
        for (String candidate : candidates) {
            if (corpus.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String normalizeDetectedCause(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = collapseWhitespace(value)
                .replace("涉嫌", "")
                .replace("一案", "")
                .replace("案件", "")
                .trim();
        if (normalized.endsWith("案")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private boolean isReasonableCause(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = collapseWhitespace(value);
        if (normalized.length() > 40) {
            return false;
        }
        if (normalized.matches(".*\\d{4}年.*")) {
            return false;
        }
        if (normalized.contains("侦查终结")
                || normalized.contains("公安局")
                || normalized.contains("人民检察院")
                || normalized.contains("人民法院")
                || normalized.contains("审查起诉")
                || normalized.contains("移送起诉")
                || normalized.contains("本院受理")
                || normalized.contains("案件类型")) {
            return false;
        }
        return normalized.contains("毒品")
                || normalized.contains("吸毒")
                || normalized.contains("洗钱")
                || normalized.endsWith("罪")
                || normalized.endsWith("案");
    }

    private boolean isSuspiciousCauseValue(String value) {
        return !StringUtils.hasText(value) || !isReasonableCause(value);
    }

    private String detectProcedureDomainForDisplay(LegalDocument document, String corpus) {
        String text = firstNonBlank(document.getTitle(), "") + "\n" + firstNonBlank(corpus, "");
        if (text.contains("起诉书") || text.contains("审查起诉") || text.contains("移送起诉")) {
            return "审查起诉";
        }
        if (text.contains("侦查终结") || text.contains("立案侦查") || text.contains("刑事拘留")) {
            return "侦查";
        }
        return firstNonBlank(document.getProcedureDomain(), "普通程序");
    }

    private Map<String, Object> buildDocumentWriteResponse(Long documentId, boolean created, boolean rebuildIndex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("documentId", documentId);
        response.put("reindexed", false);
        response.put("message", created ? "专题文书创建成功" : "专题文书更新成功");

        if (rebuildIndex) {
            Map<String, Object> rebuildInfo = rebuildLegalIndex();
            boolean reindexed = Boolean.TRUE.equals(rebuildInfo.get("success"));
            response.put("reindexed", reindexed);
            response.put("indexMessage", rebuildInfo.get("message"));
            response.put("message", reindexed
                    ? (created ? "专题文书创建成功，专题索引已刷新" : "专题文书更新成功，专题索引已刷新")
                    : (created ? "专题文书创建成功，但专题索引刷新失败" : "专题文书更新成功，但专题索引刷新失败"));
        }

        return response;
    }

    private LegalDocumentParsedData buildManualParsedDocument(LegalDocument document) {
        prepareManualDocument(document);

        String fileName = firstNonBlank(document.getSourceFileName(), sanitizeVirtualFileName(document.getTitle()) + ".txt");
        byte[] bytes = document.getRawText().getBytes(StandardCharsets.UTF_8);
        MultipartFile virtualFile = new InMemoryMultipartFile(fileName, "text/plain", bytes);
        String rawText = normalizeRawText(document.getRawText());
        String cleanText = normalizeText(rawText);
        LegalDocumentParsedData parsed = parseDocument(virtualFile, rawText, cleanText, sha256Hex(bytes));
        applyManualDocumentOverrides(parsed.document, document);
        parsed.document.setSummary(buildSummary(parsed.document));
        parsed.document.setKeywords(mergeDocumentTags(parsed.keywords, document.getKeywords()));
        parsed.document.setAliases(mergeDocumentTags(parsed.aliases, document.getAliases()));
        parsed.document.setIssueTags(joinDistinct(parsed.issueTags));
        parsed.document.setResultTags(joinDistinct(parsed.resultTags));
        parsed.document.setRetrievalText(buildDocumentRetrievalText(parsed.document));
        return parsed;
    }

    private void applyManualDocumentOverrides(LegalDocument target, LegalDocument source) {
        target.setSourceType(firstNonBlank(source.getSourceType(), target.getSourceType()));
        target.setSourceName(firstNonBlank(source.getSourceName(), target.getSourceName()));
        target.setSourceUrl(firstNonBlank(source.getSourceUrl(), target.getSourceUrl()));
        target.setSourceReliability(source.getSourceReliability() == null ? target.getSourceReliability() : source.getSourceReliability());
        target.setSourceFileName(firstNonBlank(source.getSourceFileName(), target.getSourceFileName()));
        target.setTitle(firstNonBlank(source.getTitle(), target.getTitle()));
        target.setTitleNormalized(normalizeForKey(target.getTitle()));
        target.setCaseNumber(firstNonBlank(source.getCaseNumber(), target.getCaseNumber()));
        target.setCourtName(firstNonBlank(source.getCourtName(), target.getCourtName()));
        target.setDocumentType(firstNonBlank(source.getDocumentType(), target.getDocumentType()));
        target.setDocumentSubtype(firstNonBlank(source.getDocumentSubtype(), target.getDocumentSubtype(), target.getDocumentType()));
        target.setCaseType(firstNonBlank(source.getCaseType(), target.getCaseType()));
        target.setCategoryId(source.getCategoryId() == null ? target.getCategoryId() : source.getCategoryId());
        target.setCauseRaw(firstNonBlank(source.getCauseRaw(), target.getCauseRaw()));
        target.setCauseNormalized(firstNonBlank(source.getCauseNormalized(), target.getCauseNormalized(), target.getCauseRaw()));
        target.setBusinessDomain(firstNonBlank(source.getBusinessDomain(), target.getBusinessDomain()));
        target.setProcedureDomain(firstNonBlank(source.getProcedureDomain(), target.getProcedureDomain()));
        target.setTrialStageNormalized(firstNonBlank(source.getTrialStageNormalized(), target.getTrialStageNormalized()));
        target.setSpecialProcedureType(firstNonBlank(source.getSpecialProcedureType(), target.getSpecialProcedureType()));
        target.setApplicationType(firstNonBlank(source.getApplicationType(), target.getApplicationType()));
        target.setDecisionResult(firstNonBlank(source.getDecisionResult(), target.getDecisionResult()));
        target.setDecisionResultNormalized(firstNonBlank(
                source.getDecisionResultNormalized(),
                target.getDecisionResultNormalized(),
                normalizeDecisionResult(target.getDecisionResult())
        ));
        target.setDecisionDate(source.getDecisionDate() == null ? target.getDecisionDate() : source.getDecisionDate());
        target.setEffectiveDate(source.getEffectiveDate() == null ? target.getEffectiveDate() : source.getEffectiveDate());
        target.setFilingDate(source.getFilingDate() == null ? target.getFilingDate() : source.getFilingDate());
        target.setHearingDate(source.getHearingDate() == null ? target.getHearingDate() : source.getHearingDate());
        target.setClaimsSummary(firstNonBlank(source.getClaimsSummary(), target.getClaimsSummary()));
        target.setDefenseSummary(firstNonBlank(source.getDefenseSummary(), target.getDefenseSummary()));
        target.setDisputeSummary(firstNonBlank(source.getDisputeSummary(), target.getDisputeSummary()));
        target.setTrialProcessSummary(firstNonBlank(source.getTrialProcessSummary(), target.getTrialProcessSummary()));
        target.setFactsSummary(firstNonBlank(source.getFactsSummary(), target.getFactsSummary()));
        target.setIssuesSummary(firstNonBlank(source.getIssuesSummary(), target.getIssuesSummary()));
        target.setReasoningSummary(firstNonBlank(source.getReasoningSummary(), target.getReasoningSummary()));
        target.setResultSummary(firstNonBlank(source.getResultSummary(), target.getResultSummary()));
        target.setLegalBasisText(firstNonBlank(source.getLegalBasisText(), target.getLegalBasisText()));
        target.setCaseDetailText(firstNonBlank(source.getCaseDetailText(), target.getCaseDetailText()));
        target.setRawText(firstNonBlank(source.getRawText(), target.getRawText()));
        target.setDisplayText(firstNonBlank(source.getDisplayText(), target.getDisplayText()));
        target.setCleanText(firstNonBlank(source.getCleanText(), target.getCleanText()));
        target.setCourtLevel(firstNonBlank(source.getCourtLevel(), target.getCourtLevel(), detectCourtLevel(target.getCourtName())));
        target.setProvince(firstNonBlank(source.getProvince(), target.getProvince(), detectProvince(target.getCourtName())));
        target.setCity(firstNonBlank(source.getCity(), target.getCity(), detectCity(target.getCourtName())));
        target.setParserVersion(firstNonBlank(source.getParserVersion(), target.getParserVersion(), "legal-document-manual-v1"));
        target.setParseStatus(source.getParseStatus() == null ? firstNonBlankInteger(target.getParseStatus(), 1) : source.getParseStatus());
        target.setClassifyStatus(source.getClassifyStatus() == null ? firstNonBlankInteger(target.getClassifyStatus(), 1) : source.getClassifyStatus());
        target.setExtractConfidence(source.getExtractConfidence() == null ? firstNonBlankBigDecimal(target.getExtractConfidence(), BigDecimal.valueOf(0.90)) : source.getExtractConfidence());
        target.setDocQualityScore(source.getDocQualityScore() == null ? firstNonBlankBigDecimal(target.getDocQualityScore(), BigDecimal.valueOf(cleanTextLength(target) >= 600 ? 0.88 : 0.76)) : source.getDocQualityScore());
        target.setStatus(source.getStatus() == null ? firstNonBlankInteger(target.getStatus(), 1) : source.getStatus());
    }

    private void clearRelatedRecords(Long documentId) {
        if (documentId == null) {
            return;
        }
        List<Long> chunkIds = chunkMapper.selectList(new LambdaQueryWrapper<LegalDocumentChunk>()
                        .select(LegalDocumentChunk::getId)
                        .eq(LegalDocumentChunk::getDocumentId, documentId))
                .stream()
                .map(LegalDocumentChunk::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!chunkIds.isEmpty()) {
            chunkIndexMapper.delete(new LambdaQueryWrapper<LegalDocumentChunkIndex>()
                    .in(LegalDocumentChunkIndex::getChunkId, chunkIds));
        }
        chunkMapper.delete(new LambdaQueryWrapper<LegalDocumentChunk>()
                .eq(LegalDocumentChunk::getDocumentId, documentId));
        partyMapper.delete(new LambdaQueryWrapper<LegalDocumentParty>()
                .eq(LegalDocumentParty::getDocumentId, documentId));
        referenceMapper.delete(new LambdaQueryWrapper<LegalDocumentReference>()
                .eq(LegalDocumentReference::getDocumentId, documentId));
        extMapper.delete(new LambdaQueryWrapper<LegalDocumentExt>()
                .eq(LegalDocumentExt::getDocumentId, documentId));
    }

    private String mergeDocumentTags(Collection<String> extracted, String manualText) {
        List<String> values = new ArrayList<>();
        if (!CollectionUtils.isEmpty(extracted)) {
            values.addAll(extracted);
        }
        if (StringUtils.hasText(manualText)) {
            values.addAll(List.of(manualText.split("[,，；;、\\s]+")));
        }
        return joinDistinct(values);
    }

    private Integer firstNonBlankInteger(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    private BigDecimal firstNonBlankBigDecimal(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private int cleanTextLength(LegalDocument document) {
        return document.getCleanText() == null ? 0 : document.getCleanText().length();
    }

    private void mergeManualDocument(LegalDocument target, LegalDocument source) {
        target.setSourceType(source.getSourceType());
        target.setSourceName(source.getSourceName());
        target.setSourceUrl(source.getSourceUrl());
        target.setSourceReliability(source.getSourceReliability());
        target.setSourceFileName(source.getSourceFileName());
        target.setTitle(source.getTitle());
        target.setCaseNumber(source.getCaseNumber());
        target.setCourtName(source.getCourtName());
        target.setDocumentType(source.getDocumentType());
        target.setDocumentSubtype(source.getDocumentSubtype());
        target.setCaseType(source.getCaseType());
        target.setCategoryId(source.getCategoryId());
        target.setCauseRaw(source.getCauseRaw());
        target.setCauseNormalized(source.getCauseNormalized());
        target.setBusinessDomain(source.getBusinessDomain());
        target.setProcedureDomain(source.getProcedureDomain());
        target.setTrialStageNormalized(source.getTrialStageNormalized());
        target.setSpecialProcedureType(source.getSpecialProcedureType());
        target.setApplicationType(source.getApplicationType());
        target.setDecisionResult(source.getDecisionResult());
        target.setDecisionDate(source.getDecisionDate());
        target.setEffectiveDate(source.getEffectiveDate());
        target.setClaimsSummary(source.getClaimsSummary());
        target.setDefenseSummary(source.getDefenseSummary());
        target.setDisputeSummary(source.getDisputeSummary());
        target.setTrialProcessSummary(source.getTrialProcessSummary());
        target.setFactsSummary(source.getFactsSummary());
        target.setIssuesSummary(source.getIssuesSummary());
        target.setReasoningSummary(source.getReasoningSummary());
        target.setResultSummary(source.getResultSummary());
        target.setLegalBasisText(source.getLegalBasisText());
        target.setKeywords(source.getKeywords());
        target.setAliases(source.getAliases());
        target.setSummary(source.getSummary());
        target.setCaseDetailText(source.getCaseDetailText());
        target.setDisplayText(source.getDisplayText());
        target.setRawText(source.getRawText());
        target.setCleanText(source.getCleanText());
        target.setParserVersion(source.getParserVersion());
        target.setParseStatus(source.getParseStatus());
        target.setClassifyStatus(source.getClassifyStatus());
        target.setExtractConfidence(source.getExtractConfidence());
        target.setDocQualityScore(source.getDocQualityScore());
        target.setStatus(source.getStatus());
    }

    private void prepareManualDocument(LegalDocument document) {
        document.setSourceType(normalizeOptionalText(firstNonBlank(document.getSourceType(), "manual_entry")));
        document.setSourceName(normalizeOptionalText(firstNonBlank(document.getSourceName(), "手工录入")));
        document.setSourceUrl(normalizeOptionalText(document.getSourceUrl()));
        document.setSourceFileName(normalizeOptionalText(document.getSourceFileName()));
        document.setTitle(normalizeOptionalText(firstNonBlank(
                document.getTitle(),
                document.getCaseNumber(),
                document.getDocumentSubtype(),
                document.getDocumentType(),
                "专题文书"
        )));
        document.setTitleNormalized(normalizeForKey(document.getTitle()));
        document.setCaseNumber(normalizeOptionalText(document.getCaseNumber()));
        document.setCourtName(normalizeOptionalText(document.getCourtName()));
        document.setDocumentType(normalizeOptionalText(document.getDocumentType()));
        document.setDocumentSubtype(normalizeOptionalText(firstNonBlank(document.getDocumentSubtype(), document.getDocumentType())));
        document.setCaseType(normalizeOptionalText(document.getCaseType()));
        document.setCauseRaw(normalizeOptionalText(document.getCauseRaw()));
        document.setCauseNormalized(normalizeOptionalText(firstNonBlank(document.getCauseNormalized(), document.getCauseRaw())));
        document.setBusinessDomain(normalizeOptionalText(document.getBusinessDomain()));
        document.setProcedureDomain(normalizeOptionalText(document.getProcedureDomain()));
        document.setTrialStageNormalized(normalizeOptionalText(document.getTrialStageNormalized()));
        document.setSpecialProcedureType(normalizeOptionalText(document.getSpecialProcedureType()));
        document.setApplicationType(normalizeOptionalText(document.getApplicationType()));
        document.setDecisionResult(normalizeOptionalText(document.getDecisionResult()));
        document.setClaimsSummary(normalizeOptionalText(document.getClaimsSummary()));
        document.setDefenseSummary(normalizeOptionalText(document.getDefenseSummary()));
        document.setDisputeSummary(normalizeOptionalText(document.getDisputeSummary()));
        document.setTrialProcessSummary(normalizeOptionalText(document.getTrialProcessSummary()));
        document.setFactsSummary(normalizeOptionalText(document.getFactsSummary()));
        document.setIssuesSummary(normalizeOptionalText(document.getIssuesSummary()));
        document.setReasoningSummary(normalizeOptionalText(document.getReasoningSummary()));
        document.setResultSummary(normalizeOptionalText(document.getResultSummary()));
        document.setLegalBasisText(normalizeOptionalText(document.getLegalBasisText()));
        document.setKeywords(normalizeOptionalTags(document.getKeywords()));
        document.setAliases(normalizeOptionalTags(document.getAliases()));

        String rawText = firstNonBlank(
                document.getRawText(),
                document.getDisplayText(),
                document.getCleanText(),
                document.getCaseDetailText(),
                buildManualDocumentBody(document)
        );
        if (!StringUtils.hasText(rawText)) {
            throw new IllegalArgumentException("请至少填写正文内容或摘要信息");
        }

        document.setRawText(rawText.trim());
        document.setDisplayText(buildDisplayText(rawText));
        document.setCleanText(normalizeText(firstNonBlank(document.getCleanText(), document.getDisplayText(), rawText)));
        document.setCaseDetailText(normalizeOptionalText(firstNonBlank(document.getCaseDetailText(), buildCaseDetailText(document))));
        document.setSummary(normalizeOptionalText(firstNonBlank(document.getSummary(), buildSummary(document))));
        document.setRetrievalText(buildDocumentRetrievalText(document));
        document.setCourtLevel(normalizeOptionalText(firstNonBlank(document.getCourtLevel(), detectCourtLevel(document.getCourtName()))));
        document.setProvince(normalizeOptionalText(firstNonBlank(document.getProvince(), detectProvince(document.getCourtName()))));
        document.setCity(normalizeOptionalText(firstNonBlank(document.getCity(), detectCity(document.getCourtName()))));
        document.setDecisionResultNormalized(normalizeOptionalText(firstNonBlank(
                document.getDecisionResultNormalized(),
                normalizeDecisionResult(document.getDecisionResult())
        )));

        if (document.getSourceReliability() == null) {
            document.setSourceReliability(95);
        }
        if (!StringUtils.hasText(document.getParserVersion())) {
            document.setParserVersion("legal-document-manual-v1");
        }
        if (document.getParseStatus() == null) {
            document.setParseStatus(1);
        }
        if (document.getClassifyStatus() == null) {
            document.setClassifyStatus(1);
        }
        if (document.getExtractConfidence() == null) {
            document.setExtractConfidence(BigDecimal.valueOf(0.90));
        }
        if (document.getDocQualityScore() == null) {
            document.setDocQualityScore(BigDecimal.valueOf(document.getCleanText().length() >= 600 ? 0.88 : 0.76));
        }
        if (document.getStatus() == null) {
            document.setStatus(1);
        }
    }

    private void replaceDocumentChunks(LegalDocument document) {
        List<Long> chunkIds = chunkMapper.selectList(new LambdaQueryWrapper<LegalDocumentChunk>()
                        .select(LegalDocumentChunk::getId)
                        .eq(LegalDocumentChunk::getDocumentId, document.getId()))
                .stream()
                .map(LegalDocumentChunk::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!chunkIds.isEmpty()) {
            chunkIndexMapper.delete(new LambdaQueryWrapper<LegalDocumentChunkIndex>()
                    .in(LegalDocumentChunkIndex::getChunkId, chunkIds));
        }
        chunkMapper.delete(new LambdaQueryWrapper<LegalDocumentChunk>()
                .eq(LegalDocumentChunk::getDocumentId, document.getId()));

        String chunkSource = firstNonBlank(document.getDisplayText(), document.getCleanText(), document.getRawText());
        if (!StringUtils.hasText(chunkSource)) {
            return;
        }

        Section section = new Section(
                "body",
                firstNonBlank(document.getDocumentSubtype(), document.getDocumentType(), "正文"),
                firstNonBlank(document.getTitle(), document.getCaseNumber(), "专题文书"),
                chunkSource
        );
        List<TextSlice> slices = sliceText(chunkSource, CHUNK_SIZE, CHUNK_OVERLAP);
        int index = 0;
        for (TextSlice slice : slices) {
            LegalDocumentChunk chunk = new LegalDocumentChunk();
            chunk.setDocumentId(document.getId());
            chunk.setChunkStrategy("manual_semantic");
            chunk.setChunkVersion("manual-v1");
            chunk.setSectionType(section.type);
            chunk.setSectionTitle(section.title);
            chunk.setHeaderPath(section.headerPath);
            chunk.setRetrievalRole("body");
            chunk.setSectionWeight(BigDecimal.ONE);
            chunk.setSectionOrder(0);
            chunk.setChunkIndex(index++);
            chunk.setCharStartOffset(slice.start);
            chunk.setCharEndOffset(slice.end);
            chunk.setChunkHash(sha256Hex((document.getTitle() + "|" + slice.text).getBytes(StandardCharsets.UTF_8)));
            chunk.setChunkText(slice.text);
            chunk.setRetrievalText(buildChunkRetrievalText(document, section, slice.text));
            chunk.setCharCount(slice.text.length());
            chunk.setTokenCount(estimateTokenCount(slice.text));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sourceType", "manual_entry");
            metadata.put("documentType", document.getDocumentType());
            metadata.put("documentSubtype", document.getDocumentSubtype());
            metadata.put("caseType", document.getCaseType());
            metadata.put("categoryId", document.getCategoryId());
            chunk.setMetadataJson(JSON.toJSONString(metadata));
            chunkMapper.insert(chunk);
        }
    }

    private String buildManualDocumentBody(LegalDocument document) {
        List<String> parts = new ArrayList<>();
        addLabelled(parts, "标题", document.getTitle());
        addLabelled(parts, "案号", document.getCaseNumber());
        addLabelled(parts, "法院", document.getCourtName());
        addLabelled(parts, "文书类型", firstNonBlank(document.getDocumentSubtype(), document.getDocumentType()));
        addLabelled(parts, "案件类型", document.getCaseType());
        addLabelled(parts, "案由", firstNonBlank(document.getCauseNormalized(), document.getCauseRaw()));
        addLabelled(parts, "争议概况", firstNonBlank(document.getDisputeSummary(), document.getSummary()));
        addLabelled(parts, "事实摘要", document.getFactsSummary());
        addLabelled(parts, "争议焦点", document.getIssuesSummary());
        addLabelled(parts, "法院理由", document.getReasoningSummary());
        addLabelled(parts, "裁判结果", firstNonBlank(document.getResultSummary(), document.getDecisionResult()));
        addLabelled(parts, "法律依据", document.getLegalBasisText());
        return String.join("\n", parts);
    }

    @Override
    public LegalDocumentImportResponse importFiles(MultipartFile[] files, Long categoryId, boolean rebuildIndex) {
        LegalDocumentImportResponse response = new LegalDocumentImportResponse();
        if (files == null || files.length == 0) {
            response.setIndexMessage("未选择任何文件");
            return response;
        }

        List<Category> categories = categoryService.getAllCategories();
        DedupState dedupState = buildDedupState();
        response.setTotalFiles(files.length);

        for (MultipartFile file : files) {
            LegalDocumentImportResult result = importSingleFile(file, categoryId, categories, dedupState);
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
                    response.isReindexed() ? "文书导入成功，索引已更新" : "文书导入成功，但索引更新失败"
            ));
        } else if (response.getImportedCount() > 0) {
            response.setIndexMessage("文书已入库，未执行索引更新");
        } else {
            response.setIndexMessage("没有成功导入的文书，未触发索引更新");
        }
        return response;
    }

    @Override
    public LegalDocumentImportResponse importOfficialDocuments(Long categoryId, boolean rebuildIndex) {
        LegalDocumentImportResponse response = new LegalDocumentImportResponse();
        List<Path> officialFiles = resolveOfficialDocumentFiles();
        if (officialFiles.isEmpty()) {
            response.setIndexMessage("未发现可导入的官方文书");
            return response;
        }

        List<Category> categories = categoryService.getAllCategories();
        DedupState dedupState = buildDedupState();
        response.setTotalFiles(officialFiles.size());
        int knowledgeRoutedCount = 0;

        for (Path path : officialFiles) {
            LegalDocumentImportResult result;
            try {
                OfficialSourceMeta meta = resolveOfficialSourceMeta(path);
                if (shouldRouteOfficialSourceToKnowledge(meta)) {
                    result = buildKnowledgeRoutedResult(path, meta);
                    knowledgeRoutedCount++;
                } else {
                    result = importOfficialSource(path, categoryId, categories, dedupState);
                }
            } catch (Exception ex) {
                result = new LegalDocumentImportResult();
                result.setFileName(path == null ? "unknown" : path.getFileName().toString());
                result.setSuccess(false);
                result.setMessage("官方来源识别失败: " + ex.getMessage());
            }
            response.getResults().add(result);
            if (result.isSuccess()) {
                response.setImportedCount(response.getImportedCount() + 1);
            } else if (result.isSkipped()) {
                response.setSkippedCount(response.getSkippedCount() + 1);
            } else {
                response.setFailedCount(response.getFailedCount() + 1);
            }
        }

        int migratedCount = migrateMisclassifiedOfficialDocuments();

        if (response.getImportedCount() > 0 && rebuildIndex) {
            Map<String, Object> rebuildInfo = rebuildLegalIndex();
            response.setReindexed(Boolean.TRUE.equals(rebuildInfo.get("success")));
            response.setIndexMessage(buildOfficialDocumentSyncMessage(response.isReindexed(), knowledgeRoutedCount, migratedCount));
        } else if (response.getImportedCount() > 0) {
            response.setIndexMessage(buildOfficialDocumentSyncMessage(null, knowledgeRoutedCount, migratedCount));
        } else if (knowledgeRoutedCount > 0 || migratedCount > 0) {
            response.setIndexMessage(buildOfficialDocumentOnlyKnowledgeMessage(knowledgeRoutedCount, migratedCount));
        } else if (response.getSkippedCount() > 0) {
            response.setIndexMessage("官方文书已存在，已刷新元数据");
        } else {
            response.setIndexMessage("没有成功导入任何官方文书");
        }
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    protected LegalDocumentImportResult importOfficialSource(Path filePath, Long categoryId, List<Category> categories,
                                                             DedupState dedupState) {
        LegalDocumentImportResult result = new LegalDocumentImportResult();
        result.setFileName(filePath == null ? "unknown" : filePath.getFileName().toString());

        if (filePath == null || !Files.isRegularFile(filePath)) {
            result.setSuccess(false);
            result.setMessage("官方文书不存在，无法导入");
            return result;
        }

        try {
            OfficialSourceMeta meta = resolveOfficialSourceMeta(filePath);
            String virtualFileName = buildOfficialImportFileName(filePath, meta);
            byte[] bytes = Files.readAllBytes(filePath);
            MultipartFile virtualFile = new InMemoryMultipartFile(
                    virtualFileName,
                    detectOfficialContentType(filePath),
                    bytes
            );

            result = importSingleFile(virtualFile, categoryId, categories, dedupState);
            result.setFileName(filePath.getFileName().toString());

            Long targetDocumentId = result.isSuccess() ? result.getDocumentId() : result.getExistingDocumentId();
            if (targetDocumentId != null) {
                LegalDocument document = enrichOfficialDocument(targetDocumentId, filePath, meta, categoryId, categories);
                if (document != null) {
                    result.setDocumentId(document.getId());
                    result.setTitle(document.getTitle());
                    result.setCaseNumber(document.getCaseNumber());
                    result.setCategoryId(document.getCategoryId());
                    result.setCategoryName(resolveCategoryName(document.getCategoryId(), categories));
                    result.setCharCount(document.getCleanText() == null ? 0 : document.getCleanText().length());
                    if (result.isSuccess()) {
                        result.setMessage("官方文书已入库，并已完成元数据标准化");
                    } else if (result.isSkipped()) {
                        result.setMessage("官方文书已存在，已校正元数据");
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            result.setSuccess(false);
            result.setMessage("官方文书导入失败: " + ex.getMessage());
            return result;
        }
    }

    private boolean shouldRouteOfficialSourceToKnowledge(OfficialSourceMeta meta) {
        if (meta == null) {
            return false;
        }
        String rawGroup = normalizeOfficialRawGroup(meta.rawGroup());
        return StringUtils.hasText(rawGroup) && !OFFICIAL_JUDICIAL_DOCUMENT_GROUPS.contains(rawGroup);
    }

    private LegalDocumentImportResult buildKnowledgeRoutedResult(Path filePath, OfficialSourceMeta meta) {
        LegalDocumentImportResult result = new LegalDocumentImportResult();
        result.setFileName(filePath == null ? "unknown" : filePath.getFileName().toString());
        result.setSkipped(true);
        result.setSuccess(false);
        result.setTitle(firstNonBlank(meta.documentName(), stripExtension(result.getFileName())));
        result.setMessage("已识别为官方知识内容，不再导入法律文书库");
        return result;
    }

    private String buildOfficialDocumentSyncMessage(Boolean reindexed, int knowledgeRoutedCount, int migratedCount) {
        String baseMessage;
        if (Boolean.TRUE.equals(reindexed)) {
            baseMessage = "正式法律文书同步完成，索引已刷新";
        } else if (Boolean.FALSE.equals(reindexed)) {
            baseMessage = "正式法律文书同步完成，但索引刷新失败";
        } else {
            baseMessage = "正式法律文书同步完成，本次未执行索引刷新";
        }
        List<String> notes = new ArrayList<>();
        if (knowledgeRoutedCount > 0) {
            notes.add("新增分流 " + knowledgeRoutedCount + " 条官方知识内容到知识库入口");
        }
        if (migratedCount > 0) {
            notes.add("已修复并迁出 " + migratedCount + " 条错入文书库的官方文章");
        }
        return notes.isEmpty() ? baseMessage : baseMessage + "；" + String.join("；", notes);
    }

    private String buildOfficialDocumentOnlyKnowledgeMessage(int knowledgeRoutedCount, int migratedCount) {
        List<String> notes = new ArrayList<>();
        if (knowledgeRoutedCount > 0) {
            notes.add("本地官方源中 " + knowledgeRoutedCount + " 条内容属于知识资料，未再导入文书库");
        }
        if (migratedCount > 0) {
            notes.add("已修复并迁出 " + migratedCount + " 条错入文书库的官方文章");
        }
        if (notes.isEmpty()) {
            return "未发现可导入的正式法律文书";
        }
        return "未发现可导入的正式法律文书；" + String.join("；", notes);
    }

    private int migrateMisclassifiedOfficialDocuments() {
        List<LegalDocument> documents = this.list(new LambdaQueryWrapper<LegalDocument>()
                .eq(LegalDocument::getDeleted, 0)
                .eq(LegalDocument::getSourceType, "official_crawl"));
        if (CollectionUtils.isEmpty(documents)) {
            return 0;
        }

        int migratedCount = 0;
        for (LegalDocument document : documents) {
            LegalDocumentExt ext = extMapper.selectOne(new LambdaQueryWrapper<LegalDocumentExt>()
                    .eq(LegalDocumentExt::getDocumentId, document.getId())
                    .last("limit 1"));
            OfficialSourceMeta meta = resolveOfficialSourceMeta(document, ext);
            if (!shouldMigrateOfficialDocumentToKnowledge(document, meta)) {
                continue;
            }

            JSONObject record = buildOfficialKnowledgeRecordFromDocument(document, meta);
            LegalKnowledgeImportResult importResult = legalKnowledgeService.importRecord(
                    record,
                    firstNonBlank(document.getSourceFileName(), document.getTitle(), "official-source"),
                    document.getCategoryId()
            );
            if (!importResult.isSuccess() && !importResult.isSkipped()) {
                continue;
            }
            clearRelatedRecords(document.getId());
            this.removeById(document.getId());
            migratedCount++;
        }
        return migratedCount;
    }

    private boolean shouldMigrateOfficialDocumentToKnowledge(LegalDocument document, OfficialSourceMeta meta) {
        if (document == null || !"official_crawl".equals(document.getSourceType())) {
            return false;
        }
        if (isFormalJudicialInstrument(document, meta)) {
            return false;
        }
        if (meta != null && shouldRouteOfficialSourceToKnowledge(meta)) {
            return true;
        }
        String typeCorpus = String.join("\n",
                firstNonBlank(document.getDocumentType(), ""),
                firstNonBlank(document.getDocumentSubtype(), ""),
                firstNonBlank(document.getTitle(), "")
        );
        if (containsAny(typeCorpus, KNOWLEDGE_ONLY_OFFICIAL_KEYWORDS)) {
            return true;
        }
        return typeCorpus.contains("普法")
                || typeCorpus.contains("典型案例")
                || typeCorpus.contains("指导案例")
                || typeCorpus.contains("案例解读")
                || typeCorpus.contains("以案说法")
                || typeCorpus.contains("法律法规")
                || typeCorpus.contains("司法解释");
    }

    private boolean isFormalJudicialInstrument(LegalDocument document, OfficialSourceMeta meta) {
        String rawGroup = meta == null ? "" : normalizeOfficialRawGroup(meta.rawGroup());
        if (StringUtils.hasText(rawGroup)) {
            return OFFICIAL_JUDICIAL_DOCUMENT_GROUPS.contains(rawGroup);
        }
        String corpus = String.join("\n",
                firstNonBlank(document.getTitle(), ""),
                firstNonBlank(document.getDocumentType(), ""),
                firstNonBlank(document.getDocumentSubtype(), ""),
                meta == null ? "" : firstNonBlank(meta.documentName(), meta.knowledgeSubtype(), meta.knowledgeType(), "")
        );
        if (false) {
        boolean hasInstrumentKeyword = corpus.contains("判决书")
                || corpus.contains("裁定书")
                || corpus.contains("调解书")
                || corpus.contains("决定书")
                || corpus.contains("通知书");
        boolean hasCaseNumber = StringUtils.hasText(document.getCaseNumber()) && document.getCaseNumber().contains("号");
        boolean hasCourt = StringUtils.hasText(document.getCourtName()) && document.getCourtName().contains("法院");
        return hasInstrumentKeyword && (hasCaseNumber || hasCourt);
        }
        if (!containsAny(corpus, FORMAL_JUDICIAL_DOCUMENT_KEYWORDS)) {
            return false;
        }
        if (containsAny(corpus, KNOWLEDGE_ONLY_OFFICIAL_KEYWORDS)) {
            return false;
        }
        String authorityCorpus = String.join("\n",
                firstNonBlank(document.getCourtName(), ""),
                firstNonBlank(document.getSourceName(), ""),
                meta == null ? "" : firstNonBlank(meta.issuer(), meta.sourceName(), "")
        );
        boolean hasAuthority = containsAny(authorityCorpus, JUDICIAL_AUTHORITY_KEYWORDS);
        boolean hasCaseNumber = StringUtils.hasText(document.getCaseNumber()) && document.getCaseNumber().contains("号");
        return hasAuthority && hasCaseNumber;
    }

    private String normalizeOfficialRawGroup(String rawGroup) {
        return firstNonBlank(rawGroup, "").trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String corpus, Collection<String> keywords) {
        if (!StringUtils.hasText(corpus) || CollectionUtils.isEmpty(keywords)) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && corpus.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private JSONObject buildOfficialKnowledgeRecordFromDocument(LegalDocument document, OfficialSourceMeta meta) {
        JSONObject record = new JSONObject();
        record.put("sourceType", "official_crawl");
        record.put("sourceName", firstNonBlank(meta == null ? null : meta.sourceName(), meta == null ? null : meta.issuer(),
                document.getSourceName(), document.getCourtName(), "官方来源"));
        record.put("sourceUrl", firstNonBlank(meta == null ? null : meta.url(), document.getSourceUrl()));
        record.put("sourceReliability", 1);
        record.put("documentName", firstNonBlank(meta == null ? null : meta.documentName(), document.getTitle()));
        record.put("title", firstNonBlank(meta == null ? null : meta.documentName(), document.getTitle()));
        record.put("knowledgeType", mapOfficialKnowledgeType(meta, document));
        record.put("knowledgeSubtype", firstNonBlank(
                meta == null ? null : meta.knowledgeSubtype(),
                meta == null ? null : meta.knowledgeType(),
                document.getDocumentSubtype(),
                document.getDocumentType()
        ));
        record.put("documentLevel", firstNonBlank(meta == null ? null : meta.documentLevel(), document.getDocumentSubtype(), document.getDocumentType()));
        record.put("issuer", firstNonBlank(meta == null ? null : meta.issuer(), document.getSourceName(), document.getCourtName()));
        record.put("businessDomain", firstNonBlank(meta == null ? null : meta.businessDomain(), document.getBusinessDomain()));
        record.put("topic", firstNonBlank(meta == null ? null : meta.topic(), document.getCauseRaw(), document.getCauseNormalized(), document.getTitle()));
        record.put("effectiveDate", formatDateForKnowledge(document.getEffectiveDate(), document.getDecisionDate(), meta == null ? null : meta.effectiveDate()));
        record.put("currentStatus", firstNonBlank(meta == null ? null : meta.currentStatus(), "公开发布"));
        String content = firstNonBlank(document.getDisplayText(), document.getCleanText(), document.getRawText(), document.getCaseDetailText(), document.getSummary());
        record.put("content", content);
        record.put("summary", legalDocumentTextProcessor.ellipsize(content, SUMMARY_LIMIT));
        return record;
    }

    private OfficialSourceMeta resolveOfficialSourceMeta(LegalDocument document, LegalDocumentExt ext) {
        if (ext == null || !StringUtils.hasText(ext.getExtJson())) {
            return new OfficialSourceMeta(
                    document == null ? null : document.getTitle(),
                    document == null ? null : document.getSourceUrl(),
                    document == null ? null : document.getSourceName(),
                    document == null ? null : document.getCourtName(),
                    document == null ? null : document.getDocumentType(),
                    document == null ? null : document.getDocumentSubtype(),
                    null,
                    document != null && document.getEffectiveDate() != null ? document.getEffectiveDate().toString() : null,
                    null,
                    document == null ? null : document.getBusinessDomain(),
                    document == null ? null : firstNonBlank(document.getCauseRaw(), document.getCauseNormalized()),
                    null
            );
        }
        try {
            JSONObject payload = JSON.parseObject(ext.getExtJson());
            return new OfficialSourceMeta(
                    firstNonBlank(document == null ? null : document.getTitle(), payload.getString("officialDocumentName")),
                    firstNonBlank(payload.getString("officialSourceUrl"), document == null ? null : document.getSourceUrl()),
                    firstNonBlank(payload.getString("officialSourceName"), document == null ? null : document.getSourceName()),
                    firstNonBlank(payload.getString("officialIssuer"), document == null ? null : document.getCourtName()),
                    firstNonBlank(payload.getString("officialKnowledgeType"), document == null ? null : document.getDocumentType()),
                    firstNonBlank(payload.getString("officialKnowledgeSubtype"), document == null ? null : document.getDocumentSubtype()),
                    payload.getString("officialDocumentLevel"),
                    firstNonBlank(document != null && document.getEffectiveDate() != null ? document.getEffectiveDate().toString() : null,
                            payload.getString("officialEffectiveDate")),
                    payload.getString("officialCurrentStatus"),
                    firstNonBlank(payload.getString("officialBusinessDomain"), document == null ? null : document.getBusinessDomain()),
                    firstNonBlank(payload.getString("officialTopic"), document == null ? null : document.getCauseRaw()),
                    payload.getString("officialRawGroup")
            );
        } catch (Exception ignored) {
            return new OfficialSourceMeta(
                    document == null ? null : document.getTitle(),
                    document == null ? null : document.getSourceUrl(),
                    document == null ? null : document.getSourceName(),
                    document == null ? null : document.getCourtName(),
                    document == null ? null : document.getDocumentType(),
                    document == null ? null : document.getDocumentSubtype(),
                    null,
                    document != null && document.getEffectiveDate() != null ? document.getEffectiveDate().toString() : null,
                    null,
                    document == null ? null : document.getBusinessDomain(),
                    document == null ? null : firstNonBlank(document.getCauseRaw(), document.getCauseNormalized()),
                    null
            );
        }
    }

    private String mapOfficialKnowledgeType(OfficialSourceMeta meta, LegalDocument document) {
        String rawGroup = meta == null ? "" : firstNonBlank(meta.rawGroup(), "");
        if ("laws".equals(rawGroup)) {
            return "法律法规";
        }
        if ("judicial_interpretations".equals(rawGroup)) {
            return "司法解释";
        }
        if ("guiding_cases".equals(rawGroup)) {
            return "案例规则";
        }
        if ("guides".equals(rawGroup)) {
            return "规范知识";
        }
        String fallback = firstNonBlank(
                meta == null ? null : meta.knowledgeType(),
                document == null ? null : document.getDocumentType(),
                "规范知识"
        );
        return fallback;
    }

    private String formatDateForKnowledge(LocalDate primary, LocalDate secondary, String fallback) {
        if (primary != null) {
            return primary.toString();
        }
        if (secondary != null) {
            return secondary.toString();
        }
        return fallback;
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
                    asText(rebuildResult.get("message"), success ? "法律 RAG 索引已更新" : "法律 RAG 索引刷新失败")
            ));
            return response;
        } catch (Exception ex) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("message", "法律 RAG 索引刷新失败: " + ex.getMessage());
            return response;
        }
    }

    @Override
    public Page<LegalDocumentIndexChunkVO> getIndexChunkPage(int pageNum, int pageSize) {
        Page<LegalDocumentChunk> chunkPage = new Page<>(pageNum, pageSize);
        chunkMapper.selectPage(chunkPage, new LambdaQueryWrapper<LegalDocumentChunk>()
                .eq(LegalDocumentChunk::getDeleted, 0)
                .orderByAsc(LegalDocumentChunk::getDocumentId)
                .orderByAsc(LegalDocumentChunk::getSectionOrder)
                .orderByAsc(LegalDocumentChunk::getChunkIndex));

        Set<Long> documentIds = chunkPage.getRecords().stream()
                .map(LegalDocumentChunk::getDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, LegalDocument> documentMap = loadDocumentMap(documentIds);
        Map<Long, String> categoryMap = loadCategoryNameMap(documentMap.values());

        Page<LegalDocumentIndexChunkVO> resultPage = new Page<>(pageNum, pageSize, chunkPage.getTotal());
        List<LegalDocumentIndexChunkVO> records = new ArrayList<>();
        for (LegalDocumentChunk chunk : chunkPage.getRecords()) {
            LegalDocument document = documentMap.get(chunk.getDocumentId());
            if (document == null) {
                continue;
            }
            LegalDocumentIndexChunkVO vo = new LegalDocumentIndexChunkVO();
            vo.setChunkId(chunk.getId());
            vo.setDocumentId(document.getId());
            vo.setItemId(document.getId());
            vo.setChunkIndex(chunk.getChunkIndex());
            vo.setTitle(document.getTitle());
            vo.setSummary(firstNonBlank(document.getSummary(), document.getResultSummary(), document.getFactsSummary()));
            vo.setCategoryId(document.getCategoryId());
            vo.setCategoryName(document.getCategoryId() == null ? null : categoryMap.get(document.getCategoryId()));
            vo.setTags(buildIndexTags(document));
            vo.setDocumentType(document.getDocumentType());
            vo.setDocumentSubtype(firstNonBlank(document.getDocumentSubtype(), document.getDocumentType()));
            vo.setCaseType(document.getCaseType());
            vo.setCause(firstNonBlank(document.getCauseNormalized(), document.getCauseRaw()));
            vo.setBusinessDomain(document.getBusinessDomain());
            vo.setProcedureDomain(document.getProcedureDomain());
            vo.setCourtName(document.getCourtName());
            vo.setCourtLevel(document.getCourtLevel());
            vo.setDecisionDate(document.getDecisionDate());
            vo.setDecisionResult(firstNonBlank(document.getDecisionResultNormalized(), document.getDecisionResult(), document.getResultSummary()));
            vo.setKeywords(document.getKeywords());
            vo.setAliases(document.getAliases());
            vo.setIssueTags(document.getIssueTags());
            vo.setResultTags(document.getResultTags());
            vo.setSourceReliability(document.getSourceReliability());
            vo.setSource("legal_document_db");
            vo.setSourceName(document.getSourceName());
            vo.setSourceUrl(document.getSourceUrl());
            vo.setIssuer(firstNonBlank(document.getSourceName(), document.getCourtName()));
            vo.setEffectiveDate(document.getEffectiveDate());
            vo.setCurrentStatus(firstNonBlank(document.getDecisionResultNormalized(), document.getDecisionResult()));
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
    protected LegalDocumentImportResult importSingleFile(MultipartFile file, Long categoryId, List<Category> categories,
                                                         DedupState dedupState) {
        LegalDocumentImportResult result = new LegalDocumentImportResult();
        result.setFileName(file == null ? "unknown" : file.getOriginalFilename());

        if (file == null || file.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("文件为空，无法导入");
            return result;
        }

        try {
            byte[] bytes = file.getBytes();
            String sha256 = sha256Hex(bytes);
            Map<String, Object> extracted = unwrapData(algorithmClient.extractDocumentText(file));
            boolean supported = Boolean.TRUE.equals(extracted.get("supported"));
            String rawText = normalizeRawText(asText(extracted.get("text"), ""));
            String cleanText = normalizeText(rawText);

            if (!supported) {
                result.setSuccess(false);
                result.setMessage(asText(extracted.get("message"), "当前文件格式暂不支持导入"));
                return result;
            }
            if (!StringUtils.hasText(cleanText)) {
                result.setSuccess(false);
                result.setMessage("文书解析成功，但未提取到可用文本");
                return result;
            }

            LegalDocumentParsedData parsed = parseDocument(file, rawText, cleanText, sha256);
            Category category = resolveCategory(categoryId, categories, parsed);
            parsed.document.setCategoryId(category == null ? null : category.getId());
            parsed.document.setSummary(buildSummary(parsed.document));
            parsed.document.setKeywords(joinDistinct(parsed.keywords));
            parsed.document.setAliases(joinDistinct(parsed.aliases));
            parsed.document.setIssueTags(joinDistinct(parsed.issueTags));
            parsed.document.setResultTags(joinDistinct(parsed.resultTags));
            parsed.document.setRetrievalText(buildDocumentRetrievalText(parsed.document));

            DuplicateMatch duplicateMatch = findDuplicate(parsed.document, dedupState);
            if (duplicateMatch != null) {
                result.setSuccess(false);
                result.setSkipped(true);
                result.setDuplicate(true);
                result.setExistingDocumentId(duplicateMatch.documentId);
                result.setExistingTitle(duplicateMatch.title);
                result.setDocumentId(duplicateMatch.documentId);
                result.setTitle(duplicateMatch.title);
                result.setCaseNumber(duplicateMatch.caseNumber);
                result.setCategoryId(duplicateMatch.categoryId);
                result.setCategoryName(duplicateMatch.categoryName);
                result.setCharCount(cleanText.length());
                result.setMessage("检测到重复文书，已跳过入库");
                return result;
            }

            this.save(parsed.document);
            persistRelatedRecords(parsed);
            registerImportedDocument(parsed.document, category, dedupState);

            result.setSuccess(true);
            result.setDocumentId(parsed.document.getId());
            result.setTitle(parsed.document.getTitle());
            result.setCaseNumber(parsed.document.getCaseNumber());
            result.setCategoryId(parsed.document.getCategoryId());
            result.setCategoryName(category == null ? "未分类" : category.getName());
            result.setCharCount(parsed.document.getCleanText() == null ? 0 : parsed.document.getCleanText().length());
            result.setChunkCount(parsed.chunks.size());
            result.setMessage("导入成功");
            return result;
        } catch (Exception ex) {
            result.setSuccess(false);
            result.setMessage("导入失败: " + ex.getMessage());
            return result;
        }
    }

    private List<Path> resolveOfficialDocumentFiles() {
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
                                && !OFFICIAL_DOCUMENT_EXCLUDED_FILES.contains(path.getFileName().toString());
                    })
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new IllegalStateException("读取官方文书目录失败: " + ex.getMessage(), ex);
        }
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

    private String sanitizeVirtualFileName(String value) {
        String sanitized = firstNonBlank(value, "official-document")
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        return StringUtils.hasText(sanitized) ? sanitized : "official-document";
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

    private LegalDocument enrichOfficialDocument(Long documentId, Path filePath, OfficialSourceMeta meta,
                                                 Long categoryId, List<Category> categories) throws IOException {
        LegalDocument document = this.getById(documentId);
        if (document == null) {
            return null;
        }

        document.setSourceType("official_crawl");
        document.setSourceName(firstNonBlank(meta.sourceName(), meta.issuer(), document.getSourceName()));
        document.setSourceUrl(firstNonBlank(meta.url(), document.getSourceUrl()));
        document.setSourceReliability(1);
        document.setSourceFileName(filePath.getFileName().toString());
        document.setSourceFilePath(filePath.toAbsolutePath().normalize().toString());
        document.setFileExt(firstNonBlank(getFileExtension(filePath.getFileName().toString()), document.getFileExt()));
        document.setFileSize(Files.size(filePath));
        if (StringUtils.hasText(meta.documentName())) {
            document.setTitle(meta.documentName());
            document.setTitleNormalized(normalizeForKey(meta.documentName()));
        }
        if (StringUtils.hasText(meta.businessDomain())) {
            document.setBusinessDomain(meta.businessDomain());
        }
        String documentType = mapOfficialDocumentType(meta);
        if (StringUtils.hasText(documentType)) {
            document.setDocumentType(documentType);
            document.setDocumentTypeRaw(documentType);
        }
        String documentSubtype = mapOfficialDocumentSubtype(meta);
        if (StringUtils.hasText(documentSubtype)) {
            document.setDocumentSubtype(documentSubtype);
            document.setDocumentSubtypeRaw(documentSubtype);
        }
        String caseType = detectOfficialCaseType(meta, document);
        if (StringUtils.hasText(caseType)) {
            document.setCaseType(caseType);
        }
        String procedureDomain = detectOfficialProcedureDomain(meta, document);
        if (StringUtils.hasText(procedureDomain)) {
            document.setProcedureDomain(procedureDomain);
        }
        if (!StringUtils.hasText(document.getCourtName())) {
            document.setCourtName(firstNonBlank(meta.issuer(), meta.sourceName()));
        }
        if (!StringUtils.hasText(document.getCauseRaw()) && StringUtils.hasText(meta.topic())) {
            document.setCauseRaw(meta.topic());
            document.setCauseNormalized(normalizeCause(meta.topic()));
        }

        LocalDate metaDate = parseDate(firstNonBlank(meta.effectiveDate(), ""));
        if (metaDate != null) {
            if (document.getEffectiveDate() == null || !"guiding_cases".equals(meta.rawGroup())) {
                document.setEffectiveDate(metaDate);
            }
            if (document.getDecisionDate() == null && "guiding_cases".equals(meta.rawGroup())) {
                document.setDecisionDate(metaDate);
            }
        }

        Long resolvedCategoryId = categoryId;
        if (resolvedCategoryId == null) {
            resolvedCategoryId = DrugVerticalDomainSupport.matchCategoryId(
                    categories,
                    firstNonBlank(meta.documentName(), document.getTitle(), ""),
                    firstNonBlank(meta.topic(), document.getCauseRaw(), document.getCauseNormalized(), ""),
                    firstNonBlank(meta.businessDomain(), document.getBusinessDomain(), ""),
                    firstNonBlank(document.getSummary(), document.getCleanText(), "")
            );
        }
        if (resolvedCategoryId != null) {
            document.setCategoryId(resolvedCategoryId);
        }

        document.setSummary(buildSummary(document));
        document.setCaseDetailText(buildCaseDetailText(document));
        document.setRetrievalText(buildDocumentRetrievalText(document));
        this.updateById(document);
        upsertOfficialDocumentExt(document.getId(), meta, filePath);
        return this.getById(document.getId());
    }

    private void upsertOfficialDocumentExt(Long documentId, OfficialSourceMeta meta, Path filePath) {
        LegalDocumentExt ext = extMapper.selectOne(new LambdaQueryWrapper<LegalDocumentExt>()
                .eq(LegalDocumentExt::getDocumentId, documentId)
                .last("limit 1"));

        JSONObject payload = new JSONObject();
        if (ext != null && StringUtils.hasText(ext.getExtJson())) {
            try {
                payload.putAll(JSON.parseObject(ext.getExtJson()));
            } catch (Exception ignored) {
                payload = new JSONObject();
            }
        }
        payload.put("officialSourceUrl", meta.url());
        payload.put("officialSourceName", meta.sourceName());
        payload.put("officialIssuer", meta.issuer());
        payload.put("officialDocumentName", meta.documentName());
        payload.put("officialKnowledgeType", meta.knowledgeType());
        payload.put("officialKnowledgeSubtype", meta.knowledgeSubtype());
        payload.put("officialDocumentLevel", meta.documentLevel());
        payload.put("officialEffectiveDate", meta.effectiveDate());
        payload.put("officialCurrentStatus", meta.currentStatus());
        payload.put("officialBusinessDomain", meta.businessDomain());
        payload.put("officialTopic", meta.topic());
        payload.put("officialRawGroup", meta.rawGroup());
        payload.put("officialFilePath", filePath.toAbsolutePath().normalize().toString());

        if (ext == null) {
            ext = new LegalDocumentExt();
            ext.setDocumentId(documentId);
        }
        ext.setExtTypeSummary(firstNonBlank(meta.knowledgeSubtype(), meta.knowledgeType(), meta.documentLevel(), ext.getExtTypeSummary()));
        ext.setExtJson(JSON.toJSONString(payload));
        ext.setSchemaVersion("v2.2");

        if (ext.getId() == null) {
            extMapper.insert(ext);
        } else {
            extMapper.updateById(ext);
        }
    }

    private String resolveCategoryName(Long categoryId, List<Category> categories) {
        if (categoryId == null || CollectionUtils.isEmpty(categories)) {
            return "未分类";
        }
        return categories.stream()
                .filter(category -> Objects.equals(category.getId(), categoryId))
                .map(Category::getName)
                .findFirst()
                .orElse("未分类");
    }

    private String mapOfficialDocumentType(OfficialSourceMeta meta) {
        if ("guiding_cases".equals(meta.rawGroup())) {
            return "典型案例";
        }
        if ("guides".equals(meta.rawGroup())) {
            return "普法材料";
        }
        if ("judicial_interpretations".equals(meta.rawGroup())) {
            return "司法解释";
        }
        if ("laws".equals(meta.rawGroup())) {
            return "法律法规";
        }
        return "官方发布";
    }

    private String mapOfficialDocumentSubtype(OfficialSourceMeta meta) {
        return firstNonBlank(meta.knowledgeSubtype(), meta.knowledgeType(), meta.documentLevel(), "官方文书");
    }

    private String detectOfficialCaseType(OfficialSourceMeta meta, LegalDocument document) {
        String corpus = String.join("\n",
                firstNonBlank(meta.businessDomain(), ""),
                firstNonBlank(meta.topic(), ""),
                firstNonBlank(meta.documentName(), ""),
                firstNonBlank(document.getCleanText(), ""),
                firstNonBlank(document.getCaseType(), "")
        );
        if (corpus.contains("刑事") || corpus.contains("毒品") || corpus.contains("涉毒")
                || corpus.contains("麻精药品") || corpus.contains("禁毒")) {
            return "刑事";
        }
        if ("guides".equals(meta.rawGroup())) {
            return "综合";
        }
        return firstNonBlank(document.getCaseType(), "其他");
    }

    private String detectOfficialProcedureDomain(OfficialSourceMeta meta, LegalDocument document) {
        String corpus = String.join("\n",
                firstNonBlank(meta.topic(), ""),
                firstNonBlank(meta.businessDomain(), ""),
                firstNonBlank(meta.documentName(), ""),
                firstNonBlank(document.getCleanText(), ""),
                firstNonBlank(document.getProcedureDomain(), "")
        );
        if (corpus.contains("取保候审") || corpus.contains("拘留") || corpus.contains("逮捕")
                || corpus.contains("会见") || corpus.contains("辩护")) {
            return "程序材料";
        }
        if (corpus.contains("证据") || corpus.contains("电子数据")) {
            return "证据规则";
        }
        if ("guides".equals(meta.rawGroup())) {
            return "普法引导";
        }
        return firstNonBlank(document.getProcedureDomain(), "专题文书");
    }

    private void persistRelatedRecords(LegalDocumentParsedData parsed) {
        Long documentId = parsed.document.getId();
        for (LegalDocumentParty party : parsed.parties) {
            party.setDocumentId(documentId);
            partyMapper.insert(party);
        }
        for (LegalDocumentReference reference : parsed.references) {
            reference.setDocumentId(documentId);
            referenceMapper.insert(reference);
        }
        for (LegalDocumentChunk chunk : parsed.chunks) {
            chunk.setDocumentId(documentId);
            chunkMapper.insert(chunk);

            LegalDocumentChunkIndex chunkIndex = new LegalDocumentChunkIndex();
            chunkIndex.setChunkId(chunk.getId());
            chunkIndex.setRetrievalProfile(DEFAULT_RETRIEVAL_PROFILE);
            chunkIndex.setBackendType("faiss");
            chunkIndex.setIndexName(DEFAULT_INDEX_NAME);
            chunkIndex.setEmbeddingProvider("local");
            chunkIndex.setEmbeddingModel("bge");
            chunkIndex.setBuildVersion("v1");
            chunkIndex.setIsActive(1);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("documentId", documentId);
            metadata.put("sectionType", chunk.getSectionType());
            metadata.put("chunkIndex", chunk.getChunkIndex());
            chunkIndex.setMetadataJson(JSON.toJSONString(metadata));
            chunkIndexMapper.insert(chunkIndex);
        }
        if (parsed.ext != null) {
            parsed.ext.setDocumentId(documentId);
            extMapper.insert(parsed.ext);
        }
    }

    private LegalDocumentParsedData parseDocument(MultipartFile file, String rawText, String cleanText, String sha256) {
        return legalDocumentParser.parseDocument(file, rawText, cleanText, sha256, new ServiceParserSupport());
    }

    private List<LegalDocumentChunk> buildChunks(LegalDocument document, List<Section> sections) {
        return legalDocumentChunkService.buildChunks(
                document,
                sections.stream()
                        .map(section -> new LegalDocumentSection(section.type, section.title, section.headerPath, section.content))
                        .collect(Collectors.toList()),
                new LegalDocumentChunkService.ChunkSupport() {
                    @Override
                    public String firstNonBlank(String... values) {
                        return LegalDocumentServiceImpl.this.firstNonBlank(values);
                    }

                    @Override
                    public String buildChunkRetrievalText(LegalDocument document, LegalDocumentSection section, String chunkText) {
                        return LegalDocumentServiceImpl.this.buildChunkRetrievalText(
                                document,
                                new Section(section.type, section.title, section.headerPath, section.content),
                                chunkText
                        );
                    }

                    @Override
                    public int estimateTokenCount(String text) {
                        return LegalDocumentServiceImpl.this.estimateTokenCount(text);
                    }

                    @Override
                    public String sha256Hex(byte[] bytes) {
                        return LegalDocumentServiceImpl.this.sha256Hex(bytes);
                    }
                }
        );
    }

    private List<Section> splitSections(String text) {
        return legalDocumentChunkService.splitSections(text).stream()
                .map(section -> new Section(section.type, section.title, section.headerPath, section.content))
                .collect(Collectors.toList());
    }

    private List<LegalDocumentSection> toSupportSections(List<Section> sections) {
        return sections.stream()
                .map(section -> new LegalDocumentSection(section.type, section.title, section.headerPath, section.content))
                .collect(Collectors.toList());
    }

    private List<Section> toLocalSections(List<LegalDocumentSection> sections) {
        return sections.stream()
                .map(section -> new Section(section.type, section.title, section.headerPath, section.content))
                .collect(Collectors.toList());
    }

    private String buildClaimsSummary(List<LegalDocumentSection> sections) {
        return summarizeSection(toLocalSections(sections), Set.of("诉讼请求", "上诉请求", "申请事项"));
    }

    private String buildDefenseSummary(List<LegalDocumentSection> sections) {
        return summarizeSection(toLocalSections(sections), Set.of("答辩", "抗辩", "意见"));
    }

    private String buildFactsSummary(List<LegalDocumentSection> sections) {
        return summarizeSection(toLocalSections(sections), Set.of("事实", "查明", "审理查明"));
    }

    private String buildIssuesSummary(List<LegalDocumentSection> sections) {
        return summarizeSection(toLocalSections(sections), Set.of("争议焦点", "焦点", "争议"));
    }

    private String buildReasoningSummary(List<LegalDocumentSection> sections) {
        return summarizeSection(toLocalSections(sections), Set.of("法院认为", "本院认为", "审查认为"));
    }

    private String buildResultSummary(List<LegalDocumentSection> sections) {
        return summarizeSection(toLocalSections(sections), Set.of("裁判结果", "判决如下", "裁定如下", "决定如下"));
    }

    private List<LegalDocumentParty> extractParties(List<String> lines, String title) {
        List<LegalDocumentParty> parties = new ArrayList<>();
        int seq = 1;
        for (String line : lines) {
            Matcher matcher = PARTY_LINE_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            LegalDocumentParty party = new LegalDocumentParty();
            party.setPartySeq(seq++);
            party.setPartyRoleRaw(matcher.group(1));
            party.setPartyRoleNormalized(normalizePartyRole(matcher.group(1)));
            String value = matcher.group(2).trim();
            party.setPartyName(extractPartyName(value));
            party.setPartyNameNormalized(normalizePartyName(party.getPartyName()));
            party.setPartyType(detectPartyType(value));
            party.setRepresentativeText(extractRepresentativeText(value));
            party.setIdentityText(value);
            party.setIsAnonymized(isAnonymizedName(party.getPartyName()) ? 1 : 0);
            parties.add(party);
            if (parties.size() >= 12) {
                break;
            }
        }
        if (!parties.isEmpty()) {
            return parties;
        }

        List<String> titleCandidates = splitTitleParties(title);
        for (String candidate : titleCandidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            LegalDocumentParty party = new LegalDocumentParty();
            party.setPartySeq(seq++);
            party.setPartyRoleRaw("当事人");
            party.setPartyRoleNormalized("当事人");
            party.setPartyName(candidate);
            party.setPartyNameNormalized(normalizePartyName(candidate));
            party.setPartyType(detectPartyType(candidate));
            party.setIsAnonymized(isAnonymizedName(candidate) ? 1 : 0);
            parties.add(party);
        }
        return parties;
    }

    private List<LegalDocumentReference> extractReferences(String text, String caseNumber) {
        List<LegalDocumentReference> references = new ArrayList<>();
        int order = 1;
        Set<String> seen = new HashSet<>();
        if (StringUtils.hasText(caseNumber)) {
            LegalDocumentReference ref = new LegalDocumentReference();
            ref.setRefOrder(order++);
            ref.setSectionType("header");
            ref.setRefType("case_number");
            ref.setRefName("本案案号");
            ref.setRefValue(caseNumber);
            ref.setNormalizedValue(caseNumber);
            ref.setIsPrimary(1);
            ref.setRawText(caseNumber);
            references.add(ref);
            seen.add("case_number|" + caseNumber);
        }
        Matcher caseMatcher = CASE_NUMBER_PATTERN.matcher(text);
        while (caseMatcher.find()) {
            String value = caseMatcher.group();
            String key = "case_number|" + value;
            if (!seen.add(key)) {
                continue;
            }
            LegalDocumentReference ref = new LegalDocumentReference();
            ref.setRefOrder(order++);
            ref.setSectionType("body");
            ref.setRefType("case_number");
            ref.setRefName("关联案号");
            ref.setRefValue(value);
            ref.setNormalizedValue(value);
            ref.setIsPrimary(0);
            ref.setRawText(value);
            references.add(ref);
        }
        Matcher lawMatcher = LAW_PATTERN.matcher(text);
        while (lawMatcher.find()) {
            String raw = lawMatcher.group();
            String key = "law|" + raw;
            if (!seen.add(key)) {
                continue;
            }
            LegalDocumentReference ref = new LegalDocumentReference();
            ref.setRefOrder(order++);
            ref.setSectionType("body");
            ref.setRefType("law_article");
            ref.setRefName(extractLawName(raw));
            ref.setRefValue(raw);
            ref.setNormalizedValue(raw);
            ref.setIsPrimary(0);
            ref.setRawText(raw);
            references.add(ref);
        }
        Matcher decisionMatcher = DECISION_NUMBER_PATTERN.matcher(text);
        while (decisionMatcher.find()) {
            String value = decisionMatcher.group();
            String key = "decision|" + value;
            if (!seen.add(key)) {
                continue;
            }
            LegalDocumentReference ref = new LegalDocumentReference();
            ref.setRefOrder(order++);
            ref.setSectionType("body");
            ref.setRefType("decision_number");
            ref.setRefName("决定号");
            ref.setRefValue(value);
            ref.setNormalizedValue(value);
            ref.setIsPrimary(0);
            ref.setRawText(value);
            references.add(ref);
        }
        Matcher rightMatcher = RIGHT_NUMBER_PATTERN.matcher(text);
        while (rightMatcher.find()) {
            String refName = rightMatcher.group(1);
            String value = rightMatcher.group(2);
            String key = refName + "|" + value;
            if (!seen.add(key)) {
                continue;
            }
            LegalDocumentReference ref = new LegalDocumentReference();
            ref.setRefOrder(order++);
            ref.setSectionType("body");
            ref.setRefType(mapRightRefType(refName));
            ref.setRefName(refName);
            ref.setRefValue(value);
            ref.setNormalizedValue(value);
            ref.setIsPrimary(0);
            ref.setRawText(rightMatcher.group());
            references.add(ref);
        }
        return references;
    }

    private Category resolveCategory(Long categoryId, List<Category> categories, LegalDocumentParsedData parsed) {
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
        String corpus = String.join("\n",
                firstNonBlank(parsed.document.getTitle(), ""),
                firstNonBlank(parsed.document.getCauseNormalized(), parsed.document.getCauseRaw(), ""),
                firstNonBlank(parsed.document.getBusinessDomain(), ""),
                firstNonBlank(parsed.document.getCaseType(), ""),
                firstNonBlank(parsed.document.getProcedureDomain(), ""),
                firstNonBlank(parsed.document.getCleanText(), ""));
        Category bestCategory = null;
        int bestScore = -1;
        for (Category category : categories) {
            int score = scoreCategory(category, corpus);
            if (score > bestScore) {
                bestScore = score;
                bestCategory = category;
            }
        }
        return bestCategory == null ? categories.get(0) : bestCategory;
    }

    private int scoreCategory(Category category, String corpus) {
        int score = 0;
        List<String> words = new ArrayList<>();
        words.add(category.getName());
        words.add(category.getDescription());
        words.addAll(extraKeywordsForCategory(category.getName()));
        String normalizedCorpus = corpus.toLowerCase(Locale.ROOT);
        for (String wordGroup : words) {
            if (!StringUtils.hasText(wordGroup)) {
                continue;
            }
            for (String word : wordGroup.split("[,，、/\\s]+")) {
                String token = word.trim().toLowerCase(Locale.ROOT);
                if (token.length() >= 2 && normalizedCorpus.contains(token)) {
                    score++;
                }
            }
        }
        return score;
    }

    private List<String> extraKeywordsForCategory(String categoryName) {
        return new ArrayList<>(DrugVerticalDomainSupport.keywordsForCategory(categoryName));
    }

    private DedupState buildDedupState() {
        DedupState state = new DedupState();
        List<LegalDocument> documents = this.list(new LambdaQueryWrapper<LegalDocument>()
                .select(LegalDocument::getId, LegalDocument::getTitle, LegalDocument::getTitleNormalized,
                        LegalDocument::getCaseNumber, LegalDocument::getCategoryId, LegalDocument::getFileSha256));
        Map<Long, String> categoryNames = loadCategoryNameMap(documents);
        for (LegalDocument document : documents) {
            registerInDedupState(document, categoryNames.get(document.getCategoryId()), state);
        }
        return state;
    }

    private DuplicateMatch findDuplicate(LegalDocument document, DedupState state) {
        if (StringUtils.hasText(document.getFileSha256())) {
            DuplicateMatch match = state.bySha256.get(document.getFileSha256());
            if (match != null) {
                return match;
            }
        }
        if (StringUtils.hasText(document.getCaseNumber()) && StringUtils.hasText(document.getTitleNormalized())) {
            DuplicateMatch match = state.byCaseAndTitle.get(document.getCaseNumber() + "|" + document.getTitleNormalized());
            if (match != null) {
                return match;
            }
        }
        if (StringUtils.hasText(document.getCaseNumber())) {
            DuplicateMatch match = state.byCaseNumber.get(document.getCaseNumber());
            if (match != null) {
                return match;
            }
        }
        if (StringUtils.hasText(document.getTitleNormalized())) {
            return state.byTitle.get(document.getTitleNormalized());
        }
        return null;
    }

    private void registerImportedDocument(LegalDocument document, Category category, DedupState state) {
        registerInDedupState(document, category == null ? null : category.getName(), state);
    }

    private void registerInDedupState(LegalDocument document, String categoryName, DedupState state) {
        DuplicateMatch match = new DuplicateMatch(
                document.getId(),
                document.getTitle(),
                document.getCaseNumber(),
                document.getCategoryId(),
                categoryName
        );
        if (StringUtils.hasText(document.getFileSha256())) {
            state.bySha256.putIfAbsent(document.getFileSha256(), match);
        }
        if (StringUtils.hasText(document.getCaseNumber()) && StringUtils.hasText(document.getTitleNormalized())) {
            state.byCaseAndTitle.putIfAbsent(document.getCaseNumber() + "|" + document.getTitleNormalized(), match);
        }
        if (StringUtils.hasText(document.getCaseNumber())) {
            state.byCaseNumber.putIfAbsent(document.getCaseNumber(), match);
        }
        if (StringUtils.hasText(document.getTitleNormalized())) {
            state.byTitle.putIfAbsent(document.getTitleNormalized(), match);
        }
    }

    private Map<Long, LegalDocument> loadDocumentMap(Collection<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Map.of();
        }
        return this.listByIds(ids).stream().collect(Collectors.toMap(LegalDocument::getId, item -> item));
    }

    private Map<Long, String> loadCategoryNameMap(Collection<LegalDocument> documents) {
        Set<Long> categoryIds = documents.stream()
                .map(LegalDocument::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryService.getAllCategories().stream()
                .filter(category -> categoryIds.contains(category.getId()))
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a, LinkedHashMap::new));
    }

    private String buildIndexTags(LegalDocument document) {
        List<String> tags = new ArrayList<>();
        addIfHasText(tags, document.getCaseType());
        addIfHasText(tags, document.getBusinessDomain());
        addIfHasText(tags, document.getProcedureDomain());
        addIfHasText(tags, document.getCauseNormalized());
        addIfHasText(tags, document.getIssueTags());
        addIfHasText(tags, document.getResultTags());
        return joinDistinct(tags);
    }

    private String buildDocumentRetrievalText(LegalDocument document) {
        List<String> parts = new ArrayList<>();
        addLabelled(parts, "标题", document.getTitle());
        addLabelled(parts, "案号", document.getCaseNumber());
        addLabelled(parts, "法院", document.getCourtName());
        addLabelled(parts, "文书类型", document.getDocumentSubtype());
        addLabelled(parts, "案件类型", document.getCaseType());
        addLabelled(parts, "案由", firstNonBlank(document.getCauseNormalized(), document.getCauseRaw()));
        addLabelled(parts, "纠纷概况", document.getDisputeSummary());
        addLabelled(parts, "审理经过", document.getTrialProcessSummary());
        addLabelled(parts, "程序领域", document.getProcedureDomain());
        addLabelled(parts, "争议焦点", document.getIssuesSummary());
        addLabelled(parts, "法院理由", document.getReasoningSummary());
        addLabelled(parts, "裁判结果", firstNonBlank(document.getResultSummary(), document.getDecisionResult()));
        addLabelled(parts, "法律依据", document.getLegalBasisText());
        addLabelled(parts, "案件详情", document.getCaseDetailText());
        addLabelled(parts, "关键词", document.getKeywords());
        return String.join("\n", parts);
    }

    private String buildChunkRetrievalText(LegalDocument document, Section section, String chunkText) {
        List<String> parts = new ArrayList<>();
        addLabelled(parts, "标题", document.getTitle());
        addLabelled(parts, "案号", document.getCaseNumber());
        addLabelled(parts, "法院", document.getCourtName());
        addLabelled(parts, "文书类型", document.getDocumentSubtype());
        addLabelled(parts, "案由", firstNonBlank(document.getCauseNormalized(), document.getCauseRaw()));
        addLabelled(parts, "纠纷概况", document.getDisputeSummary());
        addLabelled(parts, "程序领域", document.getProcedureDomain());
        addLabelled(parts, "章节", firstNonBlank(section.headerPath, section.title, section.type));
        addLabelled(parts, "裁判结果", firstNonBlank(document.getResultSummary(), document.getDecisionResult()));
        parts.add(chunkText);
        return String.join("\n", parts);
    }

    private String buildSummary(LegalDocument document) {
        return ellipsize(firstNonBlank(
                document.getCaseDetailText(),
                document.getResultSummary(),
                document.getIssuesSummary(),
                document.getFactsSummary(),
                document.getReasoningSummary(),
                document.getCleanText()
        ), SUMMARY_LIMIT);
    }

    private String buildDisputeSummary(LegalDocument document, List<Section> sections) {
        List<String> parts = new ArrayList<>();
        addLabelled(parts, "案由", firstNonBlank(document.getCauseNormalized(), document.getCauseRaw()));
        addLabelled(parts, "诉求", document.getClaimsSummary());
        addLabelled(parts, "答辩", document.getDefenseSummary());
        addLabelled(parts, "基本事实", document.getFactsSummary());
        addLabelled(parts, "争议事项", summarizeSection(sections, Set.of("申请事项", "上诉请求", "诉称")));
        if (parts.isEmpty()) {
            String fallback = firstNonBlank(
                    summarizeSection(sections, Set.of("事实", "查明", "诉称", "请求", "上诉请求")),
                    document.getTitle()
            );
            return ellipsize(fallback, SUMMARY_LIMIT);
        }
        return ellipsize(String.join("；", parts), SUMMARY_LIMIT);
    }

    private String extractTrialProcessSummary(String cleanText, List<Section> sections) {
        String direct = summarizeSection(sections, Set.of("审理经过", "诉讼经过", "程序经过", "申请再审", "上诉"));
        if (StringUtils.hasText(direct)) {
            return ellipsize(direct, SUMMARY_LIMIT);
        }
        List<String> sentences = splitSentences(cleanText);
        List<String> hits = new ArrayList<>();
        for (String sentence : sentences) {
            if (sentence.contains("不服")
                    || sentence.contains("提起上诉")
                    || sentence.contains("申请再审")
                    || sentence.contains("依法审理")
                    || sentence.contains("立案受理")
                    || sentence.contains("公开开庭")) {
                hits.add(collapseWhitespace(sentence));
            }
            if (hits.size() >= 3) {
                break;
            }
        }
        return hits.isEmpty() ? null : ellipsize(String.join("；", hits), SUMMARY_LIMIT);
    }

    private String buildCaseDetailText(LegalDocument document) {
        List<String> parts = new ArrayList<>();
        addLabelled(parts, "纠纷概况", document.getDisputeSummary());
        addLabelled(parts, "审理经过", document.getTrialProcessSummary());
        addLabelled(parts, "争议焦点", document.getIssuesSummary());
        addLabelled(parts, "法院理由", document.getReasoningSummary());
        addLabelled(parts, "裁判结果", firstNonBlank(document.getResultSummary(), document.getDecisionResult()));
        return String.join("\n", parts);
    }

    private String buildDisplayText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }
        return legalDocumentTextProcessor.cleanDisplayText(rawText, null, null, null);
    }

    private List<String> buildKeywords(LegalDocument document, List<LegalDocumentParty> parties,
                                       List<LegalDocumentReference> references) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        addIfHasText(keywords, document.getCaseType());
        addIfHasText(keywords, document.getBusinessDomain());
        addIfHasText(keywords, document.getProcedureDomain());
        addIfHasText(keywords, document.getDocumentSubtype());
        addIfHasText(keywords, document.getCauseNormalized());
        addIfHasText(keywords, document.getSpecialProcedureType());
        addIfHasText(keywords, document.getApplicationType());
        for (LegalDocumentParty party : parties) {
            addIfHasText(keywords, party.getPartyName());
        }
        for (LegalDocumentReference reference : references) {
            if ("law_article".equals(reference.getRefType())) {
                addIfHasText(keywords, reference.getRefName());
            }
        }
        return new ArrayList<>(keywords);
    }

    private List<String> buildAliases(LegalDocument document) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        String cause = firstNonBlank(document.getCauseNormalized(), document.getCauseRaw());
        if (StringUtils.hasText(cause)) {
            aliases.add(cause);
        }
        String title = asText(document.getTitle(), "");
        if (title.contains("贩卖毒品") || title.contains("走私") || title.contains("运输毒品") || title.contains("制造毒品")) {
            aliases.add("毒品犯罪");
            aliases.add("贩毒");
        }
        if (title.contains("容留他人吸毒")) {
            aliases.add("容留吸毒");
        }
        if (title.contains("麻醉药品") || title.contains("精神药品") || title.contains("麻精药品")
                || title.contains("依托咪酯") || title.contains("右美沙芬") || title.contains("笑气")) {
            aliases.add("麻精药品");
            aliases.add("新型涉毒风险");
        }
        if (title.contains("取保候审") || title.contains("会见") || title.contains("拘留") || title.contains("证据")) {
            aliases.add("程序应对");
        }
        if (title.contains("家属") || title.contains("普法")) {
            aliases.add("家属咨询");
        }
        return new ArrayList<>(aliases);
    }

    private List<String> buildIssueTags(LegalDocument document, List<Section> sections) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addIfHasText(tags, document.getProcedureDomain());
        addIfHasText(tags, document.getSpecialProcedureType());
        addIfHasText(tags, document.getApplicationType());
        String issuesText = asText(firstNonBlank(document.getIssuesSummary(), document.getReasoningSummary()), "");
        if (issuesText.contains("主观明知")) {
            tags.add("主观明知");
        }
        if (issuesText.contains("毒品数量") || issuesText.contains("数量认定")) {
            tags.add("数量认定");
        }
        if (issuesText.contains("证据") || issuesText.contains("电子数据")) {
            tags.add("证据审查");
        }
        if (issuesText.contains("取保候审") || issuesText.contains("会见")) {
            tags.add("程序权利");
        }
        for (Section section : sections) {
            if (section.title.contains("争议焦点")) {
                tags.add("争议焦点");
            }
        }
        return new ArrayList<>(tags);
    }

    private List<String> buildResultTags(LegalDocument document) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        String result = asText(firstNonBlank(document.getDecisionResult(), document.getResultSummary()), "");
        if (result.contains("驳回")) {
            tags.add("驳回");
        }
        if (result.contains("维持")) {
            tags.add("维持原判");
        }
        if (result.contains("撤销")) {
            tags.add("撤销");
        }
        if (result.contains("罚款")) {
            tags.add("司法罚款");
        }
        if (!tags.isEmpty()) {
            return new ArrayList<>(tags);
        }
        addIfHasText(tags, document.getDecisionResultNormalized());
        return new ArrayList<>(tags);
    }

    private String extractCaseNumber(String title, String text) {
        Matcher matcher = CASE_NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        matcher = CASE_NUMBER_PATTERN.matcher(title);
        return matcher.find() ? matcher.group() : null;
    }

    private String extractCourtName(List<String> lines, String title, String text) {
        Matcher labelMatcher = INSTITUTION_LABEL_PATTERN.matcher(text);
        if (labelMatcher.find()) {
            String candidate = normalizeInstitutionCandidate(labelMatcher.group(2));
            if (isOfficialInstitutionName(candidate)) {
                return ellipsize(candidate, 80);
            }
        }
        for (int index = 0; index < lines.size() && index < 24; index++) {
            String candidate = normalizeInstitutionCandidate(lines.get(index));
            if (isOfficialInstitutionName(candidate)) {
                return ellipsize(candidate, 80);
            }
        }
        String titleInstitution = extractInstitutionFromTitle(title);
        if (StringUtils.hasText(titleInstitution)) {
            return titleInstitution;
        }
        if (title.contains("最高人民法院")) {
            return "最高人民法院";
        }
        return null;
    }

    private LocalDate extractDecisionDate(List<String> lines, String text) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            LocalDate date = parseDate(lines.get(i));
            if (date != null) {
                return date;
            }
        }
        Matcher matcher = NUMERIC_DATE_PATTERN.matcher(text);
        LocalDate last = null;
        while (matcher.find()) {
            last = parseDate(matcher.group());
        }
        return last;
    }

    private LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.length() >= 10) {
            try {
                return LocalDate.parse(normalized.substring(0, 10));
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }
        Matcher isoMatcher = Pattern.compile("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})").matcher(normalized);
        if (isoMatcher.find()) {
            try {
                return LocalDate.of(
                        Integer.parseInt(isoMatcher.group(1)),
                        Integer.parseInt(isoMatcher.group(2)),
                        Integer.parseInt(isoMatcher.group(3))
                );
            } catch (DateTimeParseException | NumberFormatException ignored) {
                return null;
            }
        }
        Matcher matcher = NUMERIC_DATE_PATTERN.matcher(raw);
        if (matcher.find()) {
            try {
                return LocalDate.of(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))
                );
            } catch (DateTimeParseException | NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String buildTitle(String fileName, List<String> lines) {
        String titleFromHeader = extractFormalHeaderTitle(lines);
        if (isUsableTitle(titleFromHeader)) {
            return titleFromHeader;
        }
        String titleFromFileName = sanitizeTitleCandidate(stripExtension(fileName));
        if (isUsableTitle(titleFromFileName)) {
            return titleFromFileName;
        }
        for (String line : lines) {
            String candidate = sanitizeTitleCandidate(line);
            if (isUsableTitle(candidate)) {
                return candidate;
            }
        }
        return StringUtils.hasText(titleFromFileName) ? titleFromFileName : "未命名法律文书";
    }

    private boolean isUsableTitle(String title) {
        if (!StringUtils.hasText(title) || title.length() > 120 || containsSentencePunctuation(title)) {
            return false;
        }
        String compact = title.replace(" ", "");
        return FORMAL_DOCUMENT_TYPES.stream().anyMatch(type ->
                compact.endsWith(type)
                        || compact.equals(type)
                        || compact.startsWith(type + "（")
                        || compact.startsWith(type + "(")
                        || compact.contains("案" + type)
        );
    }

    private String sanitizeTitleCandidate(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String result = value.trim();
        result = result.replace('_', ' ');
        result = result.replaceAll("^[#*\\-\\d.、\\s]+", "");
        String previous;
        do {
            previous = result;
            result = result.replaceAll("(?i)^(12309|PDF|DOCX?|TXT|HTML?|HTM)\\s+", "");
        } while (!previous.equals(result));
        result = result.replaceAll("[\\s\\-]*(19|20)\\d{2}$", "");
        result = result.replaceAll("\\.{2,}$", "");
        return collapseWhitespace(result);
    }

    private String extractFormalHeaderTitle(List<String> lines) {
        int limit = Math.min(lines.size(), 20);
        for (int index = 0; index < limit; index++) {
            String institution = normalizeInstitutionCandidate(lines.get(index));
            if (!isOfficialInstitutionName(institution)) {
                continue;
            }
            for (int lookAhead = index + 1; lookAhead < limit && lookAhead <= index + 4; lookAhead++) {
                String documentType = normalizeDocumentTypeLine(lines.get(lookAhead));
                if (StringUtils.hasText(documentType)) {
                    return institution + documentType;
                }
            }
        }
        for (int index = 0; index < limit; index++) {
            String candidate = sanitizeTitleCandidate(lines.get(index));
            if (isUsableTitle(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String normalizeDocumentTypeLine(String line) {
        if (!StringUtils.hasText(line)) {
            return null;
        }
        String compact = collapseWhitespace(line).replace(" ", "");
        if (compact.length() > 24 || containsSentencePunctuation(compact)) {
            return null;
        }
        for (String type : FORMAL_DOCUMENT_TYPES) {
            if (compact.equals(type)) {
                return type;
            }
        }
        return null;
    }

    private String normalizeInstitutionCandidate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String result = collapseWhitespace(value)
                .replaceAll("^(审理法院|法院|检察机关|检察院|承办机关|发布机构)[：:]", "")
                .replaceAll("^[#*\\-\\d.、\\s]+", "")
                .trim();
        return result.replace(" ", "");
    }

    private boolean isOfficialInstitutionName(String value) {
        if (!StringUtils.hasText(value) || value.length() < 6 || value.length() > 60) {
            return false;
        }
        if (containsSentencePunctuation(value)
                || value.contains("被")
                || value.contains("因")
                || value.contains("犯")
                || value.contains("判")
                || value.contains("理由")
                || value.contains("认为")
                || value.contains("审理")
                || value.contains("本院")
                || value.contains("原公诉机关")) {
            return false;
        }
        return value.endsWith("人民检察院")
                || value.endsWith("人民法院")
                || value.endsWith("知识产权法院")
                || value.endsWith("互联网法院")
                || value.endsWith("铁路运输法院")
                || value.endsWith("海事法院")
                || value.endsWith("军事法院");
    }

    private String extractInstitutionFromTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        Matcher matcher = Pattern.compile("([\\u4e00-\\u9fa5]{2,45}(人民检察院|人民法院|知识产权法院|互联网法院|铁路运输法院|海事法院|军事法院))")
                .matcher(title.replace(" ", ""));
        if (matcher.find()) {
            String candidate = normalizeInstitutionCandidate(matcher.group(1));
            return isOfficialInstitutionName(candidate) ? candidate : null;
        }
        return null;
    }

    private boolean containsSentencePunctuation(String value) {
        return StringUtils.hasText(value) && Pattern.compile("[，,。；;！!？?]").matcher(value).find();
    }

    private String detectDocumentSubtype(String title, String text) {
        List<String> prosecutionTypes = List.of(
                "刑事附带民事公益诉讼起诉书", "行政公益诉讼起诉书", "民事公益诉讼起诉书",
                "刑事起诉书", "起诉书"
        );
        for (String candidate : prosecutionTypes) {
            if (title.contains(candidate)) {
                return candidate;
            }
        }
        if (hasProsecutionHeader(text)) {
            return "起诉书";
        }

        List<String> candidates = List.of(
                "执行裁定书", "行政判决书", "民事判决书", "刑事判决书",
                "民事裁定书", "刑事裁定书", "行政裁定书",
                "司法制裁决定书", "司法罚款决定书", "通知书", "裁定书", "判决书", "决定书"
        );
        for (String candidate : candidates) {
            if (title.contains(candidate)) {
                return candidate;
            }
        }
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return candidate;
            }
        }
        return "法律文书";
    }

    private boolean hasProsecutionHeader(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String head = text.substring(0, Math.min(text.length(), 600)).replaceAll("\\s+", "");
        int institutionIndex = head.indexOf("人民检察院");
        int indictmentIndex = head.indexOf("起诉书");
        return institutionIndex >= 0
                && indictmentIndex > institutionIndex
                && indictmentIndex - institutionIndex <= 80;
    }

    private String detectDocumentType(String documentSubtype, String title, String text) {
        if (StringUtils.hasText(documentSubtype)) {
            if (documentSubtype.endsWith("起诉书")) {
                return "起诉书";
            }
            if (documentSubtype.endsWith("判决书")) {
                return "判决书";
            }
            if (documentSubtype.endsWith("裁定书")) {
                return "裁定书";
            }
            if (documentSubtype.endsWith("决定书")) {
                return "决定书";
            }
            if (documentSubtype.endsWith("通知书")) {
                return "通知书";
            }
        }
        if (title.contains("判决书") || text.contains("判决如下")) {
            return "判决书";
        }
        if (title.contains("裁定书") || text.contains("裁定如下")) {
            return "裁定书";
        }
        if (title.contains("决定书") || text.contains("决定如下")) {
            return "决定书";
        }
        if (title.contains("起诉书") || text.contains("提起公诉")) {
            return "起诉书";
        }
        if (title.contains("通知书")) {
            return "通知书";
        }
        return "法律文书";
    }

    private String detectCaseType(String documentSubtype, String title, String text) {
        String corpus = title + "\n" + text + "\n" + documentSubtype;
        if (corpus.contains("刑事") || corpus.contains("毒品") || corpus.contains("涉毒")
                || corpus.contains("禁毒") || corpus.contains("麻精药品") || corpus.contains("罪")) {
            return "刑事";
        }
        if (corpus.contains("普法") || corpus.contains("宣传教育")) {
            return "综合";
        }
        return "其他";
    }

    private String detectCause(String title, String text) {
        Matcher labelMatcher = CAUSE_LABEL_PATTERN.matcher(text);
        if (labelMatcher.find()) {
            return labelMatcher.group(2).trim();
        }
        List<String> candidates = List.of(
                "走私、贩卖、运输、制造毒品罪", "贩卖毒品罪", "运输毒品罪", "制造毒品罪",
                "非法持有毒品罪", "容留他人吸毒罪", "引诱、教唆、欺骗他人吸毒罪",
                "以危险方法危害公共安全罪", "洗钱罪", "麻精药品犯罪"
        );
        for (String candidate : candidates) {
            if (title.contains(candidate)) {
                return candidate;
            }
        }
        Matcher matcher = Pattern.compile("案由[:：]?\\s*([^\\n，。；]{2,40})").matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return detectCauseFromTitle(title);
    }

    private String detectCauseFromTitle(String title) {
        String[] separators = {"与", "申请", "纠纷", "罪", "赔偿", "执行", "裁定"};
        for (String separator : separators) {
            int index = title.indexOf(separator);
            if (index > 0 && index < 40) {
                return title.substring(Math.max(0, index - 20), Math.min(title.length(), index + separator.length() + 6)).trim();
            }
        }
        return null;
    }

    private String normalizeCause(String cause) {
        if (!StringUtils.hasText(cause)) {
            return null;
        }
        if (cause.contains("走私") && cause.contains("贩卖") && cause.contains("运输") && cause.contains("制造") && cause.contains("毒品")) {
            return "走私、贩卖、运输、制造毒品罪";
        }
        if (cause.contains("贩卖毒品")) {
            return "贩卖毒品罪";
        }
        if (cause.contains("非法持有毒品")) {
            return "非法持有毒品罪";
        }
        if (cause.contains("容留他人吸毒")) {
            return "容留他人吸毒罪";
        }
        if (cause.contains("引诱") || cause.contains("教唆") || cause.contains("欺骗")) {
            return "引诱、教唆、欺骗他人吸毒罪";
        }
        return collapseWhitespace(cause);
    }

    private String detectBusinessDomain(String title, String text, String cause) {
        String inferred = DrugVerticalDomainSupport.inferCategoryName(title, text, cause);
        if (StringUtils.hasText(inferred)) {
            return inferred;
        }
        String corpus = title + "\n" + text + "\n" + firstNonBlank(cause, "");
        if (DrugVerticalDomainSupport.containsTopicalTerm(corpus)) {
            return DrugVerticalDomainSupport.DEFAULT_CATEGORY_NAME;
        }
        return "综合";
    }

    private String detectProcedureDomain(String title, String text) {
        String corpus = title + "\n" + text;
        if (corpus.contains("起诉书") || corpus.contains("提起公诉") || corpus.contains("审查起诉")) {
            return "审查起诉";
        }
        if (corpus.contains("执行监督")) {
            return "执行监督";
        }
        if (corpus.contains("审判监督") || corpus.contains("再审")) {
            return "审判监督";
        }
        if (corpus.contains("司法协助") || corpus.contains("承认外国")) {
            return "司法协助";
        }
        if (corpus.contains("司法罚款") || corpus.contains("司法制裁")) {
            return "司法制裁";
        }
        if (corpus.contains("行为保全")) {
            return "保全复议";
        }
        if (corpus.contains("指定管辖")) {
            return "管辖程序";
        }
        return "普通程序";
    }

    private String detectTrialStage(String title, String text) {
        String corpus = title + "\n" + text;
        if (corpus.contains("再审")) {
            return "再审";
        }
        if (corpus.contains("二审")) {
            return "二审";
        }
        if (corpus.contains("一审")) {
            return "一审";
        }
        if (corpus.contains("复议")) {
            return "复议";
        }
        if (corpus.contains("执行")) {
            return "执行";
        }
        return "其他";
    }

    private String detectProcedureStageRaw(String title, String text) {
        List<String> candidates = List.of(
                "再审审查与审判监督", "再审审查和审判监督", "执行监督", "特殊程序", "区际司法协助",
                "行为保全复议", "指定管辖", "国家赔偿与司法救助"
        );
        String corpus = title + "\n" + text;
        for (String candidate : candidates) {
            if (corpus.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String detectSpecialProcedureType(String title, String text) {
        String corpus = title + "\n" + text;
        if (corpus.contains("承认外国") || corpus.contains("港澳台")) {
            return "承认外国及港澳台裁判";
        }
        if (corpus.contains("行为保全")) {
            return "行为保全复议";
        }
        if (corpus.contains("指定管辖")) {
            return "指定管辖";
        }
        if (corpus.contains("破产清算")) {
            return "破产清算申请";
        }
        return null;
    }

    private String detectApplicationType(String title, String text) {
        String corpus = title + "\n" + text;
        if (corpus.contains("申请再审")) {
            return "再审申请";
        }
        if (corpus.contains("申诉")) {
            return "申诉";
        }
        if (corpus.contains("行为保全复议")) {
            return "保全复议";
        }
        if (corpus.contains("破产清算")) {
            return "破产清算申请";
        }
        if (corpus.contains("承认外国")) {
            return "承认与执行申请";
        }
        return null;
    }

    private String extractDecisionResult(String text) {
        Matcher matcher = Pattern.compile("(裁判结果|判决如下|裁定如下|决定如下)[：:]?\\s*([\\s\\S]{0,180})").matcher(text);
        if (matcher.find()) {
            String body = stripDecisionTail(collapseWhitespace(matcher.group(2)));
            if (StringUtils.hasText(body)) {
                return ellipsize(body, SUMMARY_LIMIT);
            }
        }
        String[] anchors = {"裁定如下", "判决如下", "决定如下"};
        for (String anchor : anchors) {
            int index = text.indexOf(anchor);
            if (index >= 0) {
                String tail = text.substring(index, Math.min(text.length(), index + 220));
                return ellipsize(stripDecisionTail(collapseWhitespace(tail)), SUMMARY_LIMIT);
            }
        }
        for (String paragraph : splitParagraphs(text)) {
            if (paragraph.contains("驳回") || paragraph.contains("维持") || paragraph.contains("撤销") || paragraph.contains("罚款")) {
                return ellipsize(stripDecisionTail(collapseWhitespace(paragraph)), SUMMARY_LIMIT);
            }
        }
        return null;
    }

    private String stripDecisionTail(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String normalized = text;
        String[] anchors = {"审判长", "审判员", "书记员", "法官助理", "二〇", "二○"};
        int cut = normalized.length();
        for (String anchor : anchors) {
            int index = normalized.indexOf(anchor);
            if (index > 0) {
                cut = Math.min(cut, index);
            }
        }
        normalized = normalized.substring(0, cut).trim();
        normalized = normalized.replaceAll("[：:，,;；\\-]+$", "").trim();
        return normalized;
    }

    private String normalizeDecisionResult(String decisionResult) {
        if (!StringUtils.hasText(decisionResult)) {
            return null;
        }
        if (decisionResult.contains("驳回")) {
            return "驳回";
        }
        if (decisionResult.contains("维持")) {
            return "维持";
        }
        if (decisionResult.contains("撤销")) {
            return "撤销";
        }
        if (decisionResult.contains("罚款")) {
            return "罚款";
        }
        return ellipsize(decisionResult, 60);
    }

    private String detectCourtLevel(String courtName) {
        if (!StringUtils.hasText(courtName)) {
            return null;
        }
        if (courtName.contains("最高")) {
            return "最高法院";
        }
        if (courtName.contains("高级")) {
            return "高级法院";
        }
        if (courtName.contains("中级")) {
            return "中级法院";
        }
        if (courtName.contains("基层") || courtName.contains("区人民法院") || courtName.contains("县人民法院")) {
            return "基层法院";
        }
        return null;
    }

    private String detectProvince(String courtName) {
        if (!StringUtils.hasText(courtName)) {
            return null;
        }
        String[] provinces = {"北京", "天津", "上海", "重庆", "河北", "山西", "辽宁", "吉林", "黑龙江", "江苏", "浙江",
                "安徽", "福建", "江西", "山东", "河南", "湖北", "湖南", "广东", "海南", "四川", "贵州", "云南",
                "陕西", "甘肃", "青海", "台湾", "内蒙古", "广西", "西藏", "宁夏", "新疆", "香港", "澳门"};
        for (String province : provinces) {
            if (courtName.contains(province)) {
                return province;
            }
        }
        return null;
    }

    private String detectCity(String courtName) {
        if (!StringUtils.hasText(courtName)) {
            return null;
        }
        Matcher matcher = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}市)").matcher(courtName);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String summarizeSection(List<Section> sections, Set<String> keywords) {
        for (Section section : sections) {
            for (String keyword : keywords) {
                if (section.title.contains(keyword) || section.type.contains(keyword)) {
                    return ellipsize(collapseWhitespace(section.content), SUMMARY_LIMIT);
                }
            }
        }
        return null;
    }

    private String buildPartiesText(List<LegalDocumentParty> parties) {
        List<String> values = new ArrayList<>();
        for (LegalDocumentParty party : parties) {
            values.add(firstNonBlank(party.getPartyRoleNormalized(), party.getPartyRoleRaw()) + "：" + party.getPartyName());
        }
        return joinDistinct(values);
    }

    private String extractLegalBasisText(List<LegalDocumentReference> references, String text) {
        String joined = references.stream()
                .filter(reference -> "law_article".equals(reference.getRefType()))
                .map(LegalDocumentReference::getRefValue)
                .distinct()
                .collect(Collectors.joining("；"));
        if (StringUtils.hasText(joined)) {
            return joined;
        }
        Matcher matcher = LEGAL_BASIS_LABEL_PATTERN.matcher(text);
        if (matcher.find()) {
            return ellipsize(collapseWhitespace(matcher.group(2)), SUMMARY_LIMIT);
        }
        return null;
    }

    private String extractRelatedCaseNumbers(List<LegalDocumentReference> references, String currentCaseNumber) {
        return references.stream()
                .filter(reference -> "case_number".equals(reference.getRefType()))
                .map(LegalDocumentReference::getRefValue)
                .filter(value -> !Objects.equals(value, currentCaseNumber))
                .distinct()
                .collect(Collectors.joining("；"));
    }

    private String extractRelatedDecisionNumbers(List<LegalDocumentReference> references) {
        return references.stream()
                .filter(reference -> "decision_number".equals(reference.getRefType()))
                .map(LegalDocumentReference::getRefValue)
                .distinct()
                .collect(Collectors.joining("；"));
    }

    private BigDecimal scoreDocumentQuality(String text, String title, String caseNumber, String courtName, List<Section> sections) {
        double score = 0.45;
        if (StringUtils.hasText(title)) {
            score += 0.15;
        }
        if (StringUtils.hasText(caseNumber)) {
            score += 0.15;
        }
        if (StringUtils.hasText(courtName)) {
            score += 0.10;
        }
        if (sections.size() >= 3) {
            score += 0.10;
        }
        if (text.length() >= 1000) {
            score += 0.05;
        }
        return BigDecimal.valueOf(Math.min(0.99, score));
    }

    private boolean isHeading(String value) {
        return value.length() <= 30 && HEADING_PATTERN.matcher(value).find();
    }

    private String mapSectionType(String title) {
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
                if (split > start + 120) {
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
        int[] candidates = {
                text.lastIndexOf("\n\n", end),
                text.lastIndexOf('\n', end),
                text.lastIndexOf('。', end),
                text.lastIndexOf('；', end),
                text.lastIndexOf('，', end),
                text.lastIndexOf(' ', end)
        };
        int split = start;
        for (int candidate : candidates) {
            if (candidate > split && candidate < end) {
                split = candidate + 1;
            }
        }
        return split;
    }

    private List<TextSlice> splitOrderedItems(String text) {
        List<TextSlice> slices = new ArrayList<>();
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
                slices.add(new TextSlice(start, end, item));
            }
        }
        return slices;
    }

    private List<String> splitParagraphs(String text) {
        return List.of(text.replace("\r", "\n").split("\\n+"));
    }

    private List<String> splitSentences(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return List.of(text.replace("\r", "\n").split("(?<=[。！？；])\\s*"));
    }

    private List<String> extractMeaningfulLines(String text) {
        return splitParagraphs(text).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .limit(80)
                .collect(Collectors.toList());
    }

    private String extractPartyName(String value) {
        String result = value.replaceAll("（.*?）", "");
        result = result.split("[，。；]")[0].trim();
        if (result.length() > 40) {
            result = result.substring(0, 40).trim();
        }
        return result;
    }

    private String extractRepresentativeText(String value) {
        Matcher matcher = Pattern.compile("(法定代表人|委托诉讼代理人|诉讼代理人|辩护人)[：: ]*([^，。；\\n]{2,40})").matcher(value);
        return matcher.find() ? matcher.group() : null;
    }

    private String normalizePartyRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        if (role.contains("上诉")) {
            return role.contains("被") ? "被上诉人" : "上诉人";
        }
        if (role.contains("申请")) {
            return role;
        }
        if (role.contains("原告")) {
            return "原告";
        }
        if (role.contains("被告")) {
            return "被告";
        }
        return role.trim();
    }

    private String normalizePartyName(String partyName) {
        return normalizeForKey(partyName);
    }

    private String detectPartyType(String value) {
        if (!StringUtils.hasText(value)) {
            return "未知";
        }
        if (value.contains("公司") || value.contains("有限公司") || value.contains("集团")) {
            return "公司";
        }
        if (value.contains("局") || value.contains("政府") || value.contains("法院") || value.contains("检察院")) {
            return "机关";
        }
        return "个人";
    }

    private boolean isAnonymizedName(String name) {
        return StringUtils.hasText(name) && name.contains("某");
    }

    private List<String> splitTitleParties(String title) {
        String cleaned = stripDocumentSuffix(title);
        String[] tokens = cleaned.split("与|申请人|被申请人|上诉人|被上诉人");
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            String candidate = token.trim();
            if (candidate.length() >= 2 && candidate.length() <= 30) {
                result.add(candidate);
            }
        }
        return result.stream().distinct().limit(6).collect(Collectors.toList());
    }

    private String stripDocumentSuffix(String title) {
        return title.replaceAll("(判决书|裁定书|决定书|通知书|调解书)$", "").trim();
    }

    private String extractLawName(String raw) {
        Matcher matcher = Pattern.compile("《([^》]+)》").matcher(raw);
        return matcher.find() ? matcher.group(1) : "法律法规";
    }

    private String mapRightRefType(String refName) {
        if (refName.contains("案号")) {
            return "case_number";
        }
        if (refName.contains("决定号")) {
            return "decision_number";
        }
        if (refName.contains("专利")) {
            return "patent_number";
        }
        if (refName.contains("品种")) {
            return "right_number";
        }
        return "reference_number";
    }

    private int estimateTokenCount(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / 1.6));
    }

    private String getFileExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return collapseWhitespace(value).trim();
    }

    private String normalizeOptionalTags(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return joinDistinct(List.of(value.split("[,，；;、\\s]+")));
    }

    private String normalizeText(String text) {
        return legalDocumentTextProcessor.normalizeText(text);
    }

    private String normalizeRawText(String text) {
        return legalDocumentTextProcessor.normalizeRawText(text);
    }

    private String collapseWhitespace(String text) {
        return legalDocumentTextProcessor.collapseWhitespace(text);
    }

    private String cleanChunkSource(String text) {
        return legalDocumentTextProcessor.cleanChunkSource(text);
    }

    private String injectHeadingBreaks(String text) {
        return legalDocumentTextProcessor.injectHeadingBreaks(text);
    }

    private String normalizeLineText(String line) {
        return legalDocumentTextProcessor.normalizeLineText(line);
    }

    private boolean isPageMarkerLine(String line) {
        return legalDocumentTextProcessor.isPageMarkerLine(line);
    }

    private String ellipsize(String text, int maxLength) {
        return legalDocumentTextProcessor.ellipsize(text, maxLength);
    }

    private String normalizeForKey(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsHan}a-z0-9]", "");
    }

    private Map<String, Object> unwrapData(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : dataMap.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return payload;
    }

    private String asText(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : defaultValue;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private void addLabelled(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + "：" + value.trim());
        }
    }

    private void addIfHasText(Collection<String> collection, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        for (String token : value.split("[,，；;、|]")) {
            String trimmed = token.trim();
            if (StringUtils.hasText(trimmed)) {
                collection.add(trimmed);
            }
        }
    }

    private String joinDistinct(Collection<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("计算文件指纹失败", ex);
        }
    }

    private class ServiceParserSupport implements LegalDocumentParser.ParserSupport {

        @Override
        public List<String> extractMeaningfulLines(String text) {
            return LegalDocumentServiceImpl.this.extractMeaningfulLines(text);
        }

        @Override
        public String buildTitle(String fileName, List<String> lines) {
            return LegalDocumentServiceImpl.this.buildTitle(fileName, lines);
        }

        @Override
        public String extractCaseNumber(String title, String text) {
            return LegalDocumentServiceImpl.this.extractCaseNumber(title, text);
        }

        @Override
        public String extractCourtName(List<String> lines, String title, String text) {
            return LegalDocumentServiceImpl.this.extractCourtName(lines, title, text);
        }

        @Override
        public String detectDocumentSubtype(String title, String text) {
            return LegalDocumentServiceImpl.this.detectDocumentSubtype(title, text);
        }

        @Override
        public String detectDocumentType(String documentSubtype, String title, String text) {
            return LegalDocumentServiceImpl.this.detectDocumentType(documentSubtype, title, text);
        }

        @Override
        public String detectCaseType(String documentSubtype, String title, String text) {
            return LegalDocumentServiceImpl.this.detectCaseType(documentSubtype, title, text);
        }

        @Override
        public String detectCause(String title, String text) {
            return LegalDocumentServiceImpl.this.detectCause(title, text);
        }

        @Override
        public String detectBusinessDomain(String title, String text, String cause) {
            return LegalDocumentServiceImpl.this.detectBusinessDomain(title, text, cause);
        }

        @Override
        public String detectProcedureDomain(String title, String text) {
            return LegalDocumentServiceImpl.this.detectProcedureDomain(title, text);
        }

        @Override
        public String detectTrialStage(String title, String text) {
            return LegalDocumentServiceImpl.this.detectTrialStage(title, text);
        }

        @Override
        public String detectProcedureStageRaw(String title, String text) {
            return LegalDocumentServiceImpl.this.detectProcedureStageRaw(title, text);
        }

        @Override
        public String detectSpecialProcedureType(String title, String text) {
            return LegalDocumentServiceImpl.this.detectSpecialProcedureType(title, text);
        }

        @Override
        public String detectApplicationType(String title, String text) {
            return LegalDocumentServiceImpl.this.detectApplicationType(title, text);
        }

        @Override
        public LocalDate extractDecisionDate(List<String> lines, String text) {
            return LegalDocumentServiceImpl.this.extractDecisionDate(lines, text);
        }

        @Override
        public List<LegalDocumentSection> splitSections(String text) {
            return toSupportSections(LegalDocumentServiceImpl.this.splitSections(text));
        }

        @Override
        public String extractTrialProcessSummary(String cleanText, List<LegalDocumentSection> sections) {
            return LegalDocumentServiceImpl.this.extractTrialProcessSummary(cleanText, toLocalSections(sections));
        }

        @Override
        public String buildClaimsSummary(List<LegalDocumentSection> sections) {
            return LegalDocumentServiceImpl.this.buildClaimsSummary(sections);
        }

        @Override
        public String buildDefenseSummary(List<LegalDocumentSection> sections) {
            return LegalDocumentServiceImpl.this.buildDefenseSummary(sections);
        }

        @Override
        public String buildFactsSummary(List<LegalDocumentSection> sections) {
            return LegalDocumentServiceImpl.this.buildFactsSummary(sections);
        }

        @Override
        public String buildIssuesSummary(List<LegalDocumentSection> sections) {
            return LegalDocumentServiceImpl.this.buildIssuesSummary(sections);
        }

        @Override
        public String buildReasoningSummary(List<LegalDocumentSection> sections) {
            return LegalDocumentServiceImpl.this.buildReasoningSummary(sections);
        }

        @Override
        public String buildResultSummary(List<LegalDocumentSection> sections) {
            return LegalDocumentServiceImpl.this.buildResultSummary(sections);
        }

        @Override
        public String extractDecisionResult(String text) {
            return LegalDocumentServiceImpl.this.extractDecisionResult(text);
        }

        @Override
        public String stripDecisionTail(String text) {
            return LegalDocumentServiceImpl.this.stripDecisionTail(text);
        }

        @Override
        public List<LegalDocumentParty> extractParties(List<String> lines, String title) {
            return legalDocumentRelationExtractor.extractParties(lines, title);
        }

        @Override
        public List<LegalDocumentReference> extractReferences(String text, String caseNumber) {
            return legalDocumentRelationExtractor.extractReferences(text, caseNumber);
        }

        @Override
        public String defaultSourceName() {
            return "中国裁判文书网";
        }

        @Override
        public String getFileExtension(String fileName) {
            return LegalDocumentServiceImpl.this.getFileExtension(fileName);
        }

        @Override
        public String normalizeForKey(String text) {
            return LegalDocumentServiceImpl.this.normalizeForKey(text);
        }

        @Override
        public String detectCourtLevel(String courtName) {
            return LegalDocumentServiceImpl.this.detectCourtLevel(courtName);
        }

        @Override
        public String detectProvince(String courtName) {
            return LegalDocumentServiceImpl.this.detectProvince(courtName);
        }

        @Override
        public String detectCity(String courtName) {
            return LegalDocumentServiceImpl.this.detectCity(courtName);
        }

        @Override
        public String normalizeCause(String cause) {
            return LegalDocumentServiceImpl.this.normalizeCause(cause);
        }

        @Override
        public String normalizeDecisionResult(String decisionResult) {
            return LegalDocumentServiceImpl.this.normalizeDecisionResult(decisionResult);
        }

        @Override
        public String buildPartiesText(List<LegalDocumentParty> parties) {
            return LegalDocumentServiceImpl.this.buildPartiesText(parties);
        }

        @Override
        public String buildDisputeSummary(LegalDocument document, List<LegalDocumentSection> sections) {
            return LegalDocumentServiceImpl.this.buildDisputeSummary(document, toLocalSections(sections));
        }

        @Override
        public String extractLegalBasisText(List<LegalDocumentReference> references, String text) {
            return LegalDocumentServiceImpl.this.extractLegalBasisText(references, text);
        }

        @Override
        public String extractRelatedCaseNumbers(List<LegalDocumentReference> references, String currentCaseNumber) {
            return LegalDocumentServiceImpl.this.extractRelatedCaseNumbers(references, currentCaseNumber);
        }

        @Override
        public String extractRelatedDecisionNumbers(List<LegalDocumentReference> references) {
            return LegalDocumentServiceImpl.this.extractRelatedDecisionNumbers(references);
        }

        @Override
        public String buildCaseDetailText(LegalDocument document) {
            return LegalDocumentServiceImpl.this.buildCaseDetailText(document);
        }

        @Override
        public String buildDisplayText(String rawText) {
            return LegalDocumentServiceImpl.this.buildDisplayText(rawText);
        }

        @Override
        public BigDecimal scoreDocumentQuality(String text,
                                               String title,
                                               String caseNumber,
                                               String courtName,
                                               List<LegalDocumentSection> sections) {
            return LegalDocumentServiceImpl.this.scoreDocumentQuality(text, title, caseNumber, courtName, toLocalSections(sections));
        }

        @Override
        public List<String> buildKeywords(LegalDocument document,
                                          List<LegalDocumentParty> parties,
                                          List<LegalDocumentReference> references) {
            return LegalDocumentServiceImpl.this.buildKeywords(document, parties, references);
        }

        @Override
        public List<String> buildAliases(LegalDocument document) {
            return LegalDocumentServiceImpl.this.buildAliases(document);
        }

        @Override
        public List<String> buildIssueTags(LegalDocument document, List<LegalDocumentSection> sections) {
            return LegalDocumentServiceImpl.this.buildIssueTags(document, toLocalSections(sections));
        }

        @Override
        public List<String> buildResultTags(LegalDocument document) {
            return LegalDocumentServiceImpl.this.buildResultTags(document);
        }

        @Override
        public List<LegalDocumentChunk> buildChunks(LegalDocument document, List<LegalDocumentSection> sections) {
            return legalDocumentChunkService.buildChunks(
                    document,
                    sections,
                    new LegalDocumentChunkService.ChunkSupport() {
                        @Override
                        public String firstNonBlank(String... values) {
                            return LegalDocumentServiceImpl.this.firstNonBlank(values);
                        }

                        @Override
                        public String buildChunkRetrievalText(LegalDocument document, LegalDocumentSection section, String chunkText) {
                            return LegalDocumentServiceImpl.this.buildChunkRetrievalText(
                                    document,
                                    new Section(section.type, section.title, section.headerPath, section.content),
                                    chunkText
                            );
                        }

                        @Override
                        public int estimateTokenCount(String text) {
                            return LegalDocumentServiceImpl.this.estimateTokenCount(text);
                        }

                        @Override
                        public String sha256Hex(byte[] bytes) {
                            return LegalDocumentServiceImpl.this.sha256Hex(bytes);
                        }
                    }
            );
        }

        @Override
        public String firstNonBlank(String... values) {
            return LegalDocumentServiceImpl.this.firstNonBlank(values);
        }
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

    private static class DedupState {
        private final Map<String, DuplicateMatch> bySha256 = new HashMap<>();
        private final Map<String, DuplicateMatch> byCaseAndTitle = new HashMap<>();
        private final Map<String, DuplicateMatch> byCaseNumber = new HashMap<>();
        private final Map<String, DuplicateMatch> byTitle = new HashMap<>();
    }

    private static class DuplicateMatch {
        private final Long documentId;
        private final String title;
        private final String caseNumber;
        private final Long categoryId;
        private final String categoryName;

        private DuplicateMatch(Long documentId, String title, String caseNumber, Long categoryId, String categoryName) {
            this.documentId = documentId;
            this.title = title;
            this.caseNumber = caseNumber;
            this.categoryId = categoryId;
            this.categoryName = categoryName;
        }
    }

    private static class Section extends LegalDocumentSection {
        private Section(String type, String title, String headerPath, String content) {
            super(type, title, headerPath, content);
        }
    }

    private static class TextSlice extends LegalDocumentTextSlice {
        private TextSlice(int start, int end, String text) {
            super(start, end, text);
        }
    }
}
