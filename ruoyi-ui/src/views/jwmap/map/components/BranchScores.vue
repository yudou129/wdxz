<template>
  <div class="branch-scores">
    <div v-for="s in items" :key="s.scoreCategory" class="score-row" :class="{ compact }">
      <span class="s-label">{{ label(s) }}</span>
      <div class="s-bar-track">
        <div class="s-bar" :style="{ width: pct(s), background: color(s) }" />
      </div>
      <div class="s-right">
        <span class="s-value">{{ val(s) }}</span>
        <span class="s-gap" v-if="s.gap && s.gap > 0">距最高 {{ fmtGap(s.gap) }}</span>
        <span class="s-top" v-else-if="s.topScore != null && s.gap === 0">最高分</span>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  name: 'BranchScores',
  props: { scores: Array, compact: Boolean },
  computed: {
    items() {
      return (this.scores || []).filter(s => s.scoreCategory !== 'overall')
    }
  },
  methods: {
    fmtGap(v) { return typeof v === 'number' ? v.toFixed(2) : '-' },
    label(s) {
      return s.categoryName || { revenue: '营收', indicator: '业绩', customer: '客户', operation: '运营', overall: '综合' }[s.scoreCategory] || s.scoreCategory
    },
    val(s) {
      const v = s.categoryScore
      return typeof v === 'number' ? v.toFixed(2) : '-'
    },
    pct(s) {
      const v = s.categoryScore
      return typeof v === 'number' ? Math.min(v * 100, 100) + '%' : '0%'
    },
    color(s) {
      if (typeof s.categoryScore !== 'number') return '#e2e4ea'
      const CAT_COLORS = {
        '业务运营': 'linear-gradient(90deg, #4f6ef6, #6b8af8)',
        '业绩表现': 'linear-gradient(90deg, #f0a050, #f6b870)',
        '客户发展': 'linear-gradient(90deg, #52c41a, #73d13d)',
        '经营情况': 'linear-gradient(90deg, #a855f7, #c084fc)',
      }
      return CAT_COLORS[this.label(s)] || 'linear-gradient(90deg, #4f6ef6, #6b8af8)'
    }
  }
}
</script>
<style scoped>
.score-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  font-size: 13px;
}
.score-row + .score-row {
  border-top: 1px solid rgba(79, 110, 246, 0.04);
}
.score-row.compact {
  font-size: 13px;
  padding: 4px 0;
}
.s-label {
  min-width: 48px;
  color: #444;
  font-weight: 500;
  flex-shrink: 0;
  font-size: 14px;
}
.s-bar-track {
  flex: 1;
  height: 10px;
  background: #eef0f5;
  border-radius: 5px;
  overflow: hidden;
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.08);
}
.s-bar {
  height: 100%;
  border-radius: 5px;
  transition: width 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94);
  position: relative;
}
.s-bar::after {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 45%;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 5px 5px 0 0;
}
.s-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.s-value {
  min-width: 44px;
  text-align: right;
  color: #232845;
  font-weight: 700;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}
.s-gap {
  font-size: 12px;
  color: #e6a23c;
  background: rgba(230, 162, 60, 0.1);
  padding: 0 6px;
  border-radius: 4px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  line-height: 18px;
}
.s-top {
  font-size: 12px;
  color: #52c41a;
  background: rgba(82, 196, 26, 0.1);
  padding: 0 6px;
  border-radius: 4px;
  font-weight: 600;
  white-space: nowrap;
  line-height: 18px;
}
</style>
