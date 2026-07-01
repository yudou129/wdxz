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
          <!-- 合并头卡：网格编号 + 坐标 + 得分 -->
          <GridInfoCard :grid="gridData" :score="gridScore" />

          <!-- 得分排名：RankBadge 水平并列 + 进度条 -->
          <div class="section-title"><i class="el-icon-trophy" /> 得分排名</div>
          <div class="rank-row">
            <RankBadge label="全市排名" :rank="gridRankMeta.cityRank" :total="gridRankMeta.cityTotal" />
            <RankBadge label="区县排名" :rank="gridRankMeta.districtRank" :total="gridRankMeta.districtTotal" />
          </div>
          <ScoreProgressBar
            :myScore="gridScore"
            :districtTopScore="gridRankMeta.districtTopScore"
            :cityTopScore="gridRankMeta.topScore" />

          <!-- 三聚集指标：雷达图 + 得分表 + 入口链接 -->
          <div class="section-title"><i class="el-icon-data-line" /> 三聚集指标</div>
          <PillarRadar :pillar="pillar" :pillarGap="pillarGap" :compact="mode === 'split'"
            @view-detail="$emit('view-detail', 'grid')" />

          <!-- 空白服务点：最近网点 -->
          <div v-if="nearestBranch && nearestBranch.distance > 0" class="nb-section">
            <div class="section-title"><i class="el-icon-map-location" /> 最近网点</div>
            <div class="nb-card">
              <div class="nb-card-name">{{ nearestBranch.branchName || nearestBranch.primaryBranch }}</div>
              <div class="nb-card-dist">
                <i class="el-icon-location-outline" /> 距离网格中心 {{ nearestBranch.distance }}km
              </div>
            </div>
          </div>
        </template>
        </div>

        <!-- ===== 网点内容 ===== -->
        <div :class="{ 'split-col': mode === 'split' }">
        <template v-if="mode === 'branch-only' || mode === 'split'">
          <BranchInfoCard :branch="branchData" @zoom="$emit('zoom-branch')" />

          <ScoreCard :score="overallScore" :rank="branchRankMeta.cityRank" :gap="branchScoreGap" label="内部效能得分与排名" size="small"
            :branch-rank="branchRankMeta.branchRank" :branch-total="branchRankMeta.branchTotal"
            :city-rank="branchRankMeta.cityRank" :city-total="branchRankMeta.cityTotal" />

          <div class="info-card">
            <div class="section-title"><i class="el-icon-trophy" /> 四象限所在位置</div>
            <div class="qp-block">
              <QuadrantPosition :quadrant="branchQuadrant" />
            </div>
          </div>

          <div class="info-card">
            <div class="section-title"><i class="el-icon-s-data" /> 效能得分</div>
            <BranchScores :scores="branchScores" />
            <el-button type="text" class="detail-link" @click="$emit('view-detail', 'branch')">
              <i class="el-icon-document" /> 查看详细指标数据
            </el-button>
          </div>

          <div class="info-card">
            <PeerBankSection :items="peerBanks" />

            <div v-if="nearbyBranches.length" class="nb-section">
              <div class="section-title"><i class="el-icon-map-location" /> 周围网点 <span class="ps-count">{{ nearbyBranches.length }}</span></div>
              <div v-for="(item, i) in nearbyBranches.slice(0, 5)" :key="i" class="nb-row">
                <span class="nb-name">{{ item.branchName }}</span>
                <span class="nb-dist">{{ item.distance }}km</span>
              </div>
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
import BranchScores from './BranchScores'
import BranchInfoCard from './BranchInfoCard'
import GridInfoCard from './GridInfoCard'
import RankBadge from './RankBadge'
import QuadrantPosition from './QuadrantPosition'
import ScoreProgressBar from './ScoreProgressBar'
import PillarRadar from './PillarRadar'
import PeerBankSection from './PeerBankSection'

export default {
  name: 'SidebarPanel',
  components: {
    ScoreCard, BranchScores,
    BranchInfoCard, GridInfoCard, RankBadge, QuadrantPosition, ScoreProgressBar, PillarRadar,
    PeerBankSection
  },
  props: {
    visible: Boolean,
    mode: { type: String, default: 'grid-only' },
    width: { type: Number, default: 380 },
    gridData: { type: Object, default: () => ({}) },
    gridRank: { type: Number, default: null },
    gridRankMeta: { type: Object, default: () => ({ cityRank: 0, cityTotal: 0, districtRank: 0, districtTotal: 0, scoreGap: 0, topScore: 0, districtTopScore: 0, districtScoreGap: 0 }) },
    gridIndicators: { type: Array, default: () => [] },
    branchData: { type: Object, default: () => ({}) },
    branchScores: { type: Array, default: () => [] },
    branchRankMeta: { type: Object, default: () => ({ branchRank: 0, branchTotal: 0, cityRank: 0, cityTotal: 0 }) },
    branchQuadrant: { type: String, default: '' },
    pillar: { type: Object, default: () => ({ population: { score: 0, count: 0 }, enterprise: { score: 0, count: 0 }, business: { score: 0, count: 0 } }) },
    peerBanks: { type: Array, default: () => [] },
    nearbyBranches: { type: Array, default: () => [] },
    pillarGap: { type: Object, default: () => ({ population: { maxCity: 0, maxDistrict: 0, gapCity: 0, gapDistrict: 0, name: '---' }, enterprise: { maxCity: 0, maxDistrict: 0, gapCity: 0, gapDistrict: 0, name: '---' }, business: { maxCity: 0, maxDistrict: 0, gapCity: 0, gapDistrict: 0, name: '---' } }) },
    years: { type: Array, default: () => [] },
    year: { type: Number, default: null },
    nearestBranch: { type: Object, default: null }
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
    },
    gridScore() {
      return this.gridData && this.gridData.siteScore != null ? this.gridData.siteScore : 0
    },
    branchScoreGap() {
      return this.branchRankMeta ? (this.branchRankMeta.scoreGap || 0) : 0
    }
  },
}
</script>

<style scoped>
.sidebar-panel {
  position: absolute; left: 12px; top: 100px; bottom: 12px;
  isolation: isolate; overflow: visible; border-radius: 10px;
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
.sidebar-title { font-weight: 700; font-size: 16px; color: #232845; }
.close-btn { color: #444; }
.close-btn:hover { color: #4f6ef6; }
.sidebar-body { flex: 1; overflow-y: auto; padding: 12px 16px; }
.sidebar-body::-webkit-scrollbar { width: 4px; }
.sidebar-body::-webkit-scrollbar-thumb { background: rgba(79,110,246,0.15); border-radius: 4px; }
.year-picker { margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }
.year-picker .label { font-size: 13px; color: #444; font-weight: 500; }
.thin-divider {
  margin: 10px 0; border: none; height: 1px;
  background: linear-gradient(90deg, rgba(79,110,246,0.25) 0%, rgba(79,110,246,0.06) 60%, transparent 100%) !important;
}
.section-title {
  font-size: 14px; font-weight: 600; color: #333; margin-bottom: 6px;
  display: flex; align-items: center; gap: 4px;
  border-left: 3px solid #4f6ef6; padding-left: 8px;
  background: linear-gradient(90deg, rgba(79,110,246,0.04) 0%, transparent 100%);
}
.section-title i { color: #4f6ef6; font-size: 13px; }
.info-card {
  background: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 10px;
  border: 1px solid rgba(79, 110, 246, 0.08);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
}
.rank-row {
  display: flex;
  gap: 10px;
}
.qp-block {
  display: flex;
  justify-content: center;
  padding: 6px 0 10px;
}
.detail-link {
  display: block; width: 100%; text-align: center;
  padding: 8px 0; margin-top: 4px;
  background: rgba(79,110,246,0.04); border-radius: 6px;
  color: #4f6ef6; font-weight: 500; font-size: 13px;
  transition: background 0.2s ease, box-shadow 0.2s ease;
}
.detail-link:hover { background: rgba(79,110,246,0.08); color: #3b54d4; }
.full-width { width: 100%; text-align: center; }
.split-layout { display: flex; gap: 16px; }
.split-col { flex: 1; min-width: 0; }
.mode-badge {
  font-size: 12px; font-weight: 600; color: #4f6ef6;
  background: rgba(79,110,246,0.08); padding: 2px 10px; border-radius: 10px;
}
/* 周围网点 */
.nb-section { margin-bottom: 0; }
.nb-section .section-title { margin-bottom: 6px; }
.ps-count {
  font-size: 12px; font-weight: 400; color: #666; margin-left: auto;
}
.nb-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 10px; font-size: 13px; border-radius: 6px;
  transition: background 0.15s;
}
.nb-row + .nb-row { margin-top: 2px; }
.nb-row:hover { background: rgba(79, 110, 246, 0.04); }
.nb-name { color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; font-weight: 500; }
.nb-dist {
  color: #4f6ef6; font-weight: 600; flex-shrink: 0; margin-left: 8px;
  font-size: 13px; background: rgba(79, 110, 246, 0.06);
  padding: 1px 8px; border-radius: 10px; font-variant-numeric: tabular-nums;
}
.nb-card {
  background: #fff; border-radius: 8px; padding: 12px 14px;
  border: 1px solid rgba(79,110,246,0.08);
  box-shadow: 0 1px 4px rgba(0,0,0,0.03);
}
.nb-card-name {
  font-size: 14px; font-weight: 600; color: #232845;
  margin-bottom: 4px;
}
.nb-card-dist {
  font-size: 13px; color: #888;
  display: flex; align-items: center; gap: 4px;
}
.nb-card-dist i { color: #4f6ef6; }
.slide-enter-active, .slide-leave-active { transition: transform 0.3s cubic-bezier(0.25,0.46,0.45,0.94), opacity 0.25s ease; }
.slide-enter, .slide-leave-to { transform: translateX(-20px); opacity: 0; }
</style>
