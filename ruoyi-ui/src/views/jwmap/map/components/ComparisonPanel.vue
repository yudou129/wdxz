<template>
  <div v-if="visible">
    <!-- 收起标签 -->
    <div v-if="collapsed" class="compare-tab" @click="collapsed = false">
      <i class="el-icon-data-analysis" />
      <span class="tab-label">对比</span>
      <span v-if="branches.length" class="tab-badge">{{ branches.length }}</span>
    </div>

    <!-- 展开面板 -->
    <div v-else class="comparison-panel" :class="{ 'is-empty': branches.length === 0 }">
      <div class="cp-header">
        <span class="cp-title">
          <i class="el-icon-data-analysis" /> 网点对比
          <span v-if="branches.length" class="cp-count">({{ branches.length }}/4)</span>
        </span>
        <div class="cp-actions">
          <el-button v-if="branches.length" type="text" size="mini" class="act-clear"
                     @click="$emit('clear-all')">清空</el-button>
          <el-button type="text" size="mini" class="act-fold"
                     @click="collapsed = true">
            <i class="el-icon-d-arrow-left" /> 收起
          </el-button>
          <el-button type="text" size="mini" class="act-exit"
                     @click="$emit('close')">退出对比</el-button>
        </div>
      </div>

      <div class="cp-body">
        <!-- 空态 -->
        <div v-if="branches.length === 0" class="cp-empty">
          <i class="el-icon-data-analysis" />
          <p class="cp-empty-title">开始网点对比</p>
          <div class="cp-empty-steps">
            <div class="step"><span class="step-num">1</span> 点击工具栏「多网点对比分析」</div>
            <div class="step"><span class="step-num">2</span> 搜索栏输入网点名称</div>
            <div class="step"><span class="step-num">3</span> 点击「加入对比」添加（最多 4 个）</div>
          </div>
        </div>

        <!-- 对比内容 -->
        <div v-else class="cp-content">
          <!-- 综合排名 -->
          <div class="cp-card">
            <div class="cpc-title"><i class="el-icon-trophy" /> 综合排名</div>
            <div class="rank-row" :class="'rank-count-' + branches.length">
              <div v-for="(b, i) in rankedBranches" :key="b.branchId"
                   :class="['rank-card', 'rank-' + (i + 1)]">
                <span class="rc-medal">{{ medals[i] }}</span>
                <div class="rc-info">
                  <span class="rc-name">{{ getBranchName(b) }}</span>
                  <span class="rc-meta" v-if="b.rankMeta && b.rankMeta.branchRank">
                    支行第 {{ b.rankMeta.branchRank }} · 全市第 {{ b.rankMeta.cityRank }}
                  </span>
                </div>
                <div class="rc-score-wrap">
                  <span class="rc-score">{{ fmtScore(overallScore(b)) }}</span>
                  <span class="rc-sub">综合得分</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 能力对比：雷达图 -->
          <div class="cp-card" v-if="hasScores">
            <div class="cpc-title"><i class="el-icon-s-data" /> 能力对比</div>
            <div ref="radarEl" class="radar-chart"></div>
          </div>

          <!-- 各项得分对比 -->
          <div class="cp-card" v-if="hasScores">
            <div class="cpc-title"><i class="el-icon-s-marketing" /> 各项得分对比</div>
            <div v-for="cat in scoreCategories" :key="cat.key" class="sc-group">
              <div class="sc-header">
                <span class="sc-label">{{ cat.label }}</span>
                <span class="sc-insight" v-if="categoryInsight(cat.key)">{{ categoryInsight(cat.key) }}</span>
              </div>
              <div v-for="b in sortedByScore(cat.key)" :key="b.branchId" class="sc-row">
                <span class="scr-name">{{ getBranchName(b) }}</span>
                <div class="scr-track">
                  <div class="scr-fill" :style="{ width: getScorePct(b, cat.key), background: catColor(cat.key) }"></div>
                </div>
                <span class="scr-val">{{ fmtScore(getScoreVal(b, cat.key)) }}</span>
                <span class="scr-best" v-if="isBest(b, cat.key)">🏆</span>
                <span class="scr-gap" v-else-if="getGap(b, cat.key) > 0">-{{ getGap(b, cat.key).toFixed(2) }}</span>
              </div>
            </div>
          </div>

          <!-- 基础信息 -->
          <div class="cp-card">
            <div class="cpc-title cpc-collapse" @click="showInfo = !showInfo">
              <i class="el-icon-info" /> 基础信息
              <i :class="showInfo ? 'el-icon-arrow-up' : 'el-icon-arrow-down'" class="collapse-icon" />
            </div>
            <div v-if="showInfo" class="info-grid">
              <div v-for="row in basicInfoRows" :key="row.key" class="info-row">
                <span class="ir-label">{{ row.label }}</span>
                <div class="ir-vals">
                  <span v-for="b in branches" :key="b.branchId" class="ir-val">
                    {{ getCellValue(b, row) }}
                  </span>
                </div>
              </div>
              <div class="info-row">
                <span class="ir-label">支行排名</span>
                <div class="ir-vals">
                  <span v-for="b in branches" :key="b.branchId"
                        :class="['ir-val', bestRankClass(b, 'branchRank')]">
                    {{ fmtRank(b, 'branchRank') }}
                  </span>
                </div>
              </div>
              <div class="info-row">
                <span class="ir-label">全市排名</span>
                <div class="ir-vals">
                  <span v-for="b in branches" :key="b.branchId"
                        :class="['ir-val', bestRankClass(b, 'cityRank')]">
                    {{ fmtRank(b, 'cityRank') }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 加载遮罩 -->
      <div v-if="loading" class="cp-loading">
        <i class="el-icon-loading" /> 加载数据中...
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'

const CAT_COLORS = {
  '业务运营': 'linear-gradient(90deg, #4f6ef6, #6b8af8)',
  '业绩表现': 'linear-gradient(90deg, #f0a050, #f6b870)',
  '客户发展': 'linear-gradient(90deg, #52c41a, #73d13d)',
  '经营情况': 'linear-gradient(90deg, #a855f7, #c084fc)',
  '综合': 'linear-gradient(90deg, #4f6ef6, #6b8af8)',
}

const CATEGORY_NAME_FALLBACK = {
  overall: '综合', revenue: '营收', indicator: '业绩',
  customer: '客户', operation: '运营'
}

const BASIC_INFO_ROWS = [
  { key: 'primaryBranch', label: '所属支行' },
  { key: 'districtName', label: '区县' },
  { key: 'address', label: '地址' },
  { key: 'branchType', label: '网点类型' },
  { key: 'totalStaff', label: '员工数' },
  { key: 'totalArea', label: '面积(m²)' },
  { key: 'propertyRight', label: '产权' }
]

const RADAR_COLORS = ['#4f6ef6', '#52c41a', '#f0a050', '#f56c6c']

export default {
  name: 'ComparisonPanel',
  props: {
    visible: { type: Boolean, default: false },
    branches: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false }
  },
  data() {
    return { collapsed: false, showInfo: true, radarChart: null }
  },
  watch: {
    visible(v) {
      if (!v) { this.collapsed = false; this.destroyRadar() }
    },
    branches: {
      handler() {
        if (this.visible && this.branches.length > 0) {
          this.$nextTick(() => this.renderRadar())
        }
      },
      deep: true
    }
  },
  beforeDestroy() { this.destroyRadar() },
  computed: {
    basicInfoRows() { return BASIC_INFO_ROWS },
    hasScores() {
      return this.branches.some(b => b.scores && b.scores.length > 0)
    },
    scoreCategories() {
      const first = this.branches.find(b => b.scores && b.scores.length)
      if (!first) return []
      const cats = first.scores.map(s => ({
        key: s.scoreCategory,
        label: s.categoryName || CATEGORY_NAME_FALLBACK[s.scoreCategory] || s.scoreCategory
      }))
      cats.sort((a, b) => {
        if (a.key === 'overall') return -1
        if (b.key === 'overall') return 1
        return a.label.localeCompare(b.label, 'zh-CN')
      })
      return cats
    },
    rankedBranches() {
      return [...this.branches].sort((a, b) => {
        const sa = this.overallScore(a) || 0
        const sb = this.overallScore(b) || 0
        return sb - sa
      })
    },
    medals() {
      return ['🥇', '🥈', '🥉', '④']
    },
  },
  methods: {
    getBranchName(b) {
      return b.branchData ? (b.branchData.secondaryBranch || b.branchData.primaryBranch || '--') : '--'
    },
    getCellValue(b, row) {
      const v = b.branchData ? b.branchData[row.key] : null
      return v != null && v !== '' ? v : '--'
    },
    overallScore(b) {
      if (!b.scores) return null
      const s = b.scores.find(s => s.scoreCategory === 'overall')
      return s ? s.categoryScore : null
    },
    getScoreVal(b, catKey) {
      if (!b.scores) return null
      const s = b.scores.find(s => s.scoreCategory === catKey)
      return s ? s.categoryScore : null
    },
    getScorePct(b, catKey) {
      const v = this.getScoreVal(b, catKey)
      return typeof v === 'number' ? Math.min(v * 100, 100) + '%' : '0%'
    },
    fmtScore(v) { return typeof v === 'number' ? v.toFixed(2) : '--' },
    fmtRank(b, key) {
      if (!b.rankMeta) return '--'
      const rank = b.rankMeta[key]
      const totalKey = key === 'branchRank' ? 'branchTotal' : 'cityTotal'
      const total = b.rankMeta[totalKey]
      if (!rank || rank <= 0) return '--'
      return `${rank}/${total}`
    },
    catColor(catKey) {
      const cat = this.scoreCategories.find(c => c.key === catKey)
      return CAT_COLORS[cat ? cat.label : ''] || 'linear-gradient(90deg, #4f6ef6, #6b8af8)'
    },
    sortedByScore(catKey) {
      return [...this.branches].sort((a, b) => {
        const va = this.getScoreVal(a, catKey) || 0
        const vb = this.getScoreVal(b, catKey) || 0
        return vb - va
      })
    },
    isBest(b, catKey) {
      const val = this.getScoreVal(b, catKey)
      if (val == null) return false
      const vals = this.branches.map(br => this.getScoreVal(br, catKey)).filter(v => v != null)
      return vals.length > 0 && val >= Math.max(...vals) && val > 0
    },
    getGap(b, catKey) {
      const val = this.getScoreVal(b, catKey)
      if (val == null) return 0
      const vals = this.branches.map(br => this.getScoreVal(br, catKey)).filter(v => v != null)
      if (!vals.length) return 0
      return Math.max(0, Math.max(...vals) - val)
    },
categoryInsight(catKey) {
      if (catKey === 'overall') {
        const best = this.rankedBranches[0]
        return best ? `🏆 ${this.getBranchName(best)} 最优` : ''
      }
      const best = this.sortedByScore(catKey)[0]
      if (!best || !this.isBest(best, catKey)) return ''
      return `💡 ${this.getBranchName(best)} 优势项`
    },
    bestRankClass(b, rankKey) {
      const vals = this.branches
        .map(br => br.rankMeta ? br.rankMeta[rankKey] : null)
        .filter(v => typeof v === 'number' && v > 0)
      if (!vals.length) return ''
      const best = Math.min(...vals)
      const cur = b.rankMeta ? b.rankMeta[rankKey] : null
      return typeof cur === 'number' && cur === best && best > 0 ? 'ir-best' : ''
    },
    // ─── 雷达图 ───
    renderRadar() {
      if (!this.$refs.radarEl || !this.hasScores) return
      this.destroyRadar()
      this.radarChart = echarts.init(this.$refs.radarEl)

      const cats = this.scoreCategories.filter(c => c.key !== 'overall')
      if (!cats.length) return

      const option = {
        tooltip: { trigger: 'item' },
        legend: {
          data: this.branches.map(b => this.getBranchName(b)),
          bottom: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 12, color: '#555' }
        },
        radar: {
          indicator: cats.map(c => ({ name: c.label, max: 1 })),
          center: ['50%', '52%'],
          radius: '62%',
          axisName: { color: '#666', fontSize: 12, fontWeight: 500 },
          splitArea: { areaStyle: { color: ['rgba(79,110,246,0.02)', 'rgba(79,110,246,0.04)'] } },
          splitLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } }
        },
        series: [{
          type: 'radar',
          data: this.branches.map((b, i) => ({
            value: cats.map(c => Math.max(this.getScoreVal(b, c.key) || 0, 0.01)),
            name: this.getBranchName(b),
            lineStyle: { color: RADAR_COLORS[i % RADAR_COLORS.length], width: 2 },
            areaStyle: { color: RADAR_COLORS[i % RADAR_COLORS.length], opacity: 0.15 },
            itemStyle: { color: RADAR_COLORS[i % RADAR_COLORS.length] }
          }))
        }]
      }

      this.radarChart.setOption(option)
    },
    destroyRadar() {
      if (this.radarChart) { this.radarChart.dispose(); this.radarChart = null }
    }
  }
}
</script>

<style scoped>
/* ===== 收起标签 ===== */
.compare-tab {
  position: absolute; left: 12px; top: 50%; transform: translateY(-50%);
  z-index: 1001; isolation: isolate;
  border-radius: 0 8px 8px 0; border: 1px solid rgba(255,255,255,0.28); border-left: none;
  background: rgba(255,255,255,0.85); backdrop-filter: blur(22px);
  box-shadow: 0 4px 16px rgba(79,110,246,0.08);
  padding: 14px 6px; display: flex; flex-direction: column;
  align-items: center; gap: 4px; cursor: pointer; writing-mode: vertical-lr;
  transition: box-shadow 0.2s, background 0.2s;
}
.compare-tab i { color: #4f6ef6; font-size: 16px; writing-mode: horizontal-tb; }
.compare-tab:hover { background: rgba(255,255,255,0.92); box-shadow: 0 4px 20px rgba(79,110,246,0.14); }
.tab-label { font-size: 13px; font-weight: 600; color: #232845; letter-spacing: 2px; }
.tab-badge {
  position: absolute; top: -4px; right: -4px;
  background: #f56c6c; color: #fff; font-size: 12px; font-weight: 700;
  min-width: 18px; height: 18px; line-height: 18px; text-align: center;
  border-radius: 9px; padding: 0 5px; writing-mode: horizontal-tb;
}

/* ===== 面板 ===== */
.comparison-panel {
  position: absolute; left: 12px; top: 100px; bottom: 12px; width: min(936px, 92vw);
  isolation: isolate; border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.28);
  background: rgba(255,255,255,0.92); backdrop-filter: blur(22px) saturate(170%);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.44), 0 8px 32px rgba(79,110,246,0.08), 0 1px 4px rgba(0,0,0,0.05);
  z-index: 1001; display: flex; flex-direction: column; transition: width 0.3s ease;
}
.comparison-panel::before {
  content: ''; position: absolute; inset: 0; z-index: -1; border-radius: inherit;
  background: radial-gradient(circle at 20% 0%, rgba(255,255,255,0.48), transparent 34%),
              linear-gradient(90deg, rgba(255,255,255,0.16), transparent 42%, rgba(255,255,255,0.12));
  pointer-events: none;
}

/* ===== 头部 ===== */
.cp-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 22px; border-bottom: 1px solid rgba(79,110,246,0.06); flex-shrink: 0;
}
.cp-title { font-size: 17px; font-weight: 700; color: #232845; display: flex; align-items: center; gap: 8px; }
.cp-title i { color: #4f6ef6; font-size: 18px; }
.cp-count { font-size: 13px; color: #999; font-weight: 400; }
.cp-actions { display: flex; align-items: center; gap: 8px; }
.act-clear { font-size: 13px; color: #e6a23c; padding: 4px 8px; }
.act-clear:hover { color: #d4941f; }
.act-fold { font-size: 13px; color: #4f6ef6; padding: 4px 8px; }
.act-fold:hover { color: #3b54d4; }
.act-exit { font-size: 13px; color: #666; padding: 4px 8px; }
.act-exit:hover { color: #f56c6c; }

/* ===== 主体 ===== */
.cp-body { flex: 1; overflow-y: auto; padding: 16px 20px; }
.cp-body::-webkit-scrollbar { width: 5px; }
.cp-body::-webkit-scrollbar-thumb { background: rgba(79,110,246,0.1); border-radius: 5px; }
.cp-content { display: flex; flex-direction: column; gap: 16px; }

/* ===== 空态 ===== */
.cp-empty {
  display: flex; flex-direction: column; align-items: center;
  justify-content: center; padding: 40px 20px; color: #555;
}
.cp-empty i { font-size: 40px; color: #cdd5e6; margin-bottom: 12px; }
.cp-empty-title { font-size: 16px; font-weight: 600; color: #333; margin-bottom: 16px; }
.cp-empty-steps { display: flex; flex-direction: column; gap: 8px; }
.step { font-size: 14px; color: #444; display: flex; align-items: center; gap: 8px; }
.step-num {
  width: 22px; height: 22px; border-radius: 50%;
  background: rgba(79,110,246,0.08); color: #4f6ef6;
  font-size: 12px; font-weight: 700; display: flex;
  align-items: center; justify-content: center; flex-shrink: 0;
}

/* ===== 通用卡片 ===== */
.cp-card {
  background: #fff; border-radius: 10px; padding: 16px 20px;
  border: 1px solid rgba(79,110,246,0.06); box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.cpc-title {
  font-size: 15px; font-weight: 700; color: #232845; margin-bottom: 14px;
  padding-left: 12px; border-left: 3px solid #4f6ef6;
  display: flex; align-items: center; gap: 8px;
}
.cpc-title i { color: #4f6ef6; font-size: 14px; }
.cpc-collapse { cursor: pointer; user-select: none; }
.cpc-collapse:hover { opacity: 0.8; }
.collapse-icon { margin-left: auto; color: #aaa; font-size: 14px; transition: transform 0.2s; }
.cpc-foot {
  margin-top: 8px; padding: 6px 10px; font-size: 13px; color: #555;
  background: rgba(79,110,246,0.04); border-radius: 6px;
  display: flex; align-items: center; gap: 6px;
}
.cpc-foot i { color: #4f6ef6; font-size: 13px; }

/* ===== 综合排名 ===== */
.rank-row { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.rank-row.rank-count-1 { grid-template-columns: 1fr; }
.rank-card {
  display: flex; align-items: center; gap: 14px;
  border-radius: 12px; padding: 18px 20px;
  border: 1px solid rgba(0,0,0,0.05);
  transition: transform 0.2s, box-shadow 0.2s;
}
.rank-card:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(0,0,0,0.06); }
.rank-1 { background: linear-gradient(135deg, rgba(255,215,0,0.12), rgba(255,243,205,0.4)); border-color: rgba(255,215,0,0.22); }
.rank-2 { background: linear-gradient(135deg, rgba(168,178,196,0.10), rgba(227,230,237,0.3)); border-color: rgba(168,178,196,0.18); }
.rank-3 { background: linear-gradient(135deg, rgba(205,127,50,0.10), rgba(237,201,171,0.3)); border-color: rgba(205,127,50,0.18); }
.rank-4 { background: linear-gradient(135deg, #f8f9fc, #f2f3f7); border-color: rgba(0,0,0,0.06); }
.rc-medal { font-size: 36px; flex-shrink: 0; line-height: 1; }
.rc-info { flex: 1; min-width: 0; }
.rc-name {
  font-size: 14px; font-weight: 600; color: #232845; display: block;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.rc-meta {
  font-size: 12px; color: #777; display: block; margin-top: 2px;
}
.rc-score-wrap { text-align: right; flex-shrink: 0; }
.rc-score { font-size: 30px; font-weight: 800; color: #4f6ef6; display: block; line-height: 1.1; letter-spacing: -0.8px; }
.rc-sub { font-size: 12px; color: #666; }

/* ===== 雷达图 ===== */
.radar-chart { width: 100%; height: 320px; }

/* ===== 得分对比组 ===== */
.sc-group { margin-bottom: 18px; }
.sc-group:last-child { margin-bottom: 0; }
.sc-header { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.sc-label { font-size: 14px; font-weight: 700; color: #333; }
.sc-insight {
  font-size: 12px; color: #4f6ef6; margin-left: auto;
  white-space: nowrap; background: rgba(79,110,246,0.05);
  padding: 2px 10px; border-radius: 12px;
}
.sc-row {
  display: flex; align-items: center; gap: 10px; padding: 7px 6px; font-size: 13px;
  border-radius: 6px; transition: background 0.15s;
}
.sc-row:hover { background: #f8f9fd; }
.sc-row + .sc-row { border-top: 1px solid #f5f6fa; }
.scr-name {
  width: 110px; font-weight: 600; color: #333; flex-shrink: 0;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.scr-track {
  flex: 1; height: 14px; background: #f0f1f6; border-radius: 7px; overflow: hidden;
  box-shadow: inset 0 1px 2px rgba(0,0,0,0.04);
}
.scr-fill {
  height: 100%; border-radius: 7px; transition: width 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
  position: relative;
}
.scr-val {
  min-width: 48px; text-align: right; font-weight: 700; color: #4f6ef6;
  font-variant-numeric: tabular-nums; font-size: 13px;
}
.scr-best { font-size: 16px; min-width: 24px; text-align: center; }
.scr-gap { font-size: 12px; color: #f56c6c; min-width: 64px; text-align: right; white-space: nowrap; font-weight: 500; }

/* ===== 基础信息 ===== */
.info-grid { display: flex; flex-direction: column; gap: 2px; }
.info-row {
  display: flex; padding: 8px 4px; font-size: 13px;
  border-radius: 6px; transition: background 0.15s;
}
.info-row:hover { background: #f8f9fd; }
.info-row:last-child { border-bottom: none; }
.ir-label { width: 72px; color: #555; font-weight: 500; flex-shrink: 0; font-size: 13px; line-height: 1.6; }
.ir-vals { display: flex; flex: 1; gap: 10px; }
.ir-val {
  flex: 1; color: #232845; font-weight: 600; font-size: 13px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  padding: 3px 8px; background: #f8f9fd; border-radius: 5px;
}
.ir-best { color: #4f6ef6; font-weight: 700; background: rgba(79,110,246,0.04); }

/* ===== 加载遮罩 ===== */
.cp-loading {
  position: absolute; inset: 0; z-index: 5;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.6); backdrop-filter: blur(2px);
  border-radius: 12px; font-size: 14px; color: #555; gap: 8px;
}
</style>
