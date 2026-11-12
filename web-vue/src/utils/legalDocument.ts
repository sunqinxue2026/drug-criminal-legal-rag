import type { LegalDocument, LegalDocumentChunk } from '@/types'

export interface LegalDocumentFieldItem {
  label: string
  value: string
  wide?: boolean
}

export interface LegalDocumentSummaryBlock {
  label: string
  value: string
}

function asCleanText(value?: unknown) {
  return String(value ?? '')
    .replace(/\r\n?/g, '\n')
    .replace(/\u3000/g, ' ')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .join('\n')
    .trim()
}

function hasMeaningfulText(value?: unknown) {
  return Boolean(asCleanText(value))
}

function collapseInlineText(value?: unknown) {
  return asCleanText(value).replace(/\n+/g, ' ').replace(/\s+/g, ' ').trim()
}

function isSuspiciousCause(value?: string) {
  if (!value) return false
  return /侦查终结|公安局|人民检察院|人民法院|审查起诉|移送起诉|本院受理|案件类型/.test(value)
}

function isDecisionDocument(document: Partial<LegalDocument>) {
  const typeCorpus = `${document.documentSubtype || ''} ${document.documentType || ''}`
  return /判决书|裁定书|决定书/.test(typeCorpus)
}

function isProsecutionDocument(document: Partial<LegalDocument>) {
  const corpus = `${document.documentSubtype || ''} ${document.documentType || ''} ${document.title || ''} ${document.courtName || ''}`
  return /起诉书|人民检察院/.test(corpus)
}

function getDateLabel(document: Partial<LegalDocument>) {
  return isDecisionDocument(document) ? '裁判日期' : '文书日期'
}

function getInstitutionLabel(document: Partial<LegalDocument>) {
  return isProsecutionDocument(document) ? '检察机关' : '法院/发布机构'
}

function buildField(label: string, value?: unknown, wide = false): LegalDocumentFieldItem | null {
  const normalized = collapseInlineText(value)
  if (!normalized) return null
  return {
    label,
    value: normalized,
    wide: wide || normalized.length >= 18
  }
}

export function cleanLegalDocumentBodyText(...candidates: Array<string | null | undefined>) {
  const source = candidates
    .map((item) => asCleanText(item))
    .find((item) => Boolean(item))

  if (!source) return ''

  const seenLeadingLines = new Map<string, number>()
  const cleanedLines: string[] = []
  let previousLine = ''

  for (const [index, rawLine] of source.split('\n').entries()) {
    const line = rawLine.trim()
    if (!line) continue
    if (line === '正文' || line === '律师阅读版正文') continue
    if (line === previousLine) continue

    const seenCount = seenLeadingLines.get(line) ?? 0
    if (index < 10 && seenCount > 0 && line.length <= 40) {
      seenLeadingLines.set(line, seenCount + 1)
      continue
    }

    seenLeadingLines.set(line, seenCount + 1)
    cleanedLines.push(line)
    previousLine = line
  }

  return cleanedLines.join('\n').replace(/\n{3,}/g, '\n\n').trim()
}

export function buildLegalDocumentTags(document: Partial<LegalDocument>) {
  const parts = [
    document.caseType,
    document.businessDomain,
    document.procedureDomain,
    document.causeNormalized || document.causeRaw,
    document.specialProcedureType,
    document.applicationType
  ]
    .map((item) => collapseInlineText(item))
    .filter(Boolean)
    .filter((item) => !isSuspiciousCause(item))

  return Array.from(new Set(parts)).slice(0, 6)
}

export function buildLegalDocumentFieldItems(document: Partial<LegalDocument>) {
  return [
    buildField('案号', document.caseNumber),
    buildField(getInstitutionLabel(document), document.courtName, true),
    buildField('文书类型', document.documentSubtype || document.documentType),
    buildField('案件类型', document.caseType),
    buildField('程序领域', document.procedureDomain),
    buildField('案由', isSuspiciousCause(document.causeNormalized || document.causeRaw) ? '' : (document.causeNormalized || document.causeRaw), true),
    buildField('特殊程序', document.specialProcedureType),
    buildField(getDateLabel(document), formatLegalDocumentDate(document.decisionDate || document.effectiveDate))
  ].filter((item): item is LegalDocumentFieldItem => Boolean(item))
}

export function pickLegalDocumentSummaryBlocks(document: Partial<LegalDocument>) {
  const blocks: LegalDocumentSummaryBlock[] = [
    { label: '纠纷概况', value: cleanLegalDocumentBodyText(document.disputeSummary) },
    { label: '审理经过', value: cleanLegalDocumentBodyText(document.trialProcessSummary) },
    { label: '基本事实', value: cleanLegalDocumentBodyText(document.factsSummary) },
    { label: '争议焦点', value: cleanLegalDocumentBodyText(document.issuesSummary) },
    { label: '法院理由', value: cleanLegalDocumentBodyText(document.reasoningSummary) },
    { label: '裁判结果', value: cleanLegalDocumentBodyText(document.resultSummary || document.decisionResult) },
    { label: '法律依据', value: cleanLegalDocumentBodyText(document.legalBasisText) }
  ].filter((item) => hasMeaningfulText(item.value))

  const seenValues = new Set<string>()
  return blocks.filter((item) => {
    const signature = collapseInlineText(item.value)
    if (!signature || seenValues.has(signature)) {
      return false
    }
    seenValues.add(signature)
    return true
  })
}

export function cropLegalDocumentText(text?: string, length = 56) {
  const normalized = collapseInlineText(text)
  if (!normalized) return '暂无摘要'
  return normalized.length > length ? `${normalized.slice(0, length)}...` : normalized
}

export function formatLegalDocumentDate(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function formatLegalDocumentLength(text?: string | null) {
  const normalized = cleanLegalDocumentBodyText(text)
  return normalized ? `${normalized.length} 字` : '--'
}

export function mapLegalDocumentSectionLabel(sectionType?: string) {
  switch (sectionType) {
    case 'overview':
      return '案件概览'
    case 'process':
      return '审理经过'
    case 'facts':
      return '事实'
    case 'issues':
      return '争议焦点'
    case 'reasoning':
      return '法院理由'
    case 'result':
      return '裁判结果'
    case 'law':
      return '法律依据'
    case 'full_text':
      return '全文'
    default:
      return '正文'
  }
}

export function mapLegalDocumentReferenceType(refType?: string) {
  switch (refType) {
    case 'case_number':
      return '案号'
    case 'law_article':
      return '法条'
    case 'decision_number':
      return '决定号'
    case 'patent_number':
      return '专利号'
    case 'right_number':
      return '权利号'
    case 'reference_number':
      return '编号'
    default:
      return '引用'
  }
}

export function mapLegalDocumentRetrievalRole(role?: string) {
  switch (role) {
    case 'case_overview':
      return '案件概览'
    case 'trial_process':
      return '审理经过'
    case 'issue_focus':
      return '争议焦点'
    case 'court_reasoning':
      return '法院理由'
    case 'decision_result':
      return '裁判结果'
    case 'law_basis':
      return '法律依据'
    case 'facts':
      return '基本事实'
    default:
      return '正文'
  }
}

export function formatLegalDocumentSectionWeight(weight?: number) {
  if (weight == null) return '--'
  if (weight >= 1.3) return `高（${weight.toFixed(2)}）`
  if (weight >= 1.1) return `中（${weight.toFixed(2)}）`
  return `基础（${weight.toFixed(2)}）`
}

export function formatLegalDocumentChunkTitle(chunk: Partial<LegalDocumentChunk>) {
  const index = (chunk.chunkIndex ?? 0) + 1
  const base = chunk.headerPath || chunk.sectionTitle || mapLegalDocumentSectionLabel(chunk.sectionType)
  if (!base || base === '全文' || chunk.sectionType === 'full_text' || chunk.sectionType === 'body') {
    return `正文片段 ${index}`
  }
  return `${base} · 片段 ${index}`
}
