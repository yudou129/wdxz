<template>
  <transition name="quad-slide">
    <div v-if="visible" class="quadrant-panel">
      <div class="quadrant-header">
        <span class="quadrant-title"><i class="el-icon-s-data" /> 四象限综合分析</span>
        <div class="header-right">
          <el-select v-if="years && years.length" :value="year" size="mini" style="width:76px"
                     @change="$emit('year-change', $event)">
            <el-option v-for="y in years" :key="y" :label="String(y)" :value="y" />
          </el-select>
          <el-button type="text" icon="el-icon-close" size="mini" class="close-btn" @click="$emit('close')" />
        </div>
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

        <div class="qs-hint">提示：点击卡片可筛选对应象限网点，点击散点可定位网点。在网点详情中可使用AI深度分析。</div>
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
    data: { type: Object, default: null },
    year: { type: Number, default: null },
    years: { type: Array, default: () => [] }
  },
  data() {
    return { chart: null }
  },
  computed: {
    branchesLoaded() {
      return this.data && this.data.allData && this.data.allData.length > 0
    },
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
    /**
     * 生成分段刻度数组：分界线左侧密集、右侧稀疏，让四象限视觉均衡
     * @param {number} cutoff - 分界值
     * @param {number} max - 坐标轴最大值
     * @param {number} count - 每侧刻度数量
     * @returns {number[]} 升序刻度数组（包含1、cutoff、max）
     */
    generateThresholdLabels(cutoff, max, count = 5) {
      if (cutoff <= 1 || cutoff >= max) {
        // 极端情况退化为均匀刻度
        const step = Math.max(1, Math.round(max / (count * 2)))
        const labels = []
        for (let i = 1; i <= max; i += step) labels.push(i)
        if (labels[labels.length - 1] !== max) labels.push(max)
        return labels
      }

      const labels = new Set()

      // 左侧：从 1 到 cutoff，取 count 个点
      for (let i = 0; i < count; i++) {
        const val = 1 + (cutoff - 1) * (i / (count - 1))
        labels.add(Math.round(val))
      }

      // 右侧：从 cutoff 到 max，取 count 个点
      for (let i = 0; i < count; i++) {
        const val = cutoff + (max - cutoff) * (i / (count - 1))
        labels.add(Math.round(val))
      }

      // 确保包含边界
      labels.add(1)
      labels.add(cutoff)
      labels.add(max)

      return Array.from(labels).sort((a, b) => a - b)
    },

    /**
     * 将排名值映射到 category 轴的插值索引位置
     * 例如 labels=[1,2,3,4,5,250,500,750,3000], rank=3 → 索引 2（第三个刻度）
     * rank=125 → 在 5(索引4) 和 250(索引5) 之间插值 → 约 4.49
     */
    rankToIndex(rank, labels) {
      if (rank <= labels[0]) return 0
      if (rank >= labels[labels.length - 1]) return labels.length - 1
      for (let i = 0; i < labels.length - 1; i++) {
        if (rank >= labels[i] && rank < labels[i + 1]) {
          if (labels[i + 1] === labels[i]) return i
          return i + (rank - labels[i]) / (labels[i + 1] - labels[i])
        }
      }
      return labels.length - 1
    },

    escapeHtml(str) {
      const div = document.createElement('div')
      div.appendChild(document.createTextNode(str || ''))
      return div.innerHTML
    },
    renderChart() {
      if (!this.$refs.chartEl) return
      if (this.chart) this.chart.dispose()
      this.chart = echarts.init(this.$refs.chartEl)

      const escapeHtml = this.escapeHtml.bind(this)
      const rankToIdx = this.rankToIndex.bind(this)

      const allData = this.data.allData || []
      const totalBranch = allData.length || 1
      const totalGrid = this.data.totalGrids || totalBranch
      const medianSite = this.data.medianSiteRank || Math.round(totalGrid / 2)
      const medianBranch = this.data.medianBranchRank || Math.round(totalBranch / 2)

      // 分段刻度：分界线左侧密集、右侧稀疏，使四象限视觉均衡
      const siteLabels = this.generateThresholdLabels(medianSite, totalGrid, 5)
      const branchLabels = this.generateThresholdLabels(medianBranch, totalBranch, 5)

      // 将 medianSite/medianBranch 映射到 category 轴的索引位置
      const medianSiteIdx = rankToIdx(medianSite, siteLabels)
      const medianBranchIdx = rankToIdx(medianBranch, branchLabels)
      const totalSiteIdx = siteLabels.length - 1
      const totalBranchIdx = branchLabels.length - 1

      const seriesData = {}
      for (const item of allData) {
        const q = item.quadrant || 'Q1'
        if (!seriesData[q]) seriesData[q] = []
        // [categoryX, categoryY, branchName, branchId, siteScore, branchScore, siteRank(原始), branchRank(原始)]
        seriesData[q].push([
          rankToIdx(item.siteRank, siteLabels),
          rankToIdx(item.branchRank, branchLabels),
          item.branchName, item.branchId,
          item.siteScore, item.branchScore,
          item.siteRank, item.branchRank
        ])
      }

      const option = {
        tooltip: {
          trigger: 'item',
          formatter(p) {
            const d = p.data
            if (!d || !d.length) return ''
            const qLabel = QUADRANT_LABELS[p.seriesName] || p.seriesName
            // d[6]=原始siteRank, d[7]=原始branchRank
            const sr = d[6] != null ? d[6] : d[0]
            const br = d[7] != null ? d[7] : d[1]
            return `<div style="font-size:13px;line-height:1.6">
              <b style="font-size:14px;color:#232845">${escapeHtml(d[2])}</b><br/>
              <span style="color:#888">选址排名</span> #${sr}
              <span style="color:#888;margin-left:10px">得分</span> ${d[4] != null ? Number(d[4]).toFixed(4) : '-'}<br/>
              <span style="color:#888">网点排名</span> #${br}
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
        grid: { left: 60, right: 24, top: 24, bottom: 48 },
        xAxis: {
          name: '选址评分排名 →（全城网格排名）',
          nameLocation: 'center',
          nameGap: 30,
          nameTextStyle: { fontSize: 12, color: '#888', fontWeight: 500 },
          type: 'category',
          data: siteLabels.map(String),
          inverse: true,
          boundaryGap: false,
          splitLine: { show: true, lineStyle: { color: '#f0f0f0', type: 'dashed' } },
          axisLabel: { fontSize: 12, color: '#888', fontWeight: 500, interval: 0 }
        },
        yAxis: {
          name: '网点效能排名 →（全城网点排名）',
          nameLocation: 'center',
          nameGap: 38,
          nameTextStyle: { fontSize: 12, color: '#888', fontWeight: 500 },
          type: 'category',
          data: branchLabels.map(v => '#' + v),
          inverse: true,
          boundaryGap: false,
          splitLine: { show: true, lineStyle: { color: '#f0f0f0', type: 'dashed' } },
          axisLabel: { fontSize: 12, color: '#888', fontWeight: 500, interval: 0 }
        },
        series: [
          {
            type: 'scatter', name: 'Q1',
            data: seriesData['Q1'] || [],
            symbolSize: 11,
            itemStyle: { color: QUADRANT_COLORS['Q1'], opacity: 0.7 },
            emphasis: { scale: 1.8 },
            markArea: {
              silent: true,
              label: { show: true, position: 'inside', fontSize: 16, fontWeight: 700, color: 'rgba(79,110,246,0.25)' },
              data: [
                [{ xAxis: 0, yAxis: 0, label: { formatter: 'Q1' }, itemStyle: { color: 'rgba(79,110,246,0.15)', borderColor: '#4f6ef6', borderWidth: 0 } },
                 { xAxis: medianSiteIdx, yAxis: medianBranchIdx }]
              ]
            }
          },
          {
            type: 'scatter', name: 'Q2',
            data: seriesData['Q2'] || [],
            symbolSize: 11,
            itemStyle: { color: QUADRANT_COLORS['Q2'], opacity: 0.7 },
            emphasis: { scale: 1.8 },
            markArea: {
              silent: true,
              label: { show: true, position: 'inside', fontSize: 16, fontWeight: 700, color: 'rgba(82,196,26,0.25)' },
              data: [
                [{ xAxis: totalSiteIdx, yAxis: 0, label: { formatter: 'Q2' }, itemStyle: { color: 'rgba(82,196,26,0.15)' } },
                 { xAxis: medianSiteIdx, yAxis: medianBranchIdx }]
              ]
            }
          },
          {
            type: 'scatter', name: 'Q3',
            data: seriesData['Q3'] || [],
            symbolSize: 11,
            itemStyle: { color: QUADRANT_COLORS['Q3'], opacity: 0.7 },
            emphasis: { scale: 1.8 },
            markArea: {
              silent: true,
              label: { show: true, position: 'inside', fontSize: 16, fontWeight: 700, color: 'rgba(240,160,80,0.25)' },
              data: [
                [{ xAxis: totalSiteIdx, yAxis: totalBranchIdx, label: { formatter: 'Q3' }, itemStyle: { color: 'rgba(240,160,80,0.15)' } },
                 { xAxis: medianSiteIdx, yAxis: medianBranchIdx }]
              ]
            }
          },
          {
            type: 'scatter', name: 'Q4',
            data: seriesData['Q4'] || [],
            symbolSize: 11,
            itemStyle: { color: QUADRANT_COLORS['Q4'], opacity: 0.7 },
            emphasis: { scale: 1.8 },
            markArea: {
              silent: true,
              label: { show: true, position: 'inside', fontSize: 16, fontWeight: 700, color: 'rgba(245,108,108,0.25)' },
              data: [
                [{ xAxis: 0, yAxis: totalBranchIdx, label: { formatter: 'Q4' }, itemStyle: { color: 'rgba(245,108,108,0.15)' } },
                 { xAxis: medianSiteIdx, yAxis: medianBranchIdx }]
              ]
            }
          }
        ],
      }

      // 分界线 markLine（用 category 索引位置）
      option.series.push({
        type: 'scatter', name: 'markLine', data: [], symbolSize: 0,
        markLine: {
          silent: true, symbol: 'none',
          lineStyle: { color: '#232845', type: 'solid', width: 2.5 },
          label: {
            show: true, fontSize: 11, color: '#232845', fontWeight: 600,
            backgroundColor: 'rgba(255,255,255,0.92)',
            padding: [2, 8],
            borderRadius: 4,
            borderColor: 'rgba(35,40,69,0.15)',
            borderWidth: 1,
            formatter(p) {
              return '#' + Math.round(p.value)
            }
          },
          data: [
            { xAxis: medianSiteIdx, label: { position: 'start' } },
            { yAxis: medianBranchIdx, label: { position: 'end' } }
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
  position: absolute; right: 16px; top: 80px; bottom: 16px; width: min(680px, 92vw);
  isolation: isolate; overflow: visible; border-radius: 12px;
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
  padding: 16px 20px; border-bottom: 1px solid rgba(79,110,246,0.08);
  display: flex; justify-content: space-between; align-items: center; flex-shrink: 0;
}
.quadrant-title { font-weight: 700; font-size: 16px; color: #232845; display: flex; align-items: center; gap: 6px; }
.quadrant-title i { color: #4f6ef6; }
.close-btn { color: #444; }
.close-btn:hover { color: #4f6ef6; }
.quadrant-body { flex: 1; overflow-y: auto; padding: 16px 20px 20px; }
.quadrant-body::-webkit-scrollbar { width: 4px; }
.quadrant-body::-webkit-scrollbar-thumb { background: rgba(79,110,246,0.12); border-radius: 4px; }

/* ─── 概览统计 ─── */
.qs-bar {
  display: flex; align-items: center; gap: 20px;
  padding: 12px 16px; margin-bottom: 14px;
  background: linear-gradient(135deg, rgba(79,110,246,0.05), rgba(79,110,246,0.02));
  border-radius: 8px; border: 1px solid rgba(79,110,246,0.06);
}
.qs-total { display: flex; align-items: baseline; gap: 4px; }
.qs-num { font-size: 30px; font-weight: 700; color: #4f6ef6; font-variant-numeric: tabular-nums; line-height: 1; }
.qs-unit { font-size: 14px; color: #555; }
.qs-dots { display: flex; gap: 14px; margin-left: auto; }
.qs-dot-item {
  display: flex; align-items: center; gap: 5px;
  font-size: 15px; font-weight: 600; color: #444;
}
.qs-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }

/* ─── 图表 ─── */
.quadrant-chart { width: 100%; height: min(460px, 50vh); flex-shrink: 0; }

/* ─── 象限卡片 ─── */
.qc-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 14px;
}
.qc-card {
  background: #fff; border-radius: 8px; padding: 10px 12px;
  border: 1px solid rgba(0,0,0,0.04); border-top: 3px solid;
  cursor: pointer; transition: all 0.2s ease;
}
.qc-card:hover { transform: translateY(-2px); box-shadow: 0 4px 14px rgba(0,0,0,0.06); }
.qcc-top { display: flex; align-items: center; gap: 8px; }
.qcc-code { font-size: 16px; font-weight: 800; color: #232845; flex-shrink: 0; letter-spacing: 0.5px; }
.qcc-label { font-size: 14px; font-weight: 500; color: #232845; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qcc-count { font-size: 14px; font-weight: 600; color: #444; flex-shrink: 0; }
.qcc-pct { font-size: 20px; font-weight: 800; flex-shrink: 0; font-variant-numeric: tabular-nums; }
.qcc-bar { height: 4px; background: #eef0f5; border-radius: 2px; overflow: hidden; margin-top: 6px; }
.qcc-fill { height: 100%; border-radius: 2px; transition: width 0.6s ease; }

/* ─── 底部 ─── */
.qs-insight {
  margin-top: 14px; padding: 10px 14px; font-size: 14px; color: #555;
  background: rgba(79,110,246,0.04); border-radius: 6px;
  display: flex; align-items: center; gap: 6px;
}
.qs-insight i { color: #4f6ef6; font-size: 14px; }
.qs-hint { margin-top: 10px; font-size: 13px; color: #888; text-align: center; }

/* ===== AI 入口 ===== */
.quadrant-ai-entry {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.quadrant-ai-btn {
  background: linear-gradient(135deg, #f43f5e 0%, #fb7185 100%) !important;
  border: none !important;
  color: #fff !important;
  font-weight: 500;
  padding: 7px 14px !important;
  border-radius: 8px !important;
  transition: all 0.25s ease !important;
  box-shadow: 0 2px 8px rgba(244, 63, 94, 0.25) !important;
  letter-spacing: 0.3px;
}
.quadrant-ai-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(244, 63, 94, 0.35) !important;
}
.quadrant-ai-btn:active { transform: translateY(0); }
.quadrant-ai-btn.is-loading {
  background: linear-gradient(135deg, #cc8888, #aa88aa) !important;
}
.quadrant-ai-done { font-size: 12px; color: #52c41a; }

/* ─── 动画 ─── */
.quad-slide-enter-active, .quad-slide-leave-active {
  transition: transform 0.3s cubic-bezier(0.25,0.46,0.45,0.94), opacity 0.25s ease;
}
.quad-slide-enter, .quad-slide-leave-to { transform: translateX(30px); opacity: 0; }
</style>
