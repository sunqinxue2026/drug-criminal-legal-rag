<template>
  <el-dialog
    v-model="visible"
    :title="mode === 'create' ? '新增法律文书' : '编辑法律文书'"
    width="1080px"
    destroy-on-close
  >
    <el-form label-width="104px" class="editor-form">
      <div class="form-grid">
        <el-form-item label="文书标题" class="field-span-2" required>
          <el-input v-model="form.title" maxlength="180" show-word-limit placeholder="请输入文书标题" />
        </el-form-item>

        <el-form-item label="专题分类">
          <el-select v-model="form.categoryId" clearable placeholder="请选择专题分类" style="width: 100%">
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="文书状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>

        <el-form-item label="来源名称">
          <el-input v-model="form.sourceName" placeholder="如：最高人民法院、手工录入" />
        </el-form-item>

        <el-form-item label="来源类型">
          <el-input v-model="form.sourceType" placeholder="如：official_site / manual_entry" />
        </el-form-item>

        <el-form-item label="来源链接" class="field-span-2">
          <el-input v-model="form.sourceUrl" placeholder="可选，填写原始来源网址" />
        </el-form-item>

        <el-form-item label="文书类型">
          <el-input v-model="form.documentType" placeholder="如：典型案例、判决书" />
        </el-form-item>

        <el-form-item label="文书子类">
          <el-input v-model="form.documentSubtype" placeholder="如：刑事判决书" />
        </el-form-item>

        <el-form-item label="案件类型">
          <el-input v-model="form.caseType" placeholder="如：刑事、综合" />
        </el-form-item>

        <el-form-item label="案号">
          <el-input v-model="form.caseNumber" placeholder="请输入案号" />
        </el-form-item>

        <el-form-item label="法院/检察院">
          <el-input v-model="form.courtName" placeholder="请输入法院或检察院名称" />
        </el-form-item>

        <el-form-item label="案由">
          <el-input v-model="form.causeNormalized" placeholder="请输入案由" />
        </el-form-item>

        <el-form-item label="业务领域">
          <el-input v-model="form.businessDomain" placeholder="如：涉毒刑事、程序应对" />
        </el-form-item>

        <el-form-item label="程序领域">
          <el-input v-model="form.procedureDomain" placeholder="如：证据规则、专题文书" />
        </el-form-item>

        <el-form-item label="裁判结果">
          <el-input v-model="form.decisionResult" placeholder="请输入裁判结果" />
        </el-form-item>

        <el-form-item label="裁判日期">
          <el-date-picker
            v-model="form.decisionDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择裁判日期"
            style="width: 100%"
          />
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

        <el-form-item label="关键词" class="field-span-2">
          <el-input v-model="form.keywords" placeholder="多个关键词可用逗号、顿号或空格分隔" />
        </el-form-item>

        <el-form-item label="别名" class="field-span-2">
          <el-input v-model="form.aliases" placeholder="可选，填写别名或同义描述" />
        </el-form-item>

        <el-form-item label="文书摘要" class="field-span-2">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>

        <el-form-item label="事实摘要" class="field-span-2">
          <el-input v-model="form.factsSummary" type="textarea" :rows="3" maxlength="1200" show-word-limit />
        </el-form-item>

        <el-form-item label="争议焦点" class="field-span-2">
          <el-input v-model="form.issuesSummary" type="textarea" :rows="3" maxlength="1200" show-word-limit />
        </el-form-item>

        <el-form-item label="裁判理由" class="field-span-2">
          <el-input v-model="form.reasoningSummary" type="textarea" :rows="4" maxlength="1600" show-word-limit />
        </el-form-item>

        <el-form-item label="结果摘要" class="field-span-2">
          <el-input v-model="form.resultSummary" type="textarea" :rows="3" maxlength="1200" show-word-limit />
        </el-form-item>

        <el-form-item label="法律依据" class="field-span-2">
          <el-input v-model="form.legalBasisText" type="textarea" :rows="3" maxlength="1200" show-word-limit />
        </el-form-item>

        <el-form-item label="案件详情" class="field-span-2">
          <el-input v-model="form.caseDetailText" type="textarea" :rows="5" maxlength="3000" show-word-limit />
        </el-form-item>

        <el-form-item label="正文内容" class="field-span-2" required>
          <el-input
            v-model="form.displayText"
            type="textarea"
            :rows="14"
            maxlength="30000"
            show-word-limit
            placeholder="请输入文书正文，系统会自动生成检索文本与切块"
          />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="$emit('submit')">
        {{ mode === 'create' ? '创建文书' : '保存修改' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Category, LegalDocument } from '@/types'

const props = withDefaults(defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  form: LegalDocument
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
