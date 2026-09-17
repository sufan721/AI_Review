import hljs from 'highlight.js/lib/common'
import MarkdownIt from 'markdown-it'

/**
 * html: false 是 XSS 的第一道防线：笔记正文里的原始 HTML 会被转义成文本而不是插入 DOM。
 * 链接协议由 markdown-it 内置的 validateLink 兜底，javascript: 一类的地址会被拒绝。
 */
const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  highlight(code, language) {
    if (language !== '' && hljs.getLanguage(language)) {
      try {
        return hljs.highlight(code, { language, ignoreIllegals: true }).value
      } catch {
        // 高亮失败时退回 markdown-it 自带的转义
      }
    }
    return ''
  },
})

export function renderMarkdown(source: string): string {
  return md.render(source)
}

/** 列表页展示用：把 Markdown 压成一行纯文本摘要。 */
export function toPlainSummary(source: string, maxLength = 100): string {
  const plain = source
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/^\s{0,3}#{1,6}\s+/gm, '')
    .replace(/^\s{0,3}>\s?/gm, '')
    .replace(/^\s{0,3}(?:[-*+]|\d+\.)\s+/gm, '')
    .replace(/[*_~]{1,3}/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  return plain.length > maxLength ? `${plain.slice(0, maxLength)}…` : plain
}
