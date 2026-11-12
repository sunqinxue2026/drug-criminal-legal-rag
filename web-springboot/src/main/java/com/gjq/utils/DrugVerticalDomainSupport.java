package com.gjq.utils;

import com.gjq.entity.Category;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DrugVerticalDomainSupport {

    public static final String DEFAULT_CATEGORY_NAME = "家属应对与普法指引";

    private static final List<SeedCategory> DEFAULT_CATEGORIES = List.of(
            new SeedCategory("涉毒犯罪认定", "聚焦毒品犯罪构成要件、罪名区分、共同犯罪和主观明知认定。", 1, "/category-icons/drug-crime.svg", "#2563eb"),
            new SeedCategory("量刑与从宽处罚", "聚焦毒品数量、主从犯区分、自首立功、未成年人和从宽从严规则。", 2, "/category-icons/sentencing.svg", "#7c3aed"),
            new SeedCategory("程序权利与证据规则", "聚焦拘留逮捕、律师会见、取保候审、搜查扣押和电子证据审查。", 3, "/category-icons/evidence.svg", "#0891b2"),
            new SeedCategory("家属应对与普法指引", "聚焦家属沟通、律师委托、戒治衔接、风险提示和禁毒普法。", 4, "/category-icons/family-guide.svg", "#16a34a"),
            new SeedCategory("麻精药品与新型涉毒风险", "聚焦麻醉药品、精神药品、依托咪酯、右美沙芬、笑气等新型风险。", 5, "/category-icons/medicine-risk.svg", "#dc2626")
    );

    private static final List<SeedItem> DEFAULT_ITEMS = List.of(
            new SeedItem(
                    "代购“上头电子烟”是否会被认定为毒品犯罪",
                    "围绕依托咪酯电子烟、主观明知和代购牟利等情节，说明何时可能触发走私、贩卖、运输毒品类风险。",
                    "当事人以“帮朋友带货”“代买电子烟”为名接触依托咪酯等被列管物质时，司法机关通常会结合聊天记录、转账、购买频次、售价差额和收货对象来判断其是否明知系毒品或国家管制精神药品。若存在反复代购、加价牟利、拆分转卖、联系上下家等情节，风险会明显上升，可能触发走私、贩卖、运输毒品相关认定。",
                    "涉毒犯罪认定",
                    "涉毒犯罪认定,依托咪酯,主观明知,代购风险"
            ),
            new SeedItem(
                    "涉毒案件中家属第一时间应该做什么",
                    "梳理家属在接到拘留或传唤消息后更稳妥的应对顺序，避免因慌乱造成额外法律风险。",
                    "家属在接到涉毒刑事风险消息后，通常应先核实承办机关和案件阶段，再尽快保存通知书、转账记录、聊天记录和病历处方等材料，并及时联系律师了解会见、取保候审和证据固定路径。不要代为串供、删聊天记录或转移物品，也不要轻信“花钱捞人”式承诺。系统更适合提供程序普法和风险提醒，而不是替代正式法律意见。",
                    "家属应对与普法指引",
                    "家属应对,律师委托,取保候审,程序普法"
            ),
            new SeedItem(
                    "涉毒案件量刑会重点看哪些因素",
                    "从毒品种类数量、主从犯地位、自首立功、未成年人参与和再犯等角度说明量刑判断逻辑。",
                    "涉毒刑事案件的量刑通常会围绕毒品种类与数量、行为方式、是否牟利、是否向未成年人出售、是否多次实施、是否系累犯毒犯，以及是否存在自首、立功、从犯、坦白、认罪认罚等情节综合判断。对于麻精药品类案件，还会结合是否违规套购、医疗机构身份、处方来源和实际流向来判断社会危害性。",
                    "量刑与从宽处罚",
                    "量刑与从宽处罚,毒品数量,自首立功,认罪认罚"
            ),
            new SeedItem(
                    "聊天记录和转账记录在涉毒案件里有多重要",
                    "说明电子数据、指认笔录、扣押清单和言词证据在涉毒案件中的常见证明作用与争议点。",
                    "在涉毒案件中，聊天记录、转账流水、快递信息、定位轨迹、同案人供述和扣押清单常被组合使用，用于证明主观明知、交易链条和资金往来。辩护与风险分析时应重点审查电子数据提取过程、取证时间链、截图完整性、鉴定意见和供述之间是否相互印证，避免单一证据被过度放大。",
                    "程序权利与证据规则",
                    "程序权利与证据规则,电子证据,聊天记录,转账记录"
            ),
            new SeedItem(
                    "药店和医疗机构违规出售麻精药品的刑事风险",
                    "围绕麻醉药品、精神药品、医疗机构管理漏洞和非法流向问题，说明何时可能升级为刑事风险。",
                    "药店从业者、诊所经营者或医院工作人员若利用处方审核、库存管理、系统权限等漏洞，违规出售、截留、套购麻醉药品和精神药品，并流入吸贩毒环节，通常会被重点审查其身份职责、处方真实性、购销差额、流向对象和牟利情况。达到一定程度时，不再只是行业合规问题，而可能转化为贩卖毒品等刑事风险。",
                    "麻精药品与新型涉毒风险",
                    "麻精药品与新型涉毒风险,麻醉药品,精神药品,医疗机构"
            )
    );

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    private static final Map<String, List<String>> CATEGORY_PRIORITY_HINTS = new LinkedHashMap<>();
    private static final Map<String, List<String>> TAG_KEYWORDS = new LinkedHashMap<>();
    private static final List<String> TOPICAL_TERMS = List.of(
            "禁毒", "毒品", "涉毒", "麻精药品", "麻醉药品", "精神药品", "依托咪酯", "右美沙芬", "笑气"
    );

    static {
        CATEGORY_KEYWORDS.put("涉毒犯罪认定", List.of(
                "毒品犯罪", "走私", "贩卖", "运输", "制造", "非法持有", "容留他人吸毒", "教唆吸毒", "引诱吸毒",
                "欺骗吸毒", "共同犯罪", "主观明知", "毒资", "上下家", "代购", "贩毒", "吸食毒品"
        ));
        CATEGORY_KEYWORDS.put("量刑与从宽处罚", List.of(
                "量刑", "从轻", "从重", "减轻", "缓刑", "自首", "立功", "从犯", "主犯", "累犯",
                "毒品数量", "未成年人", "认罪认罚", "罚金"
        ));
        CATEGORY_KEYWORDS.put("程序权利与证据规则", List.of(
                "刑事拘留", "逮捕", "取保候审", "会见", "辩护", "审查起诉", "侦查", "证据", "电子数据",
                "口供", "搜查", "扣押", "鉴定", "讯问", "同步录音录像"
        ));
        CATEGORY_KEYWORDS.put("家属应对与普法指引", List.of(
                "家属", "律师", "委托", "通知", "普法", "风险提示", "禁毒宣传", "宣传教育", "社区戒毒",
                "社区康复", "强制隔离戒毒", "帮扶", "监护", "学校", "家庭教育", "督促监护令", "家庭教育指导令",
                "家长责任承诺书", "未成年人用药安全", "未成年人药品滥用", "药物滥用", "禁毒公益宣传", "宣传驿站",
                "检察服务站", "进学校", "进乡村", "青少年", "留学期间", "大麻电子烟", "禁种铲除"
        ));
        CATEGORY_KEYWORDS.put("麻精药品与新型涉毒风险", List.of(
                "麻精药品", "麻醉药品", "精神药品", "依托咪酯", "右美沙芬", "笑气", "处方药",
                "列管", "医疗机构", "药店", "曲马多", "复方制剂", "新型毒品"
        ));

        CATEGORY_PRIORITY_HINTS.put("家属应对与普法指引", List.of(
                "督促保护未成年人用药安全公益诉讼案",
                "万某某等人未继续执行剩余强制隔离戒毒期限检察监督案",
                "运用大数据思维督促整治未成年人药品滥用问题",
                "黄某彬贩卖毒品案",
                "张某东贩卖毒品、容留他人吸毒案",
                "陈某豪走私毒品案",
                "江苏省常州市检察机关推动共建禁毒公益宣传驿站",
                "贵州省人民检察院制发检察建议推动毒品原植物禁种铲除工作"
        ));

        TAG_KEYWORDS.put("涉毒犯罪认定", CATEGORY_KEYWORDS.get("涉毒犯罪认定"));
        TAG_KEYWORDS.put("量刑风险", CATEGORY_KEYWORDS.get("量刑与从宽处罚"));
        TAG_KEYWORDS.put("证据审查", CATEGORY_KEYWORDS.get("程序权利与证据规则"));
        TAG_KEYWORDS.put("家属咨询", CATEGORY_KEYWORDS.get("家属应对与普法指引"));
        TAG_KEYWORDS.put("麻精药品", CATEGORY_KEYWORDS.get("麻精药品与新型涉毒风险"));
        TAG_KEYWORDS.put("普法提示", List.of("禁毒", "宣传教育", "社区戒毒", "社区康复", "预防", "帮扶"));
    }

    private DrugVerticalDomainSupport() {
    }

    public static List<SeedCategory> defaultCategories() {
        return DEFAULT_CATEGORIES;
    }

    public static List<SeedItem> defaultItems() {
        return DEFAULT_ITEMS;
    }

    public static Map<String, List<String>> tagKeywordDictionary() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        TAG_KEYWORDS.forEach((key, value) -> copy.put(key, new ArrayList<>(value)));
        return copy;
    }

    public static List<String> keywordsForCategory(String categoryName) {
        if (!StringUtils.hasText(categoryName)) {
            return List.of();
        }
        List<String> direct = CATEGORY_KEYWORDS.get(categoryName.trim());
        if (direct != null) {
            return direct;
        }

        String normalized = categoryName.toLowerCase(Locale.ROOT);
        if (normalized.contains("认定") || normalized.contains("犯罪")) {
            return CATEGORY_KEYWORDS.get("涉毒犯罪认定");
        }
        if (normalized.contains("量刑") || normalized.contains("从宽")) {
            return CATEGORY_KEYWORDS.get("量刑与从宽处罚");
        }
        if (normalized.contains("程序") || normalized.contains("证据")) {
            return CATEGORY_KEYWORDS.get("程序权利与证据规则");
        }
        if (normalized.contains("家属") || normalized.contains("普法")) {
            return CATEGORY_KEYWORDS.get("家属应对与普法指引");
        }
        if (normalized.contains("麻精") || normalized.contains("新型") || normalized.contains("药品")) {
            return CATEGORY_KEYWORDS.get("麻精药品与新型涉毒风险");
        }
        return List.of(categoryName.trim());
    }

    public static String inferCategoryName(String... texts) {
        String aggregate = normalizeTexts(texts);
        if (!StringUtils.hasText(aggregate)) {
            return "";
        }

        String hintedCategory = findPriorityCategoryName(aggregate);
        if (StringUtils.hasText(hintedCategory)) {
            return hintedCategory;
        }

        String bestName = "";
        int bestScore = 0;
        for (SeedCategory category : DEFAULT_CATEGORIES) {
            int score = scoreCategory(category.name, category.description, aggregate);
            if (score > bestScore) {
                bestScore = score;
                bestName = category.name;
            }
        }

        if (bestScore > 0) {
            return bestName;
        }
        return containsTopicalTerm(aggregate) ? DEFAULT_CATEGORY_NAME : "";
    }

    public static Long matchCategoryId(List<Category> categories, String... texts) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        String aggregate = normalizeTexts(texts);
        if (!StringUtils.hasText(aggregate)) {
            return null;
        }

        String hintedCategory = findPriorityCategoryName(aggregate);
        if (StringUtils.hasText(hintedCategory)) {
            return categories.stream()
                    .filter(Objects::nonNull)
                    .filter(category -> hintedCategory.equals(category.getName()))
                    .map(Category::getId)
                    .findFirst()
                    .orElse(null);
        }

        Category bestCategory = null;
        int bestScore = 0;
        for (Category category : categories) {
            if (category == null || !StringUtils.hasText(category.getName())) {
                continue;
            }
            int score = scoreCategory(category.getName(), category.getDescription(), aggregate);
            if (score > bestScore) {
                bestScore = score;
                bestCategory = category;
            }
        }

        if (bestCategory != null && bestScore > 0) {
            return bestCategory.getId();
        }

        if (containsTopicalTerm(aggregate)) {
            return categories.stream()
                    .filter(Objects::nonNull)
                    .filter(category -> DEFAULT_CATEGORY_NAME.equals(category.getName()))
                    .map(Category::getId)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    public static boolean containsTopicalTerm(String... texts) {
        return containsTopicalTerm(normalizeTexts(texts));
    }

    public static boolean containsTopicalTerm(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return TOPICAL_TERMS.stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private static String findPriorityCategoryName(String aggregate) {
        if (!StringUtils.hasText(aggregate)) {
            return "";
        }
        for (Map.Entry<String, List<String>> entry : CATEGORY_PRIORITY_HINTS.entrySet()) {
            for (String hint : entry.getValue()) {
                if (StringUtils.hasText(hint) && aggregate.contains(hint.toLowerCase(Locale.ROOT))) {
                    return entry.getKey();
                }
            }
        }
        return "";
    }

    private static int scoreCategory(String categoryName, String description, String aggregate) {
        Set<String> tokens = new LinkedHashSet<>();
        addTokens(tokens, categoryName);
        addTokens(tokens, description);
        for (String keyword : keywordsForCategory(categoryName)) {
            addTokens(tokens, keyword);
        }

        int score = 0;
        for (String token : tokens) {
            if (token.length() >= 2 && aggregate.contains(token.toLowerCase(Locale.ROOT))) {
                score++;
            }
        }
        if (StringUtils.hasText(categoryName) && aggregate.contains(categoryName.toLowerCase(Locale.ROOT))) {
            score += 2;
        }
        return score;
    }

    private static void addTokens(Collection<String> target, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        for (String token : value.split("[,，、/\\s]+")) {
            String trimmed = token.trim();
            if (trimmed.length() >= 2) {
                target.add(trimmed);
            }
        }
    }

    private static String normalizeTexts(String... texts) {
        if (texts == null || texts.length == 0) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String text : texts) {
            if (StringUtils.hasText(text)) {
                parts.add(text.trim().toLowerCase(Locale.ROOT));
            }
        }
        return String.join(" ", parts);
    }

    public static final class SeedCategory {
        private final String name;
        private final String description;
        private final int sort;
        private final String iconUrl;
        private final String themeColor;

        public SeedCategory(String name, String description, int sort, String iconUrl, String themeColor) {
            this.name = name;
            this.description = description;
            this.sort = sort;
            this.iconUrl = iconUrl;
            this.themeColor = themeColor;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public int getSort() {
            return sort;
        }

        public String getIconUrl() {
            return iconUrl;
        }

        public String getThemeColor() {
            return themeColor;
        }
    }

    public static final class SeedItem {
        private final String title;
        private final String summary;
        private final String content;
        private final String categoryName;
        private final String tags;

        public SeedItem(String title, String summary, String content, String categoryName, String tags) {
            this.title = title;
            this.summary = summary;
            this.content = content;
            this.categoryName = categoryName;
            this.tags = tags;
        }

        public String getTitle() {
            return title;
        }

        public String getSummary() {
            return summary;
        }

        public String getContent() {
            return content;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public String getTags() {
            return tags;
        }
    }
}
