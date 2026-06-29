<template>
  <div class="rank-badge">
    <div class="rb-header">
      <span class="rb-label">{{ label }}</span>
    </div>
    <div class="rb-body">
      <span class="rb-number" v-if="rank > 0">{{ rank }}</span>
      <span class="rb-number na" v-else>-</span>
      <span class="rb-total" v-if="total > 0">/ {{ total }}</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'RankBadge',
  props: { label: String, rank: Number, total: Number }
}
</script>

<style scoped>
.rank-badge {
  text-align: center;
  padding: 8px 6px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 8px;
  flex: 1;
}
.rb-header {
  margin-bottom: 2px;
}
.rb-label {
  font-size: 12px;
  color: #888;
  font-weight: 500;
  letter-spacing: 0.5px;
}
.rb-body {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 2px;
  margin-bottom: 2px;
}
.rb-number {
  font-size: 22px;
  font-weight: 700;
  color: #4f6ef6;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}
.rb-number.na {
  color: #ccc;
  font-weight: 400;
  font-size: 20px;
}
.rb-total {
  font-size: 14px;
  color: #999;
  font-weight: 400;
}
</style>
