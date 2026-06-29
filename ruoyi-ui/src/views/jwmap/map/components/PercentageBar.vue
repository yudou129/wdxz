<template>
  <div class="pct-bar-row" @mouseenter="hovered = true" @mouseleave="hovered = false">
    <span class="pct-name">{{ name }}</span>
    <div class="pct-track">
      <div class="pct-fill" :style="{ width: pct + '%' }"></div>
    </div>
    <span class="pct-text">{{ pct }}%</span>
    <transition name="tip">
      <div v-if="hovered && value != null" class="pct-tooltip">
        {{ name }}: {{ typeof value === 'number' ? formatNum(value) : value }}
      </div>
    </transition>
  </div>
</template>
<script>
export default {
  name: 'PercentageBar',
  props: { name: String, pct: Number, value: [Number, String] },
  data() { return { hovered: false } },
  methods: {
    formatNum(v) { return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 }) }
  }
}
</script>
<style scoped>
.pct-bar-row { position: relative; display: flex; align-items: center; gap: 6px; padding: 4px 0; font-size: 13px; }
.pct-name { width: 72px; color: #555; flex-shrink: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pct-track { flex: 1; height: 6px; background: #eef0f5; border-radius: 3px; overflow: hidden; }
.pct-fill { height: 100%; border-radius: 3px; background: linear-gradient(90deg, #4f6ef6, #8fabff); transition: width 0.3s ease; }
.pct-text { width: 36px; text-align: right; color: #333; font-weight: 500; font-variant-numeric: tabular-nums; }
.pct-tooltip { position: absolute; bottom: 100%; left: 78px; background: rgba(0,0,0,0.78); color: #fff; padding: 3px 8px; border-radius: 4px; font-size: 11px; white-space: nowrap; z-index: 10; pointer-events: none; }
.tip-enter-active, .tip-leave-active { transition: opacity 0.15s; }
.tip-enter, .tip-leave-to { opacity: 0; }
</style>
