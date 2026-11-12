package com.gjq.service.support;

import com.alibaba.fastjson2.JSON;
import com.gjq.entity.LegalDocument;
import com.gjq.entity.LegalDocumentChunk;
import com.gjq.entity.LegalDocumentExt;
import com.gjq.entity.LegalDocumentParty;
import com.gjq.entity.LegalDocumentReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class LegalDocumentParser {

    public LegalDocumentParsedData parseDocument(MultipartFile file,
                                                 String rawText,
                                                 String cleanText,
                                                 String sha256,
                                                 ParserSupport support) {
        LegalDocumentParsedData parsed = new LegalDocumentParsedData();
        String fileName = file.getOriginalFilename();
        List<String> lines = support.extractMeaningfulLines(cleanText);
        String title = support.buildTitle(fileName, lines);
        String caseNumber = support.extractCaseNumber(title, cleanText);
        String courtName = support.extractCourtName(lines, title, cleanText);
        String documentSubtype = support.detectDocumentSubtype(title, cleanText);
        String documentType = support.detectDocumentType(documentSubtype, title, cleanText);
        String caseType = support.detectCaseType(documentSubtype, title, cleanText);
        String cause = support.detectCause(title, cleanText);
        String businessDomain = support.detectBusinessDomain(title, cleanText, cause);
        String procedureDomain = support.detectProcedureDomain(title, cleanText);
        String trialStage = support.detectTrialStage(title, cleanText);
        String procedureStageRaw = support.detectProcedureStageRaw(title, cleanText);
        String specialProcedureType = support.detectSpecialProcedureType(title, cleanText);
        String applicationType = support.detectApplicationType(title, cleanText);
        LocalDate decisionDate = support.extractDecisionDate(lines, cleanText);

        List<LegalDocumentSection> sections = support.splitSections(cleanText);
        String trialProcessSummary = support.extractTrialProcessSummary(cleanText, sections);
        String factsSummary = support.buildFactsSummary(sections);
        String issuesSummary = support.buildIssuesSummary(sections);
        String reasoningSummary = support.buildReasoningSummary(sections);
        String resultSummary = support.buildResultSummary(sections);
        if (!StringUtils.hasText(resultSummary)) {
            resultSummary = support.extractDecisionResult(cleanText);
        }
        resultSummary = support.stripDecisionTail(resultSummary);

        List<LegalDocumentParty> parties = support.extractParties(lines, title);
        List<LegalDocumentReference> references = support.extractReferences(cleanText, caseNumber);

        LegalDocument document = new LegalDocument();
        document.setSourceType("court_website");
        document.setSourceName(resolveSourceName(fileName, documentType, documentSubtype, title, cleanText, support));
        document.setSourceReliability(1);
        document.setSourceFileName(fileName);
        document.setSourceFilePath(fileName);
        document.setFileExt(support.getFileExtension(fileName));
        document.setFileSize(file.getSize());
        document.setFileSha256(sha256);
        document.setTitle(title);
        document.setTitleNormalized(support.normalizeForKey(title));
        document.setCaseNumber(caseNumber);
        document.setCourtName(courtName);
        document.setCourtLevel(support.detectCourtLevel(courtName));
        document.setProvince(support.detectProvince(courtName));
        document.setCity(support.detectCity(courtName));
        document.setDocumentType(documentType);
        document.setDocumentTypeRaw(documentType);
        document.setDocumentSubtype(documentSubtype);
        document.setDocumentSubtypeRaw(documentSubtype);
        document.setCaseType(caseType);
        document.setCauseRaw(cause);
        document.setCauseNormalized(support.normalizeCause(cause));
        document.setBusinessDomain(businessDomain);
        document.setProcedureDomain(procedureDomain);
        document.setTrialStageNormalized(trialStage);
        document.setProcedureStageRaw(procedureStageRaw);
        document.setSpecialProcedureType(specialProcedureType);
        document.setApplicationType(applicationType);
        document.setDecisionResult(support.stripDecisionTail(support.extractDecisionResult(cleanText)));
        document.setDecisionResultNormalized(support.normalizeDecisionResult(document.getDecisionResult()));
        document.setDecisionDate(decisionDate);
        document.setPartiesText(support.buildPartiesText(parties));
        document.setClaimsSummary(support.buildClaimsSummary(sections));
        document.setDefenseSummary(support.buildDefenseSummary(sections));
        document.setFactsSummary(factsSummary);
        document.setIssuesSummary(issuesSummary);
        document.setReasoningSummary(reasoningSummary);
        document.setResultSummary(resultSummary);
        document.setDisputeSummary(support.buildDisputeSummary(document, sections));
        document.setTrialProcessSummary(trialProcessSummary);
        document.setLegalBasisText(support.extractLegalBasisText(references, cleanText));
        document.setRelatedCaseNumbersText(support.extractRelatedCaseNumbers(references, caseNumber));
        document.setRelatedDecisionNumbersText(support.extractRelatedDecisionNumbers(references));
        document.setCaseDetailText(support.buildCaseDetailText(document));
        document.setDisplayText(support.buildDisplayText(rawText));
        document.setRawText(rawText);
        document.setCleanText(cleanText);
        document.setParserVersion("legal-doc-v2.2");
        document.setParseStatus(1);
        document.setClassifyStatus(1);
        document.setClassifyConfidence(BigDecimal.valueOf(0.82));
        document.setExtractConfidence(BigDecimal.valueOf(0.88));
        document.setDocQualityScore(support.scoreDocumentQuality(cleanText, title, caseNumber, courtName, sections));
        document.setStatus(1);
        document.setDeleted(0);

        parsed.document = document;
        parsed.parties = parties;
        parsed.references = references;
        parsed.chunks = support.buildChunks(document, sections);
        parsed.ext = buildExt(fileName, sections, parties, references, specialProcedureType, applicationType, businessDomain, procedureDomain, support);
        parsed.keywords = support.buildKeywords(document, parties, references);
        parsed.aliases = support.buildAliases(document);
        parsed.issueTags = support.buildIssueTags(document, sections);
        parsed.resultTags = support.buildResultTags(document);
        return parsed;
    }

    private String resolveSourceName(String fileName,
                                     String documentType,
                                     String documentSubtype,
                                     String title,
                                     String text,
                                     ParserSupport support) {
        String metadataCorpus = String.join("\n",
                fileName == null ? "" : fileName,
                documentType == null ? "" : documentType,
                documentSubtype == null ? "" : documentSubtype,
                title == null ? "" : title
        );
        String textHead = text == null ? "" : text.substring(0, Math.min(text.length(), 600)).replaceAll("\\s+", "");
        if (metadataCorpus.contains("12309") || metadataCorpus.contains("起诉书")
                || (textHead.contains("人民检察院") && textHead.contains("起诉书"))) {
            return "12309中国检察网";
        }
        return support.defaultSourceName();
    }

    private LegalDocumentExt buildExt(String fileName,
                                      List<LegalDocumentSection> sections,
                                      List<LegalDocumentParty> parties,
                                      List<LegalDocumentReference> references,
                                      String specialProcedureType,
                                      String applicationType,
                                      String businessDomain,
                                      String procedureDomain,
                                      ParserSupport support) {
        LegalDocumentExt ext = new LegalDocumentExt();
        ext.setExtTypeSummary(support.firstNonBlank(specialProcedureType, businessDomain, procedureDomain, null));
        Map<String, Object> extPayload = new LinkedHashMap<>();
        extPayload.put("sourceFileName", fileName);
        extPayload.put("sectionCount", sections.size());
        extPayload.put("partyCount", parties.size());
        extPayload.put("referenceCount", references.size());
        extPayload.put("specialProcedureType", specialProcedureType);
        extPayload.put("applicationType", applicationType);
        extPayload.put("businessDomain", businessDomain);
        extPayload.put("procedureDomain", procedureDomain);
        ext.setExtJson(JSON.toJSONString(extPayload));
        ext.setSchemaVersion("v2.1");
        return ext;
    }

    public interface ParserSupport {

        List<String> extractMeaningfulLines(String text);

        String buildTitle(String fileName, List<String> lines);

        String extractCaseNumber(String title, String text);

        String extractCourtName(List<String> lines, String title, String text);

        String detectDocumentSubtype(String title, String text);

        String detectDocumentType(String documentSubtype, String title, String text);

        String detectCaseType(String documentSubtype, String title, String text);

        String detectCause(String title, String text);

        String detectBusinessDomain(String title, String text, String cause);

        String detectProcedureDomain(String title, String text);

        String detectTrialStage(String title, String text);

        String detectProcedureStageRaw(String title, String text);

        String detectSpecialProcedureType(String title, String text);

        String detectApplicationType(String title, String text);

        LocalDate extractDecisionDate(List<String> lines, String text);

        List<LegalDocumentSection> splitSections(String text);

        String extractTrialProcessSummary(String cleanText, List<LegalDocumentSection> sections);

        String buildClaimsSummary(List<LegalDocumentSection> sections);

        String buildDefenseSummary(List<LegalDocumentSection> sections);

        String buildFactsSummary(List<LegalDocumentSection> sections);

        String buildIssuesSummary(List<LegalDocumentSection> sections);

        String buildReasoningSummary(List<LegalDocumentSection> sections);

        String buildResultSummary(List<LegalDocumentSection> sections);

        String extractDecisionResult(String text);

        String stripDecisionTail(String text);

        List<LegalDocumentParty> extractParties(List<String> lines, String title);

        List<LegalDocumentReference> extractReferences(String text, String caseNumber);

        String defaultSourceName();

        String getFileExtension(String fileName);

        String normalizeForKey(String text);

        String detectCourtLevel(String courtName);

        String detectProvince(String courtName);

        String detectCity(String courtName);

        String normalizeCause(String cause);

        String normalizeDecisionResult(String decisionResult);

        String buildPartiesText(List<LegalDocumentParty> parties);

        String buildDisputeSummary(LegalDocument document, List<LegalDocumentSection> sections);

        String extractLegalBasisText(List<LegalDocumentReference> references, String text);

        String extractRelatedCaseNumbers(List<LegalDocumentReference> references, String currentCaseNumber);

        String extractRelatedDecisionNumbers(List<LegalDocumentReference> references);

        String buildCaseDetailText(LegalDocument document);

        String buildDisplayText(String rawText);

        BigDecimal scoreDocumentQuality(String text,
                                        String title,
                                        String caseNumber,
                                        String courtName,
                                        List<LegalDocumentSection> sections);

        List<String> buildKeywords(LegalDocument document,
                                   List<LegalDocumentParty> parties,
                                   List<LegalDocumentReference> references);

        List<String> buildAliases(LegalDocument document);

        List<String> buildIssueTags(LegalDocument document, List<LegalDocumentSection> sections);

        List<String> buildResultTags(LegalDocument document);

        List<LegalDocumentChunk> buildChunks(LegalDocument document, List<LegalDocumentSection> sections);

        String firstNonBlank(String... values);
    }
}
