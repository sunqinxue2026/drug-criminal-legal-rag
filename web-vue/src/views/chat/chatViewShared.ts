import type { ChatMessage, ChatStructuredAnswer } from '@/types'

export const CONSULTATION_PROFILE = 'drug_crime'
export const DEFAULT_SCOPE_STATEMENT = '当前系统聚焦涉毒刑事法律风险分析、程序权利和家属应对场景。'
export const DEFAULT_DISCLAIMER = '本系统回答仅用于涉毒刑事普法与风险提示，不构成正式法律意见或律师代理意见；涉及个案请尽快咨询执业律师。'
export const PRIVACY_REMINDER = '上传图片、聊天记录和文书前，请尽量隐去身份证号、住址、电话、银行卡号等敏感信息。'

export const quickCategories = ['刑事', '毒品', '取保候审', '诈骗', '合同', '定罪量刑']
export const intakeChecklist = [
  '你是本人、家属还是朋友',
  '现在处于什么阶段：询问、拘留、取保、起诉或审判',
  '你最担心什么：会不会判、能不能取保、家属该做什么',
  '手里有没有通知书、聊天记录、转账记录、检测结果等材料'
]

export type GuidedIntakePrompt = {
  title: string
  subtitle: string
  template: string
}

export const guidedIntakePrompts: GuidedIntakePrompt[] = [
  {
    title: '我是家属',
    subtitle: '先说人与阶段，我来帮你梳理下一步',
    template: '我是家属。\n现在已知情况是：\n办案阶段：\n收到的通知或口头信息：\n我最想先知道：接下来该怎么做？'
  },
  {
    title: '我是本人',
    subtitle: '先说经过和你最担心的问题',
    template: '我是本人。\n事情经过大致是：\n现在所在阶段：\n办案机关是否已经联系过我：\n我最担心的是：会不会被认定为犯罪、会怎么处理？'
  },
  {
    title: '我有材料',
    subtitle: '有截图、通知书、转账记录都可以一起带入',
    template: '我这边有一些材料想一起给你看。\n材料类型：\n材料里最关键的信息：\n我希望你重点帮我判断：'
  },
  {
    title: '你来追问我',
    subtitle: '不知道怎么问时，先让我一步步回答',
    template: '我现在不知道该怎么描述，你可以像律师接待咨询一样，先一步步问我最关键的几个问题。'
  }
]

export const starterQuestions = [
  '家属收到涉毒案件拘留通知后，现在最应该先做什么？',
  '非法持有毒品罪和容留他人吸毒罪有什么区别？',
  '涉毒案件里自首、立功、认罪认罚通常会怎样影响处理结果？'
]

export type PendingDocument = {
  name: string
  url: string
  preview: string
  extractedText: string
  charCount: number
  extension: string
  status: 'parsed' | 'linked' | 'failed'
  statusLabel: string
}

export type AnswerSections = ChatStructuredAnswer
type ParsedAnswerCacheEntry = {
  signature: string
  sections: AnswerSections
}
export type ParsedAnswerCache = WeakMap<ChatMessage, ParsedAnswerCacheEntry>

const escapeRegExp = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

const stripDecorators = (value: string) => {
  let result = (value || '').trim()
  let previous = ''
  while (result && result !== previous) {
    previous = result
    result = result
      .replace(/^```[\w-]*\s*/u, '')
      .replace(/^>+\s*/u, '')
      .replace(/^#{1,6}\s*/u, '')
      .replace(/^(?:[（(]?[0-9一二三四五六七八九十]+[）).、．])\s*/u, '')
      .trim()
  }
  return result
}

const stripSectionLabel = (value: string, labels: string[]) => {
  let result = stripDecorators(value)
  for (const label of labels) {
    const matcher = new RegExp(`^${escapeRegExp(label)}[：:]?\\s*`, 'u')
    result = stripDecorators(result.replace(matcher, '').trim())
  }
  return result
}

const hasStructuredSections = (sections?: Partial<AnswerSections> | null) => {
  if (!sections) return false
  return ['conclusion', 'basis', 'risk', 'advice', 'followUp'].some((key) => {
    const value = sections[key as keyof AnswerSections]
    return typeof value === 'string' && value.trim().length > 0
  })
}

const buildAnswerSections = (content: string): AnswerSections => {
  const text = (content || '').trim()
  const sections = {
    conclusion: text,
    basis: '',
    risk: '',
    advice: '',
    followUp: ''
  }

  const basisMarkers = ['法律依据', '法条依据', '二、']
  const riskMarkers = ['风险提示', '注意事项', '三、']
  const adviceMarkers = ['行动建议', '维权建议', '四、']
  const followUpMarkers = ['律师追问', '补充追问', '进一步追问', '五、']

  const findMarker = (markers: string[]) => markers.find((marker) => text.includes(marker)) || ''
  const basisMarker = findMarker(basisMarkers)
  const riskMarker = findMarker(riskMarkers)
  const adviceMarker = findMarker(adviceMarkers)
  const followUpMarker = findMarker(followUpMarkers)

  if (basisMarker || riskMarker || adviceMarker || followUpMarker) {
    const basisIndex = basisMarker ? text.indexOf(basisMarker) : -1
    const riskIndex = riskMarker ? text.indexOf(riskMarker) : -1
    const adviceIndex = adviceMarker ? text.indexOf(adviceMarker) : -1
    const followUpIndex = followUpMarker ? text.indexOf(followUpMarker) : -1
    const indexes = [basisIndex, riskIndex, adviceIndex, followUpIndex].filter((index) => index >= 0).sort((a, b) => a - b)

    if (indexes.length) {
      sections.conclusion = text.slice(0, indexes[0]).trim() || text
      if (basisIndex >= 0) {
        const end = [riskIndex, adviceIndex, followUpIndex]
          .filter((index) => index > basisIndex)
          .sort((a, b) => a - b)[0] ?? text.length
        sections.basis = text.slice(basisIndex, end).trim()
      }
      if (riskIndex >= 0) {
        const end = [adviceIndex, followUpIndex]
          .filter((index) => index > riskIndex)
          .sort((a, b) => a - b)[0] ?? text.length
        sections.risk = text.slice(riskIndex, end).trim()
      }
      if (adviceIndex >= 0) {
        const end = [followUpIndex].filter((index) => index > adviceIndex).sort((a, b) => a - b)[0] ?? text.length
        sections.advice = text.slice(adviceIndex, end).trim()
      }
      if (followUpIndex >= 0) {
        sections.followUp = text.slice(followUpIndex).trim()
      }
    }
  }

  return sections
}

const normalizeStructuredSections = (sections?: Partial<AnswerSections> | null): AnswerSections => ({
  conclusion: String(sections?.conclusion || '').trim(),
  basis: String(sections?.basis || '').trim(),
  risk: String(sections?.risk || '').trim(),
  advice: String(sections?.advice || '').trim(),
  followUp: String(sections?.followUp || '').trim()
})

const buildAnswerSignature = (message: ChatMessage) => {
  const structured = normalizeStructuredSections(message.structuredAnswer)
  return JSON.stringify({
    content: String(message.content || ''),
    responseMode: String(message.responseMode || ''),
    conclusion: structured.conclusion,
    basis: structured.basis,
    risk: structured.risk,
    advice: structured.advice,
    followUp: structured.followUp
  })
}

const compactComparableText = (value: string) => {
  return (value || '')
    .replace(/[\s，。！？!?,、；;：:()（）【】\[\]<>《》"'“”‘’]/g, '')
    .trim()
}

const normalizeFollowUpText = (value: string) => {
  const text = stripDecorators(value).replace(/\s+/g, ' ').trim()
  if (!text) return ''

  const indexes = ['？', '?', '。', '！', '!', '；', ';']
    .map((marker) => text.indexOf(marker))
    .filter((index) => index >= 0)

  if (!indexes.length) return text
  return text.slice(0, Math.min(...indexes) + 1).trim()
}

const hasMeaningfulText = (value: string) => {
  return compactComparableText(value).length > 1
}

const isBriefGreetingMessage = (message: ChatMessage) => {
  const text = (message.content || '').trim()
  if (!text) return false
  if (message.responseMode === 'brief_greeting' || message.responseMode === 'guided_intake') return true
  return /^你好[，,！!。]?/.test(text) && /AI\s*律师/.test(text) && !(message.references?.length)
}

const escapeHtml = (value: string) => {
  return (value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

const formatInlineText = (value: string) => {
  return escapeHtml(value)
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
}

const normalizeDisplayLine = (value: string) => {
  let text = stripDecorators(value)
  if (!text) return ''
  if (/^```/.test(text)) return ''
  if (/^[-*_]{3,}$/.test(text)) return ''
  if (/^\|?[-:\s|]+\|?$/.test(text)) return ''
  if ((text.match(/\|/g) || []).length >= 2) {
    text = text
      .replace(/^\|/, '')
      .replace(/\|$/, '')
      .split('|')
      .map((item) => item.trim())
      .filter(Boolean)
      .join(' ')
  }
  return text.trim()
}

export const parseAnswer = (message: ChatMessage, parsedCache: ParsedAnswerCache) => {
  const nextSignature = buildAnswerSignature(message)
  const cached = parsedCache.get(message)

  if (!cached || cached.signature !== nextSignature) {
    const structured = normalizeStructuredSections(message.structuredAnswer)
    parsedCache.set(message, {
      signature: nextSignature,
      sections: hasStructuredSections(structured) ? structured : buildAnswerSections(message.content)
    })
  }
  return parsedCache.get(message)!.sections
}

export const getSectionDisplayText = (
  message: ChatMessage,
  section: keyof AnswerSections,
  parsedCache: ParsedAnswerCache
) => {
  const sections = parseAnswer(message, parsedCache)
  if (section === 'basis') return stripSectionLabel(sections.basis, ['法律依据', '法条依据'])
  if (section === 'risk') return stripSectionLabel(sections.risk, ['风险提示', '注意事项'])
  if (section === 'advice') return stripSectionLabel(sections.advice, ['行动建议', '维权建议'])
  if (section === 'followUp') {
    return normalizeFollowUpText(stripSectionLabel(sections.followUp, ['律师追问', '补充追问', '进一步追问']))
  }

  const structuredConclusion = stripSectionLabel(sections.conclusion, ['初步结论', '结论'])
  const structuredCompact = compactComparableText(structuredConclusion)
  if (structuredCompact && !['初步', '结论', '初步结论'].includes(structuredCompact)) {
    return structuredConclusion
  }

  const rebuiltConclusion = stripSectionLabel(buildAnswerSections(message.content).conclusion, ['初步结论', '结论'])
  const rebuiltCompact = compactComparableText(rebuiltConclusion)
  if (rebuiltCompact && !['初步', '结论', '初步结论'].includes(rebuiltCompact)) {
    return rebuiltConclusion
  }

  return structuredConclusion || rebuiltConclusion || message.content
}

export const hasMeaningfulSection = (
  message: ChatMessage,
  section: keyof AnswerSections,
  parsedCache: ParsedAnswerCache
) => {
  return hasMeaningfulText(getSectionDisplayText(message, section, parsedCache))
}

export const shouldUseStructuredAssistantLayout = (
  message: ChatMessage,
  parsedCache: ParsedAnswerCache
) => {
  const text = (message.content || '').trim()
  if (!text) return false
  if (isBriefGreetingMessage(message)) return false
  const sections = parseAnswer(message, parsedCache)
  if (sections.basis.trim() || sections.risk.trim() || sections.advice.trim() || sections.followUp.trim()) return true
  if (message.references?.length) return true
  return /(法律依据|法条依据|风险提示|行动建议|维权建议|律师追问|补充追问|进一步追问|量刑参考|一、|二、|三、|四、|五、)/.test(text)
}

export const getAssistantPrimaryText = (
  message: ChatMessage,
  parsedCache: ParsedAnswerCache
) => {
  if (!shouldUseStructuredAssistantLayout(message, parsedCache)) return message.content
  return getSectionDisplayText(message, 'conclusion', parsedCache) || message.content
}

export const shouldShowRiskLevel = (
  message: ChatMessage,
  parsedCache: ParsedAnswerCache
) => {
  if (!shouldUseStructuredAssistantLayout(message, parsedCache)) return false
  if (isBriefGreetingMessage(message)) return false
  const signalText = [
    message.content || '',
    getSectionDisplayText(message, 'risk', parsedCache),
    getSectionDisplayText(message, 'advice', parsedCache)
  ].join('\n')
  return /(风险|违法|犯罪|刑事|处罚|量刑|拘留|逮捕|起诉|侦查|取保|证据|家属|下一步|尽快|立即)/.test(signalText)
}

export const getRiskLevelLabel = (
  message: ChatMessage,
  parsedCache: ParsedAnswerCache
) => {
  if (!shouldShowRiskLevel(message, parsedCache)) return '一般'
  const text = `${message.content}\n${getSectionDisplayText(message, 'risk', parsedCache)}\n${getSectionDisplayText(message, 'advice', parsedCache)}`.toLowerCase()
  if (/(立即|尽快|严重|高风险|逮捕|拘留|起诉|刑事责任|强制措施)/.test(text)) return '较高'
  if (/(建议|注意|补充|核实|争议|协商|证据|取保|程序)/.test(text)) return '中等'
  return '一般'
}

export const getRiskLevelClass = (
  message: ChatMessage,
  parsedCache: ParsedAnswerCache
) => {
  const level = getRiskLevelLabel(message, parsedCache)
  if (level === '较高') return 'high'
  if (level === '中等') return 'medium'
  return 'low'
}

export const getRiskLevelDescription = (
  message: ChatMessage,
  parsedCache: ParsedAnswerCache
) => {
  const level = getRiskLevelLabel(message, parsedCache)
  if (level === '较高') return '当前问题已明显触及刑事风险或强制措施阶段，建议尽快委托律师、核实办案机关并保留通知材料。'
  if (level === '中等') return '当前问题存在一定不确定性，建议先核实案情经过、涉案物质、程序节点和手头材料。'
  return '当前回答更适合作为一般普法参考，正式处理前仍建议结合完整案情做一次专业核验。'
}

export const formatMessage = (content: string) => {
  const lines = (content || '').replace(/\r\n/g, '\n').split('\n')
  const blocks: string[] = []
  let listMode: 'ul' | 'ol' | null = null
  let listItems: string[] = []

  const flushList = () => {
    if (!listMode || !listItems.length) return
    blocks.push(`<${listMode}>${listItems.join('')}</${listMode}>`)
    listMode = null
    listItems = []
  }

  for (const rawLine of lines) {
    const trimmed = rawLine.trim()
    if (!trimmed) {
      flushList()
      continue
    }

    const unorderedMatch = trimmed.match(/^(?:[-*•●▪▫◦]|✅|☑️?|✔️?|🟩|🟢|\[\s?[xX√]?\s?\])\s+(.*)$/u)
    const orderedMatch = trimmed.match(/^\d+[.、]\s+(.*)$/u)
    const listText = unorderedMatch?.[1] || orderedMatch?.[1]

    if (listText) {
      const nextMode = orderedMatch ? 'ol' : 'ul'
      const normalized = normalizeDisplayLine(listText)
      if (!normalized) continue
      if (listMode && listMode !== nextMode) flushList()
      listMode = nextMode
      listItems.push(`<li>${formatInlineText(normalized)}</li>`)
      continue
    }

    flushList()
    const normalized = normalizeDisplayLine(trimmed)
    if (!normalized) continue
    blocks.push(`<p>${formatInlineText(normalized)}</p>`)
  }

  flushList()
  return blocks.join('') || `<p>${formatInlineText(content || '')}</p>`
}

export const formatSidebarTime = (value?: string) => {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()} ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`
}

export const formatShortTimestamp = (value: string) => {
  const date = new Date(value)
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

export const cropCase = (value?: string, limit = 100) => {
  if (!value) return '暂无摘要'
  return value.length > limit ? `${value.slice(0, limit)}...` : value
}

export const getReferenceTags = (value?: string) => {
  if (!value) return []
  return value
    .split(/[，、\s]+/)
    .map((entry) => entry.trim())
    .filter(Boolean)
    .slice(0, 3)
}

export const getReferenceSourceLabel = (source?: string) => {
  if (source === 'case_db') return '案例库'
  if (source === 'legal_knowledge_db') return '法律规范/官方案例'
  if (source === 'legal_document_db') return '专题文书库'
  if (source === 'official_topic_corpus') return '官方专题语料'
  if (source === 'demo') return '演示知识'
  return '检索来源'
}

export const getReferenceSimilarity = (distance?: number) => {
  if (typeof distance !== 'number') return '--'
  const similarity = Math.max(0, Math.min(1, 1 - distance))
  return `${(similarity * 100).toFixed(1)}%`
}

export const getFileExtension = (filename: string) => {
  const lastDotIndex = filename.lastIndexOf('.')
  return lastDotIndex >= 0 ? filename.slice(lastDotIndex + 1).toLowerCase() : ''
}

export const formatModelLabel = (model: string) => {
  return model
    .split('-')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('-')
}

export const isCaseConsultationText = (value: string) => {
  const text = String(value || '').replace(/\s+/g, '').trim()
  if (!text) return false

  if (/(案例|判例|类案|同类案件|裁判文书|判决书|裁定书|判决结果|裁判结果|审判结果|量刑结果)/.test(text)) {
    return true
  }
  if (/(某|被告人|上诉人|原审被告人).{0,18}(贩卖毒品|运输毒品|制造毒品|走私毒品|非法持有毒品|容留他人吸毒).{0,6}案/.test(text)) {
    return true
  }
  return /^[\u4e00-\u9fa5]{1,8}(贩卖毒品|运输毒品|制造毒品|走私毒品|非法持有毒品|容留他人吸毒)案$/.test(text)
}
