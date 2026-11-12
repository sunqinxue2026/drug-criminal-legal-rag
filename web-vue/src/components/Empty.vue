<template>
  <div class="empty-container">
    <el-empty :description="description" :image-size="imageSize">
      <template v-if="showButton" #default>
        <el-button type="primary" @click="handleClick">{{ buttonText }}</el-button>
      </template>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
interface Props {
  description?: string
  imageSize?: number
  showButton?: boolean
  buttonText?: string
}

interface Emits {
  (e: 'click'): void
}

withDefaults(defineProps<Props>(), {
  description: '暂无数据',
  imageSize: 200,
  showButton: false,
  buttonText: '立即创建'
})

const emit = defineEmits<Emits>()

const handleClick = () => {
  emit('click')
}
</script>

<style scoped>
.empty-container {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
}
</style>
