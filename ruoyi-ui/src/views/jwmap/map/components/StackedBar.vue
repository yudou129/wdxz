<template>
  <div class="stacked-block">
    <div class="stacked-header">{{ label }}</div>
    <!-- 分段标签行（窄段智能显示数值缩写） -->
    <div class="seg-labels">
      <span v-for="(seg, i) in segments" :key="i"
            class="seg-label"
            :title="seg.name"
            :style="{ width: seg.pct + '%' }">
        <template v-if="seg.pct < 10 && seg.displayValue">{{ seg.displayValue }}</template>
        <template v-else>{{ seg.name }}</template>
      </span>
    </div>
    <!-- 堆叠百分比条 -->
    <div class="stacked-track"
         @mouseenter="hovered = true"
         @mouseleave="hovered = false">
      <div v-for="(seg, i) in segments" :key="i"
           class="stacked-seg"
           :style="{ width: seg.pct + '%', background: seg.color }">
      </div>
      <!-- 悬浮展示全部指标信息 -->
      <transition name="tip">
        <div v-if="hovered" class="stacked-tooltip">
          <div v-for="(seg, i) in segments" :key="i" class="tooltip-row">
            <span class="tip-dot" :style="{ background: seg.color }"></span>
            <span class="tip-name">{{ seg.name }}</span>
            <span class="tip-val">{{ seg.displayValue }}</span>
            <span class="tip-pct">{{ seg.pct }}%</span>
          </div>
        </div>
      </transition>
    </div>
    <!-- 简约刻度线 -->
    <div class="seg-scale">
      <span class="seg-scale-tick"></span>
      <span class="seg-scale-tick"></span>
      <span class="seg-scale-tick"></span>
      <span class="seg-scale-tick"></span>
      <span class="seg-scale-tick"></span>
    </div>
  </div>
</template>
<script>
const COLORS = ['#4f6ef6', '#52c41a', '#f0a050', '#f56c6c', '#8b5cf6', '#06b6d4', '#84cc16', '#ef4444',
                '#6366f1', '#14b8a6', '#f59e0b', '#ec4899']

export default {
  name: 'StackedBar',
  props: {
    label: String,
    segments: { type: Array, default: () => [] }
  },
  data() {
    return { hovered: false }
  },
  watch: {
    segments: {
      immediate: true,
      handler(segs) {
        if (!segs) return
        for (let i = 0; i < segs.length; i++) {
          this.$set(segs[i], 'color', COLORS[i % COLORS.length])
          const v = segs[i].value
          this.$set(segs[i], 'displayValue', typeof v === 'number'
            ? Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) : String(v))
        }
      }
    }
  }
}
</script>
<style scoped>
.stacked-block { margin-bottom: 18px; }
.stacked-header {
  font-size: 13px; font-weight: 600; color: #444;
  margin-bottom: 6px; padding-left: 2px;
}
.seg-labels { display: flex; align-items: flex-end; margin-bottom: 3px; min-height: 20px; }
.seg-label {
  font-size: 12px; color: #444; text-align: center;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  line-height: 1.4; font-variant-numeric: tabular-nums;
}
.stacked-track {
  height: 22px; background: #eef0f5; border-radius: 4px;
  overflow: visible; display: flex; position: relative;
}
.stacked-seg { height: 100%; position: relative; transition: width 0.3s ease; overflow: hidden; }
.stacked-seg:first-child { border-radius: 4px 0 0 4px; }
.stacked-seg:last-child { border-radius: 0 4px 4px 0; }
.stacked-seg:only-child { border-radius: 4px; }
.stacked-seg::after {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 44%;
  background: linear-gradient(180deg, rgba(255,255,255,0.35) 0%, rgba(255,255,255,0) 100%);
  border-radius: 4px 4px 0 0; pointer-events: none;
}

.stacked-tooltip {
  position: absolute; bottom: calc(100% + 6px); left: -4px; right: -4px;
  background: rgba(255,255,255,0.97); border: 1px solid rgba(79,110,246,0.15);
  border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.1);
  padding: 8px 12px; z-index: 20; min-width: 200px;
}
.tooltip-row {
  display: flex; align-items: center; gap: 8px;
  padding: 3px 0; font-size: 13px;
}
.tip-dot {
  width: 10px; height: 10px; border-radius: 2px; flex-shrink: 0;
}
.tip-name { flex: 1; color: #444; font-weight: 500; }
.tip-val { width: 80px; text-align: right; color: #555; font-variant-numeric: tabular-nums; }
.tip-pct { width: 40px; text-align: right; color: #4f6ef6; font-weight: 600; }

/* 简约刻度线 */
.seg-scale {
  display: flex; justify-content: space-between; margin-top: 2px; padding: 0 1px;
  height: 4px;
}
.seg-scale-tick {
  display: block; width: 1px; height: 4px; background: #d0d3dc;
}
.seg-scale-tick:first-child, .seg-scale-tick:last-child {
  background: transparent;
}
.tip-enter-active, .tip-leave-active { transition: opacity 0.15s, transform 0.15s; }
.tip-enter, .tip-leave-to { opacity: 0; transform: translateY(4px); }
</style>
