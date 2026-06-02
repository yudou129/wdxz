<template>
  <div v-if="quadrant" class="quadrant-pos">
    <span class="qp-label">四象限位置</span>
    <div class="qp-grid">
      <div class="qp-cell q2" :class="{ active: quadrant === 'Q2' }"><span>Q2</span></div>
      <div class="qp-cell q1" :class="{ active: quadrant === 'Q1' }"><span>Q1</span></div>
      <div class="qp-cell q3" :class="{ active: quadrant === 'Q3' }"><span>Q3</span></div>
      <div class="qp-cell q4" :class="{ active: quadrant === 'Q4' }"><span>Q4</span></div>
    </div>
    <div class="qp-desc">{{ desc }}</div>
  </div>
</template>
<script>
const LABELS = { Q1: '高能效 + 高聚集', Q2: '高能效 + 低聚集', Q3: '低能效 + 低聚集', Q4: '低能效 + 高聚集' }
export default {
  name: 'QuadrantPosition',
  props: { quadrant: String },
  computed: { desc() { return LABELS[this.quadrant] || '-' } }
}
</script>
<style scoped>
.quadrant-pos { margin-bottom: 12px; }
.qp-label { font-size: 12px; color: #888; display: block; margin-bottom: 4px; }
.qp-grid { display: grid; grid-template-columns: 1fr 1fr; width: 80px; height: 80px; gap: 2px; border-radius: 6px; overflow: hidden; }
.qp-cell { display: flex; align-items: center; justify-content: center; font-size: 10px; font-weight: 600; color: #ccc; background: #f5f6fa; }
.qp-cell.q1.active { background: #e8edff; color: #4f6ef6; }
.qp-cell.q2.active { background: #edfbe8; color: #52c41a; }
.qp-cell.q3.active { background: #fff7ee; color: #f0a050; }
.qp-cell.q4.active { background: #ffeeee; color: #f56c6c; }
.qp-desc { font-size: 12px; color: #4f6ef6; font-weight: 600; margin-top: 4px; }
</style>
