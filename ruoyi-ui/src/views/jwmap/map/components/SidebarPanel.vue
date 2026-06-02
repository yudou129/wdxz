<template>
  <transition name="slide">
    <div v-if="visible" class="sidebar-panel" :style="{ width: width + 'px' }">
      <div class="sidebar-header">
        <span class="sidebar-title">{{ title }}</span>
        <el-button type="text" icon="el-icon-close" class="close-btn" @click="$emit('close')" />
      </div>
      <div class="sidebar-body">
        <div v-if="showYearPicker" class="year-picker">
          <span class="label">年份</span>
          <el-select :value="year" size="mini" @change="$emit('year-change', $event)">
            <el-option v-for="y in years" :key="y" :label="String(y)" :value="y" />
          </el-select>
        </div>

        <div :class="{ 'split-layout': mode === 'split' }">
        <!-- ===== 网格内容 ===== -->
        <div :class="{ 'split-col': mode === 'split' }">
        <template v-if="mode === 'grid-only' || mode === 'split'">
          <GridInfoCard :grid="gridData" />
          <ScoreCard :score="gridData.siteScore" :rank="gridRank" label="选址得分" size="small" />
          <el-divider class="thin-divider" />

          <div class="section-title"><i class="el-icon-trophy" /> 得分排名</div>
          <RankBadge label="全市排名" :rank="gridRankMeta.cityRank" :total="gridRankMeta.cityTotal" />
          <RankBadge label="区县排名" :rank="gridRankMeta.districtRank" :total="gridRankMeta.districtTotal" />
          <div class="score-gap" v-if="gridRankMeta.scoreGap > 0">
            距全市最高分 <b>{{ gridRankMeta.scoreGap.toFixed(6) }}</b>
          </div>
          <div class="pillar-gap-row">
            <span class="pg-label">三聚集与全市最高差距</span>
            <span>人口 {{ fmtGap(pillarGap.population.gap) }}</span>
            <span>企业 {{ fmtGap(pillarGap.enterprise.gap) }}</span>
            <span>商圈 {{ fmtGap(pillarGap.business.gap) }}</span>
          </div>
          <el-divider class="thin-divider" />

          <div class="section-title"><i class="el-icon-data-line" /> 三聚集指标</div>
          <ThreeColumnCards :pop="pillar.population" :ent="pillar.enterprise" :biz="pillar.business" />

          <div class="section-title"><i class="el-icon-s-data" /> 分项指标</div>
          <el-button type="text" class="detail-link" @click="$emit('view-detail', 'grid')">
            <i class="el-icon-document" /> 查看具体指标数据
          </el-button>
        </template>
        </div>

        <!-- ===== 网点内容 ===== -->
        <div :class="{ 'split-col': mode === 'split' }">
        <template v-if="mode === 'branch-only' || mode === 'split'">
          <BranchInfoCard :branch="branchData" @zoom="$emit('zoom-branch')" />
          <el-divider class="thin-divider" />

          <div class="section-title"><i class="el-icon-trophy" /> 内部效能排名</div>
          <RankBadge label="支行排名" :rank="branchRankMeta.branchRank" :total="branchRankMeta.branchTotal" />
          <RankBadge label="分行排名" :rank="branchRankMeta.cityRank" :total="branchRankMeta.cityTotal" />

          <QuadrantPosition :quadrant="branchQuadrant" />
          <el-divider class="thin-divider" />

          <div class="section-title"><i class="el-icon-s-data" /> 效能得分</div>
          <BranchScores :scores="branchScores" />
          <el-button type="text" class="detail-link" @click="$emit('view-detail', 'branch')">
            <i class="el-icon-document" /> 查看详细指标数据
          </el-button>

          <PeerBankSection :items="peerBanks" />
          <div v-if="nearbyBranches.length" class="peer-section">
            <div class="section-title"><i class="el-icon-map-location" /> 周围网点 ({{ nearbyBranches.length }})</div>
            <div v-for="(item, i) in nearbyBranches.slice(0, 5)" :key="i" class="peer-row">
              <span class="peer-name">{{ item.branchName }}</span>
              <span class="peer-dist">{{ item.distance }}km</span>
            </div>
          </div>
        </template>
        </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import ScoreCard from './ScoreCard'
import IndicatorSection from './IndicatorSection'
import BranchScores from './BranchScores'
import BranchInfoCard from './BranchInfoCard'
import GridInfoCard from './GridInfoCard'
import RankBadge from './RankBadge'
import QuadrantPosition from './QuadrantPosition'
import ThreeColumnCards from './ThreeColumnCards'
import PeerBankSection from './PeerBankSection'

export default {
  name: 'SidebarPanel',
  components: {
    ScoreCard, IndicatorSection, BranchScores,
    BranchInfoCard, GridInfoCard, RankBadge, QuadrantPosition, ThreeColumnCards,
    PeerBankSection
  },
  props: {
    visible: Boolean,
    mode: { type: String, default: 'grid-only' },
    width: { type: Number, default: 380 },
    gridData: { type: Object, default: () => ({}) },
    gridRank: { type: Number, default: null },
    gridRankMeta: { type: Object, default: () => ({ cityRank: 0, cityTotal: 0, districtRank: 0, districtTotal: 0, scoreGap: 0 }) },
    gridIndicators: { type: Array, default: () => [] },
    branchData: { type: Object, default: () => ({}) },
    branchScores: { type: Array, default: () => [] },
    branchRankMeta: { type: Object, default: () => ({ branchRank: 0, branchTotal: 0, cityRank: 0, cityTotal: 0 }) },
    branchQuadrant: { type: String, default: '' },
    pillar: { type: Object, default: () => ({ population: { score: 0, count: 0 }, enterprise: { score: 0, count: 0 }, business: { score: 0, count: 0 } }) },
    peerBanks: { type: Array, default: () => [] },
    nearbyBranches: { type: Array, default: () => [] },
    pillarGap: { type: Object, default: () => ({ population: { gap: 0 }, enterprise: { gap: 0 }, business: { gap: 0 } }) },
    years: { type: Array, default: () => [] },
    year: { type: Number, default: null }
  },
  computed: {
    title() {
      return { 'grid-only': '网格信息', 'branch-only': '网点信息', 'split': '网格详情（含网点）' }[this.mode] || '详情'
    },
    showYearPicker() {
      return this.mode === 'branch-only' || this.mode === 'split'
    },
    overallScore() {
      const s = this.branchScores.find(s => s.scoreCategory === 'overall')
      return s ? s.categoryScore : null
    }
  },
  methods: {
    fmtGap(v) { return typeof v === 'number' ? v.toFixed(4) : '-' }
  }
}
</script>

<style scoped>
.sidebar-panel {
  position: absolute; left: 12px; top: 60px; bottom: 12px;
  isolation: isolate; overflow: hidden; border-radius: 10px;
  border: 1px solid rgba(255,255,255,0.28);
  background: linear-gradient(135deg, rgba(255,255,255,0.28), rgba(255,255,255,0.08)), rgba(255,255,255,0.10);
  backdrop-filter: blur(22px) saturate(170%) contrast(1.04);
  -webkit-backdrop-filter: blur(22px) saturate(170%) contrast(1.04);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.44), inset 0 -1px 0 rgba(255,255,255,0.10), 0 8px 32px rgba(79,110,246,0.08), 0 1px 4px rgba(0,0,0,0.05);
  z-index: 1000; display: flex; flex-direction: column; transition: width 0.3s ease;
}
.sidebar-panel::before {
  content: ''; position: absolute; inset: 0; z-index: -1; border-radius: inherit;
  background: radial-gradient(circle at 20% 0%, rgba(255,255,255,0.48), transparent 34%), linear-gradient(90deg, rgba(255,255,255,0.16), transparent 42%, rgba(255,255,255,0.12));
  pointer-events: none;
}
.sidebar-panel::after {
  content: ''; position: absolute; inset: 1px; border-radius: 9px;
  border: 1px solid rgba(255,255,255,0.12); pointer-events: none;
}
@media (prefers-reduced-transparency: reduce) {
  .sidebar-panel { background: rgba(255,255,255,0.94); backdrop-filter: none; -webkit-backdrop-filter: none; }
}
.sidebar-header { padding: 14px 16px; border-bottom: 1px solid rgba(79,110,246,0.08); display: flex; justify-content: space-between; align-items: center; flex-shrink: 0; }
.sidebar-title { font-weight: 700; font-size: 15px; color: #232845; }
.close-btn { color: #8c95a8; }
.close-btn:hover { color: #4f6ef6; }
.sidebar-body { flex: 1; overflow-y: auto; padding: 12px 16px; }
.sidebar-body::-webkit-scrollbar { width: 4px; }
.sidebar-body::-webkit-scrollbar-thumb { background: rgba(79,110,246,0.15); border-radius: 4px; }
.year-picker { margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }
.year-picker .label { font-size: 13px; color: #6b7280; font-weight: 500; }
.thin-divider { margin: 10px 0; }
.section-title { font-size: 12px; font-weight: 600; color: #556; margin-bottom: 6px; display: flex; align-items: center; gap: 4px; }
.section-title i { color: #4f6ef6; font-size: 13px; }
.score-gap { font-size: 12px; color: #e6a23c; padding: 2px 0; }
.score-gap b { color: #e6a23c; }
.detail-link { margin-top: 2px; color: #4f6ef6; font-weight: 500; }
.detail-link:hover { color: #3b54d4; }
.full-width { width: 100%; text-align: center; }
.split-layout { display: flex; gap: 16px; }
.split-col { flex: 1; min-width: 0; }
.mode-badge {
  font-size: 11px; font-weight: 600; color: #4f6ef6;
  background: rgba(79,110,246,0.08); padding: 2px 10px; border-radius: 10px;
}
/* nearby branches + peer bank shared styles */
.peer-section { margin-top: 10px; padding-top: 10px; border-top: 1px solid rgba(79,110,246,0.08); }
.section-title { font-size: 12px; font-weight: 600; color: #556; margin-bottom: 6px; display: flex; align-items: center; gap: 4px; }
.section-title i { color: #4f6ef6; font-size: 13px; }
.peer-row { display: flex; justify-content: space-between; padding: 3px 0; font-size: 12px; }
.peer-name { color: #444; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.peer-dist { color: #4f6ef6; font-weight: 500; flex-shrink: 0; margin-left: 8px; }
.pillar-gap-row { font-size: 11px; color: #888; display: flex; flex-wrap: wrap; gap: 2px 8px; margin: 6px 0; }
.pg-label { width: 100%; font-weight: 500; color: #666; }
.slide-enter-active, .slide-leave-active { transition: transform 0.3s cubic-bezier(0.25,0.46,0.45,0.94), opacity 0.25s ease; }
.slide-enter, .slide-leave-to { transform: translateX(-20px); opacity: 0; }
</style>
