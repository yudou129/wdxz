<template>
  <div v-if="visible">
    <!-- 收起标签 -->
    <div v-if="collapsed" class="compare-tab" @click="collapsed = false">
      <i class="el-icon-data-analysis" />
      <span class="tab-label">对比</span>
      <span v-if="branches.length" class="tab-badge">{{ branches.length }}</span>
    </div>

    <!-- 展开面板 -->
    <div v-else class="comparison-panel"
         :style="{ width: panelWidth + 'px' }"
         :class="{ 'is-empty': branches.length === 0 }">
      <div class="cp-header">
        <span class="cp-title">
          <i class="el-icon-data-analysis" /> 网点对比
          <span v-if="branches.length" class="branch-count">({{ branches.length }}/4)</span>
        </span>
        <div class="cp-header-actions">
          <el-button v-if="branches.length" type="text" size="mini" class="clear-btn"
                     @click="$emit('clear-all')">清空</el-button>
          <el-button type="text" size="mini" class="collapse-btn"
                     @click="collapsed = true">
            <i class="el-icon-d-arrow-left" /> 收起
          </el-button>
          <el-button type="text" size="mini" class="exit-btn"
                     @click="$emit('close')">退出对比</el-button>
        </div>
      </div>

      <div class="cp-body">
        <!-- 空状态 -->
        <div v-if="branches.length === 0" class="cp-empty">
          <i class="el-icon-data-analysis" />
          <p>点击地图标记或搜索添加网点</p>
          <p class="cp-hint">选择后即可对比（最多4个）</p>
        </div>

        <!-- 对比表格 -->
        <div v-else class="cp-table-wrap">
          <table class="cp-table">
            <thead>
              <tr>
                <th class="attr-th">对比项</th>
                <th v-for="b in branches" :key="b.branchId" class="branch-th">
                  <div class="branch-th-content">
                    <span class="branch-th-name">{{ b.branchData.secondaryBranch || '--' }}</span>
                    <el-button type="text" icon="el-icon-close" size="mini"
                               class="remove-btn"
                               @click="$emit('remove-branch', b.branchId)" />
                  </div>
                </th>
              </tr>
            </thead>
            <tbody>
              <!-- 基础信息 -->
              <tr class="section-tr"><td colspan="6">基础信息</td></tr>
              <tr v-for="row in basicInfoRows" :key="row.key">
                <td class="attr-td">{{ row.label }}</td>
                <td v-for="b in branches" :key="b.branchId" class="val-td">
                  {{ getCellValue(b, row) }}
                </td>
              </tr>

              <!-- 排名 -->
              <tr class="section-tr"><td colspan="6">效能排名</td></tr>
              <tr>
                <td class="attr-td">支行内排名</td>
                <td v-for="b in branches" :key="b.branchId"
                    :class="['val-td', bestRankClass(b, 'branchRank')]">
                  <span v-if="b.rankMeta && b.rankMeta.branchRank > 0">
                    {{ b.rankMeta.branchRank }}/{{ b.rankMeta.branchTotal }}
                  </span>
                  <span v-else class="na">--</span>
                </td>
              </tr>
              <tr>
                <td class="attr-td">全市排名</td>
                <td v-for="b in branches" :key="b.branchId"
                    :class="['val-td', bestRankClass(b, 'cityRank')]">
                  <span v-if="b.rankMeta && b.rankMeta.cityRank > 0">
                    {{ b.rankMeta.cityRank }}/{{ b.rankMeta.cityTotal }}
                  </span>
                  <span v-else class="na">--</span>
                </td>
              </tr>

              <!-- 得分 -->
              <tr class="section-tr"><td colspan="6">效能得分</td></tr>
              <tr v-for="cat in scoreCategories" :key="cat.key">
                <td class="attr-td">{{ cat.label }}</td>
                <td v-for="b in branches" :key="b.branchId"
                    :class="['val-td', bestScoreClass(b, cat.key)]">
                  <div class="score-cell">
                    <div class="mini-bar-track">
                      <div class="mini-bar-fill"
                           :style="{ width: getScorePct(b, cat.key), background: getScoreColor(getScoreVal(b, cat.key)) }" />
                    </div>
                    <span class="score-val">{{ fmtScore(getScoreVal(b, cat.key)) }}</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 加载遮罩 -->
      <div v-if="loading" class="cp-loading">
        <i class="el-icon-loading" /> 加载中...
      </div>
    </div>
  </div>
</template>

<script>
// 兜底名称映射（indicatorNameMap 未加载时使用）
const CATEGORY_NAME_FALLBACK = {
  overall: '综合',
  revenue: '营收',
  indicator: '业绩',
  customer: '客户',
  operation: '运营'
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

export default {
  name: 'ComparisonPanel',
  props: {
    visible: { type: Boolean, default: false },
    branches: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false }
  },
  data() {
    return {
      collapsed: false
    }
  },
  watch: {
    visible(v) {
      if (!v) this.collapsed = false
    },
    branches: {
      handler() {
        // 清空时自动展开
        if (this.visible && this.collapsed && this.branches.length > 0) {
          // keep collapsed, user decides when to expand
        }
      },
      deep: true
    }
  },
  computed: {
    basicInfoRows() { return BASIC_INFO_ROWS },
    scoreCategories() {
      // 从实际score数据动态构建分类，确保匹配DB中的score_category值
      const first = this.branches.find(b => b.scores && b.scores.length)
      if (!first) return []
      const cats = first.scores.map(s => ({
        key: s.scoreCategory,
        label: s.categoryName || CATEGORY_NAME_FALLBACK[s.scoreCategory] || s.scoreCategory
      }))
      // 确保'综合'(overall)排最前
      cats.sort((a, b) => {
        if (a.key === 'overall') return -1
        if (b.key === 'overall') return 1
        return a.label.localeCompare(b.label, 'zh-CN')
      })
      return cats
    },
    panelWidth() {
      if (!this.branches.length) return 480
      // 属性列120px + 每网点列210px + 间距（1.5倍宽）
      const w = 120 + this.branches.length * 210 + 30
      return Math.max(480, Math.min(w, 1020))
    }
  },
  methods: {
    getCellValue(b, row) {
      const v = b.branchData ? b.branchData[row.key] : null
      return v != null && v !== '' ? v : '--'
    },
    getScoreVal(b, catKey) {
      if (!b.scores || !b.scores.length) return null
      const s = b.scores.find(s => s.scoreCategory === catKey)
      return s ? s.categoryScore : null
    },
    getScorePct(b, catKey) {
      const v = this.getScoreVal(b, catKey)
      return typeof v === 'number' ? Math.min(v * 100, 100) + '%' : '0%'
    },
    getScoreColor(v) {
      if (typeof v !== 'number') return '#e2e4ea'
      if (v >= 0.8) return 'linear-gradient(90deg, #4f6ef6, #6b8af8)'
      if (v >= 0.6) return 'linear-gradient(90deg, #f0a050, #f6b870)'
      return 'linear-gradient(90deg, #e87060, #f09080)'
    },
    fmtScore(v) {
      return typeof v === 'number' ? v.toFixed(2) : '--'
    },
    bestScoreClass(b, catKey) {
      const vals = this.branches.map(br => this.getScoreVal(br, catKey)).filter(v => typeof v === 'number')
      if (!vals.length) return ''
      const best = Math.max(...vals)
      const cur = this.getScoreVal(b, catKey)
      return typeof cur === 'number' && cur === best && best > 0 ? 'best-value' : ''
    },
    bestRankClass(b, rankKey) {
      const vals = this.branches
        .map(br => br.rankMeta ? br.rankMeta[rankKey] : null)
        .filter(v => typeof v === 'number' && v > 0)
      if (!vals.length) return ''
      const best = Math.min(...vals)
      const cur = b.rankMeta ? b.rankMeta[rankKey] : null
      return typeof cur === 'number' && cur === best && best > 0 ? 'best-value' : ''
    }
  }
}
</script>

<style scoped>
/* ===== 收起标签 ===== */
.compare-tab {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1001;
  isolation: isolate;
  border-radius: 0 8px 8px 0;
  border: 1px solid rgba(255,255,255,0.28);
  border-left: none;
  background:
    linear-gradient(135deg, rgba(255,255,255,0.28), rgba(255,255,255,0.08)),
    rgba(255,255,255,0.10);
  backdrop-filter: blur(22px) saturate(170%) contrast(1.04);
  -webkit-backdrop-filter: blur(22px) saturate(170%) contrast(1.04);
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.44),
    0 4px 16px rgba(79,110,246,0.08);
  padding: 14px 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  transition: box-shadow 0.2s, background 0.2s;
  writing-mode: vertical-lr;
}
.compare-tab i {
  color: #4f6ef6;
  font-size: 16px;
  writing-mode: horizontal-tb;
}
.tab-label {
  font-size: 12px;
  font-weight: 600;
  color: #232845;
  letter-spacing: 2px;
}
.tab-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  background: #f56c6c;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  text-align: center;
  border-radius: 8px;
  padding: 0 4px;
  writing-mode: horizontal-tb;
}
.compare-tab:hover {
  background:
    linear-gradient(135deg, rgba(255,255,255,0.34), rgba(255,255,255,0.12)),
    rgba(255,255,255,0.14);
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.44),
    0 4px 20px rgba(79,110,246,0.14);
}
.compare-tab::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  border-radius: inherit;
  background:
    radial-gradient(circle at 20% 0%, rgba(255,255,255,0.48), transparent 34%),
    linear-gradient(90deg, rgba(255,255,255,0.16), transparent 42%, rgba(255,255,255,0.12));
  pointer-events: none;
}
.compare-tab::after {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: 7px;
  border: 1px solid rgba(255,255,255,0.12);
  pointer-events: none;
}

/* ===== 全表面板 ===== */
.comparison-panel {
  position: absolute;
  left: 12px;
  top: 60px;
  bottom: 12px;
  isolation: isolate;
  border-radius: 10px;
  border: 1px solid rgba(255,255,255,0.28);
  background:
    linear-gradient(135deg, rgba(255,255,255,0.28), rgba(255,255,255,0.08)),
    rgba(255,255,255,0.10);
  backdrop-filter: blur(22px) saturate(170%) contrast(1.04);
  -webkit-backdrop-filter: blur(22px) saturate(170%) contrast(1.04);
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.44),
    inset 0 -1px 0 rgba(255,255,255,0.10),
    0 8px 32px rgba(79,110,246,0.08),
    0 1px 4px rgba(0,0,0,0.05);
  z-index: 1001;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
}
.comparison-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  border-radius: inherit;
  background:
    radial-gradient(circle at 20% 0%, rgba(255,255,255,0.48), transparent 34%),
    linear-gradient(90deg, rgba(255,255,255,0.16), transparent 42%, rgba(255,255,255,0.12));
  pointer-events: none;
}
.comparison-panel::after {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: 9px;
  border: 1px solid rgba(255,255,255,0.12);
  pointer-events: none;
}

.cp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(79,110,246,0.08);
  flex-shrink: 0;
}
.cp-title {
  font-size: 14px;
  font-weight: 600;
  color: #232845;
  display: flex;
  align-items: center;
  gap: 6px;
}
.cp-title i { color: #4f6ef6; }
.branch-count {
  font-size: 12px;
  color: #999;
  font-weight: 400;
}
.cp-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.clear-btn {
  color: #e6a23c;
  font-size: 12px;
}
.clear-btn:hover { color: #d4941f; }
.collapse-btn {
  font-size: 12px;
  color: #4f6ef6;
}
.collapse-btn:hover { color: #3b54d4; }
.exit-btn {
  font-size: 12px;
  color: #e6a23c;
}
.exit-btn:hover { color: #d4941f; }

.cp-body {
  flex: 1;
  overflow: visible;
  display: flex;
}
.cp-body::-webkit-scrollbar { width: 4px; height: 4px; }
.cp-body::-webkit-scrollbar-thumb { background: rgba(79,110,246,0.15); border-radius: 4px; }

.cp-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 13px;
  padding: 20px;
}
.cp-empty i {
  font-size: 36px;
  color: #cdd5e6;
  margin-bottom: 12px;
}
.cp-empty p { margin: 2px 0; }
.cp-hint { font-size: 12px; color: #bbb; }

.cp-table-wrap {
  flex: 1;
  overflow: auto;
  padding: 0;
}
.cp-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  min-width: 100%;
  font-size: 12px;
}
.cp-table thead {
  position: sticky;
  top: 0;
  z-index: 3;
}
.attr-th {
  width: 80px;
  min-width: 80px;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(8px);
  padding: 8px 10px;
  text-align: left;
  font-weight: 600;
  color: #888;
  font-size: 11px;
  border-bottom: 1px solid rgba(79,110,246,0.08);
  position: sticky;
  left: 0;
  z-index: 4;
}
.branch-th {
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(8px);
  padding: 6px 10px;
  border-bottom: 1px solid rgba(79,110,246,0.08);
  min-width: 130px;
}
.branch-th-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
}
.branch-th-name {
  font-size: 12px;
  font-weight: 600;
  color: #232845;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.remove-btn { font-size: 12px; color: #ccc; flex-shrink: 0; }
.remove-btn:hover { color: #f56c6c; }

.section-tr td {
  font-size: 12px;
  font-weight: 700;
  color: #232845;
  background: rgba(79,110,246,0.04);
  padding: 6px 10px;
  border-top: 1px solid rgba(79,110,246,0.08);
  border-bottom: 1px solid rgba(79,110,246,0.04);
}
.attr-td {
  width: 80px;
  min-width: 80px;
  padding: 7px 10px;
  color: #6b7280;
  font-weight: 500;
  border-bottom: 1px solid rgba(0,0,0,0.03);
  background: rgba(255,255,255,0.5);
  position: sticky;
  left: 0;
  z-index: 1;
}
.val-td {
  padding: 7px 10px;
  color: #303651;
  border-bottom: 1px solid rgba(0,0,0,0.03);
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.val-td.best-value {
  color: #4f6ef6;
  font-weight: 700;
  background: rgba(79,110,246,0.04);
}
.score-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.mini-bar-track {
  width: 64px;
  height: 6px;
  background: #eef0f5;
  border-radius: 3px;
  overflow: hidden;
  flex-shrink: 0;
}
.mini-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease;
}
.score-val {
  font-size: 12px;
  font-weight: 600;
  color: #303651;
  font-variant-numeric: tabular-nums;
  min-width: 36px;
  text-align: right;
}
.best-value .score-val { color: #4f6ef6; }
.na { color: #ccc; }

.cp-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(2px);
  border-radius: 10px;
  font-size: 13px;
  color: #666;
  gap: 6px;
  z-index: 5;
}

/* 面板过渡 */
.panel-expand-enter-active,
.panel-expand-leave-active {
  transition: transform 0.3s cubic-bezier(0.25,0.46,0.45,0.94), opacity 0.25s ease;
}
.panel-expand-enter,
.panel-expand-leave-to {
  transform: translateX(-20px);
  opacity: 0;
}

/* 标签过渡 */
.tab-fade-enter-active,
.tab-fade-leave-active {
  transition: opacity 0.2s ease;
}
.tab-fade-enter,
.tab-fade-leave-to {
  opacity: 0;
}
</style>
