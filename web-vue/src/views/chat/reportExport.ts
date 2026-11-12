import type { ChatMessage, ChatReference, ChatSession } from '@/types'

import {
  type AnswerSections,
  DEFAULT_DISCLAIMER,
  DEFAULT_SCOPE_STATEMENT,
  type ParsedAnswerCache,
  getReferenceSimilarity,
  getReferenceSourceLabel,
  getSectionDisplayText,
  hasMeaningfulSection,
  isCaseConsultationText
} from './chatViewShared'

const REPORT_TITLE = '涉毒刑事案件初步法律风险分析报告'
const REPORT_SUBTITLE = 'AI法律咨询分析报告'

export interface ExportConsultationReportOptions {
  session: ChatSession | null
  messages: ChatMessage[]
  parsedCache: ParsedAnswerCache
  displayUserName: string
  scopeStatement?: string
  disclaimer?: string
}

export const canExportConsultationReport = (messages: ChatMessage[]) => {
  return messages.some((message) => message.role === 'assistant' && Boolean(message.content.trim()))
}

export const exportConsultationReport = (options: ExportConsultationReportOptions) => {
  const assistantMessages = options.messages.filter((message) => message.role === 'assistant' && message.content.trim())
  if (!assistantMessages.length) {
    throw new Error('当前会话还没有可导出的AI分析结果')
  }

  const latestAssistant = assistantMessages[assistantMessages.length - 1]
  const latestUser = findLatestUserBefore(options.messages, latestAssistant) || findFirstUserMessage(options.messages)
  const sections = buildReportSections(latestAssistant, options.parsedCache)
  if (isCaseConsultationText(String(latestAssistant.queryAnalysis?.question || latestUser?.content || ''))) {
    sections.followUp = ''
  }
  const references = collectReferences(assistantMessages)
  const generatedAt = formatDateTime(new Date())
  const sessionTitle = normalizeSessionTitle(options.session?.title)
  const filename = buildReportFilename(options.session, sessionTitle)

  const html = buildWordHtml({
    generatedAt,
    session: options.session,
    sessionTitle,
    displayUserName: options.displayUserName,
    scopeStatement: options.scopeStatement || DEFAULT_SCOPE_STATEMENT,
    disclaimer: options.disclaimer || DEFAULT_DISCLAIMER,
    latestUser,
    sections,
    references,
    messages: options.messages
  })

  downloadWordDocument(filename, html)
  return filename
}

const buildReportSections = (message: ChatMessage, parsedCache: ParsedAnswerCache): AnswerSections => {
  return {
    conclusion: getSectionDisplayText(message, 'conclusion', parsedCache),
    basis: hasMeaningfulSection(message, 'basis', parsedCache) ? getSectionDisplayText(message, 'basis', parsedCache) : '',
    risk: hasMeaningfulSection(message, 'risk', parsedCache) ? getSectionDisplayText(message, 'risk', parsedCache) : '',
    advice: hasMeaningfulSection(message, 'advice', parsedCache) ? getSectionDisplayText(message, 'advice', parsedCache) : '',
    followUp: hasMeaningfulSection(message, 'followUp', parsedCache) ? getSectionDisplayText(message, 'followUp', parsedCache) : ''
  }
}

const buildWordHtml = (payload: {
  generatedAt: string
  session: ChatSession | null
  sessionTitle: string
  displayUserName: string
  scopeStatement: string
  disclaimer: string
  latestUser?: ChatMessage
  sections: AnswerSections
  references: ChatReference[]
  messages: ChatMessage[]
}) => {
  const knownFacts = buildKnownFacts(payload.messages)
  const question = payload.latestUser?.content || payload.sessionTitle
  const referencesHtml = payload.references.length
    ? payload.references.map((reference, index) => renderReference(reference, index)).join('')
    : '<p class="muted">本次报告未附带检索引用资料。</p>'
  const messagesHtml = payload.messages.map(renderConversationMessage).join('')

  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8" />
  <title>${escapeHtml(REPORT_TITLE)}</title>
  <style>
    @page { margin: 28mm 24mm; }
    body {
      font-family: "Microsoft YaHei", "SimSun", Arial, sans-serif;
      color: #1f2937;
      font-size: 11.5pt;
      line-height: 1.75;
    }
    h1 {
      margin: 0 0 8pt;
      text-align: center;
      font-size: 21pt;
      color: #111827;
      letter-spacing: 0;
    }
    .subtitle {
      margin: 0 0 18pt;
      text-align: center;
      color: #4b5563;
      font-size: 12pt;
      font-weight: 600;
    }
    .meta-table, .reference-table {
      width: 100%;
      border-collapse: collapse;
      margin: 10pt 0 16pt;
    }
    .meta-table td, .reference-table th, .reference-table td {
      border: 1px solid #d9e2ef;
      padding: 7pt 8pt;
      vertical-align: top;
    }
    .meta-table td:first-child {
      width: 24%;
      background: #f5f7fb;
      color: #374151;
      font-weight: 700;
    }
    .reference-table th {
      background: #f5f7fb;
      color: #374151;
      text-align: left;
    }
    h2 {
      margin: 18pt 0 8pt;
      padding-bottom: 4pt;
      border-bottom: 1px solid #d9e2ef;
      color: #1f3b6d;
      font-size: 14pt;
    }
    h3 {
      margin: 10pt 0 6pt;
      color: #374151;
      font-size: 12pt;
    }
    p {
      margin: 0 0 8pt;
      text-align: justify;
    }
    ol {
      margin: 0 0 10pt 20pt;
      padding: 0;
    }
    li {
      margin: 0 0 5pt;
    }
    .notice {
      padding: 10pt 12pt;
      border: 1px solid #f3d3a1;
      background: #fff8ed;
      color: #7c4a03;
    }
    .section-box {
      padding: 10pt 12pt;
      border: 1px solid #d9e2ef;
      background: #fbfdff;
      margin-bottom: 8pt;
    }
    .conversation-entry {
      margin-bottom: 10pt;
      padding-bottom: 8pt;
      border-bottom: 1px solid #edf2f7;
    }
    .role {
      color: #1f3b6d;
      font-weight: 700;
    }
    .muted {
      color: #6b7280;
    }
  </style>
</head>
<body>
  <h1>${escapeHtml(REPORT_TITLE)}</h1>
  <div class="subtitle">${escapeHtml(REPORT_SUBTITLE)}</div>

  <table class="meta-table">
    <tr><td>生成时间</td><td>${escapeHtml(payload.generatedAt)}</td></tr>
    <tr><td>会话编号</td><td>${escapeHtml(payload.session?.id ? String(payload.session.id) : '未记录')}</td></tr>
    <tr><td>会话标题</td><td>${escapeHtml(payload.sessionTitle)}</td></tr>
    <tr><td>咨询用户</td><td>${escapeHtml(payload.displayUserName || '未记录')}</td></tr>
    <tr><td>系统范围</td><td>${escapeHtml(payload.scopeStatement)}</td></tr>
  </table>

  <h2>一、重要声明</h2>
  <p class="notice">${escapeHtml(payload.disclaimer)} 本报告为系统根据当前会话材料生成的阶段性法律风险分析，不是法院判决、裁定或正式律师代理意见；案件最终处理结果以办案机关、检察机关、审判机关依法作出的文书为准。</p>

  <h2>二、用户核心问题</h2>
  <div class="section-box">${renderParagraphs(question)}</div>

  <h2>三、已知事实摘要</h2>
  ${knownFacts.length ? `<ol>${knownFacts.map((fact) => `<li>${escapeHtml(fact)}</li>`).join('')}</ol>` : '<p class="muted">当前会话中尚未形成明确事实摘要。</p>'}

  <h2>四、核心结论</h2>
  <div class="section-box">${renderParagraphs(payload.sections.conclusion || '当前会话尚未形成可导出的核心结论。')}</div>

  <h2>五、法律依据</h2>
  <div class="section-box">${renderParagraphs(payload.sections.basis || '本轮回答未单独列明法律依据。')}</div>

  <h2>六、风险提示</h2>
  <div class="section-box">${renderParagraphs(payload.sections.risk || '本轮回答未单独列明风险提示。')}</div>

  <h2>七、行动建议</h2>
  <div class="section-box">${renderParagraphs(payload.sections.advice || '本轮回答未单独列明行动建议。')}</div>

  ${payload.sections.followUp ? `<h2>八、待核实事项</h2><div class="section-box">${renderParagraphs(payload.sections.followUp)}</div>` : ''}

  <h2>${payload.sections.followUp ? '九' : '八'}、引用资料</h2>
  ${payload.references.length ? `<table class="reference-table"><thead><tr><th>序号</th><th>标题</th><th>来源</th><th>摘要或片段</th></tr></thead><tbody>${referencesHtml}</tbody></table>` : referencesHtml}

  <h2>${payload.sections.followUp ? '十' : '九'}、完整问答记录</h2>
  ${messagesHtml || '<p class="muted">暂无完整问答记录。</p>'}
</body>
</html>`
}

const buildKnownFacts = (messages: ChatMessage[]) => {
  return messages
    .filter((message) => message.role === 'user')
    .map((message) => {
      const parts = [compactText(message.content)]
      if (message.imageName) parts.push(`上传图片：${message.imageName}`)
      if (message.attachedCases?.length) {
        parts.push(`引用案例：${message.attachedCases.map((item) => item.title).filter(Boolean).join('、')}`)
      }
      if (message.attachedDocuments?.length) {
        parts.push(`上传文书：${message.attachedDocuments.map((item) => item.name).filter(Boolean).join('、')}`)
      }
      return parts.filter(Boolean).join('；')
    })
    .filter(Boolean)
}

const collectReferences = (assistantMessages: ChatMessage[]) => {
  const references: ChatReference[] = []
  const seen = new Set<string>()

  for (const message of [...assistantMessages].reverse()) {
    for (const reference of message.references || []) {
      const key = [
        reference.source || '',
        reference.item_id || '',
        reference.document_id || '',
        reference.chunk_id || '',
        reference.title || '',
        reference.content || ''
      ].join('|')
      if (seen.has(key)) continue
      seen.add(key)
      references.push(reference)
    }
  }

  return references.slice(0, 12)
}

const findLatestUserBefore = (messages: ChatMessage[], assistant: ChatMessage) => {
  const assistantIndex = messages.indexOf(assistant)
  for (let index = assistantIndex - 1; index >= 0; index -= 1) {
    if (messages[index]?.role === 'user') return messages[index]
  }
  return null
}

const findFirstUserMessage = (messages: ChatMessage[]) => messages.find((message) => message.role === 'user')

const renderReference = (reference: ChatReference, index: number) => {
  const sourceParts = [
    getReferenceSourceLabel(reference.source),
    reference.category,
    typeof reference.distance === 'number' ? `相似度 ${getReferenceSimilarity(reference.distance)}` : '',
    reference.source_name ? `来源：${reference.source_name}` : '',
    reference.issuer ? `发布主体：${reference.issuer}` : '',
    reference.effective_date ? `日期：${reference.effective_date}` : ''
  ].filter(Boolean)

  return `<tr>
    <td>${index + 1}</td>
    <td>${escapeHtml(reference.title || `参考片段 ${reference.rank || index + 1}`)}</td>
    <td>${escapeHtml(sourceParts.join('；') || '检索来源')}</td>
    <td>${renderParagraphs(reference.summary || reference.content || '未提供摘要')}</td>
  </tr>`
}

const renderConversationMessage = (message: ChatMessage) => {
  const role = message.role === 'user' ? '用户' : 'AI法律咨询助手'
  const time = message.createTime ? ` · ${formatDateTime(new Date(message.createTime))}` : ''
  const extras = [
    message.model ? `模型：${message.model}` : '',
    message.imageName ? `图片：${message.imageName}` : '',
    message.attachedDocuments?.length ? `文书：${message.attachedDocuments.map((item) => item.name).join('、')}` : '',
    message.attachedCases?.length ? `案例：${message.attachedCases.map((item) => item.title).join('、')}` : ''
  ].filter(Boolean)

  return `<div class="conversation-entry">
    <p class="role">${escapeHtml(role)}${escapeHtml(time)}</p>
    ${extras.length ? `<p class="muted">${escapeHtml(extras.join('；'))}</p>` : ''}
    ${renderParagraphs(message.content)}
  </div>`
}

const renderParagraphs = (value: string) => {
  const paragraphs = String(value || '')
    .replace(/\r\n/g, '\n')
    .split(/\n+/)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)

  if (!paragraphs.length) return '<p class="muted">未记录。</p>'
  return paragraphs.map((paragraph) => `<p>${escapeHtml(paragraph)}</p>`).join('')
}

const downloadWordDocument = (filename: string, html: string) => {
  const blob = new Blob([`\ufeff${html}`], { type: 'application/msword;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

const buildReportFilename = (session: ChatSession | null, title: string) => {
  const date = new Date()
  const datePart = [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0')
  ].join('')
  const sessionPart = session?.id ? `会话${session.id}` : '当前会话'
  return `${REPORT_TITLE}-${sessionPart}-${safeFilename(title).slice(0, 24)}-${datePart}.doc`
}

const normalizeSessionTitle = (title?: string) => {
  const normalized = String(title || '').replace(/\s*\(\d{1,2}\/\d{1,2}\s\d{2}:\d{2}\)\s*$/, '').trim()
  return normalized || 'AI法律咨询'
}

const safeFilename = (value: string) => {
  return String(value || '咨询报告')
    .replace(/[\\/:*?"<>|]/g, '_')
    .replace(/\s+/g, ' ')
    .trim()
}

const formatDateTime = (date: Date) => {
  if (Number.isNaN(date.getTime())) return ''
  const parts = [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0')
  ]
  const timeParts = [
    String(date.getHours()).padStart(2, '0'),
    String(date.getMinutes()).padStart(2, '0')
  ]
  return `${parts.join('-')} ${timeParts.join(':')}`
}

const compactText = (value: string) => {
  return String(value || '').replace(/\s+/g, ' ').trim()
}

const escapeHtml = (value: string | number | undefined | null) => {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
