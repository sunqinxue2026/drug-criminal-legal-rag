# 涉毒垂直语料库说明

本目录用于支撑论文题目：

`《基于RAG和云端API的法律咨询问答系统》——以涉毒刑事法律风险分析与普法场景为例`

当前只保留和“涉毒刑事法律风险分析、程序权利、家属应对、普法场景”直接相关的活动语料。

## 当前活动清单

唯一活动清单文件：

- [source_manifest.json](D:/CODE/RAG/data/legal_sources/drug_vertical/source_manifest.json)

当前已接入的 12 组活动语料：

1. `anti_drug_law_zygov`
   - 文档：`中华人民共和国禁毒法（全文）`
   - 数量：`71` 条
2. `criminal_law_drug_provisions_szjd`
   - 文档：`中华人民共和国刑法（涉毒条款节录）`
   - 数量：`11` 条
3. `criminal_law_general_sentencing_qtxjcy`
   - 文档：`中华人民共和国刑法（涉毒量刑情节专题节录）`
   - 数量：`15` 条
4. `criminal_procedure_law_drug_procedure_sdcourt`
   - 文档：`中华人民共和国刑事诉讼法（涉毒程序权利专题节录）`
   - 数量：`24` 条
5. `lawyer_practice_protection_shsjcy`
   - 文档：`人民检察院保障律师在刑事诉讼中依法执业的规定`
   - 数量：`5` 条说明分节
6. `hotline_12309_common_ynfumin`
   - 文档：`12309检察服务热线常识`
   - 数量：`3` 条说明分节
7. `electronic_data_rules_hejcy`
   - 文档：`最高人民法院 最高人民检察院 公安部关于办理刑事案件收集提取和审查判断电子数据若干问题的规定`
   - 数量：`30` 条
8. `drug_crime_judicial_interpretation_2016`
   - 文档：`最高人民法院关于审理毒品犯罪案件适用法律若干问题的解释`
   - 数量：`15` 条
9. `medical_drug_typical_cases_2025`
   - 文档：`涉麻精药品犯罪典型案例`
   - 数量：`7` 篇
10. `drug_crime_typical_cases_2024`
   - 文档：`涉“毒”必严、涉“瘾”必惩、涉“未”必重！最高法发布10件典型案例`
   - 数量：`10` 篇
11. `guiding_case_248_illegal_drug_purchase_meritorious_service`
   - 文档：`最高人民法院发布第44批指导性案例`
   - 数量：`1` 篇
12. `anhui_drug_case_examples_2024`
   - 文档：`全省检察机关毒品案件办理典型案事例`
   - 数量：`5` 篇

活动知识总量：`197`

## 文件夹用途

- `raw/laws/`
  规范类原始网页和元信息
- `raw/judicial_interpretations/`
  司法解释原始网页和元信息
- `raw/guiding_cases/`
  典型案例原始网页和元信息
- `processed/laws/`
  清洗后的法条 JSON
- `processed/judicial_interpretations/`
  清洗后的司法解释 JSON
- `processed/guiding_cases/`
  清洗后的案例 JSON
- `processed/import_batch_official_sources.json`
  当前导入批处理文件

## 当前语料重点覆盖

- 是否可能构成涉毒犯罪
- 涉毒案件中哪些情节会影响量刑
- 什么时候可以委托律师、申请会见
- 取保候审、监视居住、拘留、逮捕的基本程序规则
- 家属收到拘留通知或批捕信息后应当关注什么
- 12309 能提供什么帮助、律师会见受阻时怎么办
- 笑气、依托咪酯、曲马多等新型或麻精药品场景会怎样处理
- 聊天记录、转账记录、鉴定报告、称量笔录、扣押清单会怎样影响定案

## 仍待补齐

- 更多涉毒参考案例和公开典型案例
- 更直接面向家属的官方问答和操作指引
- 证据规则问题的专题普法材料
