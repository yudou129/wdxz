<template>
  <div class="ai-fab-container" :class="{ 'is-inline': inline }" @click.stop>
    <!-- 展开菜单 -->
    <transition-group name="fab-item" tag="div" class="ai-fab-speed-dial">
      <div
        v-for="item in fabItems"
        :key="item.type"
        v-show="expanded"
        class="fab-item"
        :class="{ disabled: item.disabled, loading: item.loading, done: item.done }"
        :data-type="item.type"
        :style="{ '--fab-item-color': item.color }"
        @click.stop="handleItemClick(item)">
        <span class="fab-item-accent" :style="{ background: item.color }" />
        <i :class="item.icon" class="fab-item-icon" />
        <span class="fab-item-label">{{ item.label }}</span>
        <span class="fab-item-status">
          <i v-if="item.loading" class="el-icon-loading" style="color:#4f6ef6;font-size:12px;" />
          <i v-else-if="item.done" class="el-icon-success" style="color:#52c41a;font-size:12px;" />
        </span>
      </div>
    </transition-group>

    <!-- 主按钮 -->
    <div
      class="ai-fab-main"
      :class="{ open: expanded }"
      :title="expanded ? '收起' : 'AI 智能分析'"
      @click.stop="toggleExpand">
      <i class="el-icon-cpu" />
    </div>
  </div>
</template>

<script>
const FAB_ITEMS = [
  { type: 'grid',       label: 'AI选址建议', icon: 'el-icon-s-grid',         color: '#06b6d4', needsGrid: true, needsBranch: false, needsCompare: false },
  { type: 'branch',     label: 'AI网点诊断', icon: 'el-icon-office-building', color: '#a855f7', needsGrid: false, needsBranch: true,  needsCompare: false },
  { type: 'comparison', label: 'AI对比分析', icon: 'el-icon-data-analysis',  color: '#f59e0b', needsGrid: false, needsBranch: false, needsCompare: true },
  { type: 'quadrant',   label: 'AI象限深度分析', icon: 'el-icon-pie-chart',  color: '#f43f5e', needsGrid: false, needsBranch: true,  needsCompare: false },
  { type: 'relocation', label: 'AI迁址建议', icon: 'el-icon-s-promotion',    color: '#8b5cf6', needsGrid: false, needsBranch: true,  needsCompare: false }
]

export default {
  name: 'AiFabButton',
  props: {
    gridContext:       { type: Object, default: null },
    branchContext:     { type: Object, default: null },
    comparisonCount:   { type: Number, default: 0 },
    aiStates:          { type: Object, default: () => ({}) },
    inline:            { type: Boolean, default: false }
  },
  data() {
    return {
      expanded: true,  // 默认展开
      items: FAB_ITEMS
    }
  },
  computed: {
    fabItems() {
      return this.items.map(item => {
        const aiState = this.aiStates[item.type] || {}
        const disabled = this.isDisabled(item)
        return {
          ...item,
          label: this.getItemLabel(item),
          disabled,
          loading: !disabled && !!aiState.loading,
          done: !disabled && !!aiState.content && !aiState.loading
        }
      })
    }
  },
  methods: {
    isDisabled(item) {
      if (item.needsGrid && !this.gridContext) return true
      if (item.needsBranch && !this.branchContext) return true
      if (item.needsCompare && this.comparisonCount < 2) return true
      return false
    },
    getItemLabel(item) {
      if (item.type === 'grid' && this.gridContext) {
        return this.gridContext.blankSpot ? 'AI选址建议' : 'AI网格分析'
      }
      return item.label
    },
    toggleExpand() {
      this.expanded = !this.expanded
    },
    closeSpeedDial() {
      if (this.expanded) this.expanded = false
    },
    handleItemClick(item) {
      if (item.disabled) {
        if (item.needsGrid) this.$message.info('请先在地图上点击选择一个网格')
        else if (item.needsBranch) this.$message.info('请先在地图上点击选择一个网点')
        else if (item.needsCompare) this.$message.info('请在对比面板中选择至少 2 个网点')
        return
      }
      this.$emit('select', item.type)
    },
    onBodyClick(e) {
      if (!this.$el.contains(e.target)) {
        this.closeSpeedDial()
      }
    }
  },
  mounted() {
    document.body.addEventListener('click', this.onBodyClick)
  },
  beforeDestroy() {
    document.body.removeEventListener('click', this.onBodyClick)
  }
}
</script>

<style scoped>
/* ========== 容器 ========== */
.ai-fab-container {
  position: absolute;
  bottom: 120px;
  right: 24px;
  z-index: 10002;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.ai-fab-container.is-inline {
  position: relative;
  bottom: auto;
  right: auto;
  z-index: auto;
}

/* ========== 主按钮（品牌蓝渐变 + 霓虹光晕） ========== */
.ai-fab-main {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4f6ef6 0%, #06b6d4 100%);
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s cubic-bezier(0.22, 1, 0.36, 1);
  box-shadow:
    0 4px 20px rgba(79, 110, 246, 0.35),
    0 0 0 0 rgba(79, 110, 246, 0.3);
  animation: fab-pulse 2.5s ease-in-out infinite;
}
.ai-fab-main:hover {
  box-shadow: 0 6px 28px rgba(79, 110, 246, 0.5);
  transform: scale(1.08);
}
.ai-fab-main.open {
  animation: none;
  box-shadow: 0 4px 20px rgba(79, 110, 246, 0.35);
}
.ai-fab-main i {
  font-size: 22px;
  color: #fff;
  filter: drop-shadow(0 0 4px rgba(255, 255, 255, 0.4));
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}
.ai-fab-main.open i {
  transform: rotate(45deg);
}

@keyframes fab-pulse {
  0%, 100% { box-shadow: 0 4px 20px rgba(79, 110, 246, 0.35), 0 0 0 0 rgba(79, 110, 246, 0.3); }
  50% { box-shadow: 0 4px 20px rgba(79, 110, 246, 0.35), 0 0 0 16px rgba(79, 110, 246, 0); }
}

/* ========== 展开菜单（浅色玻璃态） ========== */
.ai-fab-speed-dial {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  margin-bottom: 12px;
}

.fab-item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  min-width: 150px;
  padding: 0 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(14px) saturate(170%);
  -webkit-backdrop-filter: blur(14px) saturate(170%);
  border: 1px solid rgba(79, 110, 246, 0.18);
  color: #333;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.22, 1, 0.36, 1);
  user-select: none;
  white-space: nowrap;
  box-shadow: 0 2px 10px rgba(79, 110, 246, 0.06);
  position: relative;
}
.fab-item:hover {
  background: #fff;
  border-color: var(--fab-item-color, rgba(79, 110, 246, 0.3));
  transform: translateX(-4px);
  box-shadow: 0 4px 16px rgba(79, 110, 246, 0.12);
}
.fab-item.disabled {
  opacity: 0.3;
  cursor: not-allowed;
  transform: none !important;
  box-shadow: none !important;
  border-color: rgba(79, 110, 246, 0.08);
}
.fab-item.disabled:hover {
  background: rgba(255, 255, 255, 0.96);
  border-color: rgba(79, 110, 246, 0.08);
}

.fab-item-accent {
  width: 4px;
  height: 18px;
  border-radius: 2px;
  flex-shrink: 0;
  transition: box-shadow 0.2s;
}
.fab-item:not(.disabled):hover .fab-item-accent {
  box-shadow: 0 0 8px var(--fab-item-color, rgba(79, 110, 246, 0.4));
}
.fab-item-icon {
  font-size: 15px;
  flex-shrink: 0;
  color: var(--fab-item-color, #666);
  transition: color 0.2s;
}
.fab-item-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #333;
  font-weight: 600;
}
.fab-item-status {
  flex-shrink: 0;
  min-width: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ========== 动画 ========== */
.fab-item-enter-active {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.fab-item-leave-active {
  transition: all 0.2s cubic-bezier(0.22, 1, 0.36, 1);
}
.fab-item-enter,
.fab-item-leave-to {
  opacity: 0;
  transform: translateX(16px) scale(0.9);
}
</style>
