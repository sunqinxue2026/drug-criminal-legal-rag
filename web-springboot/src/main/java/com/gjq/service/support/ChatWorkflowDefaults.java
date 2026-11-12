package com.gjq.service.support;

public final class ChatWorkflowDefaults {

    private ChatWorkflowDefaults() {
    }

    public static final String DEFAULT_PROFILE = "drug_crime";
    public static final String DEFAULT_IMAGE_PROMPT = "请结合我上传的图片进行涉毒刑事法律风险分析。";
    public static final String DEFAULT_EMPTY_ERROR = "消息内容和图片不能同时为空";
    public static final String DEFAULT_AI_EMPTY_ERROR = "AI 服务返回空响应";
    public static final String DEFAULT_AI_FAILURE_PREFIX = "AI 服务调用失败: ";
    public static final String DEFAULT_FALLBACK_ANSWER = "抱歉，我暂时无法回答这个问题，请稍后再试。";
    public static final String DEFAULT_DISCLAIMER = "本系统回答仅供涉毒刑事普法与风险识别参考，不构成正式法律意见或律师代理意见。";
    public static final String DEFAULT_SCOPE_STATEMENT = "当前系统聚焦涉毒刑事法律风险分析、程序权利提示与家属应对场景。";
    public static final String DEFAULT_PRIVACY_NOTICE = "上传图片、聊天记录、文书材料前，请尽量隐去身份证号、住址、电话、银行卡号等敏感信息。";
}
