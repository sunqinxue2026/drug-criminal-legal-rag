<template>
  <div class="page-shell chat-page">
    <header class="top-nav glass-panel">
      <div class="page-width nav-inner">
        <div class="brand" @click="goHome">
          <div class="brand-mark">
            <el-icon><ScaleToOriginal /></el-icon>
          </div>
          <strong>涉毒刑事法律咨询系统</strong>
        </div>

        <nav class="main-nav">
          <button class="nav-link" @click="goHome">首页</button>
          <button class="nav-link" @click="goItems">专题案例</button>
          <button class="nav-link active">AI律师咨询</button>
        </nav>

        <div class="nav-actions">
          <span class="nav-status" :class="{ warning: llmStatus !== 'ready' && smartConsulting }">
            <span class="dot"></span>
            {{ headerStatusText }}
          </span>
          <el-dropdown @command="handleUserCommand">
            <div class="user-chip">
              <el-avatar :size="36">{{ displayUserName.slice(0, 1) }}</el-avatar>
              <span>{{ displayUserName }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item v-if="isAdmin" command="admin">管理后台</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <main class="page-width chat-layout">
      <aside class="session-panel panel-card">
        <div class="panel-head">
          <div>
            <div class="eyebrow">我的AI律师咨询</div>
            <h2>会话列表</h2>
          </div>
          <button class="new-session" @click="createNewSession">
            <el-icon><Plus /></el-icon>
            新建咨询
          </button>
        </div>

        <div class="session-scroll">
          <div
              v-for="session in sessions"
              :key="session.id"
              class="session-item"
              :class="{ active: session.id === currentSessionId }"
              @click="selectSession(session.id!)"
            >
              <div class="session-copy">
                <strong>{{ formatSessionTitle(session.title) }}</strong>
                <span>{{ session.lastMessage || '等待输入法律问题...' }}</span>
                <small>{{ formatSidebarTime(session.updateTime || session.createTime) }}</small>
              </div>
            <button type="button" class="session-delete" @click.stop="deleteSession(session.id!)">
              <el-icon><Delete /></el-icon>
            </button>
          </div>

          <div v-if="!sessions.length" class="empty-sessions">
            <strong>还没有咨询记录</strong>
            <span>输入问题、上传材料或引用案例后，系统会自动创建会话。</span>
          </div>
        </div>
      </aside>

      <section class="chat-panel panel-card">
        <div class="chat-head">
          <div class="chat-head-main">
            <div class="chat-title-block">
              <div class="eyebrow">当前会话</div>
              <h1>{{ currentSessionTitle }}</h1>
              <p class="chat-head-subtitle">
                {{ currentScopeStatement }}
                <span v-if="lastAssistantModel"> · 最近回复：{{ formatModelLabel(lastAssistantModel) }}</span>
              </p>
            </div>
            <button
              type="button"
              class="report-export-button"
              :disabled="!canExportReport"
              title="导出当前会话咨询报告"
              @click="exportReport"
            >
              <el-icon><Download /></el-icon>
              导出报告
            </button>
          </div>
        </div>

        <div ref="messageHost" class="message-board">
          <div v-if="!messages.length && !sending" class="empty-board">
            <div class="empty-kicker">不会提问也没关系</div>
            <div class="empty-title">我会像律师接待咨询一样，先一步步引导你</div>
            <div class="empty-subtitle">你不用一开始就把问题说得很专业。先从身份、阶段、最担心的事情说起，我会继续追问，把案情慢慢梳理清楚。</div>
            <div class="intake-checklist">
              <span v-for="item in intakeChecklist" :key="item" class="intake-chip">{{ item }}</span>
            </div>
            <div class="guide-grid">
              <button
                v-for="item in guidedIntakePrompts"
                :key="item.title"
                class="guide-card"
                @click="inputMessage = item.template"
              >
                <strong>{{ item.title }}</strong>
                <span>{{ item.subtitle }}</span>
              </button>
            </div>
            <div class="suggestion-grid">
              <button v-for="tip in starterQuestions" :key="tip" @click="inputMessage = tip">{{ tip }}</button>
            </div>
          </div>

          <article
            v-for="(message, messageIndex) in messages"
            :key="message.id || `${message.role}-${message.createTime}`"
            class="message-item"
            :class="message.role"
          >
            <div class="message-role">{{ message.role === 'user' ? '我' : 'AI律师' }}</div>

            <div v-if="message.imageUrl" class="message-image-block">
              <img :src="message.imageUrl" :alt="message.imageName || '上传图片'" />
              <div class="message-image-name">{{ message.imageName || '上传图片' }}</div>
            </div>

            <template v-if="message.role === 'assistant'">
              <div class="assistant-stack">
                <section class="assistant-main-card" :class="{ compact: !shouldUseStructuredAssistantLayout(message) }">
                  <div v-if="shouldShowAssistantMeta(message, messageIndex)" class="assistant-meta">
                    <span v-if="shouldShowRiskLevel(message)" class="assistant-chip" :class="getRiskLevelClass(message)">
                      风险{{ getRiskLevelLabel(message) }}
                    </span>
                    <span v-if="message.references?.length" class="assistant-chip neutral">
                      引用 {{ message.references.length }} 条资料
                    </span>
                    <span v-if="getRelatedMaterials(messageIndex)" class="assistant-chip neutral">已结合补充材料</span>
                  </div>
                  <div class="message-content" v-html="formatMessage(getAssistantPrimaryText(message, messageIndex))"></div>
                </section>

                <details v-if="shouldShowAssistantDetails(message, messageIndex)" class="assistant-detail-card">
                  <summary class="assistant-detail-summary">
                    <span>查看依据与细节</span>
                    <small>{{ getAssistantDetailSummary(message, messageIndex) }}</small>
                  </summary>

                  <div class="assistant-detail-grid">
                    <section v-if="hasMeaningfulSection(message, 'basis')" class="assistant-detail-block">
                      <h3>法律依据</h3>
                      <div class="message-content" v-html="formatMessage(getSectionDisplayText(message, 'basis'))"></div>
                    </section>

                    <section v-if="hasMeaningfulSection(message, 'risk')" class="assistant-detail-block">
                      <h3>风险提示</h3>
                      <div class="message-content" v-html="formatMessage(getSectionDisplayText(message, 'risk'))"></div>
                    </section>

                    <section v-if="hasMeaningfulSection(message, 'advice')" class="assistant-detail-block">
                      <h3>行动建议</h3>
                      <div class="message-content" v-html="formatMessage(getSectionDisplayText(message, 'advice'))"></div>
                    </section>

                    <section v-if="shouldShowRiskLevel(message)" class="assistant-detail-block risk-note-block">
                      <h3>风险判断</h3>
                      <p class="risk-level-copy">{{ getRiskLevelDescription(message) }}</p>
                    </section>

                    <section v-if="message.references?.length" class="assistant-detail-block refs-block">
                      <h3>参考资料</h3>
                      <div class="reference-list">
                        <article v-for="reference in message.references" :key="`${reference.rank}-${reference.title}`" class="reference-card">
                          <div class="reference-top">
                            <strong>{{ reference.title || `参考片段 ${reference.rank}` }}</strong>
                            <span v-if="typeof reference.distance === 'number'">相似度 {{ getReferenceSimilarity(reference.distance) }}</span>
                          </div>
                          <div class="reference-badges">
                            <span class="reference-badge accent">{{ getReferenceSourceLabel(reference.source) }}</span>
                            <span v-if="reference.category" class="reference-badge">{{ reference.category }}</span>
                            <span v-for="tag in getReferenceTags(reference.tags)" :key="`${reference.rank}-${tag}`" class="reference-badge muted">{{ tag }}</span>
                          </div>
                          <div class="reference-meta">
                            <span v-if="reference.item_id">案例 ID #{{ reference.item_id }}</span>
                            <span v-if="reference.source_name">来源：{{ reference.source_name }}</span>
                            <span v-else-if="reference.issuer">发布主体：{{ reference.issuer }}</span>
                            <span v-if="reference.effective_date">日期：{{ reference.effective_date }}</span>
                            <span v-if="reference.current_status">状态：{{ reference.current_status }}</span>
                            <span v-if="reference.chunk_index !== undefined">切片 {{ Number(reference.chunk_index) + 1 }}</span>
                          </div>
                          <p v-if="reference.summary" class="reference-summary">{{ reference.summary }}</p>
                          <p>{{ reference.content }}</p>
                          <div v-if="reference.item_id" class="reference-actions">
                            <button class="tool-btn compact" @click="viewCaseDetail(reference.item_id)">查看案例详情</button>
                          </div>
                        </article>
                      </div>
                    </section>
                  </div>
                </details>
              </div>
            </template>

            <template v-else>
              <div class="message-content user-bubble" v-html="formatMessage(message.content)"></div>
              <div v-if="message.attachedCases?.length || message.attachedDocuments?.length" class="message-attachments">
                <div v-if="message.attachedCases?.length" class="message-attachment-group">
                  <strong>本轮引用案例</strong>
                  <div class="material-trace-badges">
                    <button
                      v-for="item in message.attachedCases"
                      :key="`attached-case-${item.id}-${item.title}`"
                      class="tool-btn compact"
                      @click="item.id && viewCaseDetail(item.id)"
                    >
                      {{ item.title }}
                    </button>
                  </div>
                </div>
                <div v-if="message.attachedDocuments?.length" class="message-attachment-group">
                  <strong>本轮文书材料</strong>
                  <div class="material-trace-badges">
                    <span
                      v-for="doc in message.attachedDocuments"
                      :key="`attached-doc-${doc.name}-${doc.statusLabel}`"
                      class="material-badge"
                    >
                      {{ doc.name }} · {{ doc.statusLabel }}{{ doc.charCount ? ` · ${doc.charCount} 字` : '' }}
                    </span>
                  </div>
                </div>
              </div>
            </template>
          </article>

          <article v-if="sending" class="message-item assistant">
            <div class="message-role">AI律师</div>
            <div class="analysis-card loading-card">
              <div class="loading-title">{{ loadingTitle }}</div>
              <div class="loading-dots"><span></span><span></span><span></span></div>
            </div>
          </article>
        </div>

        <div class="composer">
          <div v-if="pendingImageUrl" class="pending-image">
            <img :src="pendingImageUrl" :alt="pendingImageName || '待发送图片'" />
            <div class="pending-image-meta">
              <strong>{{ pendingImageName || '待发送图片' }}</strong>
              <span>本轮会把图片识别结果一起带入分析。</span>
              <button class="remove-image-btn" @click="clearPendingImage">移除图片</button>
            </div>
          </div>

          <div v-if="selectedCaseItems.length" class="cited-cases">
            <div class="cited-head">
              <strong>已引用案例</strong>
              <span>{{ selectedCaseItems.length }} 条</span>
            </div>
            <div class="cited-list">
              <div v-for="item in selectedCaseItems" :key="item.id" class="cited-card">
                <div>
                  <strong>{{ item.title }}</strong>
                  <p>{{ item.summary || item.content || '暂无摘要' }}</p>
                </div>
                <button class="tool-btn compact" @click="removeSelectedCase(item.id!)">移除</button>
              </div>
            </div>
          </div>

          <div v-if="pendingDocuments.length" class="pending-documents">
            <div class="material-summary-head">
              <strong>待发送文书</strong>
              <span>{{ pendingDocuments.length }} 份 · {{ totalDocumentChars }} 字</span>
            </div>
            <div class="cited-list">
              <div v-for="doc in pendingDocuments" :key="doc.url || doc.name" class="cited-card">
                <div class="document-card-copy">
                  <div class="document-card-head">
                    <strong>{{ doc.name }}</strong>
                    <span class="document-badge" :class="doc.status">{{ doc.statusLabel }}</span>
                  </div>
                  <p>{{ doc.preview }}</p>
                </div>
                <button class="tool-btn compact" @click="removePendingDocument(doc.url)">移除</button>
              </div>
            </div>
          </div>

          <div class="composer-shell">
            <el-input
              v-model="inputMessage"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 4 }"
              resize="none"
              placeholder="请输入你的涉毒刑事法律问题，也可以先上传通知书、聊天截图、转账记录等材料。"
              @keydown="handleComposerKeydown"
            />

            <div class="composer-footer">
              <div class="composer-controls">
                <el-dropdown @command="handleModelCommand">
                  <button class="model-menu-button" type="button">
                    <span>模型：{{ formatModelLabel(selectedModel) }}</span>
                    <el-icon><ArrowDown /></el-icon>
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item
                        v-for="model in modelOptions"
                        :key="model"
                        :command="model"
                        :disabled="model === selectedModel"
                      >
                        {{ formatModelLabel(model) }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <label class="rag-switch">
                  <span>RAG</span>
                  <el-switch v-model="smartConsulting" size="small" />
                </label>
              </div>

              <div class="composer-actions">
                <input ref="imageInputRef" type="file" accept="image/*" class="hidden-input" @change="handleImageSelected" />
                <input ref="documentInputRef" type="file" accept=".txt,.md,.json,.csv,.html,.htm,.pdf,.doc,.docx" class="hidden-input" @change="handleDocumentSelected" />
                <el-dropdown trigger="click" placement="top-end" @command="handleAttachmentCommand">
                  <button type="button" class="icon-action attach-action">
                    <el-icon><Paperclip /></el-icon>
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="image">上传图片</el-dropdown-item>
                      <el-dropdown-item command="document">上传文书</el-dropdown-item>
                      <el-dropdown-item command="case">引用案例</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <button
                  class="icon-action send-action"
                  :class="{ disabled: !canSend, sending }"
                  :disabled="!canSend || sending"
                  :title="sending ? '正在生成回复' : '发送消息'"
                  @click="sendMessage"
                >
                  <el-icon v-if="!sending"><Position /></el-icon>
                  <span v-else class="sending-stop-icon" aria-hidden="true"></span>
                </button>
              </div>
            </div>
          </div>
          <div class="composer-privacy">{{ privacyReminder }}</div>
        </div>
      </section>
    </main>

    <el-dialog v-model="casePickerVisible" title="引用案例" width="920px">
      <div class="case-picker">
        <div class="material-trace-badges">
          <button
            v-for="category in quickCategories"
            :key="category"
            class="tool-btn"
            @click="selectQuickCategory(category)"
          >
            {{ category }}
          </button>
        </div>
        <div class="case-picker-bar">
          <el-input v-model="caseKeyword" placeholder="搜索案例标题或内容" clearable />
          <el-button type="primary" @click="loadCaseItems">搜索</el-button>
        </div>
        <div class="case-picker-list">
          <article v-for="item in displayCaseItems" :key="item.id" class="case-picker-card">
            <div class="case-picker-copy">
              <strong>{{ item.title }}</strong>
              <p>{{ cropCase(item.summary || item.content, 120) }}</p>
            </div>
            <div class="case-picker-actions">
              <el-button text @click="viewCaseDetail(item.id!)">查看详情</el-button>
              <el-button type="primary" :disabled="isCaseSelected(item.id)" @click="selectCase(item)">
                {{ isCaseSelected(item.id) ? '已引用' : '引用到对话' }}
              </el-button>
            </div>
          </article>
          <div v-if="!displayCaseItems.length" class="case-picker-empty">
            暂无匹配案例，请换用“刑事、毒品、取保候审、诈骗、合同”等关键词。
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown, Delete, Download, Paperclip, Plus, Position, ScaleToOriginal } from '@element-plus/icons-vue'

import { useChatView } from './useChatView'

const {
  canSend,
  canExportReport,
  caseKeyword,
  casePickerVisible,
  clearPendingImage,
  cropCase,
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
  formatSessionTitle,
  formatMessage,
  formatModelLabel,
  formatSidebarTime,
  getAssistantDetailSummary,
  getAssistantPrimaryText,
  getReferenceSimilarity,
  getReferenceSourceLabel,
  getReferenceTags,
  guidedIntakePrompts,
  getRelatedMaterials,
  getRiskLevelClass,
  getRiskLevelDescription,
  getRiskLevelLabel,
  getSectionDisplayText,
  goHome,
  goItems,
  handleAttachmentCommand,
  handleComposerKeydown,
  handleDocumentSelected,
  handleImageSelected,
  handleModelCommand,
  handleUserCommand,
  hasMeaningfulSection,
  headerStatusText,
  imageInputRef,
  inputMessage,
  isAdmin,
  isCaseSelected,
  lastAssistantModel,
  llmStatus,
  loadingTitle,
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
  shouldShowRiskLevel,
  shouldUseStructuredAssistantLayout,
  smartConsulting,
  starterQuestions,
  totalDocumentChars,
  viewCaseDetail
} = useChatView()
</script>

<style scoped src="./ChatView.css"></style>

