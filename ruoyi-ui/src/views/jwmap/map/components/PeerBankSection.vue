<template>
  <div v-if="items.length" class="peer-section">
    <div class="section-title"><i class="el-icon-bank-card" /> 附近同业 <span class="ps-count">{{ items.length }}</span></div>
    <div v-for="(item, i) in items.slice(0, 5)" :key="i" class="ps-row">
      <span class="ps-name">{{ item.bankName || item.orgName }}</span>
      <span class="ps-dist">{{ item.distance }}km</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'PeerBankSection',
  props: { items: { type: Array, default: () => [] } }
}
</script>

<style scoped>
.peer-section {
  margin-bottom: 12px;
}
.section-title {
  font-size: 14px; font-weight: 600; color: #333; margin-bottom: 6px;
  display: flex; align-items: center; gap: 4px;
  border-left: 3px solid #4f6ef6; padding-left: 8px;
  background: linear-gradient(90deg, rgba(79,110,246,0.04) 0%, transparent 100%);
}
.section-title i { color: #4f6ef6; font-size: 13px; }
.ps-count {
  font-size: 11px; font-weight: 400; color: #888; margin-left: auto;
}
.ps-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  font-size: 13px;
  border-radius: 6px;
  transition: background 0.15s;
}
.ps-row + .ps-row {
  margin-top: 2px;
}
.ps-row:hover {
  background: rgba(79, 110, 246, 0.04);
}
.ps-name {
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  font-weight: 500;
}
.ps-dist {
  color: #4f6ef6;
  font-weight: 600;
  flex-shrink: 0;
  margin-left: 8px;
  font-size: 12px;
  background: rgba(79, 110, 246, 0.06);
  padding: 1px 8px;
  border-radius: 10px;
  font-variant-numeric: tabular-nums;
}
</style>
