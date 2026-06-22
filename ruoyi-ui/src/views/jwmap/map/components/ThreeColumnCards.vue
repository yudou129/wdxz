<template>
  <div class="three-columns">
    <div class="tcol">
      <div class="tcol-header pop"><i class="el-icon-user" /> {{ pop.name || '指标一' }}</div>
      <div class="tcol-num">{{ fmtScore(pop.score) }}</div>
      <div class="tcol-sub">{{ pop.count }} 项指标</div>
    </div>
    <div class="tcol">
      <div class="tcol-header ent"><i class="el-icon-office-building" /> {{ ent.name || '指标二' }}</div>
      <div class="tcol-num">{{ fmtScore(ent.score) }}</div>
      <div class="tcol-sub">{{ ent.count }} 项指标</div>
    </div>
    <div class="tcol">
      <div class="tcol-header biz"><i class="el-icon-s-shop" /> {{ biz.name || '指标三' }}</div>
      <div class="tcol-num">{{ fmtScore(biz.score) }}</div>
      <div class="tcol-sub">{{ biz.count }} 项指标</div>
    </div>
  </div>
</template>
<script>
export default {
  name: 'ThreeColumnCards',
  props: {
    pop: { type: Object, default: () => ({ score: 0, count: 0 }) },
    ent: { type: Object, default: () => ({ score: 0, count: 0 }) },
    biz: { type: Object, default: () => ({ score: 0, count: 0 }) }
  },
  methods: {
    fmtScore(v) { return typeof v === 'number' ? v.toFixed(2) : '-' }
  }
}
</script>
<style scoped>
.three-columns { display: flex; gap: 8px; margin-bottom: 12px; }
.tcol { flex: 1; background: #f8faff; border-radius: 8px; padding: 10px; text-align: center; }
.tcol-header { font-size: 11px; font-weight: 600; color: #666; display: flex; align-items: center; justify-content: center; gap: 3px; }
.tcol-header.pop i { color: #4f6ef6; }
.tcol-header.ent i { color: #52c41a; }
.tcol-header.biz i { color: #f0a050; }
.tcol-num { font-size: 20px; font-weight: 700; color: #232845; margin: 4px 0; font-variant-numeric: tabular-nums; }
.tcol-sub { font-size: 10px; color: #aaa; }
</style>
