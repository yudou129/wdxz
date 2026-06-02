<template>
  <div class="rank-badge">
    <span class="rank-label">{{ label }}</span>
    <span class="rank-value" v-if="rank > 0">#{{ rank }}<span class="rank-total">/{{ total }}</span></span>
    <span class="rank-value na" v-else>-</span>
  </div>
</template>
<script>
export default {
  name: 'RankBadge',
  props: { label: String, rank: Number, total: Number }
}
</script>
<style scoped>
.rank-badge { display: flex; justify-content: space-between; align-items: center; padding: 4px 0; }
.rank-label { font-size: 12px; color: #888; }
.rank-value { font-size: 13px; font-weight: 700; color: #4f6ef6; }
.rank-total { font-weight: 400; color: #aaa; font-size: 11px; }
.rank-value.na { color: #ccc; font-weight: 400; }
</style>
