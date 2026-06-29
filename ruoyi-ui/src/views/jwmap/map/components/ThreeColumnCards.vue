<template>
  <div class="three-columns">
    <div class="tcol">
      <div class="tcol-header pop"><i class="el-icon-user" /> {{ pop.name || '指标一' }}</div>
      <div class="tcol-num">{{ fmtScore(pop.score) }}</div>
      <div class="tcol-gap" :class="{ 'is-top': popGap === 0 }">
        <template v-if="popGap === 0">最高分</template>
        <template v-else>距最高 <span>{{ fmtGap(popGap) }}</span></template>
      </div>
      <div class="tcol-sub">{{ pop.count }} 项指标</div>
    </div>
    <div class="tcol">
      <div class="tcol-header ent"><i class="el-icon-office-building" /> {{ ent.name || '指标二' }}</div>
      <div class="tcol-num">{{ fmtScore(ent.score) }}</div>
      <div class="tcol-gap" :class="{ 'is-top': entGap === 0 }">
        <template v-if="entGap === 0">最高分</template>
        <template v-else>距最高 <span>{{ fmtGap(entGap) }}</span></template>
      </div>
      <div class="tcol-sub">{{ ent.count }} 项指标</div>
    </div>
    <div class="tcol">
      <div class="tcol-header biz"><i class="el-icon-s-shop" /> {{ biz.name || '指标三' }}</div>
      <div class="tcol-num">{{ fmtScore(biz.score) }}</div>
      <div class="tcol-gap" :class="{ 'is-top': bizGap === 0 }">
        <template v-if="bizGap === 0">最高分</template>
        <template v-else>距最高 <span>{{ fmtGap(bizGap) }}</span></template>
      </div>
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
    biz: { type: Object, default: () => ({ score: 0, count: 0 }) },
    popGap: { type: Number, default: 0 },
    entGap: { type: Number, default: 0 },
    bizGap: { type: Number, default: 0 }
  },
  methods: {
    fmtScore(v) { return typeof v === 'number' ? v.toFixed(2) : '-' },
    fmtGap(v) { return typeof v === 'number' ? v.toFixed(4) : '-' }
  }
}
</script>
<style scoped>
.three-columns { display: flex; gap: 8px; margin-bottom: 12px; }
.tcol {
  flex: 1; background: linear-gradient(180deg, #f8faff 0%, #f0f4ff 100%);
  border-radius: 8px; padding: 12px 8px; text-align: center;
  border: 1px solid rgba(79,110,246,0.08);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}
.tcol:hover { box-shadow: 0 3px 12px rgba(79,110,246,0.1); transform: translateY(-1px); }
.tcol-header { font-size: 13px; font-weight: 600; color: #444; display: flex; align-items: center; justify-content: center; gap: 3px; }
.tcol-header.pop i { color: #4f6ef6; }
.tcol-header.ent i { color: #52c41a; }
.tcol-header.biz i { color: #f0a050; }
.tcol-num { font-size: 20px; font-weight: 700; color: #232845; margin: 6px 0 3px; font-variant-numeric: tabular-nums; }
.tcol-sub { font-size: 13px; color: #555; margin-top: 2px; }
.tcol-gap { font-size: 12px; color: #e6a23c; margin: 0 0 3px; }
.tcol-gap span { font-weight: 600; font-variant-numeric: tabular-nums; }
.tcol-gap.is-top { color: #52c41a; font-weight: 500; }
</style>
