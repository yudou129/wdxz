<template>
  <transition name="slide">
    <div v-if="visible" class="sidebar-panel" :style="{ width: width + 'px' }">
      <div class="sidebar-header">
        <span class="sidebar-title">{{ title }}</span>
        <el-button type="text" icon="el-icon-close" @click="$emit('close')" />
      </div>
      <div class="sidebar-body">
        <div v-if="showYearPicker" class="year-picker">
          <span class="label">年份：</span>
          <el-select :value="year" size="mini" @change="$emit('year-change', $event)">
            <el-option v-for="y in years" :key="y" :label="y" :value="y" />
          </el-select>
        </div>

        <template v-if="mode === 'grid-only'">
          <ScoreCard :score="gridData.siteScore" :rank="gridRank" label="选址得分" />
          <IndicatorSection title="分项指标" :items="gridIndicators" grouped />
          <div class="info-section">
            <p>网格编码：{{ gridData.gridCode }}</p>
            <p>所属区县：{{ gridData.district || '-' }}</p>
            <el-button type="text" @click="$emit('view-detail', 'grid')">查看详情 →</el-button>
          </div>
        </template>

        <template v-if="mode === 'branch-only'">
          <ScoreCard :score="overallScore" :rank="branchData.rankNum" label="综合得分" />
          <BranchScores :scores="branchScores" />
          <div class="info-section">
            <p>网点名称：{{ branchData.secondaryBranch }}</p>
            <p>地址：{{ branchData.address }}</p>
            <p>总面积：{{ branchData.totalArea }}m²</p>
            <p>总人数：{{ branchData.totalStaff }}人</p>
            <p>个人经理：{{ branchData.personalManager }}人</p>
            <p>对公经理：{{ branchData.corporateManager }}人</p>
            <p>现金柜台：{{ branchData.cashCounter }}个</p>
            <p>非现金柜台：{{ branchData.nonCashCounter }}个</p>
            <el-button type="text" @click="$emit('view-detail', 'branch')">查看详情 →</el-button>
          </div>
        </template>

        <template v-if="mode === 'split'">
          <div class="split-layout">
            <div class="split-left">
              <h4>📊 网格资源</h4>
              <ScoreCard :score="gridData.siteScore" :rank="gridRank" label="选址得分" size="small" />
              <IndicatorSection title="资源概况" :items="gridIndicators" compact grouped />
              <p class="meta">编码：{{ gridData.gridCode }}</p>
            </div>
            <div class="split-right">
              <h4>🏦 网点经营</h4>
              <ScoreCard :score="overallScore" :rank="branchData.rankNum" label="综合得分" size="small" />
              <BranchScores :scores="branchScores" compact />
              <p class="meta">{{ branchData.secondaryBranch }} · {{ branchData.totalStaff }}人</p>
            </div>
          </div>
          <el-button type="text" style="width:100%;text-align:center;" @click="$emit('view-detail', 'all')">
            📋 查看详细指标数据表
          </el-button>
        </template>
      </div>
    </div>
  </transition>
</template>

<script>
import ScoreCard from './ScoreCard'
import IndicatorSection from './IndicatorSection'
import BranchScores from './BranchScores'

export default {
  name: 'SidebarPanel',
  components: { ScoreCard, IndicatorSection, BranchScores },
  props: {
    visible: Boolean,
    mode: { type: String, default: 'grid-only' },
    width: { type: Number, default: 380 },
    gridData: { type: Object, default: () => ({}) },
    gridRank: { type: Number, default: null },
    gridIndicators: { type: Array, default: () => [] },
    branchData: { type: Object, default: () => ({}) },
    branchScores: { type: Array, default: () => [] },
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
  }
}
</script>

<style scoped>
.sidebar-panel {
  position: absolute; left: 12px; top: 60px; bottom: 12px;
  background: #fff; border-radius: 8px; box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  z-index: 1000; display: flex; flex-direction: column; overflow: hidden;
  transition: width 0.3s ease;
}
.sidebar-header { padding: 14px 16px; border-bottom: 1px solid #f0f0f0; display: flex; justify-content: space-between; align-items: center; }
.sidebar-title { font-weight: 600; font-size: 15px; color: #333; }
.sidebar-body { flex: 1; overflow-y: auto; padding: 12px 16px; }
.year-picker { margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }
.year-picker .label { font-size: 13px; color: #888; }
.info-section { font-size: 13px; color: #666; margin-top: 16px; padding-top: 12px; border-top: 1px solid #f0f0f0; }
.info-section p { margin: 4px 0; }
.split-layout { display: flex; gap: 16px; }
.split-left, .split-right { flex: 1; min-width: 0; }
.split-left h4, .split-right h4 { font-size: 13px; margin: 0 0 8px; color: #555; }
.meta { font-size: 12px; color: #999; margin-top: 8px; }
.slide-enter-active, .slide-leave-active { transition: transform 0.3s ease, opacity 0.3s ease; }
.slide-enter, .slide-leave-to { transform: translateX(-100%); opacity: 0; }
</style>
