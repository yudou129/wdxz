<template>
  <div class="score-card" :class="size">
    <div class="score-label">{{ label }}</div>
    <div class="score-value">{{ typeof score === 'number' ? score.toFixed(2) : '-' }}</div>
    <div class="score-rank" v-if="rank != null">全市排名 <span class="rank-num">#{{ rank }}</span></div>
  </div>
</template>
<script>
export default {
  name: 'ScoreCard',
  props: { score: Number, rank: Number, label: String, size: { type: String, default: 'normal' } }
}
</script>
<style scoped>
.score-card {
  background: linear-gradient(135deg, #f0f4ff 0%, #f8faff 100%);
  border: 1px solid rgba(79, 110, 246, 0.12);
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(79, 110, 246, 0.06);
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}
.score-card:hover {
  box-shadow: 0 4px 16px rgba(79, 110, 246, 0.12);
}
.score-card.small {
  padding: 10px 14px;
  border-radius: 8px;
}
.score-label {
  font-size: 12px;
  color: #8c95a8;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
  text-transform: uppercase;
}
.score-value {
  font-size: 32px;
  font-weight: 700;
  color: #4f6ef6;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.5px;
}
.score-card.small .score-value {
  font-size: 24px;
}
.score-rank {
  font-size: 12px;
  color: #8c95a8;
  margin-top: 4px;
}
.rank-num {
  color: #4f6ef6;
  font-weight: 600;
}
</style>
