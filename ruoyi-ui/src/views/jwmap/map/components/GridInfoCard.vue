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
.grid-info-card { margin-bottom: 12px; }
.grid-code { font-size: 14px; font-weight: 700; color: #232845; margin-bottom: 4px; }
.coord-row { display: flex; gap: 16px; font-size: 12px; color: #888; font-family: 'Courier New', monospace; }
</style>
