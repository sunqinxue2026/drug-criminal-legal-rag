package com.gjq.service.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegalDocumentTextProcessorTest {

    private final LegalDocumentTextProcessor processor = new LegalDocumentTextProcessor();

    @Test
    void normalizeTextStripsMergedOfficialSiteHeader() {
        String raw = "上海市人民检察院 无障碍 长者版 关怀版 首页 检务公开 机构设置 检察业务 新闻中心 检察文化 队伍建设 数字检察 12309 "
                + "外部监督 今天2016年3月23日 星期三 以案说法 贩毒后，他竟想这样逃脱法律制裁 日期：2025年11月04日 作者：陈某 来源：检察日报 大 中 小 "
                + "贩毒人员为了掩饰、隐瞒犯罪所得，用父亲的账户收取毒资后再转入自己账户。";

        String normalized = processor.normalizeText(raw);

        assertThat(normalized).startsWith("贩毒人员为了掩饰、隐瞒犯罪所得");
        assertThat(normalized).doesNotContain("无障碍");
        assertThat(normalized).doesNotContain("长者版");
        assertThat(normalized).doesNotContain("关怀版");
        assertThat(normalized).doesNotContain("首页");
        assertThat(normalized).doesNotContain("12309");
        assertThat(normalized).doesNotContain("外部监督");
        assertThat(normalized).doesNotContain("以案说法");
        assertThat(normalized).doesNotContain("作者");
        assertThat(normalized).doesNotContain("来源");
        assertThat(normalized).doesNotContain("大 中 小");
    }
}
