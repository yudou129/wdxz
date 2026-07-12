<template>
  <div class="grid-info-card">
    <div class="gic-top">
      <div class="gic-code">
        <span class="gic-tag">网格</span>
        {{ grid.gridCode || '-' }}
      </div>
      <div class="gic-score">
        <span class="gic-score-label">选址得分</span>
        <span class="gic-score-val">{{ fmtScore(score) }}</span>
      </div>
    </div>
    <div class="coord-row">
      <span>经度 {{ formatCoord(grid.longitude) }}</span>
      <span>纬度 {{ formatCoord(grid.latitude) }}</span>
    </div>
    <div v-if="grid.address" class="gic-address">
      📍 {{ grid.address }}
    </div>
  </div>
</template>
<script>
export default {
  name: 'GridInfoCard',
  props: {
    grid: Object,
    score: { type: Number, default: 0 }
  },
  methods: {
    formatCoord(v) { return typeof v === 'number' ? v.toFixed(6) : '-' },
    fmtScore(v) { return typeof v === 'number' ? v.toFixed(3) : '0.000' }
  }
}
</script>
<style scoped>
.grid-info-card {
  background: linear-gradient(135deg, #f0f4ff 0%, #f8faff 100%);
  border: 1px solid rgba(79, 110, 246, 0.12);
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: 0 2px 8px rgba(79, 110, 246, 0.06);
}
.gic-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.gic-code {
  font-size: 15px;
  font-weight: 700;
  color: #232845;
  display: flex;
  align-items: center;
  gap: 8px;
}
.gic-tag {
  font-size: 11px;
  font-weight: 600;
  color: #4f6ef6;
  background: rgba(79, 110, 246, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
  line-height: 1.4;
}
.gic-score {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.gic-score-label {
  font-size: 12px;
  color: #666;
  font-weight: 500;
}
.gic-score-val {
  font-size: 22px;
  font-weight: 700;
  color: #4f6ef6;
  font-variant-numeric: tabular-nums;
}
.coord-row {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #888;
  font-family: 'Courier New', monospace;
}
.gic-address {
  margin-top: 8px;
  font-size: 13px;
  color: #555;
  line-height: 1.5;
  padding: 6px 8px;
  background: rgba(79, 110, 246, 0.05);
  border-radius: 6px;
  border: 1px solid rgba(79, 110, 246, 0.08);
  word-break: break-all;
}
</style>
