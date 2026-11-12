package com.gjq.service.support;

import com.gjq.entity.LegalDocumentParty;
import com.gjq.entity.LegalDocumentReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class LegalDocumentRelationExtractor {

    private static final Pattern PARTY_LINE_PATTERN = Pattern.compile(
            "^(原告|被告|上诉人|被上诉人|申请再审人|再审申请人|被申请人|申请人|申诉人|被申请执行人|申请执行人|被执行人|赔偿请求人|赔偿义务机关|罚款被处罚人|被处罚人|复议申请人|复议被申请人|当事人|一审原告|一审被告|二审上诉人|二审被上诉人)[：:]?\\s*(.+)$"
    );
    private static final Pattern LAW_PATTERN = Pattern.compile("《[^》]{1,50}》[^。；\\n]{0,80}");
    private static final Pattern DECISION_NUMBER_PATTERN = Pattern.compile("第[0-9A-Za-z一二三四五六七八九十百千万〇零]{2,40}号");
    private static final Pattern RIGHT_NUMBER_PATTERN = Pattern.compile("(专利号|申请号|专利申请号|品种权号|登记号|注册号|案号|决定号)[：: ]*([A-Za-z0-9\\-\\.]+)");
    private static final Pattern REPRESENTATIVE_PATTERN = Pattern.compile("(法定代表人|委托诉讼代理人|诉讼代理人|辩护人)[：: ]*([^，。；\\n]{2,40})");

    public List<LegalDocumentParty> extractParties(List<String> lines, String title) {
        List<LegalDocumentParty> parties = new ArrayList<>();
        int seq = 1;
        for (String line : lines) {
            Matcher matcher = PARTY_LINE_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String role = matcher.group(1);
            String value = matcher.group(2).trim();

            LegalDocumentParty party = new LegalDocumentParty();
            party.setPartySeq(seq++);
            party.setPartyRoleRaw(role);
            party.setPartyRoleNormalized(normalizePartyRole(role));
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

        for (String candidate : splitTitleParties(title)) {
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

    public List<LegalDocumentReference> extractReferences(String text, String caseNumber) {
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

        order = extractPatternReferences(
                Pattern.compile("[（(]\\d{4}[)）][^\\s]{1,40}?号"),
                text,
                order,
                seen,
                references,
                "case_number",
                "关联案号"
        );
        order = extractLawReferences(text, order, seen, references);
        order = extractPatternReferences(
                DECISION_NUMBER_PATTERN,
                text,
                order,
                seen,
                references,
                "decision_number",
                "决定号"
        );

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

    private int extractPatternReferences(Pattern pattern,
                                         String text,
                                         int order,
                                         Set<String> seen,
                                         List<LegalDocumentReference> references,
                                         String refType,
                                         String refName) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            String key = refType + "|" + value;
            if (!seen.add(key)) {
                continue;
            }
            LegalDocumentReference ref = new LegalDocumentReference();
            ref.setRefOrder(order++);
            ref.setSectionType("body");
            ref.setRefType(refType);
            ref.setRefName(refName);
            ref.setRefValue(value);
            ref.setNormalizedValue(value);
            ref.setIsPrimary(0);
            ref.setRawText(value);
            references.add(ref);
        }
        return order;
    }

    private int extractLawReferences(String text,
                                     int order,
                                     Set<String> seen,
                                     List<LegalDocumentReference> references) {
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
        return order;
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
        Matcher matcher = REPRESENTATIVE_PATTERN.matcher(value);
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
            return role.trim();
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
        return partyName == null ? "" : partyName.replaceAll("\\s+", "").trim().toLowerCase();
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
        if (!StringUtils.hasText(title)) {
            return "";
        }
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
}
