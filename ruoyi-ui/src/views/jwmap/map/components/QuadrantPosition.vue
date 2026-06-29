<template>
  <div v-if="quadrant" class="quadrant-pos">
    <div class="qp-grid">
      <div class="qp-cell q2" :class="{ active: quadrant === 'Q2' }">
        <span class="qp-txt">低聚集</span>
        <span class="qp-sub">高能效</span>
      </div>
      <div class="qp-cell q1" :class="{ active: quadrant === 'Q1' }">
        <span class="qp-txt">高聚集</span>
        <span class="qp-sub">高能效</span>
      </div>
      <div class="qp-cell q3" :class="{ active: quadrant === 'Q3' }">
        <span class="qp-txt">低聚集</span>
        <span class="qp-sub">低能效</span>
      </div>
      <div class="qp-cell q4" :class="{ active: quadrant === 'Q4' }">
        <span class="qp-txt">高聚集</span>
        <span class="qp-sub">低能效</span>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'QuadrantPosition',
  props: { quadrant: String }
}
</script>

<style scoped>
.quadrant-pos {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.qp-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  width: 100%;
  max-width: 200px;
  aspect-ratio: 1;
  gap: 3px;
  border-radius: 12px;
  overflow: hidden;
}
.qp-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  background: #f5f6fa;
  border: 2px solid transparent;
  border-radius: 6px;
  transition: all 0.3s ease;
}
.qp-txt {
  font-size: 14px;
  font-weight: 600;
  color: #666;
  line-height: 1.3;
}
.qp-sub {
  font-size: 12px;
  font-weight: 400;
  color: #888;
  line-height: 1.2;
}
.qp-cell.active {
  transform: scale(1.03);
}
.qp-cell.active .qp-txt {
  font-weight: 700;
}
.qp-cell.active .qp-sub {
  font-weight: 500;
}
.qp-cell.q1.active { background: #e8edff; border-color: #4f6ef6; }
.qp-cell.q1.active .qp-txt { color: #4f6ef6; }
.qp-cell.q1.active .qp-sub { color: #7a94f8; }
.qp-cell.q2.active { background: #edfbe8; border-color: #52c41a; }
.qp-cell.q2.active .qp-txt { color: #389e0d; }
.qp-cell.q2.active .qp-sub { color: #73d13d; }
.qp-cell.q3.active { background: #fff7ee; border-color: #f0a050; }
.qp-cell.q3.active .qp-txt { color: #d46b08; }
.qp-cell.q3.active .qp-sub { color: #f6b870; }
.qp-cell.q4.active { background: #ffeeee; border-color: #f56c6c; }
.qp-cell.q4.active .qp-txt { color: #cf1322; }
.qp-cell.q4.active .qp-sub { color: #f89898; }
</style>
