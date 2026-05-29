<template>
  <div class="score-card" :class="size">
    <div class="score-label">{{ label }}</div>
    <div class="score-value">{{ typeof score === 'number' ? score.toFixed(2) : '-' }}</div>
    <div class="score-rank" v-if="rank != null">全市排名：#{{ rank }}</div>
  </div>
</template>
<script>
export default {
  name: 'ScoreCard',
  props: { score: Number, rank: Number, label: String, size: { type: String, default: 'normal' } }
}
</script>
<style scoped>
.score-card { background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 6px; padding: 12px; margin-bottom: 12px; }
.score-card.small { padding: 8px 12px; }
.score-label { font-size: 12px; color: #888; margin-bottom: 2px; }
.score-value { font-size: 28px; font-weight: 700; color: #52c41a; }
.score-card.small .score-value { font-size: 22px; }
.score-rank { font-size: 12px; color: #888; margin-top: 2px; }
</style>
