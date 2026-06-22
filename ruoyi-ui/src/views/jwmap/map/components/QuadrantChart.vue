<template>
  <transition name="quad-slide">
    <div v-if="visible" class="quadrant-panel">
      <div class="quadrant-header">
        <span class="quadrant-title"><i class="el-icon-s-data" /> 四象限分析</span>
        <el-button type="text" icon="el-icon-close" size="mini" class="close-btn" @click="$emit('close')" />
      </div>
      <div class="quadrant-body">
        <div ref="chartEl" class="quadrant-chart"></div>
        <div class="quadrant-legend">
          <div v-for="q in legendItems" :key="q.code"
               class="legend-item"
               :style="{ borderLeftColor: q.color }"
               @click="$emit('filter-quadrant', q.code)">
            <span class="legend-dot" :style="{ background: q.color }"></span>
            <span class="legend-label">{{ q.label }}</span>
            <span class="legend-count">{{ q.count }}</span>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import * as echarts from 'echarts'

const QUADRANT_COLORS = {
  Q1: '#4f6ef6',
  Q2: '#52c41a',
  Q3: '#f0a050',
  Q4: '#f56c6c'
}

const QUADRANT_LABELS = {
  Q1: '高能效+高聚集',
  Q2: '高能效+低聚集',
  Q3: '低能效+低聚集',
  Q4: '低能效+高聚集'
}

export default {
  name: 'QuadrantChart',
  props: {
    visible: Boolean,
    data: { type: Object, default: null }
  },
  data() {
    return { chart: null }
  },
  computed: {
    legendItems() {
      if (!this.data || !this.data.quadrants) return []
      return Object.keys(QUADRANT_COLORS).map(code => ({
        code,
        label: QUADRANT_LABELS[code],
        color: QUADRANT_COLORS[code],
        count: (this.data.quadrants[code] || []).length
      }))
    }
  },
  watch: {
    data(val) {
      if (val && this.visible) this.$nextTick(() => this.renderChart())
    },
    visible(val) {
      if (val && this.data) this.$nextTick(() => this.renderChart())
    }
  },
  beforeDestroy() {
    if (this.chart) { this.chart.dispose(); this.chart = null }
  },
  methods: {
    renderChart() {
      if (!this.$refs.chartEl) return
      if (this.chart) this.chart.dispose()
      this.chart = echarts.init(this.$refs.chartEl)

      const allData = this.data.allData || []
      const total = allData.length || 1
      const medianSite = this.data.medianSiteScore || 0
      const medianBranch = this.data.medianBranchScore || 0

      const seriesData = {}
      for (const item of allData) {
        const q = item.quadrant || 'Q1'
        if (!seriesData[q]) seriesData[q] = []
        // [x=siteRank, y=branchRank, name, id, siteScore, branchScore]
        seriesData[q].push([
          item.siteRank, item.branchRank,
          item.branchName, item.branchId,
          item.siteScore, item.branchScore
        ])
      }

      const option = {
        tooltip: {
          trigger: 'item',
          formatter: function (p) {
            const d = p.data
            return '<b>' + d[2] + '</b><br/>'
              + '选址排名: #' + d[0] + ' / 得分: ' + (d[4] != null ? Number(d[4]).toFixed(4) : '-') + '<br/>'
              + '网点排名: #' + d[1] + ' / 得分: ' + (d[5] != null ? Number(d[5]).toFixed(4) : '-') + '<br/>'
              + '象限: ' + (QUADRANT_LABELS[p.seriesName] || p.seriesName)
          }
        },
        legend: { show: false },
        grid: { left: 50, right: 16, top: 16, bottom: 40 },
        xAxis: {
          name: '选址排名',
          nameLocation: 'center',
          nameGap: 28,
          type: 'value', min: 1, max: total, inverse: true,
          splitLine: { lineStyle: { color: '#f0f0f0' } },
          axisLabel: { fontSize: 10, color: '#888' }
        },
        yAxis: {
          name: '网点排名',
          nameLocation: 'center',
          nameGap: 36,
          type: 'value', min: 1, max: total, inverse: true,
          splitLine: { lineStyle: { color: '#f0f0f0' } },
          axisLabel: { fontSize: 10, color: '#888' }
        },
        series: [
          {
            type: 'scatter', name: 'Q1',
            data: seriesData['Q1'] || [],
            symbolSize: 8,
            itemStyle: { color: QUADRANT_COLORS['Q1'], opacity: 0.75 },
            emphasis: { scale: 1.5 }
          },
          {
            type: 'scatter', name: 'Q2',
            data: seriesData['Q2'] || [],
            symbolSize: 8,
            itemStyle: { color: QUADRANT_COLORS['Q2'], opacity: 0.75 },
            emphasis: { scale: 1.5 }
          },
          {
            type: 'scatter', name: 'Q3',
            data: seriesData['Q3'] || [],
            symbolSize: 8,
            itemStyle: { color: QUADRANT_COLORS['Q3'], opacity: 0.75 },
            emphasis: { scale: 1.5 }
          },
          {
            type: 'scatter', name: 'Q4',
            data: seriesData['Q4'] || [],
            symbolSize: 8,
            itemStyle: { color: QUADRANT_COLORS['Q4'], opacity: 0.75 },
            emphasis: { scale: 1.5 }
          }
        ],
      }

      // 添加中位线标记线（标注中位数排名）
      option.series.push({
        type: 'scatter', name: 'markLine', data: [], symbolSize: 0,
        markLine: {
          silent: true,
          symbol: 'none',
          label: { show: true, position: 'end', fontSize: 10, color: '#999',
            formatter: function (p) { return '中位:#' + p.value } },
          data: [
            { xAxis: medianSite, lineStyle: { color: '#bbb', type: 'dashed', width: 1 } },
            { yAxis: medianBranch, lineStyle: { color: '#bbb', type: 'dashed', width: 1 } }
          ]
        }
      })

      this.chart.setOption(option)

      this.chart.on('click', (params) => {
        if (params.data && params.data.length >= 4) {
          this.$emit('item-click', {
            branchId: params.data[3],
            branchName: params.data[2],
            quadrant: params.seriesName
          })
        }
      })
    }
  }
}
</script>

<style scoped>
.quadrant-panel {
  position: absolute;
  right: 12px;
  top: 60px;
  width: 440px;
  isolation: isolate;
  overflow: visible;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.28), rgba(255, 255, 255, 0.08)),
    rgba(255, 255, 255, 0.10);
  backdrop-filter: blur(22px) saturate(170%) contrast(1.04);
  -webkit-backdrop-filter: blur(22px) saturate(170%) contrast(1.04);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.44),
    inset 0 -1px 0 rgba(255, 255, 255, 0.10),
    0 8px 32px rgba(79, 110, 246, 0.08),
    0 1px 4px rgba(0, 0, 0, 0.05);
  z-index: 1000;
}
.quadrant-panel::before {
  content: '';
  position: absolute; inset: 0; z-index: -1; border-radius: inherit;
  background:
    radial-gradient(circle at 20% 0%, rgba(255,255,255,0.48), transparent 34%),
    linear-gradient(90deg, rgba(255,255,255,0.16), transparent 42%, rgba(255,255,255,0.12));
  pointer-events: none;
}
.quadrant-header {
  padding: 12px 14px; border-bottom: 1px solid rgba(79,110,246,0.08);
  display: flex; justify-content: space-between; align-items: center;
}
.quadrant-title { font-weight: 700; font-size: 13px; color: #232845; }
.quadrant-title i { color: #4f6ef6; margin-right: 4px; }
.close-btn { color: #8c95a8; }
.close-btn:hover { color: #4f6ef6; }
.quadrant-body { padding: 8px 0; }
.quadrant-chart { width: 100%; height: 320px; }
.quadrant-legend { display: flex; flex-wrap: wrap; gap: 4px; padding: 4px 14px 10px; }
.legend-item {
  flex: 1; min-width: 100px; cursor: pointer;
  padding: 6px 8px; border-left: 3px solid;
  border-radius: 0 4px 4px 0;
  transition: background 0.15s;
  display: flex; align-items: center; gap: 4px;
}
.legend-item:hover { background: rgba(79,110,246,0.04); }
.legend-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.legend-label { font-size: 11px; color: #666; flex: 1; }
.legend-count { font-size: 12px; font-weight: 600; color: #333; }
.quad-slide-enter-active, .quad-slide-leave-active {
  transition: transform 0.28s cubic-bezier(0.25,0.46,0.45,0.94), opacity 0.2s ease;
}
.quad-slide-enter, .quad-slide-leave-to { transform: translateY(-12px); opacity: 0; }
</style>
