<template>
  <div class="branch-scores">
    <div v-for="s in scores" :key="s.scoreCategory" class="score-row" :class="{ compact }">
      <span class="s-label">{{ label(s.scoreCategory) }}</span>
      <div class="s-bar-bg">
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
    label(c) { return { revenue: '营收', indicator: '业绩', customer: '客户', operation: '运营', overall: '综合' }[c] || c },
    val(s) { const v = s.categoryScore; return (typeof v === 'number' ? v.toFixed(2) : '-') },
    pct(s) { const v = s.categoryScore; return (typeof v === 'number' ? v * 100 : 0) + '%' },
    color(s) {
      const v = s.categoryScore
      if (typeof v !== 'number') return '#d9d9d9'
      return v >= 0.8 ? '#52c41a' : v >= 0.6 ? '#faad14' : '#f5222d'
    }
  }
}
</script>
<style scoped>
.score-row { display: flex; align-items: center; gap: 8px; padding: 6px 0; font-size: 13px; }
.score-row.compact { font-size: 12px; padding: 3px 0; }
.s-label { width: 36px; color: #666; }
.s-bar-bg { flex: 1; height: 8px; background: #f0f0f0; border-radius: 4px; overflow: hidden; }
.s-bar { height: 100%; border-radius: 4px; transition: width 0.3s; }
.s-value { width: 44px; text-align: right; color: #333; font-weight: 500; }
</style>
