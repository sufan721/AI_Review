import { describe, expect, it } from 'vitest'
import { renderMarkdown, toPlainSummary } from './markdown'

describe('renderMarkdown', () => {
  it('renders common markdown syntax', () => {
    const html = renderMarkdown('# 标题\n\n**加粗** 与 [链接](https://example.com)')

    expect(html).toContain('<h1>')
    expect(html).toContain('<strong>加粗</strong>')
    expect(html).toContain('href="https://example.com"')
  })

  it('escapes raw HTML instead of injecting it into the DOM', () => {
    const html = renderMarkdown('<script>alert(1)</script>')

    expect(html).not.toContain('<script>')
    expect(html).toContain('&lt;script&gt;')
  })

  it('escapes inline event handlers', () => {
    const html = renderMarkdown('<img src=x onerror="alert(1)">')

    expect(html).not.toContain('<img')
    expect(html).toContain('&lt;img')
  })

  it('refuses javascript: link targets', () => {
    const html = renderMarkdown('[点我](javascript:alert(1))')

    expect(html).not.toContain('href="javascript:')
  })

  it('highlights fenced code blocks', () => {
    const html = renderMarkdown('```js\nconst answer = 42\n```')

    expect(html).toContain('hljs')
    expect(html).toContain('answer')
  })

  it('escapes code content that looks like HTML', () => {
    const html = renderMarkdown('```\n<script>alert(1)</script>\n```')

    expect(html).not.toContain('<script>')
  })

  it('renders tables and blockquotes', () => {
    const html = renderMarkdown('| a | b |\n| - | - |\n| 1 | 2 |\n\n> 引用')

    expect(html).toContain('<table>')
    expect(html).toContain('<blockquote>')
  })
})

describe('toPlainSummary', () => {
  it('flattens markdown into a single line', () => {
    expect(toPlainSummary('# 标题\n\n- 第一项\n- 第二项')).toBe('标题 第一项 第二项')
  })

  it('unwraps links and drops images', () => {
    expect(toPlainSummary('看 [文档](https://example.com) ![图](a.png)')).toBe('看 文档')
  })

  it('collapses fenced code blocks', () => {
    expect(toPlainSummary('说明\n\n```js\nconst a = 1\n```\n\n结尾')).toBe('说明 结尾')
  })

  it('truncates long text with an ellipsis', () => {
    const summary = toPlainSummary('字'.repeat(200), 10)

    expect(summary).toHaveLength(11)
    expect(summary.endsWith('…')).toBe(true)
  })
})
