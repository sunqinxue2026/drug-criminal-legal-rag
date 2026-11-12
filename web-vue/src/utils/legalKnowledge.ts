import type { LegalKnowledge, LegalKnowledgeChunk } from '@/types'

export function buildLegalKnowledgeFieldItems(row: Partial<LegalKnowledge>) {
  return [
    { label: '知识类型', value: row.knowledgeType || '--' },
    { label: '规范名称', value: row.documentName || '--' },
    { label: '条号', value: row.articleNumber || '--' },
    { label: '业务领域', value: row.businessDomain || '--' },
    { label: '主题', value: row.topic || '--' },
    { label: '发布机关', value: row.issuer || '--' },
    { label: '生效日期', value: row.effectiveDate || '--' },
    { label: '现行状态', value: row.currentStatus || '--' }
  ]
}

export function formatLegalKnowledgeChunkTitle(chunk: Partial<LegalKnowledgeChunk>) {
  return `${chunk.sectionTitle || '条文'} #${chunk.chunkIndex ?? 0}`
}

export function flattenLegalKnowledgePayload(payload: unknown) {
  const records: Record<string, any>[] = []
  flattenNode(payload, records)
  return records
}

function flattenNode(source: unknown, output: Record<string, any>[]) {
  if (Array.isArray(source)) {
    source.forEach((item) => flattenNode(item, output))
    return
  }

  if (!isRecord(source)) {
    return
  }

  const nested = firstArray(source, ['data', 'records', 'items', 'entries', 'articles', 'clauses'])
  const content = firstNonBlank(
    source.content,
    source.text,
    source.displayText,
    source['正文']
  )

  if (nested?.length && !content) {
    nested.forEach((child) => {
      if (!isRecord(child)) return
      const merged = { ...source, ...child }
      delete merged.data
      delete merged.records
      delete merged.items
      delete merged.entries
      delete merged.articles
      delete merged.clauses
      flattenNode(merged, output)
    })
    return
  }

  output.push(source)
}

function firstArray(source: Record<string, any>, keys: string[]) {
  for (const key of keys) {
    const value = source[key]
    if (Array.isArray(value)) {
      return value
    }
  }
  return null
}

function isRecord(value: unknown): value is Record<string, any> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function pickKnowledgePreviewTitle(record: Record<string, any>) {
  return firstNonBlank(record.title, record.documentName, record.lawName, record.name) || '未命名规范'
}

export function pickKnowledgePreviewArticle(record: Record<string, any>) {
  return firstNonBlank(record.articleNumber, record.articleNo, record.clauseNo, record['条号']) || '--'
}

export function pickKnowledgePreviewSummary(record: Record<string, any>) {
  return firstNonBlank(
    record.summary,
    record.articleTitle,
    record.topic,
    record.businessDomain,
    record.content,
    record.text,
    record.displayText,
    record['正文']
  ) || '--'
}

function firstNonBlank(...values: unknown[]) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }
  return ''
}
