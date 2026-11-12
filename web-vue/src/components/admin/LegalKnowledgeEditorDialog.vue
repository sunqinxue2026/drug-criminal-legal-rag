<template>
  <el-dialog
    v-model="visible"
    :title="mode === 'create' ? '新增法律知识' : '编辑法律知识'"
    width="1040px"
    destroy-on-close
  >
    <el-form label-width="104px" class="editor-form">
      <div class="form-grid">
        <el-form-item label="知识标题" class="field-span-2">
          <el-input v-model="form.title" maxlength="180" show-word-limit placeholder="可选，不填会自动生成标题" />
        </el-form-item>

        <el-form-item label="专题分类">
          <el-select v-model="form.categoryId" clearable placeholder="请选择专题分类" style="width: 100%">
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="知识状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>

        <el-form-item label="来源名称">
          <el-input v-model="form.sourceName" placeholder="如：司法解释汇编、手工录入" />
        </el-form-item>

        <el-form-item label="来源类型">
          <el-input v-model="form.sourceType" placeholder="如：manual_entry / official_file" />
        </el-form-item>

        <el-form-item label="来源链接" class="field-span-2">
          <el-input v-model="form.sourceUrl" placeholder="可选，填写原始来源地址" />
        </el-form-item>

        <el-form-item label="知识类型">
          <el-input v-model="form.knowledgeType" placeholder="如：法律法规、司法解释、规范知识" />
        </el-form-item>

        <el-form-item label="知识子类">
          <el-input v-model="form.knowledgeSubtype" placeholder="如：条文解读、案例规则" />
        </el-form-item>

        <el-form-item label="规范名称">
          <el-input v-model="form.documentName" placeholder="请输入规范名称" />
        </el-form-item>

        <el-form-item label="规范层级">
          <el-input v-model="form.documentLevel" placeholder="如：法律、司法解释" />
        </el-form-item>

        <el-form-item label="发布机关">
          <el-input v-model="form.issuer" placeholder="请输入发布机关" />
        </el-form-item>

        <el-form-item label="业务领域">
          <el-input v-model="form.businessDomain" placeholder="如：涉毒刑事、程序应对" />
        </el-form-item>

        <el-form-item label="主题">
          <el-input v-model="form.topic" placeholder="请输入主题" />
        </el-form-item>

        <el-form-item label="章节标题">
          <el-input v-model="form.chapterTitle" placeholder="可选" />
        </el-form-item>

        <el-form-item label="小节标题">
          <el-input v-model="form.sectionTitle" placeholder="可选" />
        </el-form-item>

        <el-form-item label="条号">
          <el-input v-model="form.articleNumber" placeholder="如：第十条" />
        </el-form-item>

        <el-form-item label="条文标题">
          <el-input v-model="form.articleTitle" placeholder="如：关于取证的规定" />
        </el-form-item>

        <el-form-item label="生效日期">
          <el-date-picker
            v-model="form.effectiveDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择生效日期"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="失效日期">
          <el-date-picker
            v-model="form.expiryDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择失效日期"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="当前状态">
          <el-input v-model="form.currentStatus" placeholder="如：现行有效" />
        </el-form-item>

        <el-form-item label="关键词" class="field-span-2">
          <el-input v-model="form.keywords" placeholder="多个关键词可用逗号、顿号或空格分隔" />
        </el-form-item>

        <el-form-item label="别名" class="field-span-2">
          <el-input v-model="form.aliases" placeholder="可选，填写别名或同义表达" />
        </el-form-item>

        <el-form-item label="摘要" class="field-span-2">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>

        <el-form-item label="正文内容" class="field-span-2" required>
          <el-input
            v-model="form.displayText"
            type="textarea"
            :rows="14"
            maxlength="20000"
            show-word-limit
            placeholder="请输入法律知识正文，系统会自动生成检索文本与切块"
          />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="$emit('submit')">
        {{ mode === 'create' ? '创建知识' : '保存修改' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Category, LegalKnowledge } from '@/types'

const props = withDefaults(defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  form: LegalKnowledge
  categories: Category[]
  submitting?: boolean
}>(), {
  submitting: false
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'submit'): void
}>()

const form = computed(() => props.form)
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})
</script>

<style scoped>
.editor-form {
  padding-right: 8px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
}

.field-span-2 {
  grid-column: span 2;
}

@media (max-width: 960px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .field-span-2 {
    grid-column: auto;
  }
}
</style>
