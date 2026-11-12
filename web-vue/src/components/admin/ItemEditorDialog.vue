<template>
  <el-dialog
    v-model="visible"
    :title="mode === 'create' ? '新增法律案件' : '编辑法律案件'"
    width="760px"
    destroy-on-close
  >
    <el-form label-width="96px" class="editor-form">
      <div class="form-grid">
        <el-form-item label="案件标题" class="field-span-2" required>
          <el-input v-model="form.title" maxlength="160" show-word-limit placeholder="请输入案件标题" />
        </el-form-item>

        <el-form-item label="专题分类">
          <el-select v-model="form.categoryId" clearable placeholder="请选择专题分类" style="width: 100%">
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>

        <el-form-item label="案件标签" class="field-span-2">
          <el-input
            v-model="form.tags"
            placeholder="多个标签可用逗号、顿号或空格分隔"
          />
        </el-form-item>

        <el-form-item label="摘要" class="field-span-2">
          <el-input
            v-model="form.summary"
            type="textarea"
            :rows="4"
            maxlength="600"
            show-word-limit
            placeholder="请输入案件摘要，便于列表展示和快速检索"
          />
        </el-form-item>

        <el-form-item label="案件正文" class="field-span-2" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="12"
            maxlength="12000"
            show-word-limit
            placeholder="请输入案件正文或官方案例全文"
          />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="$emit('submit')">
        {{ mode === 'create' ? '创建案件' : '保存修改' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Category, Item } from '@/types'

const props = withDefaults(defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  form: Item
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

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .field-span-2 {
    grid-column: auto;
  }
}
</style>
