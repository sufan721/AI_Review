<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '../../utils/markdown'

const props = defineProps<{ content: string }>()

const html = computed(() => renderMarkdown(props.content))
</script>

<template>
  <!-- html 由 utils/markdown 统一产出，已关闭原生 HTML 解析，不可绕开该处直接渲染用户输入 -->
  <div class="markdown-body" v-html="html" />
</template>

<style scoped>
.markdown-body {
  line-height: 1.75;
  word-break: break-word;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  line-height: 1.3;
  margin: 1.4em 0 0.6em;
}

.markdown-body :deep(h1) {
  font-size: 1.6em;
  border-bottom: 1px solid var(--border);
  padding-bottom: 0.3em;
}

.markdown-body :deep(h2) {
  font-size: 1.35em;
}

.markdown-body :deep(p),
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.8em 0;
}

.markdown-body :deep(code) {
  padding: 0.15em 0.35em;
  border-radius: 4px;
  background: var(--bg-soft);
  font-family: ui-monospace, "SFMono-Regular", Consolas, monospace;
  font-size: 0.9em;
}

.markdown-body :deep(pre) {
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-soft);
  overflow-x: auto;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: none;
}

.markdown-body :deep(blockquote) {
  margin: 0.8em 0;
  padding: 0 1em;
  border-left: 3px solid var(--border);
  color: var(--fg-muted);
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 0.8em 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 6px 12px;
  border: 1px solid var(--border);
}

.markdown-body :deep(img) {
  max-width: 100%;
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 1.5em 0;
}
</style>
