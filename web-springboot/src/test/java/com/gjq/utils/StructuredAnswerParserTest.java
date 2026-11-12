package com.gjq.utils;

import com.gjq.vo.StructuredAnswerVO;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredAnswerParserTest {

    @Test
    void parseSupportsLawyerFollowUpSection() {
        String content = String.join("\n",
                "\u521d\u6b65\u7ed3\u8bba\uff1a\u4ec5\u51ed\u201c\u5438\u98df\u8fc7\u6bd2\u54c1\u201d\u8fd9\u4e00\u8868\u8ff0\uff0c\u4e0d\u80fd\u76f4\u63a5\u7b49\u540c\u4e8e\u6784\u6210\u6bd2\u54c1\u72af\u7f6a\u3002",
                "\u6cd5\u5f8b\u4f9d\u636e\uff1a\u662f\u5426\u6784\u6210\u72af\u7f6a\uff0c\u8981\u7ed3\u5408\u5177\u4f53\u884c\u4e3a\u3001\u6570\u91cf\u548c\u8bc1\u636e\u5224\u65ad\u3002",
                "\u98ce\u9669\u63d0\u793a\uff1a\u5982\u679c\u540c\u65f6\u6d89\u53ca\u6301\u6709\u3001\u8fd0\u8f93\u6216\u5bb9\u7559\u4ed6\u4eba\u5438\u6bd2\uff0c\u98ce\u9669\u4f1a\u660e\u663e\u4e0a\u5347\u3002",
                "\u884c\u52a8\u5efa\u8bae\uff1a\u5148\u6838\u5b9e\u73b0\u6709\u6750\u6599\uff0c\u518d\u51b3\u5b9a\u4e0b\u4e00\u6b65\u8fa9\u62a4\u65b9\u5411\u3002",
                "\u5f8b\u5e08\u8ffd\u95ee\uff1a\u516c\u5b89\u673a\u5173\u76ee\u524d\u662f\u5df2\u7ecf\u5bf9\u4f60\u91c7\u53d6\u62d8\u7559\u63aa\u65bd\uff0c\u8fd8\u662f\u8fd8\u5904\u4e8e\u53e3\u5934\u4f20\u5524\u9636\u6bb5\uff1f\u662f\u5426\u5df2\u7ecf\u505a\u8fc7\u5c3f\u68c0\uff1f"
        );

        StructuredAnswerVO structuredAnswer = StructuredAnswerParser.parse(content);

        assertThat(structuredAnswer.getConclusion()).contains("\u4ec5\u51ed");
        assertThat(structuredAnswer.getBasis()).contains("\u5177\u4f53\u884c\u4e3a");
        assertThat(structuredAnswer.getRisk()).contains("\u98ce\u9669");
        assertThat(structuredAnswer.getAdvice()).contains("\u6838\u5b9e");
        assertThat(structuredAnswer.getFollowUp())
                .isEqualTo("\u516c\u5b89\u673a\u5173\u76ee\u524d\u662f\u5df2\u7ecf\u5bf9\u4f60\u91c7\u53d6\u62d8\u7559\u63aa\u65bd\uff0c\u8fd8\u662f\u8fd8\u5904\u4e8e\u53e3\u5934\u4f20\u5524\u9636\u6bb5\uff1f");
    }

    @Test
    void fromObjectSupportsCamelCaseAndSnakeCaseFollowUp() {
        Map<String, Object> camelCasePayload = new HashMap<>();
        camelCasePayload.put("conclusion", "A");
        camelCasePayload.put("followUp", "B");

        StructuredAnswerVO camelCase = StructuredAnswerParser.fromObject(camelCasePayload, "");
        assertThat(camelCase.getFollowUp()).isEqualTo("B");

        Map<String, Object> snakeCasePayload = new HashMap<>();
        snakeCasePayload.put("conclusion", "A");
        snakeCasePayload.put("follow_up", "C");

        StructuredAnswerVO snakeCase = StructuredAnswerParser.fromObject(snakeCasePayload, "");
        assertThat(snakeCase.getFollowUp()).isEqualTo("C");
    }
}
