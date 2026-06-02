<template>
  <div v-if="items.length" class="peer-section">
    <div class="section-title"><i class="el-icon-bank-card" /> 附近同业 ({{ items.length }})</div>
    <div v-for="(item, i) in items.slice(0, 5)" :key="i" class="peer-row">
      <span class="peer-name">{{ item.bankName || item.orgName }}</span>
      <span class="peer-dist">{{ item.distance }}km</span>
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
.peer-section { margin-top: 10px; padding-top: 10px; border-top: 1px solid rgba(79,110,246,0.08); }
.section-title { font-size: 12px; font-weight: 600; color: #556; margin-bottom: 6px; display: flex; align-items: center; gap: 4px; }
.section-title i { color: #4f6ef6; font-size: 13px; }
.peer-row { display: flex; justify-content: space-between; padding: 3px 0; font-size: 12px; }
.peer-name { color: #444; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.peer-dist { color: #4f6ef6; font-weight: 500; flex-shrink: 0; margin-left: 8px; }
</style>
