<template>
  <div class="grid-info-card">
    <div class="grid-code">{{ grid.gridCode || '-' }}</div>
    <div class="coord-row">
      <span>经度 {{ formatCoord(grid.longitude) }}</span>
      <span>纬度 {{ formatCoord(grid.latitude) }}</span>
    </div>
  </div>
</template>
<script>
export default {
  name: 'GridInfoCard',
  props: { grid: Object },
  methods: {
    formatCoord(v) { return typeof v === 'number' ? v.toFixed(6) : '-' }
  }
}
</script>
<style scoped>
.grid-info-card {
  background: linear-gradient(135deg, #f0f4ff 0%, #f8faff 100%);
  border: 1px solid rgba(79, 110, 246, 0.12);
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(79, 110, 246, 0.06);
}
.grid-code {
  font-size: 16px;
  font-weight: 700;
  color: #232845;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.grid-code::before {
  content: '网格';
  font-size: 11px;
  font-weight: 600;
  color: #4f6ef6;
  background: rgba(79, 110, 246, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
  line-height: 1.4;
}
.coord-row {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #555;
  font-family: 'Courier New', monospace;
}
</style>
