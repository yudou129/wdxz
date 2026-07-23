<template>
  <div class="ai-card" :class="'ai-type-' + accentType">
    <!-- 流式输出区域 -->
    <div class="ai-body">
      <!-- 初始加载（无内容） — 骨架屏 -->
      <div v-if="internalLoading && !displayContent" class="ai-loading-state">
        <div class="ai-loading-skeleton">
          <div class="ai-loading-skeleton-line" style="width:75%;" />
          <div class="ai-loading-skeleton-line" style="width:55%;" />
          <div class="ai-loading-skeleton-line" style="width:85%;" />
          <div class="ai-loading-skeleton-line" style="width:40%;" />
        </div>
        <div class="ai-thinking-text">{{ thinkingText }}</div>
        <div class="ai-thinking-bar"><div class="ai-thinking-bar-inner" /></div>
      </div>

      <!-- 内容展示（深色渲染区） -->
      <div v-if="displayContent" class="ai-content" ref="aiContent">
        <!-- 可折叠段落渲染 -->
        <div v-for="(section, idx) in contentSections" :key="idx" class="ai-section">
          <div
            v-if="section.isHeading"
            class="ai-section-header"
            :class="{ collapsed: isCollapsed(idx) }"
            @click="toggleSection(idx)">
            <i class="el-icon-caret-bottom ai-section-toggle" />
            <div class="ai-text" v-html="section.html" />
          </div>
          <div v-else class="ai-section-body" :style="{ maxHeight: isCollapsed(idx) ? '0' : 'none', overflow: isCollapsed(idx) ? 'hidden' : 'visible' }">
            <div class="ai-text" v-html="section.html" @click="handleContentClick" />
          </div>
        </div>
        <span v-if="isTyping || (internalLoading)" class="ai-cursor">▋</span>
      </div>

      <!-- 错误状态 -->
      <div v-if="internalError && !internalLoading" class="ai-error-state">
        <i class="el-icon-warning" /> {{ internalError }}
        <el-button type="text" size="mini" class="ai-retry-btn" @click="$emit('retry')">重试</el-button>
      </div>
    </div>

    <!-- 返回顶部按钮 -->
    <div v-if="showScrollTop" class="ai-scroll-top" :class="{ visible: scrollTopVisible }" @click="scrollToTop">
      <i class="el-icon-arrow-up" />
    </div>

    <!-- 底部操作栏 -->
    <div v-if="!internalLoading && displayContent && !internalError" class="ai-footer">
      <span class="ai-footer-left">
        <el-button type="text" size="mini"
          :class="{ 'ai-footer-active': feedbackValue === 1 }"
          @click="handleFeedback(1)">
          <span class="ai-emoji">👍</span> 有用
        </el-button>
        <el-button type="text" size="mini"
          :class="{ 'ai-footer-active': feedbackValue === 0 }"
          @click="handleFeedback(0)">
          <span class="ai-emoji">👎</span> 没用
        </el-button>
      </span>
      <span class="ai-footer-right">
        <el-button type="text" size="mini" class="ai-footer-btn" @click="handleCopy">
          <i :class="copyIcon" /> {{ copyText }}
        </el-button>
        <el-button type="text" size="mini" class="ai-footer-btn" @click="handleRegen">
          <i class="el-icon-refresh" /> 重新分析
        </el-button>
        <el-button v-if="showReport" type="text" size="mini" class="ai-footer-btn"
          @click="$emit('generateReport')">
          <i class="el-icon-document" /> 报告
        </el-button>
      </span>
    </div>
  </div>
</template>

<script>
import { submitFeedback, saveAnalysisContent } from '@/api/jwmap/ai'

const THINKING_PHRASES = [
  '正在分析数据...',
  '正在生成分析报告...',
  '正在整理格式...'
]

export default {
  name: 'AiAnalysisCard',
  props: {
    title: { type: String, default: 'AI 分析' },
    content: { type: String, default: '' },
    loading: { type: Boolean, default: false },
    error: { type: String, default: '' },
    showReport: { type: Boolean, default: false },
    analysisType: { type: String, default: '' },
    entityKey: { type: String, default: '' },
    streamingAnimation: { type: Boolean, default: true }
  },
  data() {
    return {
      displayContent: '',
      renderedContent: '',
      feedbackValue: null,
      renderScheduled: false,
      renderRafId: null,
      thinkingText: THINKING_PHRASES[0],
      thinkingTimer: null,
      thinkingPhase: 0,
      copyText: '复制',
      copyIcon: 'el-icon-document-copy',
      copyTimer: null,
      typingQueue: [],
      typingTimer: null,
      typingSpeed: 15,
      isTyping: false,
      _lastContentLen: 0,
      collapsedSections: new Set(),
      scrollTopVisible: false,
      showScrollTop: false
    }
  },
  computed: {
    internalLoading() { return this.loading },
    internalError() { return this.error },
    accentType() {
      const map = { site: 'site', grid: 'grid', branch: 'branch', comparison: 'comparison', quadrant: 'quadrant' }
      return map[this.analysisType] || 'grid'
    },
    contentSections() {
      if (!this.displayContent) return [{ html: '', isHeading: false }]
      const lines = this.displayContent.split('\n')
      const sections = []
      let currentHtml = ''
      let currentIsHeading = false

      const flush = () => {
        if (currentHtml) {
          sections.push({ html: this.renderMarkdown(currentHtml.trim()), isHeading: currentIsHeading })
        }
      }

      for (const line of lines) {
        const isH = /^#{1,3}\s/.test(line)
        if (isH) {
          flush()
          currentHtml = line
          currentIsHeading = true
        } else {
          if (currentIsHeading) {
            flush()
            currentHtml = ''
            currentIsHeading = false
          }
          currentHtml += (currentHtml ? '\n' : '') + line
        }
      }
      flush()
      if (!sections.length) {
        sections.push({ html: this.renderMarkdown(this.displayContent), isHeading: false })
      }
      this.showScrollTop = this.displayContent.length > 800
      return sections
    }
  },
  watch: {
    content: {
      handler(val) {
        if (!val) return
        if (this.streamingAnimation) {
          const newChunk = val.slice(this._lastContentLen)
          this._lastContentLen = val.length
          if (newChunk) {
            this.typingQueue.push(newChunk)
            if (!this.isTyping) {
              this.processTypingQueue()
            }
          }
        } else {
          this.displayContent = val
          this.scheduleRender()
        }
        if (this.thinkingPhase === 0 && /\*{2,}|\n## /.test(val)) {
          this.thinkingPhase = 1
          this.thinkingText = THINKING_PHRASES[1]
        }
      },
      immediate: true
    },
    loading(val) {
      if (!val && this.displayContent) {
        this.flushRender()
        this.stopThinkingTimer()
      } else if (val && !this.displayContent) {
        this.startThinkingTimer()
      }
    }
  },
  mounted() {
    this.$nextTick(() => {
      const el = this.$refs.aiContent
      if (el) {
        el.addEventListener('scroll', this.onContentScroll)
      }
    })
  },
  beforeDestroy() {
    this.stopThinkingTimer()
    if (this.renderRafId) cancelAnimationFrame(this.renderRafId)
    if (this.copyTimer) clearTimeout(this.copyTimer)
    if (this.typingTimer) clearInterval(this.typingTimer)
    const el = this.$refs.aiContent
    if (el) el.removeEventListener('scroll', this.onContentScroll)
  },
  methods: {
    /* ---------- 逐字打印动画 ---------- */
    processTypingQueue() {
      if (!this.typingQueue.length) {
        this.isTyping = false
        return
      }
      this.isTyping = true
      const chunk = this.typingQueue.shift()
      let index = 0
      const typeChar = () => {
        if (index >= chunk.length) {
          clearInterval(this.typingTimer)
          this.typingTimer = null
          this.scheduleRender()
          if (this.typingQueue.length) {
            this.processTypingQueue()
          } else {
            this.isTyping = false
          }
          return
        }
        this.displayContent += chunk[index]
        const char = chunk[index]
        index++
        clearInterval(this.typingTimer)
        const isPunct = /[。.！!？?\n]/.test(char)
        const isMidPunct = /[，,、；;]/.test(char)
        const speed = isPunct ? 50 : isMidPunct ? 30 : this.typingSpeed
        if (index < chunk.length || this.typingQueue.length) {
          this.typingTimer = setInterval(typeChar, speed)
        } else {
          this.isTyping = false
          this.scheduleRender()
        }
      }
      this.typingTimer = setInterval(typeChar, this.typingSpeed)
    },

    /* ---------- 段落折叠 ---------- */
    isCollapsed(idx) { return this.collapsedSections.has(idx) },
    toggleSection(idx) {
      if (this.collapsedSections.has(idx)) {
        this.collapsedSections.delete(idx)
      } else {
        this.collapsedSections.add(idx)
      }
      this.collapsedSections = new Set(this.collapsedSections)
    },

    /* ---------- 返回顶部 ---------- */
    onContentScroll() {
      const el = this.$refs.aiContent
      if (el) this.scrollTopVisible = el.scrollTop > 300
    },
    scrollToTop() {
      const el = this.$refs.aiContent
      if (el) el.scrollTo({ top: 0, behavior: 'smooth' })
    },

    /* ---------- 流式渲染节流 ---------- */
    scheduleRender() {
      if (this.renderScheduled) return
      this.renderScheduled = true
      this.renderRafId = requestAnimationFrame(() => {
        this.flushRender()
        this.renderScheduled = false
      })
    },
    flushRender() {
      this.renderRafId = null
      this.$nextTick(() => this.scrollToBottom())
    },

    /* ---------- 思考动画 ---------- */
    startThinkingTimer() {
      this.thinkingPhase = 0
      this.thinkingText = THINKING_PHRASES[0]
      this.thinkingTimer = setInterval(() => {
        this.thinkingPhase = (this.thinkingPhase + 1) % THINKING_PHRASES.length
        this.thinkingText = THINKING_PHRASES[this.thinkingPhase]
      }, 2500)
    },
    stopThinkingTimer() {
      if (this.thinkingTimer) {
        clearInterval(this.thinkingTimer)
        this.thinkingTimer = null
      }
    },

    /* ---------- 反馈 ---------- */
    async handleFeedback(value) {
      this.feedbackValue = value
      if (this.analysisType && this.entityKey) {
        try {
          if (value === 1) {
            const content = this.displayContent || this.content
            await saveAnalysisContent({
              analysisType: this.analysisType,
              entityKey: this.entityKey,
              content: content
            })
          }
          await submitFeedback({ analysisType: this.analysisType, entityKey: this.entityKey, satisfied: value })
          this.$message.success(value === 1 ? '分析结果已保存，感谢反馈！' : '已记录')
        } catch (e) { /* 忽略 */ }
      }
    },

    /* ---------- 网格跳转 ---------- */
    handleContentClick(e) {
      const link = e.target.closest('.ai-grid-link')
      if (link) {
        const gridCode = link.dataset.gridCode
        if (gridCode) this.$emit('jump-to-grid', gridCode)
      }
      const codeCopy = e.target.closest('.ai-code-copy')
      if (codeCopy) {
        const pre = codeCopy.closest('.ai-codeblock')
        if (pre) {
          const code = pre.querySelector('code')
          if (code) this.copyTextToClipboard(code.textContent)
        }
      }
    },
    copyTextToClipboard(text) {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text)
      } else {
        const ta = document.createElement('textarea')
        ta.value = text
        ta.style.position = 'fixed'
        ta.style.opacity = '0'
        document.body.appendChild(ta)
        ta.select()
        document.execCommand('copy')
        document.body.removeChild(ta)
      }
      this.$message.success('代码已复制')
    },

    /* ---------- 复制 ---------- */
    handleCopy() {
      if (!this.displayContent) return
      const doCopy = () => {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          return navigator.clipboard.writeText(this.displayContent)
        } else {
          return new Promise((resolve, reject) => {
            try {
              const ta = document.createElement('textarea')
              ta.value = this.displayContent
              ta.style.position = 'fixed'
              ta.style.opacity = '0'
              document.body.appendChild(ta)
              ta.select()
              document.execCommand('copy')
              document.body.removeChild(ta)
              resolve()
            } catch (e) { reject(e) }
          })
        }
      }
      doCopy().then(() => {
        this.copyText = '已复制'
        this.copyIcon = 'el-icon-success'
        if (this.copyTimer) clearTimeout(this.copyTimer)
        this.copyTimer = setTimeout(() => {
          this.copyText = '复制'
          this.copyIcon = 'el-icon-document-copy'
        }, 2000)
      }).catch(() => { this.fallbackCopy() })
    },
    handleRegen() {
      this.$confirm('将重新生成分析内容，确认继续？', '重新分析', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'info'
      }).then(() => { this.$emit('regenerate') }).catch(() => {})
    },
    fallbackCopy() {
      const ta = document.createElement('textarea')
      ta.value = this.displayContent
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
      this.$message.success('已复制到剪贴板')
    },

    /* ---------- Markdown 渲染 ---------- */
    renderMarkdown(text) {
      if (!text) return ''
      let html = text
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/^## (.+)$/gm, '<h3 class="ai-h3">$1</h3>')
        .replace(/^##\s+(.+)$/gm, '<h3 class="ai-h3">$1</h3>')
        .replace(/^### (.+)$/gm, '<h4 class="ai-h4">$1</h4>')
        .replace(/^# (.+)$/gm, '<h2 class="ai-h2">$1</h2>')
        .replace(/^\|(.+)\|$/gm, function(m, row) {
          const cells = row.split('|').map(c => c.trim()).filter(Boolean)
          if (cells.length && /^[-:\s]+$/.test(cells[0])) return '<tr class="ai-tr-sep"/>'
          const tag = row.indexOf('---') >= 0 ? 'th' : 'td'
          return '<tr>' + cells.map(c => '<' + tag + '>' + c + '</' + tag + '>').join('') + '</tr>'
        })
        .replace(/```(\w*)\n?([\s\S]*?)```/gm, function(m, lang, code) {
          const langAttr = lang ? ' data-lang="' + lang + '"' : ''
          return '<pre class="ai-codeblock"' + langAttr + '><code>' + code.trim() + '</code>' +
            '<button class="ai-code-copy" title="复制代码"><i class="el-icon-document-copy" /></button></pre>'
        })
        .replace(/^> (.+)$/gm, '<blockquote class="ai-blockquote">$1</blockquote>')
        .replace(/^---$/gm, '<hr class="ai-hr" />')
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/\[([^\]]+)\]\(grid:([^)]+)\)/g,
          '<a href="javascript:;" class="ai-grid-link" data-grid-code="$2"><span class="ai-grid-link-icon">→</span>$1</a>')
        .replace(/^- (.+)$/gm, '<li>$1</li>')
        .replace(/^\d+\.\s(.+)$/gm, '<li class="ai-ol-li">$1</li>')
        .replace(/[★☆]{4,5}/g, function(m) {
          const filled = (m.match(/★/g) || []).length
          const total = m.length
          return '<span class="ai-stars">' + '★'.repeat(filled) + '☆'.repeat(total - filled) + '</span>'
        })
        .replace(/\n\n/g, '</p><p class="ai-p">')

      html = '<p class="ai-p">' + html + '</p>'
      html = html.replace(/((?:<li[^>]*>.*?<\/li>\s*)+)/g, '<ul class="ai-ul">$1</ul>')
      html = html.replace(/((?:<tr[^>]*>.*?<\/tr>\s*)+)/g, '<table class="ai-table">$1</table>')
      return html
    },

    scrollToBottom() {
      const el = this.$refs.aiContent
      if (el) el.scrollTop = el.scrollHeight
    }
  }
}
</script>

<style scoped>
/* ==================== 卡片容器（浅色白底 + 类型色顶部边） ==================== */
.ai-card {
  border-radius: 10px;
  border: 1px solid rgba(79, 110, 246, 0.12);
  background: #fff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.ai-card.ai-type-site {
  border-top: 3px solid #06b6d4;
  box-shadow: 0 2px 8px rgba(6, 182, 212, 0.06);
}
.ai-card.ai-type-grid {
  border-top: 3px solid #4f6ef6;
  box-shadow: 0 2px 8px rgba(79, 110, 246, 0.06);
}
.ai-card.ai-type-branch {
  border-top: 3px solid #a855f7;
  box-shadow: 0 2px 8px rgba(168, 85, 247, 0.06);
}
.ai-card.ai-type-comparison {
  border-top: 3px solid #f59e0b;
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.06);
}
.ai-card.ai-type-quadrant {
  border-top: 3px solid #f43f5e;
  box-shadow: 0 2px 8px rgba(244, 63, 94, 0.06);
}

/* ==================== 身体 ==================== */
.ai-body {
  position: relative;
}

/* ==================== 骨架屏加载（浅色系统配色） ==================== */
.ai-loading-state {
  padding: 40px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  position: relative;
}
.ai-loading-skeleton {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.ai-loading-skeleton-line {
  height: 14px;
  border-radius: 7px;
  background: linear-gradient(90deg, rgba(79,110,246,0.04), rgba(79,110,246,0.12), rgba(79,110,246,0.04));
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.8s ease-in-out infinite;
}
@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
.ai-thinking-text {
  font-size: 14px;
  color: #888;
  transition: opacity 0.3s;
  font-weight: 500;
}
.ai-thinking-bar {
  width: 200px;
  height: 3px;
  background: #e8ecf1;
  border-radius: 3px;
  overflow: hidden;
}
.ai-thinking-bar-inner {
  width: 30%;
  height: 100%;
  background: linear-gradient(90deg, #4f6ef6, #06b6d4, #4f6ef6);
  background-size: 200% 100%;
  border-radius: 3px;
  animation: ai-load-bar 1.2s ease-in-out infinite;
}
@keyframes ai-load-bar {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(400%); }
}

/* ==================== 内容区（浅色背景适配系统主题） ==================== */
.ai-content {
  padding: 12px 16px;
  overflow-y: auto;
  scroll-behavior: smooth;
  position: relative;
  max-height: calc(80vh - 200px);
}

/* ==================== 光标 ==================== */
.ai-cursor {
  display: inline-block;
  color: #4f6ef6;
  font-size: 14px;
  animation: cursor-blink 0.8s step-end infinite;
  margin-left: 2px;
}
@keyframes cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ==================== 错误 ==================== */
.ai-error-state {
  padding: 24px 16px;
  text-align: center;
  color: #e6a23c;
  font-size: 13px;
}
.ai-retry-btn { margin-left: 8px; color: #4f6ef6; }

/* ==================== 可折叠段落 ==================== */
.ai-section {
  margin-bottom: 2px;
}
.ai-section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
  padding: 2px 0;
  border-radius: 4px;
  transition: background 0.15s;
}
.ai-section-header:hover {
  background: rgba(79, 110, 246, 0.05);
}
.ai-section-toggle {
  font-size: 12px;
  color: #4f6ef6;
  transition: transform 0.2s;
  flex-shrink: 0;
}
.ai-section-header.collapsed .ai-section-toggle {
  transform: rotate(-90deg);
}
.ai-section-body {
  transition: max-height 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

/* ==================== Markdown 样式（浅色背景适配） ==================== */
.ai-text :deep(.ai-p) {
  margin: 8px 0;
  font-size: 13.5px;
  line-height: 1.8;
  color: #444;
}
.ai-text :deep(.ai-h2) {
  margin: 18px 0 10px;
  font-size: 15px;
  font-weight: 700;
  color: #232845;
  border-left: 4px solid #4f6ef6;
  padding-left: 10px;
  border-bottom: 1px solid #eef0f5;
  padding-bottom: 6px;
}
.ai-text :deep(.ai-h3) {
  margin: 14px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #4f6ef6;
  border-left: 3px solid #4f6ef6;
  padding-left: 8px;
}
.ai-text :deep(.ai-h4) {
  margin: 10px 0 6px;
  font-size: 13.5px;
  font-weight: 600;
  color: #333;
}
.ai-text :deep(strong) { color: #1a1a2e; font-weight: 700; }
.ai-text :deep(.ai-ul) {
  margin: 6px 0;
  padding-left: 20px;
}
.ai-text :deep(li) {
  font-size: 13.5px;
  line-height: 1.7;
  color: #444;
  margin-bottom: 3px;
}

/* 表格 — 浅色斑马纹 */
.ai-text :deep(.ai-table) {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 12.5px;
  border-radius: 8px;
  overflow: hidden;
}
.ai-text :deep(.ai-table td),
.ai-text :deep(.ai-table th) {
  border: 1px solid #e0e4ed;
  padding: 6px 10px;
  text-align: left;
}
.ai-text :deep(.ai-table th) {
  background: #f0f4ff;
  font-weight: 600;
  color: #232845;
}
.ai-text :deep(.ai-table td) {
  color: #444;
}
.ai-text :deep(.ai-table tr:nth-child(even) td) {
  background: rgba(79, 110, 246, 0.03);
}
.ai-text :deep(.ai-table tr:hover td) {
  background: rgba(79, 110, 246, 0.06);
}
.ai-text :deep(.ai-tr-sep) { display: none; }

/* 代码块 */
.ai-text :deep(.ai-codeblock) {
  background: #f5f7fa;
  border: 1px solid #e0e4ed;
  border-radius: 8px;
  padding: 14px 16px;
  margin: 10px 0;
  overflow-x: auto;
  font-size: 12.5px;
  line-height: 1.6;
  font-family: 'Menlo', 'Monaco', 'Consolas', 'Courier New', monospace;
  position: relative;
}
.ai-text :deep(.ai-codeblock)::before {
  content: attr(data-lang);
  position: absolute;
  top: 6px;
  right: 32px;
  font-size: 10px;
  color: #999;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
.ai-text :deep(.ai-codeblock code) {
  background: none;
  padding: 0;
  color: #333;
}
.ai-text :deep(.ai-code-copy) {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 24px;
  height: 24px;
  border: none;
  background: rgba(79, 110, 246, 0.08);
  border-radius: 4px;
  color: #666;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s, background 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}
.ai-text :deep(.ai-codeblock:hover .ai-code-copy) {
  opacity: 1;
}
.ai-text :deep(.ai-code-copy:hover) {
  background: rgba(79, 110, 246, 0.2);
  color: #4f6ef6;
}

/* 引用 */
.ai-text :deep(.ai-blockquote) {
  margin: 10px 0;
  padding: 8px 14px 8px 36px;
  border-left: 3px solid #4f6ef6;
  background: rgba(79, 110, 246, 0.04);
  color: #555;
  font-size: 13px;
  line-height: 1.7;
  position: relative;
  border-radius: 0 6px 6px 0;
}
.ai-text :deep(.ai-blockquote)::before {
  content: '💡';
  position: absolute;
  left: 10px;
  top: 8px;
  font-size: 14px;
}

/* 分割线 */
.ai-text :deep(.ai-hr) {
  border: none;
  height: 1px;
  background: linear-gradient(90deg, transparent, #d0d5e0, transparent);
  margin: 14px 0;
}

/* 星号评级 */
.ai-text :deep(.ai-stars) {
  color: #f5a623;
  letter-spacing: 1px;
}

/* ==================== 返回顶部按钮 ==================== */
.ai-scroll-top {
  position: sticky;
  bottom: 4px;
  left: 50%;
  transform: translateX(-50%);
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(79, 110, 246, 0.12);
  border: 1px solid rgba(79, 110, 246, 0.2);
  color: #4f6ef6;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.2s;
  z-index: 2;
  margin: 0 auto;
}
.ai-scroll-top:hover {
  background: rgba(79, 110, 246, 0.2);
}
.ai-scroll-top.visible {
  opacity: 1;
}
.ai-scroll-top i {
  font-size: 14px;
}

/* ==================== 底部操作栏（浅色适配系统色） ==================== */
.ai-footer {
  padding: 8px 12px;
  border-top: 1px solid rgba(79, 110, 246, 0.1);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;
  background: rgba(245, 247, 255, 0.6);
  position: sticky;
  bottom: 0;
  z-index: 1;
}
.ai-footer-left, .ai-footer-right {
  display: flex;
  align-items: center;
  gap: 4px;
}
.ai-footer-btn { font-size: 12px; padding: 0; color: #666; }
.ai-footer-btn:hover { color: #4f6ef6; }
.ai-footer-active {
  color: #4f6ef6 !important;
  font-weight: 600;
}
.ai-footer-active .ai-emoji {
  display: inline-block;
  animation: ai-like-bounce 0.3s ease;
}
.ai-footer .el-button--text { font-size: 12px; }
.ai-emoji { font-size: 14px; }
@keyframes ai-like-bounce {
  0% { transform: scale(1); }
  50% { transform: scale(1.3); }
  100% { transform: scale(1); }
}

/* 网格跳转链接 — 蓝色渐变胶囊 */
.ai-text :deep(.ai-grid-link) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #fff;
  font-weight: 600;
  text-decoration: none;
  background: linear-gradient(135deg, #4f6ef6, #06b6d4);
  border: none;
  border-radius: 12px;
  padding: 2px 12px;
  cursor: pointer;
  transition: all 0.15s ease;
  font-size: 12.5px;
  margin: 0 2px;
  box-shadow: 0 1px 4px rgba(79, 110, 246, 0.2);
}
.ai-text :deep(.ai-grid-link:hover) {
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(79, 110, 246, 0.35);
}
.ai-text :deep(.ai-grid-link-icon) {
  font-size: 13px;
  line-height: 1;
}
</style>
