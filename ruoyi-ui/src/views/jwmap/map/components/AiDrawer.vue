<template>
  <transition name="drawer-slide">
    <div v-if="visible" class="ai-drawer">
      <!-- 头部 -->
      <div class="ai-drawer-header">
        <div class="ai-drawer-title">
          <i class="el-icon-cpu" />
          <span>AI 智能分析</span>
        </div>
        <el-button type="text" class="ai-drawer-close" icon="el-icon-close" @click="$emit('close')" size="mini" />
      </div>

      <!-- Tab 栏 -->
      <div v-if="activeTabs.length" class="ai-drawer-tabs">
        <div
          v-for="tab in activeTabs"
          :key="tab.type"
          class="ai-drawer-tab"
          :class="{ 'is-active': tab.type === activeTab, 'is-loading': tab.state.loading }"
          @click="$emit('switch-tab', tab.type)">
          <i :class="tabIcon(tab.type)" />
          <span>{{ tabLabel(tab.type) }}</span>
          <!-- 状态指示 -->
          <span v-if="tab.state.loading" class="ai-tab-dot" title="分析中..." />
          <span v-else-if="tab.state.content && !tab.state.loading" class="ai-tab-check el-icon-success" title="分析完成" />
          <!-- 字符数元数据 -->
          <span class="ai-tab-meta">{{ tabMeta(tab) }}</span>
          <!-- 流式进度条 -->
          <span class="ai-tab-progress">
            <span class="ai-tab-progress-inner" :style="{ width: tabProgress(tab) + '%' }" />
          </span>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!activeTabs.length" class="ai-drawer-empty">
        <i class="el-icon-cpu" />
        <p>在左侧侧边栏或对比面板中点击 "AI分析" 按钮，<br>智能分析结果将在此处展示。</p>
      </div>

      <!-- Tab 内容 -->
      <div v-if="activeTabs.length" class="ai-drawer-body">
        <div v-for="tab in activeTabs" :key="tab.type" v-show="tab.type === activeTab">
          <AiAnalysisCard
            :title="tabLabel(tab.type)"
            :content="tab.state.content"
            :loading="tab.state.loading"
            :error="tab.state.error"
            :analysis-type="tab.state.mode"
            :entity-key="tab.state.entityKey"
            :show-report="tab.state.mode === 'site'"
            @retry="handleRetry(tab.type)"
            @regenerate="handleRegenerate(tab.type)"
            @generate-report="handleGenerateReport(tab)"
            @jump-to-grid="handleJumpToGrid" />
        </div>
      </div>

      <!-- FAB 插槽：AiDrawer 打开时 FAB 吸附到底部 -->
      <div v-if="showFab" class="ai-drawer-fab">
        <slot name="fab" />
      </div>
    </div>
  </transition>
</template>

<script>
import AiAnalysisCard from './AiAnalysisCard'

const TAB_CONFIG = {
  grid:       { label: '网格分析', icon: 'el-icon-s-grid', modeLabel: { site: '选址建议', grid: '网格分析' } },
  branch:     { label: '网点诊断', icon: 'el-icon-office-building' },
  comparison: { label: '对比分析', icon: 'el-icon-data-analysis' },
  quadrant:   { label: '象限解读', icon: 'el-icon-pie-chart' },
  relocation: { label: '迁址建议', icon: 'el-icon-s-promotion' }
}

export default {
  name: 'AiDrawer',
  components: { AiAnalysisCard },
  props: {
    visible: { type: Boolean, default: false },
    activeTab: { type: String, default: '' },
    tabsData: { type: Object, default: () => ({}) },
    showFab: { type: Boolean, default: false }
  },
  computed: {
    activeTabs() {
      const types = ['grid', 'branch', 'comparison', 'quadrant', 'weight', 'relocation']
      return types
        .filter(t => this.tabsData[t])
        .map(t => ({ type: t, state: this.tabsData[t] }))
    }
  },
  methods: {
    tabIcon(type) { return (TAB_CONFIG[type] || {}).icon || 'el-icon-cpu' },
    tabLabel(type) {
      const cfg = TAB_CONFIG[type]
      if (!cfg) return type
      if (type === 'grid' && this.tabsData.grid) {
        const mode = this.tabsData.grid.mode
        if (mode && cfg.modeLabel && cfg.modeLabel[mode]) {
          return cfg.modeLabel[mode]
        }
      }
      return cfg.label
    },
    tabMeta(tab) {
      const len = (tab.state.content || '').length
      if (!len) return ''
      if (len < 1000) return len + 'B'
      return (len / 1000).toFixed(1) + 'K'
    },
    tabProgress(tab) {
      const len = (tab.state.content || '').length
      if (!len) return 0
      return Math.min(Math.round(len / 3000 * 100), 100)
    },
    handleRetry(type) { this.$emit('regenerate', type) },
    handleRegenerate(type) { this.$emit('regenerate', type) },
    handleGenerateReport(tab) { this.$emit('generate-report', tab.state.entityKey) },
    handleJumpToGrid(gridCode) { this.$emit('jump-to-grid', gridCode) }
  }
}
</script>

<style scoped>
/* ---------- 浮动面板（浅色玻璃态 — 精确对齐 SidebarPanel） ---------- */
.ai-drawer {
  position: absolute;
  top: 100px;
  right: 12px;
  bottom: 12px;
  width: clamp(360px, 480px, calc(100vw - 24px));
  z-index: 10001;
  background: linear-gradient(135deg, rgba(255,255,255,0.28), rgba(255,255,255,0.08)), rgba(255,255,255,0.10);
  backdrop-filter: blur(22px) saturate(170%) contrast(1.04);
  -webkit-backdrop-filter: blur(22px) saturate(170%) contrast(1.04);
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.28);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.44), inset 0 -1px 0 rgba(255,255,255,0.10), 0 8px 32px rgba(79,110,246,0.08), 0 1px 4px rgba(0,0,0,0.05);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
@media (prefers-reduced-transparency: reduce) {
  .ai-drawer { background: rgba(255,255,255,0.94); backdrop-filter: none; -webkit-backdrop-filter: none; }
}

/* ---------- 头部 ---------- */
.ai-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 10px;
  border-bottom: 1px solid rgba(79,110,246,0.08);
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}
.ai-drawer-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 700;
  color: #232845;
}
.ai-drawer-title i {
  color: #4f6ef6;
  font-size: 16px;
  animation: ai-title-glow 2s ease-in-out infinite;
  filter: drop-shadow(0 0 2px rgba(79, 110, 246, 0.3));
}
.ai-drawer-close { font-size: 16px; color: #444; padding: 0; }
.ai-drawer-close:hover { color: #4f6ef6; }
@keyframes ai-title-glow {
  0%, 100% { filter: drop-shadow(0 0 2px rgba(79, 110, 246, 0.2)); }
  50% { filter: drop-shadow(0 0 6px rgba(79, 110, 246, 0.4)); }
}

/* ---------- Tab 栏 ---------- */
.ai-drawer-tabs {
  display: flex;
  gap: 4px;
  padding: 10px 16px 0;
  border-bottom: 1px solid rgba(79,110,246,0.08);
  overflow-x: auto;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}
.ai-drawer-tab {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px 8px;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
  white-space: nowrap;
  user-select: none;
  position: relative;
  flex-wrap: wrap;
}
.ai-drawer-tab:hover { color: #4f6ef6; }
.ai-drawer-tab.is-active {
  color: #4f6ef6;
  border-bottom-color: #4f6ef6;
  font-weight: 600;
}
.ai-drawer-tab.is-loading { color: #4f6ef6; }
.ai-drawer-tab i { font-size: 14px; }

/* Tab 状态标记 */
.ai-tab-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #52c41a;
  animation: ai-dot-blink 1s ease-in-out infinite;
  box-shadow: 0 0 4px rgba(82, 196, 26, 0.4);
}
.ai-tab-check { font-size: 12px; color: #52c41a; margin-left: 2px; }
@keyframes ai-dot-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

/* Tab 元数据 */
.ai-tab-meta {
  font-size: 10px;
  color: #999;
  font-variant-numeric: tabular-nums;
  margin-left: 2px;
}

/* Tab 流式进度条 */
.ai-tab-progress {
  display: block;
  width: 100%;
  height: 2px;
  background: rgba(79, 110, 246, 0.08);
  border-radius: 1px;
  overflow: hidden;
  margin-top: 4px;
  flex-basis: 100%;
}
.ai-tab-progress-inner {
  display: block;
  height: 100%;
  background: #4f6ef6;
  border-radius: 1px;
  transition: width 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}

/* ---------- 空状态 ---------- */
.ai-drawer-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
  gap: 12px;
  padding: 40px;
}
.ai-drawer-empty i { font-size: 40px; color: #cdd5e6; opacity: 0.6; }
.ai-drawer-empty p { font-size: 13px; text-align: center; line-height: 1.8; margin: 0; }

/* ---------- 内容区域 ---------- */
.ai-drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px 20px;
}
.ai-drawer-body::-webkit-scrollbar { width: 4px; }
.ai-drawer-body::-webkit-scrollbar-thumb { background: rgba(79,110,246,0.12); border-radius: 4px; }

/* ---------- FAB 容器（抽屉内部底部） ---------- */
.ai-drawer-fab {
  display: flex;
  justify-content: flex-end;
  padding: 8px 16px 12px;
  border-top: 1px solid rgba(79,110,246,0.06);
  flex-shrink: 0;
}

/* ---------- 动画 ---------- */
.drawer-slide-enter-active,
.drawer-slide-leave-active {
  transition: all 0.35s cubic-bezier(0.16, 1, 0.3, 1);
}
.drawer-slide-enter,
.drawer-slide-leave-to {
  opacity: 0;
  transform: translateX(60px) scale(0.96);
}
</style>
