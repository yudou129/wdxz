<template>
  <div class="pillar-radar" ref="radarWrap">
    <div ref="radarEl" :style="{ height: chartHeight + 'px' }"></div>
    <div class="pr-table">
      <div class="pr-row pr-header">
        <span class="pr-dim">维度</span>
        <span class="pr-val">当前得分</span>
        <span class="pr-val">区县最高</span>
        <span class="pr-val">全市最高</span>
      </div>
      <div v-for="k in pillarKeys" :key="k" class="pr-row">
        <span class="pr-dim">{{ dimName(k) }}</span>
        <span class="pr-val pr-my">{{ fmtScore(pillarScore(k)) }}</span>
        <span class="pr-val pr-dist">{{ fmtScore(districtScore(k)) }}</span>
        <span class="pr-val pr-city">{{ fmtScore(cityScore(k)) }}</span>
      </div>
    </div>
    <div class="pr-footer" @click="$emit('view-detail')">
      <i class="el-icon-data-analysis" /> 查看全部指标 <i class="el-icon-arrow-right" />
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'

const RADAR_COLORS = ['#4f6ef6', '#52c41a', '#f0a050']
const SERIES_NAMES = ['当前网格', '行政区最高', '全市最高']

export default {
  name: 'PillarRadar',
  props: {
    pillar: { type: Object, default: () => ({}) },
    pillarGap: { type: Object, default: () => ({}) },
    compact: { type: Boolean, default: false }
  },
  data() { return { chart: null } },
  computed: {
    chartHeight() { return this.compact ? 250 : 300 },
    pillarKeys() { return ['population', 'enterprise', 'business'] }
  },
  watch: {
    pillar: { deep: true, handler: 'renderChart' },
    pillarGap: { deep: true, handler: 'renderChart' },
    compact() { this.$nextTick(this.renderChart) }
  },
  mounted() { this.$nextTick(this.renderChart) },
  beforeDestroy() { this.destroyChart() },
  methods: {
    pillarScore(k) {
      const v = this.pillar && this.pillar[k] && this.pillar[k].score
      return v != null ? v : 0
    },
    districtScore(k) {
      return (this.pillarGap && this.pillarGap[k] && this.pillarGap[k].maxDistrict) || 0
    },
    cityScore(k) {
      return (this.pillarGap && this.pillarGap[k] && this.pillarGap[k].maxCity) || 0
    },
    dimName(k) {
      return (this.pillar && this.pillar[k] && this.pillar[k].name) || k
    },
    fmtScore(v) { return typeof v === 'number' ? v.toFixed(3) : '0.000' },
    renderChart() {
      const el = this.$refs.radarEl
      if (!el || el.offsetWidth === 0) { this.$nextTick(() => this.renderChart()); return }
      this.destroyChart()
      this.chart = echarts.init(el)

      const pk = this.pillarKeys
      const p = this.pillar || {}
      const pg = this.pillarGap || {}

      const indicators = pk.map(k => {
        const name = (p[k] && p[k].name) || k
        const axisMax = Math.max(this.cityScore(k), 0.01)
        return { name, max: axisMax }
      })

      const myValues = pk.map(k => this.pillarScore(k))
      const distValues = pk.map(k => this.districtScore(k))
      const cityValues = pk.map(k => this.cityScore(k))

      const option = {
        tooltip: { trigger: 'item' },
        legend: {
          data: SERIES_NAMES,
          right: 0, top: 0,
          orient: 'vertical',
          itemWidth: 14, itemHeight: 10,
          itemGap: 6,
          textStyle: { fontSize: 12, color: '#555' }
        },
        radar: {
          indicator: indicators,
          center: ['50%', '55%'],
          radius: this.compact ? '55%' : '72%',
          axisName: { color: '#666', fontSize: 13, fontWeight: 600 },
          splitArea: { areaStyle: { color: ['rgba(79,110,246,0.02)', 'rgba(79,110,246,0.04)'] } },
          splitLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } }
        },
        series: [{
          type: 'radar',
          data: [
            {
              value: myValues, name: SERIES_NAMES[0],
              lineStyle: { color: RADAR_COLORS[0], width: 2.5 },
              areaStyle: { color: RADAR_COLORS[0], opacity: 0.18 },
              itemStyle: { color: RADAR_COLORS[0] },
              symbol: 'circle', symbolSize: 5
            },
            {
              value: distValues, name: SERIES_NAMES[1],
              lineStyle: { color: RADAR_COLORS[1], width: 1.5, type: 'dashed' },
              areaStyle: { color: 'transparent' },
              itemStyle: { color: RADAR_COLORS[1] },
              symbol: 'diamond', symbolSize: 4
            },
            {
              value: cityValues, name: SERIES_NAMES[2],
              lineStyle: { color: RADAR_COLORS[2], width: 1.5, type: 'dashed' },
              areaStyle: { color: 'transparent' },
              itemStyle: { color: RADAR_COLORS[2] },
              symbol: 'diamond', symbolSize: 4
            }
          ]
        }]
      }

      this.chart.setOption(option)
    },
    destroyChart() {
      if (this.chart) { this.chart.dispose(); this.chart = null }
    }
  }
}
</script>

<style scoped>
.pillar-radar {
  background: rgba(255,255,255,0.5);
  border-radius: 8px;
  padding: 2px 8px 6px;
  margin-bottom: 6px;
  width: 100%;
}
.pillar-radar > div:first-child {
  width: 100%;
  margin-bottom: -40px;
}
.pr-table {
  font-size: 13px;
}
.pr-row {
  display: flex; align-items: center;
  padding: 3px 8px;
  border-bottom: 1px solid rgba(0,0,0,0.04);
}
.pr-header {
  font-weight: 600; color: #666; font-size: 13px;
  border-bottom: 1px solid rgba(0,0,0,0.08);
}
.pr-dim { flex: 1; color: #333; }
.pr-header .pr-dim { color: #666; }
.pr-val { flex: 1; text-align: right; font-variant-numeric: tabular-nums; font-weight: 500; }
.pr-my { color: #4f6ef6; }
.pr-dist { color: #52c41a; }
.pr-city { color: #f0a050; }
.pr-footer {
  margin-top: 4px; padding: 4px 8px; font-size: 13px;
  color: #4f6ef6; cursor: pointer; text-align: center; border-radius: 6px;
  transition: background 0.2s; display: flex; align-items: center; justify-content: center; gap: 3px;
}
.pr-footer:hover { background: rgba(79, 110, 246, 0.06); }
.pr-footer i { font-size: 13px; }
</style>
