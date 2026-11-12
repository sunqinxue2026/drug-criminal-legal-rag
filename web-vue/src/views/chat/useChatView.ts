import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { chatApi } from '@/api/chat'
import { fileApi } from '@/api/file'
import { itemApi } from '@/api/item'
import { userActionApi } from '@/api/userAction'
import { useUserStore } from '@/stores/user'
import type { ChatMessage, ChatSession, Item } from '@/types'

import {
  type AnswerSections,
  CONSULTATION_PROFILE,
  DEFAULT_DISCLAIMER,
  DEFAULT_SCOPE_STATEMENT,
  guidedIntakePrompts,
  intakeChecklist,
  PRIVACY_REMINDER,
  type PendingDocument,
  cropCase,
  formatMessage,
  formatModelLabel,
  formatShortTimestamp,
  formatSidebarTime,
  getAssistantPrimaryText,
  getFileExtension,
  getReferenceSimilarity,
  getReferenceSourceLabel,
  getReferenceTags,
  getRiskLevelClass,
  getRiskLevelDescription,
  getRiskLevelLabel,
  getSectionDisplayText,
  hasMeaningfulSection,
  isCaseConsultationText,
  type ParsedAnswerCache,
  quickCategories,
  shouldShowRiskLevel,
  shouldUseStructuredAssistantLayout,
  starterQuestions
} from './chatViewShared'
import { canExportConsultationReport, exportConsultationReport } from './reportExport'

export const useChatView = () => {
  const router = useRouter()
  const route = useRoute()
  const userStore = useUserStore()

  const selectedModel = ref('qwen-turbo')
  const modelOptions = ref<string[]>(['qwen-turbo', 'qwen-plus', 'qwen-max'])
  const smartConsulting = ref(true)
  const llmStatus = ref<'unknown' | 'ready' | 'missing'>('unknown')
  const vectorMode = ref<'unknown' | 'bridge' | 'fallback' | 'local'>('unknown')
  const vectorBridgeReady = ref(false)
  const sessions = ref<ChatSession[]>([])
  const currentSessionId = ref<number | null>(null)
  const messages = ref<ChatMessage[]>([])
  const inputMessage = ref('')
  const sendingSessionId = ref<number | null>(null)
  const streamStatusText = ref('')
  const messageHost = ref<HTMLElement | null>(null)
  const imageInputRef = ref<HTMLInputElement | null>(null)
  const documentInputRef = ref<HTMLInputElement | null>(null)
  const pendingImageUrl = ref('')
  const pendingImageName = ref('')
  const casePickerVisible = ref(false)
  const caseKeyword = ref('')
  const caseItems = ref<Item[]>([])
  const selectedCaseItems = ref<Item[]>([])
  const pendingDocuments = ref<PendingDocument[]>([])
  const activeQuickCategory = ref('')
  const parsedCache: ParsedAnswerCache = new WeakMap()
  const DRAFT_TITLES = new Set(['新的咨询', '涉毒咨询', 'AI律师咨询'])

  const normalizeSessionTitle = (title?: string) => {
    const normalized = String(title || '').replace(/\s*\(\d{1,2}\/\d{1,2}\s\d{2}:\d{2}\)\s*$/, '').trim()
    return normalized || 'AI律师咨询'
  }

  const toTimestamp = (value?: string) => {
    if (!value) return 0
    const parsed = new Date(value).getTime()
    return Number.isNaN(parsed) ? 0 : parsed
  }

  const sortSessions = (list: ChatSession[]) => {
    return [...list].sort((left, right) => {
      return toTimestamp(right.updateTime || right.createTime) - toTimestamp(left.updateTime || left.createTime)
    })
  }

  const applySessions = (list: ChatSession[]) => {
    sessions.value = sortSessions(list)
  }

  const upsertSession = (session: ChatSession) => {
    const nextSessions = sessions.value.filter((item) => item.id !== session.id)
    nextSessions.push(session)
    applySessions(nextSessions)
  }

  const patchSession = (sessionId: number, patch: Partial<ChatSession>) => {
    const current = sessions.value.find((item) => item.id === sessionId)
    if (!current) return
    upsertSession({
      ...current,
      ...patch
    })
  }

  const ellipsize = (value: string, limit: number) => {
    const normalized = value.replace(/\s+/g, ' ').trim()
    if (!normalized) return ''
    return normalized.length > limit ? `${normalized.slice(0, limit)}...` : normalized
  }

  const buildSessionTitle = (message: string) => {
    return ellipsize(message, 24) || 'AI律师咨询'
  }

  const isDraftSession = (session: ChatSession) => {
    const title = normalizeSessionTitle(session.title)
    const lastMessage = String(session.lastMessage || '').trim()
    return DRAFT_TITLES.has(title) && !lastMessage
  }

  const findExistingDraftSession = () => {
    return sessions.value.find(isDraftSession) || null
  }

  const isAdmin = computed(() => userStore.isAdmin)
  const displayUserName = computed(() => userStore.user?.username || '访客')
  const latestAssistantMessage = computed(() => [...messages.value].reverse().find((item) => item.role === 'assistant'))
  const currentDisclaimer = computed(() => latestAssistantMessage.value?.disclaimer || DEFAULT_DISCLAIMER)
  const currentScopeStatement = computed(() => latestAssistantMessage.value?.scopeStatement || DEFAULT_SCOPE_STATEMENT)
  const privacyReminder = computed(() => latestAssistantMessage.value?.privacyNotice || PRIVACY_REMINDER)
  const currentSession = computed(() => sessions.value.find((item) => item.id === currentSessionId.value) || null)
  const sending = computed(() => {
    return sendingSessionId.value !== null && sendingSessionId.value === currentSessionId.value
  })
  const lastAssistantModel = computed(() => {
    const latest = [...messages.value].reverse().find((item) => item.role === 'assistant' && item.model)
    return latest?.model || ''
  })
  const currentSessionTitle = computed(() => {
    const current = currentSession.value
    const title = normalizeSessionTitle(current?.title)
    const timestamp = current?.updateTime || current?.createTime
    return timestamp ? `${title} (${formatShortTimestamp(timestamp)})` : title
  })
  const headerStatusText = computed(() => {
    if (!smartConsulting.value) return '普通问答模式'
    if (llmStatus.value === 'ready' && vectorMode.value === 'bridge' && vectorBridgeReady.value) return '专库与模型可用'
    if (llmStatus.value === 'ready') return '模型可用'
    if (llmStatus.value === 'missing') return '模型待配置'
    return '服务检测中'
  })
  const canSend = computed(() => {
    return Boolean(inputMessage.value.trim() || pendingImageUrl.value || selectedCaseItems.value.length || pendingDocuments.value.length)
  })
  const canExportReport = computed(() => {
    return !sending.value && canExportConsultationReport(messages.value)
  })
  const totalDocumentChars = computed(() => {
    return pendingDocuments.value.reduce((sum, doc) => sum + doc.charCount, 0)
  })
  const displayCaseItems = computed(() => caseItems.value)

  const normalizeSendErrorMessage = (error: unknown) => {
    const fallback = '发送失败，请稍后重试'
    const raw = error instanceof Error ? error.message : fallback
    if (!raw) {
      return fallback
    }
    if (/timeout|超时/i.test(raw)) {
      return '回复生成时间较长，请稍后查看会话结果，或重试一次'
    }
    return raw
  }

  const scrollToBottom = () => {
    if (!messageHost.value) return
    messageHost.value.scrollTop = messageHost.value.scrollHeight
  }

  const goHome = () => {
    router.push('/home')
  }

  const goItems = () => {
    router.push('/items')
  }

  const loadLlmModels = async () => {
    try {
      const res = await chatApi.getLlmModels()
      if (Array.isArray(res.data?.allowed_models) && res.data.allowed_models.length) {
        modelOptions.value = res.data.allowed_models
        selectedModel.value = res.data.current_model || res.data.allowed_models[0]
      }
      llmStatus.value = res.data?.has_api_key ? 'ready' : 'missing'
    } catch (error) {
      llmStatus.value = 'unknown'
      console.error(error)
    }
  }

  const loadVectorStatus = async () => {
    try {
      const res = await chatApi.getVectorStatus()
      vectorMode.value = res.data?.mode || 'unknown'
      vectorBridgeReady.value = Boolean(res.data?.bridge_ready)
    } catch (error) {
      vectorMode.value = 'unknown'
      vectorBridgeReady.value = false
      console.error(error)
    }
  }

  const loadSessions = async () => {
    if (!userStore.user?.id) return
    const res = await chatApi.getUserSessions(userStore.user.id)
    applySessions(Array.isArray(res.data) ? res.data : [])
  }

  const selectSession = async (sessionId: number) => {
    currentSessionId.value = sessionId
    router.replace({ path: '/chat', query: { sessionId: String(sessionId) } })
    const res = await chatApi.getSessionMessages(sessionId)
    messages.value = Array.isArray(res.data) ? res.data : []
    await nextTick()
    scrollToBottom()
  }

  const createNewSession = async (silent = false) => {
    if (!userStore.user?.id) return
    const existingDraft = findExistingDraftSession()
    if (existingDraft?.id) {
      await selectSession(existingDraft.id)
      if (!silent) {
        ElMessage.info('当前已经有一个未提问的新咨询')
      }
      return existingDraft
    }

    const title = '新的咨询'
    const res = await chatApi.createSession(userStore.user.id, title)
    const now = new Date().toISOString()
    const nextSession: ChatSession = {
      ...res.data,
      userId: userStore.user.id,
      title: res.data?.title || title,
      createTime: res.data?.createTime || now,
      updateTime: res.data?.updateTime || res.data?.createTime || now,
      lastMessage: res.data?.lastMessage || ''
    }
    upsertSession(nextSession)
    await selectSession(nextSession.id!)
    void loadSessions()
    if (!silent) {
      ElMessage.success('已创建新的AI律师咨询会话')
    }
    return nextSession
  }

  const deleteSession = async (sessionId: number) => {
    await ElMessageBox.confirm('确认删除这条咨询记录吗？', '提示', { type: 'warning' })
    await chatApi.deleteSession(sessionId)
    ElMessage.success('咨询记录已删除')

    if (currentSessionId.value === sessionId) {
      currentSessionId.value = null
      messages.value = []
    }

    await loadSessions()
    if (!currentSessionId.value && sessions.value.length) {
      await selectSession(sessions.value[0].id!)
    }
  }

  const clearPendingImage = () => {
    pendingImageUrl.value = ''
    pendingImageName.value = ''
  }

  const handleComposerKeydown = (event: KeyboardEvent) => {
    if (event.key !== 'Enter') return
    if (event.shiftKey || event.ctrlKey || event.altKey || event.metaKey || event.isComposing) {
      return
    }
    event.preventDefault()
    if (!canSend.value || sending.value) return
    void sendMessage()
  }

  const sendMessage = async () => {
    if (sendingSessionId.value !== null) return

    const message = inputMessage.value.trim()
    if (!message && !pendingImageUrl.value && !pendingDocuments.value.length && !selectedCaseItems.value.length) return

    if (!currentSessionId.value) {
      await createNewSession()
    }
    if (!currentSessionId.value) return

    const attachedImageUrl = pendingImageUrl.value
    const attachedImageName = pendingImageName.value
    const attachedCases = [...selectedCaseItems.value]
    const attachedDocuments = [...pendingDocuments.value]
    const historyPayload = messages.value.slice(-4).map((item) => ({
      role: item.role,
      content: item.content,
      createTime: item.createTime,
      imageUrl: item.imageUrl,
      imageName: item.imageName
    }))
    const outgoingText = message || (
      attachedImageUrl
        ? '请结合我上传的图片进行涉毒刑事法律分析'
        : '请结合我上传的材料进行涉毒刑事法律分析'
    )

    const clientCreateTime = new Date().toISOString()

    messages.value.push({
      sessionId: currentSessionId.value,
      role: 'user',
      content: outgoingText,
      createTime: clientCreateTime,
      imageUrl: attachedImageUrl || undefined,
      imageName: attachedImageName || undefined,
      attachedCases: attachedCases.map((item) => ({ id: item.id, title: item.title, summary: item.summary || item.content })),
      attachedDocuments: attachedDocuments.map((doc) => ({
        name: doc.name,
        url: doc.url,
        preview: doc.preview,
        extractedText: doc.extractedText,
        statusLabel: doc.statusLabel,
        charCount: doc.charCount
      }))
    })

    inputMessage.value = ''
    clearPendingImage()
    selectedCaseItems.value = []
    pendingDocuments.value = []
    await nextTick()
    scrollToBottom()

    const activeSessionId = currentSessionId.value
    patchSession(activeSessionId, {
      lastMessage: ellipsize(outgoingText, 50),
      title: DRAFT_TITLES.has(normalizeSessionTitle(currentSession.value?.title)) ? buildSessionTitle(outgoingText) : currentSession.value?.title,
      updateTime: clientCreateTime
    })
    sendingSessionId.value = activeSessionId
    streamStatusText.value = '正在连接回答流...'
    const assistantPlaceholder: ChatMessage = {
      sessionId: activeSessionId,
      role: 'assistant',
      content: '',
      model: selectedModel.value,
      createTime: new Date().toISOString(),
      references: [],
      responseMode: 'streaming',
      disclaimer: DEFAULT_DISCLAIMER,
      scopeStatement: DEFAULT_SCOPE_STATEMENT,
      privacyNotice: PRIVACY_REMINDER
    }
    messages.value.push(assistantPlaceholder)
    await nextTick()
    scrollToBottom()
    try {
      const finalMessage: ChatMessage = await chatApi.sendMessageStream({
        sessionId: activeSessionId,
        model: selectedModel.value,
        message: outgoingText,
        smartConsulting: smartConsulting.value,
        profile: CONSULTATION_PROFILE,
        imageUrl: attachedImageUrl || undefined,
        imageName: attachedImageName || undefined,
        history: historyPayload,
        attachedCases: attachedCases.map((item) => ({
          id: item.id,
          title: item.title,
          summary: cropCase(item.summary || item.content, 220)
        })),
        attachedDocuments: attachedDocuments.map((doc) => ({
          name: doc.name,
          url: doc.url,
          preview: doc.preview,
          extractedText: doc.extractedText,
          statusLabel: doc.statusLabel,
          charCount: doc.charCount
        }))
      }, {
        onStatus: (payload) => {
          const message = String(payload.message || '').trim()
          if (message) {
            streamStatusText.value = message
          }
        },
        onMeta: (payload) => {
          if (payload.model) assistantPlaceholder.model = String(payload.model)
          if (Array.isArray(payload.references)) assistantPlaceholder.references = payload.references as any
          if (payload.responseMode) assistantPlaceholder.responseMode = String(payload.responseMode)
          if (payload.queryAnalysis && typeof payload.queryAnalysis === 'object') {
            assistantPlaceholder.queryAnalysis = payload.queryAnalysis as Record<string, unknown>
          }
        },
        onDelta: (content) => {
          if (!content) return
          assistantPlaceholder.content += content
          patchSession(activeSessionId, {
            lastMessage: ellipsize(assistantPlaceholder.content, 50),
            updateTime: new Date().toISOString()
          })
          scrollToBottom()
        }
      })
      Object.assign(assistantPlaceholder, finalMessage)
      messages.value = [...messages.value]
      patchSession(activeSessionId, {
        lastMessage: ellipsize(finalMessage.content || '', 50),
        updateTime: finalMessage.createTime || new Date().toISOString()
      })
      await loadSessions()
    } catch (error) {
      messages.value = messages.value.filter((item) => item !== assistantPlaceholder)
      ElMessage.error(normalizeSendErrorMessage(error))
      if (activeSessionId && currentSessionId.value === activeSessionId) {
        try {
          await selectSession(activeSessionId)
        } catch (reloadError) {
          console.error('同步会话消息失败:', reloadError)
        }
      }
      console.error(error)
    } finally {
      streamStatusText.value = ''
      if (sendingSessionId.value === activeSessionId) {
        sendingSessionId.value = null
      }
    }
  }

  const handleImageSelected = async (event: Event) => {
    const target = event.target as HTMLInputElement
    const file = target.files?.[0]
    if (!file) return

    try {
      const res = await fileApi.uploadFile(file, 'chat-images')
      const uploadedUrl = res?.data?.url
      if (!uploadedUrl) {
        throw new Error('上传成功但未返回图片地址')
      }
      pendingImageUrl.value = uploadedUrl
      pendingImageName.value = file.name
      ElMessage.success('图片上传成功，可以继续提问')
    } catch (error) {
      ElMessage.error('图片上传失败')
      console.error(error)
    } finally {
      target.value = ''
    }
  }

  const handleDocumentSelected = async (event: Event) => {
    const target = event.target as HTMLInputElement
    const file = target.files?.[0]
    if (!file) return

    try {
      const uploadRes = await fileApi.uploadFile(file, 'chat-documents')
      const extractResult = await fileApi.extractDocumentText(file).catch((error) => {
        console.error(error)
        return null
      })

      const extractedText = extractResult?.data?.text || ''
      const supported = Boolean(extractResult?.data?.supported && extractedText)
      const charCount = Number(extractResult?.data?.char_count || extractedText.length || 0)
      const extension = String(extractResult?.data?.extension || getFileExtension(file.name))

      const nextDocument: PendingDocument = {
        name: file.name,
        url: uploadRes?.data?.url || '',
        extractedText,
        charCount,
        extension,
        status: supported ? 'parsed' : extractResult ? 'linked' : 'failed',
        statusLabel: supported ? '已提取正文' : extractResult ? '仅附原文件' : '解析失败',
        preview: supported
          ? cropCase(extractedText, 120)
          : extractResult
            ? '当前文件暂未提取出可用正文，系统会保留文件链接并将文件名带入咨询上下文。'
            : '文书已上传，但解析服务本次未成功返回内容，系统会先保留原文件链接。'
      }

      pendingDocuments.value.push(nextDocument)

      if (supported) {
        ElMessage.success(`文书上传成功，已提取 ${charCount} 字正文`)
      } else if (extractResult) {
        ElMessage.warning('文书已上传，但暂未提取到正文，将按原文件附件参与咨询')
      } else {
        ElMessage.warning('文书已上传，但解析服务暂时不可用，先按附件参与咨询')
      }
    } catch (error) {
      ElMessage.error('文书上传失败')
      console.error(error)
    } finally {
      target.value = ''
    }
  }

  const removePendingDocument = (url: string) => {
    pendingDocuments.value = pendingDocuments.value.filter((doc) => doc.url !== url)
  }

  const loadCaseItems = async () => {
    try {
      const keyword = [activeQuickCategory.value, caseKeyword.value.trim()].filter(Boolean).join(' ')
      const res = await itemApi.getItemPage({
        pageNum: 1,
        pageSize: 12,
        keyword: keyword || undefined
      })
      caseItems.value = res.data.records || []
      const queryText = keyword.trim()
      if (userStore.user?.id && queryText) {
        try {
          await userActionApi.logSearch(userStore.user.id, queryText, '咨询页案例搜索')
        } catch (error) {
          console.error('记录咨询页案例搜索失败', error)
        }
      }
    } catch (error) {
      ElMessage.error('加载案例列表失败')
      console.error(error)
    }
  }

  const openCasePicker = async () => {
    casePickerVisible.value = true
    await loadCaseItems()
  }

  const handleModelCommand = (model: string | number) => {
    if (typeof model !== 'string') return
    selectedModel.value = model
  }

  const handleAttachmentCommand = async (command: string) => {
    if (command === 'image') {
      imageInputRef.value?.click()
      return
    }
    if (command === 'document') {
      documentInputRef.value?.click()
      return
    }
    if (command === 'case') {
      await openCasePicker()
    }
  }

  const selectQuickCategory = async (category: string) => {
    activeQuickCategory.value = category
    if (casePickerVisible.value) {
      await loadCaseItems()
    }
  }

  const isCaseSelected = (id?: number) => {
    return selectedCaseItems.value.some((item) => item.id === id)
  }

  const selectCase = (item: Item) => {
    if (isCaseSelected(item.id)) return
    selectedCaseItems.value.push(item)
    casePickerVisible.value = false
    if (inputMessage.value.trim()) {
      inputMessage.value = `${inputMessage.value}\n\n请结合我引用的案例一起分析。`
    } else {
      inputMessage.value = `请结合我引用的案例“${item.title}”一起分析。`
    }
    ElMessage.success('案例已引用到当前对话')
  }

  const removeSelectedCase = (id: number) => {
    selectedCaseItems.value = selectedCaseItems.value.filter((item) => item.id !== id)
  }

  const viewCaseDetail = (id: number) => {
    router.push(`/item/${id}`)
  }

  const getRelatedMaterials = (messageIndex: number) => {
    const currentMessage = messages.value[messageIndex]
    if (!currentMessage || currentMessage.role !== 'assistant') return null

    for (let index = messageIndex - 1; index >= 0; index -= 1) {
      const candidate = messages.value[index]
      if (candidate.role !== 'user') continue

      const hasImage = Boolean(candidate.imageUrl)
      const cases = candidate.attachedCases || []
      const documents = candidate.attachedDocuments || []

      if (hasImage || cases.length || documents.length) {
        return { hasImage, cases, documents }
      }

      return null
    }

    return null
  }

  const getPreviousUserMessage = (messageIndex: number) => {
    for (let index = messageIndex - 1; index >= 0; index -= 1) {
      const candidate = messages.value[index]
      if (candidate?.role === 'user') return candidate
    }
    return null
  }

  const shouldShowAssistantMeta = (message: ChatMessage, messageIndex: number) => {
    return shouldShowRiskLevel(message, parsedCache)
      || Boolean(message.references?.length)
      || Boolean(getRelatedMaterials(messageIndex))
  }

  const shouldShowAssistantDetails = (message: ChatMessage, messageIndex: number) => {
    if (!shouldUseStructuredAssistantLayout(message, parsedCache)) return false
    return shouldShowRiskLevel(message, parsedCache)
      || hasMeaningfulSection(message, 'basis', parsedCache)
      || hasMeaningfulSection(message, 'risk', parsedCache)
      || hasMeaningfulSection(message, 'advice', parsedCache)
      || Boolean(message.references?.length)
      || Boolean(getRelatedMaterials(messageIndex))
  }

  const getAssistantDetailSummary = (message: ChatMessage, messageIndex: number) => {
    const parts: string[] = []
    if (hasMeaningfulSection(message, 'basis', parsedCache)) parts.push('法律依据')
    if (hasMeaningfulSection(message, 'risk', parsedCache)) parts.push('风险提示')
    if (hasMeaningfulSection(message, 'advice', parsedCache)) parts.push('行动建议')
    if (message.references?.length) parts.push(`${message.references.length} 条资料`)
    if (getRelatedMaterials(messageIndex)) parts.push('补充材料')
    return parts.join(' · ') || '展开更多信息'
  }

  const shouldShowFollowUpCard = (message: ChatMessage, messageIndex: number) => {
    if (!hasMeaningfulSection(message, 'followUp', parsedCache)) return false
    const previousUser = getPreviousUserMessage(messageIndex)
    const queryText = String(message.queryAnalysis?.question || previousUser?.content || '')
    return !isCaseConsultationText(queryText)
  }

  const getAssistantPrimaryDisplayText = (message: ChatMessage, messageIndex: number) => {
    const primaryText = getAssistantPrimaryText(message, parsedCache).trim()
    if (!shouldShowFollowUpCard(message, messageIndex)) return primaryText

    const followUpText = getSectionDisplayText(message, 'followUp', parsedCache).trim()
    if (!followUpText) return primaryText
    if (!primaryText) return followUpText

    const compactPrimaryText = primaryText.replace(/\s+/g, '')
    const compactFollowUpText = followUpText.replace(/\s+/g, '')
    if (compactPrimaryText.includes(compactFollowUpText)) return primaryText

    const separator = /[。！？!?；;，,：:]$/.test(primaryText) ? '' : '。'
    return `${primaryText}${separator}${followUpText}`
  }

  const exportReport = () => {
    if (!canExportReport.value) {
      ElMessage.warning('当前会话还没有可导出的AI分析结果')
      return
    }

    try {
      const filename = exportConsultationReport({
        session: currentSession.value,
        messages: messages.value,
        parsedCache,
        displayUserName: displayUserName.value,
        scopeStatement: currentScopeStatement.value,
        disclaimer: currentDisclaimer.value
      })
      ElMessage.success(`已生成咨询报告：${filename}`)
    } catch (error) {
      const message = error instanceof Error ? error.message : '导出报告失败'
      ElMessage.error(message)
      console.error(error)
    }
  }

  const loadingTitle = computed(() => {
    if (sending.value && streamStatusText.value) return streamStatusText.value

    const latestUserMessage = [...messages.value].reverse().find((item) => item.role === 'user')
    const hasImage = Boolean(latestUserMessage?.imageUrl)
    const hasCases = Boolean(latestUserMessage?.attachedCases?.length)
    const hasDocuments = Boolean(latestUserMessage?.attachedDocuments?.length)
    const hasMaterials = hasImage || hasDocuments
    const hasCaseReferences = hasCases

    if (smartConsulting.value) {
      if (hasMaterials && hasCaseReferences) return '正在结合专库、案例和材料生成分析结果…'
      if (hasMaterials) return '正在结合专库和材料生成分析结果…'
      if (hasCaseReferences) return '正在结合专库和引用案例生成分析结果…'
      return '正在结合专库生成分析结果…'
    }

    if (hasMaterials && hasCaseReferences) return '正在基于你的问题、案例和材料生成分析结果…'
    if (hasMaterials) return '正在基于你的问题和材料生成分析结果…'
    if (hasCaseReferences) return '正在基于你的问题和引用案例生成分析结果…'
    return '正在基于你的问题生成分析结果…'
  })

  const handleUserCommand = async (command: string) => {
    if (command === 'profile') {
      router.push('/profile')
      return
    }
    if (command === 'admin') {
      router.push('/admin/items')
      return
    }
    if (command === 'logout') {
      await ElMessageBox.confirm('确认退出登录吗？', '提示', { type: 'warning' })
      userStore.logout()
      router.push('/login')
    }
  }

  onMounted(async () => {
    userStore.loadFromStorage()
    await Promise.all([loadLlmModels(), loadVectorStatus(), loadSessions()])
    const routeSessionId = Number(route.query.sessionId)
    const askedQuestion = typeof route.query.question === 'string' ? route.query.question : ''

    if (routeSessionId && sessions.value.some((item) => item.id === routeSessionId)) {
      await selectSession(routeSessionId)
    } else {
      const draft = findExistingDraftSession()
      if (draft?.id) {
        await selectSession(draft.id)
      } else {
        await createNewSession(true)
      }
    }

    if (askedQuestion) {
      inputMessage.value = askedQuestion
    }
  })

  return {
    canSend,
    canExportReport,
    caseKeyword,
    casePickerVisible,
    currentDisclaimer,
    currentScopeStatement,
    currentSessionId,
    currentSessionTitle,
    createNewSession,
    deleteSession,
    displayCaseItems,
    displayUserName,
    documentInputRef,
    exportReport,
    formatSessionTitle: normalizeSessionTitle,
    formatMessage,
    formatModelLabel,
    formatSidebarTime,
    getAssistantDetailSummary,
    getAssistantPrimaryText: (message: ChatMessage, messageIndex: number) => getAssistantPrimaryDisplayText(message, messageIndex),
    getReferenceSimilarity,
    getReferenceSourceLabel,
    getReferenceTags,
    getRelatedMaterials,
    getRiskLevelClass: (message: ChatMessage) => getRiskLevelClass(message, parsedCache),
    getRiskLevelDescription: (message: ChatMessage) => getRiskLevelDescription(message, parsedCache),
    getRiskLevelLabel: (message: ChatMessage) => getRiskLevelLabel(message, parsedCache),
    getSectionDisplayText: (message: ChatMessage, section: keyof AnswerSections) => getSectionDisplayText(message, section, parsedCache),
    goHome,
    goItems,
    guidedIntakePrompts,
    handleAttachmentCommand,
    handleComposerKeydown,
    handleDocumentSelected,
    handleImageSelected,
    handleModelCommand,
    handleUserCommand,
    hasMeaningfulSection: (message: ChatMessage, section: keyof AnswerSections) => hasMeaningfulSection(message, section, parsedCache),
    headerStatusText,
    imageInputRef,
    inputMessage,
    isAdmin,
    isCaseSelected,
    lastAssistantModel,
    llmStatus,
    loadingTitle,
    loadCaseItems,
    messageHost,
    messages,
    modelOptions,
    pendingDocuments,
    pendingImageName,
    pendingImageUrl,
    privacyReminder,
    intakeChecklist,
    quickCategories,
    removePendingDocument,
    removeSelectedCase,
    selectCase,
    selectedCaseItems,
    selectedModel,
    selectQuickCategory,
    selectSession,
    sending,
    sendMessage,
    sessions,
    shouldShowAssistantDetails,
    shouldShowAssistantMeta,
    shouldShowRiskLevel: (message: ChatMessage) => shouldShowRiskLevel(message, parsedCache),
    shouldUseStructuredAssistantLayout: (message: ChatMessage) => shouldUseStructuredAssistantLayout(message, parsedCache),
    smartConsulting,
    starterQuestions,
    totalDocumentChars,
    vectorBridgeReady,
    vectorMode,
    viewCaseDetail
  }
}



