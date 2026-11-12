/**
 * Markdown 渲染组件
 */
<template>
  <div class="markdown-viewer" v-html="renderedContent"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'

interface Props {
  content: string
}

const props = defineProps<Props>()

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true
})

const renderedContent = computed(() => {
  return md.render(props.content || '')
})
</script>

<style scoped>
.markdown-viewer {
  line-height: 1.8;
  color: #333;
}

.markdown-viewer :deep(h1) {
  font-size: 28px;
  font-weight: bold;
  margin: 20px 0 10px;
  padding-bottom: 10px;
  border-bottom: 2px solid #eee;
}

.markdown-viewer :deep(h2) {
  font-size: 24px;
  font-weight: bold;
  margin: 18px 0 8px;
}

.markdown-viewer :deep(h3) {
  font-size: 20px;
  font-weight: bold;
  margin: 16px 0 8px;
}

.markdown-viewer :deep(p) {
  margin: 10px 0;
}

.markdown-viewer :deep(ul),
.markdown-viewer :deep(ol) {
  margin: 10px 0;
  padding-left: 30px;
}

.markdown-viewer :deep(li) {
  margin: 5px 0;
}

.markdown-viewer :deep(code) {
  background-color: #f5f5f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 14px;
}

.markdown-viewer :deep(pre) {
  background-color: #f5f5f5;
  padding: 15px;
  border-radius: 5px;
  overflow-x: auto;
  margin: 15px 0;
}

.markdown-viewer :deep(pre code) {
  background-color: transparent;
  padding: 0;
}

.markdown-viewer :deep(blockquote) {
  border-left: 4px solid #409EFF;
  padding-left: 15px;
  margin: 15px 0;
  color: #666;
}

.markdown-viewer :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 15px 0;
}

.markdown-viewer :deep(th),
.markdown-viewer :deep(td) {
  border: 1px solid #ddd;
  padding: 8px 12px;
  text-align: left;
}

.markdown-viewer :deep(th) {
  background-color: #f5f5f5;
  font-weight: bold;
}

.markdown-viewer :deep(a) {
  color: #409EFF;
  text-decoration: none;
}

.markdown-viewer :deep(a:hover) {
  text-decoration: underline;
}

.markdown-viewer :deep(img) {
  max-width: 100%;
  height: auto;
  margin: 10px 0;
}
</style>
