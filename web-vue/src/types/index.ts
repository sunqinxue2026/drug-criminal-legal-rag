export interface User {
  id?: number
  username: string
  password?: string
  realName?: string
  phone?: string
  email?: string
  avatar?: string
  role?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface Category {
  id?: number
  name: string
  description?: string
  iconUrl?: string
  themeColor?: string
  parentId?: number
  sort?: number
  createTime?: string
  updateTime?: string
}

export interface ItemAsset {
  id?: number
  itemId?: number
  assetType?: string
  fileName: string
  fileUrl: string
  bucket?: string
  objectKey?: string
  fileExt?: string
  fileSize?: number
  summary?: string
  sort?: number
  status?: number
  createTime?: string
  updateTime?: string
}

export interface Item {
  id?: number
  title: string
  content?: string
  summary?: string
  categoryId?: number
  tags?: string
  viewCount?: number
  likeCount?: number
  favoriteCount?: number
  status?: number
  assets?: ItemAsset[]
  recommendReason?: string
  recommendScore?: number
  createTime?: string
  updateTime?: string
}

export interface FavoriteItem {
  id: number
  title: string
  categoryId?: number
  categoryName?: string
  createTime?: string
}

export interface ItemImportResult {
  fileName: string
  success: boolean
  skipped?: boolean
  duplicate?: boolean
  itemId?: number
  existingItemId?: number
  existingTitle?: string
  title?: string
  categoryId?: number
  categoryName?: string
  charCount?: number
  message?: string
}

export interface ItemImportResponse {
  totalFiles: number
  importedCount: number
  skippedCount: number
  failedCount: number
  reindexed: boolean
  indexMessage?: string
  results: ItemImportResult[]
}

export interface OfficialCaseSyncResult {
  title?: string
  action?: string
  sourceFileName?: string
  categoryName?: string
}

export interface OfficialCaseSyncResponse {
  totalRecords: number
  createdCount: number
  updatedCount: number
  deletedCount: number
  skippedCount: number
  cleanedBehaviorCount?: number
  message?: string
  results: OfficialCaseSyncResult[]
}

export interface LegalDocument {
  id?: number
  sourceType?: string
  sourceName?: string
  sourceUrl?: string
  sourceReliability?: number
  sourceFileName?: string
  title?: string
  titleNormalized?: string
  caseNumber?: string
  courtName?: string
  courtLevel?: string
  province?: string
  city?: string
  documentType?: string
  documentTypeRaw?: string
  documentSubtype?: string
  documentSubtypeRaw?: string
  caseType?: string
  categoryId?: number
  causeRaw?: string
  causeNormalized?: string
  businessDomain?: string
  procedureDomain?: string
  trialStageNormalized?: string
  procedureStageRaw?: string
  specialProcedureType?: string
  applicationType?: string
  decisionResult?: string
  decisionResultNormalized?: string
  decisionDate?: string
  effectiveDate?: string
  filingDate?: string
  hearingDate?: string
  partiesText?: string
  claimsSummary?: string
  defenseSummary?: string
  disputeSummary?: string
  trialProcessSummary?: string
  factsSummary?: string
  issuesSummary?: string
  issueTags?: string
  reasoningSummary?: string
  resultSummary?: string
  resultTags?: string
  legalBasisText?: string
  relatedCaseNumbersText?: string
  relatedDecisionNumbersText?: string
  keywords?: string
  aliases?: string
  summary?: string
  retrievalText?: string
  caseDetailText?: string
  displayText?: string
  rawText?: string
  cleanText?: string
  parserVersion?: string
  parseStatus?: number
  classifyStatus?: number
  extractConfidence?: number
  docQualityScore?: number
  status?: number
  createTime?: string
  updateTime?: string
}

export interface LoginResponse {
  token: string
  tokenType?: string
  user: User
}

export interface LegalDocumentParty {
  id?: number
  documentId?: number
  partySeq?: number
  partyRoleRaw?: string
  partyRoleNormalized?: string
  partyName?: string
  partyNameNormalized?: string
  partyType?: string
  representativeText?: string
  identityText?: string
  isAnonymized?: number
}

export interface LegalDocumentReference {
  id?: number
  documentId?: number
  refOrder?: number
  sectionType?: string
  refType?: string
  refName?: string
  refValue?: string
  normalizedValue?: string
  isPrimary?: number
  rawText?: string
}

export interface LegalDocumentChunk {
  id?: number
  documentId?: number
  chunkStrategy?: string
  chunkVersion?: string
  sectionType?: string
  sectionTitle?: string
  headerPath?: string
  retrievalRole?: string
  sectionWeight?: number
  sectionOrder?: number
  chunkIndex?: number
  chunkText?: string
  retrievalText?: string
  charCount?: number
  tokenCount?: number
}

export interface LegalDocumentDetail {
  document: LegalDocument
  parties: LegalDocumentParty[]
  references: LegalDocumentReference[]
  chunks: LegalDocumentChunk[]
}

export interface LegalDocumentImportResult {
  fileName: string
  success: boolean
  skipped?: boolean
  duplicate?: boolean
  documentId?: number
  existingDocumentId?: number
  existingTitle?: string
  title?: string
  caseNumber?: string
  categoryId?: number
  categoryName?: string
  charCount?: number
  chunkCount?: number
  message?: string
}

export interface LegalDocumentImportResponse {
  totalFiles: number
  importedCount: number
  skippedCount: number
  failedCount: number
  reindexed: boolean
  indexMessage?: string
  results: LegalDocumentImportResult[]
}

export interface LegalKnowledge {
  id?: number
  sourceType?: string
  sourceReliability?: number
  sourceFileName?: string
  contentSha256?: string
  title?: string
  titleNormalized?: string
  knowledgeType?: string
  knowledgeSubtype?: string
  documentName?: string
  documentLevel?: string
  issuer?: string
  categoryId?: number
  businessDomain?: string
  topic?: string
  chapterTitle?: string
  sectionTitle?: string
  articleNumber?: string
  articleTitle?: string
  keywords?: string
  aliases?: string
  summary?: string
  displayText?: string
  retrievalText?: string
  effectiveDate?: string
  expiryDate?: string
  currentStatus?: string
  parserVersion?: string
  parseStatus?: number
  extractConfidence?: number
  status?: number
  sourceName?: string
  sourceUrl?: string
  createTime?: string
  updateTime?: string
}

export interface LegalKnowledgeChunk {
  id?: number
  knowledgeId?: number
  chunkStrategy?: string
  chunkVersion?: string
  sectionType?: string
  sectionTitle?: string
  headerPath?: string
  retrievalRole?: string
  sectionWeight?: number
  sectionOrder?: number
  chunkIndex?: number
  chunkText?: string
  retrievalText?: string
  charCount?: number
}

export interface LegalKnowledgeDetail {
  knowledge: LegalKnowledge
  chunks: LegalKnowledgeChunk[]
}

export interface LegalKnowledgeImportResult {
  sourceName: string
  success: boolean
  skipped?: boolean
  duplicate?: boolean
  knowledgeId?: number
  existingKnowledgeId?: number
  title?: string
  articleNumber?: string
  categoryId?: number
  categoryName?: string
  charCount?: number
  chunkCount?: number
  message?: string
}

export interface LegalKnowledgeImportResponse {
  totalRecords: number
  importedCount: number
  skippedCount: number
  failedCount: number
  reindexed: boolean
  indexMessage?: string
  results: LegalKnowledgeImportResult[]
}

export interface ChatReference {
  rank?: number
  title?: string
  content?: string
  distance?: number
  category?: string
  tags?: string
  summary?: string
  source?: string
  source_name?: string
  source_url?: string
  issuer?: string
  effective_date?: string
  current_status?: string
  item_id?: number
  document_id?: number | string
  chunk_id?: number | string
  chunk_index?: number
}

export interface MessageAttachmentCase {
  id?: number
  title: string
  summary?: string
}

export interface MessageAttachmentDocument {
  name: string
  url?: string
  preview?: string
  extractedText?: string
  statusLabel: string
  charCount?: number
}

export interface ChatSession {
  id?: number
  userId: number
  title: string
  lastMessage?: string
  createTime?: string
  updateTime?: string
}

export interface ChatMessage {
  id?: number
  sessionId: number
  role: 'user' | 'assistant'
  content: string
  model?: string
  createTime?: string
  references?: ChatReference[]
  imageUrl?: string
  imageName?: string
  responseMode?: string
  attachedCases?: MessageAttachmentCase[]
  attachedDocuments?: MessageAttachmentDocument[]
  structuredAnswer?: ChatStructuredAnswer
  imageAnalysis?: string
  disclaimer?: string
  scopeStatement?: string
  privacyNotice?: string
  queryAnalysis?: Record<string, unknown>
}

export interface ChatStructuredAnswer {
  conclusion: string
  basis: string
  risk: string
  advice: string
  followUp: string
}

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

export interface PageResponse<T = unknown> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface RecommendationInterestCategory {
  categoryId: number
  name: string
  score: number
  iconUrl?: string
  themeColor?: string
}

export interface RecommendationKeyword {
  keyword: string
  score: number
}

export interface RecommendationResult {
  interestCategories: RecommendationInterestCategory[]
  interestKeywords: RecommendationKeyword[]
  items: Item[]
}

export interface DocumentGenerationResult {
  title: string
  documentType: string
  template: string
  format: 'markdown' | 'text' | 'html'
  content: string
  previewMarkdown: string
  summary: string
  suggestedFilename: string
  messageCount: number
  generatedAt: string
  sessionTitle: string
  sessionId: number
}
