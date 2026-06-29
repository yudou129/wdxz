<template>
  <transition name="quad-slide">
    <div v-if="visible" class="quadrant-panel">
      <div class="quadrant-header">
        <span class="quadrant-title"><i class="el-icon-s-data" /> 四象限综合分析</span>
        <el-button type="text" icon="el-icon-close" size="mini" class="close-btn" @click="$emit('close')" />
      </div>
      <div class="quadrant-body">
        <!-- 概览统计 -->
        <div class="qs-bar">
          <div class="qs-total">
            <span class="qs-num">{{ totalCount }}</span>
            <span class="qs-unit">个网点</span>
          </div>
          <div class="qs-dots">
            <span v-for="q in legendItems" :key="q.code" class="qs-dot-item">
              <span class="qs-dot" :style="{ background: q.color }"></span>
              {{ q.count }}
            </span>
          </div>
        </div>

        <!-- 散点图 -->
        <div ref="chartEl" class="quadrant-chart"></div>

        <!-- 象限卡片 -->
        <div class="qc-grid">
          <div v-for="q in legendItems" :key="q.code"
               class="qc-card"
               :style="{ borderTopColor: q.color }"
               @click="$emit('filter-quadrant', q.code)">
            <div class="qcc-top">
              <span class="qcc-code">{{ q.code }}</span>
              <span class="qcc-label">{{ q.shortLabel }}</span>
              <span class="qcc-count">{{ q.count }} 个网点</span>
              <span class="qcc-pct" :style="{ color: q.color }">{{ q.pct }}%</span>
            </div>
            <div class="qcc-bar"><div class="qcc-fill" :style="{ width: q.pct + '%', background: q.color }"></div></div>
          </div>
        </div>

        <!-- 底部洞察 -->
        <div class="qs-insight" v-if="insightText">
          <i class="el-icon-info" /> {{ insightText }}
        </div>

        <div class="qs-hint">提示：点击卡片可筛选对应象限网点，点击散点可定位网点</div>
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
    totalCount() {
      return (this.data && this.data.allData) ? this.data.allData.length : 0
    },
    legendItems() {
      if (!this.data || !this.data.quadrants) return []
      const total = this.totalCount || 1
      return Object.keys(QUADRANT_COLORS).map(code => ({
        code,
        label: QUADRANT_LABELS[code],
        shortLabel: QUADRANT_LABELS[code].replace('+', '·'),
        color: QUADRANT_COLORS[code],
        count: (this.data.quadrants[code] || []).length,
        pct: Math.round((this.data.quadrants[code] || []).length / total * 100)
      }))
    },
    insightText() {
      const items = this.legendItems
      if (!items.length) return ''
      const max = items.reduce((a, b) => a.count > b.count ? a : b)
      const min = items.reduce((a, b) => a.count < b.count ? a : b)
      if (max.count === 0) return ''
      return `"${max.shortLabel}" 聚集最多（${max.count} 个网点），"${min.shortLabel}" 最少（${min.count} 个）`
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
        seriesData[q].push([
          item.siteRank, item.branchRank,
          item.branchName, item.branchId,
          item.siteScore, item.branchScore
        ])
      }

      const option = {
        tooltip: {
          trigger: 'item',
          formatter(p) {
            const d = p.data
            if (!d || !d.length) return ''
            const qLabel = QUADRANT_LABELS[p.seriesName] || p.seriesName
            return `<div style="font-size:13px;line-height:1.6">
              <b style="font-size:14px;color:#232845">${d[2]}</b><br/>
              <span style="color:#888">选址</span> #${d[0]}
              <span style="color:#888;margin-left:10px">得分</span> ${d[4] != null ? Number(d[4]).toFixed(4) : '-'}<br/>
              <span style="color:#888">网点</span> #${d[1]}
              <span style="color:#888;margin-left:10px">得分</span> ${d[5] != null ? Number(d[5]).toFixed(4) : '-'}<br/>
              <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${QUADRANT_COLORS[p.seriesName] || '#999'};margin-right:4px;vertical-align:middle"></span>
              <span style="vertical-align:middle">${qLabel}</span>
            </div>`
          },
          backgroundColor: 'rgba(255,255,255,0.96)',
          borderColor: 'rgba(79,110,246,0.12)',
          borderWidth: 1,
          borderRadius: 8,
          padding: [10, 14],
          extraCssText: 'box-shadow: 0 4px 16px rgba(0,0,0,0.08);'
        },
        legend: { show: false },
        grid: { left: 56, right: 20, top: 20, bottom: 44 },
        xAxis: {
          name: '选址排名 →',
          nameLocation: 'center',
          nameGap: 30,
          nameTextStyle: { fontSize: 12, color: '#888', fontWeight: 500 },
          type: 'value', min: 1, max: total, inverse: true,
          splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
          axisLabel: { fontSize: 12, color: '#888', fontWeight: 500 }
        },
        yAxis: {
          name: '网点排名 →',
          nameLocation: 'center',
          nameGap: 38,
          nameTextStyle: { fontSize: 12, color: '#888', fontWeight: 500 },
          type: 'value', min: 1, max: total, inverse: true,
          splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
          axisLabel: { fontSize: 12, color: '#888', fontWeight: 500 }
        },
        series: [
          {
            type: 'scatter', name: 'Q1',
            data: seriesData['Q1'] || [],
            symbolSize: 10,
            itemStyle: { color: QUADRANT_COLORS['Q1'], opacity: 0.7 },
            emphasis: { scale: 1.8 },
            markArea: {
              silent: true,
              label: { show: true, position: 'inside', fontSize: 14, fontWeight: 700, color: 'rgba(79,110,246,0.25)' },
              data: [
                [{ xAxis: 1, yAxis: 1, label: { formatter: 'Q1' }, itemStyle: { color: 'rgba(79,110,246,0.15)', borderColor: '#4f6ef6', borderWidth: 0 } },
                 { xAxis: medianSite, yAxis: medianBranch }]
              ]
            }
          },
          {
            type: 'scatter', name: 'Q2',
            data: seriesData['Q2'] || [],
            symbolSize: 10,
            itemStyle: { color: QUADRANT_COLORS['Q2'], opacity: 0.7 },
            emphasis: { scale: 1.8 },
            markArea: {
              silent: true,
              label: { show: true, position: 'inside', fontSize: 14, fontWeight: 700, color: 'rgba(82,196,26,0.25)' },
              data: [
                [{ xAxis: total, yAxis: 1, label: { formatter: 'Q2' }, itemStyle: { color: 'rgba(82,196,26,0.15)' } },
                 { xAxis: medianSite, yAxis: medianBranch }]
              ]
            }
          },
          {
            type: 'scatter', name: 'Q3',
            data: seriesData['Q3'] || [],
            symbolSize: 10,
            itemStyle: { color: QUADRANT_COLORS['Q3'], opacity: 0.7 },
            emphasis: { scale: 1.8 },
            markArea: {
              silent: true,
              label: { show: true, position: 'inside', fontSize: 14, fontWeight: 700, color: 'rgba(240,160,80,0.25)' },
              data: [
                [{ xAxis: total, yAxis: total, label: { formatter: 'Q3' }, itemStyle: { color: 'rgba(240,160,80,0.15)' } },
                 { xAxis: medianSite, yAxis: medianBranch }]
              ]
            }
          },
          {
            type: 'scatter', name: 'Q4',
            data: seriesData['Q4'] || [],
            symbolSize: 10,
            itemStyle: { color: QUADRANT_COLORS['Q4'], opacity: 0.7 },
            emphasis: { scale: 1.8 },
            markArea: {
              silent: true,
              label: { show: true, position: 'inside', fontSize: 14, fontWeight: 700, color: 'rgba(245,108,108,0.25)' },
              data: [
                [{ xAxis: 1, yAxis: total, label: { formatter: 'Q4' }, itemStyle: { color: 'rgba(245,108,108,0.15)' } },
                 { xAxis: medianSite, yAxis: medianBranch }]
              ]
            }
          }
        ],
      }

      // 中位分隔线（加粗实线分隔四个象限）
      option.series.push({
        type: 'scatter', name: 'markLine', data: [], symbolSize: 0,
        markLine: {
          silent: true, symbol: 'none',
          lineStyle: { color: '#999', type: 'solid', width: 2 },
          label: { show: true, position: 'end', fontSize: 11, color: '#999',
            formatter(p) { return '中位 #' + p.value } },
          data: [
            { xAxis: medianSite },
            { yAxis: medianBranch }
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
  position: absolute; right: 12px; top: 100px; bottom: 12px; width: 520px;
  isolation: isolate; overflow: visible; border-radius: 10px;
  border: 1px solid rgba(255,255,255,0.28);
  background: rgba(255,255,255,0.92);
  backdrop-filter: blur(22px) saturate(170%) contrast(1.04);
  -webkit-backdrop-filter: blur(22px) saturate(170%) contrast(1.04);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.44), 0 8px 32px rgba(79,110,246,0.08), 0 1px 4px rgba(0,0,0,0.05);
  z-index: 1000; display: flex; flex-direction: column;
}
.quadrant-panel::before {
  content: ''; position: absolute; inset: 0; z-index: -1; border-radius: inherit;
  background: radial-gradient(circle at 20% 0%, rgba(255,255,255,0.48), transparent 34%),
              linear-gradient(90deg, rgba(255,255,255,0.16), transparent 42%, rgba(255,255,255,0.12));
  pointer-events: none;
}
.quadrant-header {
  padding: 14px 18px; border-bottom: 1px solid rgba(79,110,246,0.08);
  display: flex; justify-content: space-between; align-items: center; flex-shrink: 0;
}
.quadrant-title { font-weight: 700; font-size: 15px; color: #232845; display: flex; align-items: center; gap: 6px; }
.quadrant-title i { color: #4f6ef6; }
.close-btn { color: #666; }
.close-btn:hover { color: #4f6ef6; }
.quadrant-body { flex: 1; overflow-y: auto; padding: 12px 16px 16px; }
.quadrant-body::-webkit-scrollbar { width: 4px; }
.quadrant-body::-webkit-scrollbar-thumb { background: rgba(79,110,246,0.12); border-radius: 4px; }

/* ─── 概览统计 ─── */
.qs-bar {
  display: flex; align-items: center; gap: 16px;
  padding: 10px 14px; margin-bottom: 12px;
  background: linear-gradient(135deg, rgba(79,110,246,0.05), rgba(79,110,246,0.02));
  border-radius: 8px; border: 1px solid rgba(79,110,246,0.06);
}
.qs-total { display: flex; align-items: baseline; gap: 4px; }
.qs-num { font-size: 26px; font-weight: 700; color: #4f6ef6; font-variant-numeric: tabular-nums; line-height: 1; }
.qs-unit { font-size: 12px; color: #888; }
.qs-dots { display: flex; gap: 12px; margin-left: auto; }
.qs-dot-item {
  display: flex; align-items: center; gap: 4px;
  font-size: 13px; font-weight: 600; color: #555;
}
.qs-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }

/* ─── 图表 ─── */
.quadrant-chart { width: 100%; height: 380px; flex-shrink: 0; }

/* ─── 象限卡片 ─── */
.qc-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 10px;
}
.qc-card {
  background: #fff; border-radius: 6px; padding: 8px 10px;
  border: 1px solid rgba(0,0,0,0.04); border-top: 3px solid;
  cursor: pointer; transition: all 0.2s ease;
}
.qc-card:hover { transform: translateY(-1px); box-shadow: 0 4px 14px rgba(0,0,0,0.06); }
.qcc-top { display: flex; align-items: center; gap: 6px; }
.qcc-code { font-size: 15px; font-weight: 800; color: #232845; flex-shrink: 0; letter-spacing: 0.5px; }
.qcc-label { font-size: 13px; font-weight: 500; color: #232845; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qcc-count { font-size: 12px; font-weight: 600; color: #555; flex-shrink: 0; }
.qcc-pct { font-size: 18px; font-weight: 800; flex-shrink: 0; font-variant-numeric: tabular-nums; }
.qcc-bar { height: 3px; background: #eef0f5; border-radius: 2px; overflow: hidden; margin-top: 5px; }
.qcc-fill { height: 100%; border-radius: 2px; transition: width 0.6s ease; }

/* ─── 底部 ─── */
.qs-insight {
  margin-top: 12px; padding: 8px 12px; font-size: 12px; color: #666;
  background: rgba(79,110,246,0.04); border-radius: 6px;
  display: flex; align-items: center; gap: 6px;
}
.qs-insight i { color: #4f6ef6; font-size: 13px; }
.qs-hint { margin-top: 8px; font-size: 11px; color: #bbb; text-align: center; }

/* ─── 动画 ─── */
.quad-slide-enter-active, .quad-slide-leave-active {
  transition: transform 0.28s cubic-bezier(0.25,0.46,0.45,0.94), opacity 0.2s ease;
}
.quad-slide-enter, .quad-slide-leave-to { transform: translateX(20px); opacity: 0; }
</style>
