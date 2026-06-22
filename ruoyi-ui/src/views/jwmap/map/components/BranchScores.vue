<template>
  <div class="branch-scores">
    <div v-for="s in scores" :key="s.scoreCategory" class="score-row" :class="{ compact }">
      <span class="s-label">{{ label(s) }}</span>
      <div class="s-bar-track">
        <div class="s-bar" :style="{ width: pct(s), background: color(s) }" />
      </div>
      <span class="s-value">{{ val(s) }}</span>
    </div>
  </div>
</template>
<script>
export default {
  name: 'BranchScores',
  props: { scores: Array, compact: Boolean },
  methods: {
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
      const v = s.categoryScore
      if (typeof v !== 'number') return '#e2e4ea'
      if (v >= 0.8) return 'linear-gradient(90deg, #4f6ef6, #6b8af8)'
      if (v >= 0.6) return 'linear-gradient(90deg, #f0a050, #f6b870)'
      return 'linear-gradient(90deg, #e87060, #f09080)'
    }
  }
}
</script>
<style scoped>
.score-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  font-size: 13px;
}
.score-row.compact {
  font-size: 12px;
  padding: 4px 0;
}
.s-label {
  width: 40px;
  color: #6b7280;
  font-weight: 500;
  flex-shrink: 0;
}
.s-bar-track {
  flex: 1;
  height: 8px;
  background: #eef0f5;
  border-radius: 5px;
  overflow: hidden;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.06);
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
.s-value {
  width: 44px;
  text-align: right;
  color: #303651;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}
</style>
