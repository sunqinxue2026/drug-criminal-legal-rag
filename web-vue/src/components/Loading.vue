<template>
  <div class="loading-container">
    <el-icon class="loading-icon" :size="size">
      <Loading />
    </el-icon>
    <p v-if="text" class="loading-text">{{ text }}</p>
  </div>
</template>

<script setup lang="ts">
interface Props {
  size?: number
  text?: string
}

withDefaults(defineProps<Props>(), {
  size: 40,
  text: '加载中...'
})
</script>

<style scoped>
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.loading-icon {
  animation: rotate 1s linear infinite;
  color: #1890ff;
}

.loading-text {
  margin-top: 16px;
  color: #666;
  font-size: 14px;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
