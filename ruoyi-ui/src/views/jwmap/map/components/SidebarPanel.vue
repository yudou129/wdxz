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

        <template v-if="mode === 'grid-only'">
          <ScoreCard :score="gridData.siteScore" :rank="gridRank" label="选址得分" />
          <IndicatorSection title="分项指标" :items="gridIndicators" grouped />
          <div class="info-section">
            <p>网格编码：{{ gridData.gridCode }}</p>
            <p>所属区县：{{ gridData.district || '-' }}</p>
            <el-button type="text" class="detail-link" @click="$emit('view-detail', 'grid')">
              <i class="el-icon-document" /> 查看详情
            </el-button>
          </div>
        </template>

        <template v-if="mode === 'branch-only'">
          <ScoreCard :score="overallScore" :rank="branchData.rankNum" label="综合得分" />
          <BranchScores :scores="branchScores" />
          <div class="info-section">
            <p>网点名称：{{ branchData.secondaryBranch }}</p>
            <p>地址：{{ branchData.address }}</p>
            <p>总面积：{{ branchData.totalArea }}m&sup2;</p>
            <p>总人数：{{ branchData.totalStaff }}人</p>
            <el-button type="text" class="detail-link" @click="$emit('view-detail', 'branch')">
              <i class="el-icon-document" /> 查看详情
            </el-button>
          </div>
        </template>

        <template v-if="mode === 'split'">
          <div class="split-layout">
            <div class="split-left">
              <h4><i class="el-icon-s-grid" /> 网格资源</h4>
              <ScoreCard :score="gridData.siteScore" :rank="gridRank" label="选址得分" size="small" />
              <IndicatorSection title="资源概况" :items="gridIndicators" compact grouped />
              <p class="meta">编码：{{ gridData.gridCode }}</p>
            </div>
            <div class="split-right">
              <h4><i class="el-icon-office-building" /> 网点经营</h4>
              <ScoreCard :score="overallScore" :rank="branchData.rankNum" label="综合得分" size="small" />
              <BranchScores :scores="branchScores" compact />
              <p class="meta">{{ branchData.secondaryBranch }} &middot; {{ branchData.totalStaff }}人</p>
            </div>
          </div>
          <el-button type="text" class="detail-link full-width" @click="$emit('view-detail', 'all')">
            <i class="el-icon-tickets" /> 查看详细指标数据表
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
  position: absolute;
  left: 12px;
  top: 60px;
  bottom: 12px;
  isolation: isolate;
  overflow: hidden;
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
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
}
.sidebar-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  border-radius: inherit;
  background:
    radial-gradient(circle at 20% 0%, rgba(255, 255, 255, 0.48), transparent 34%),
    linear-gradient(90deg, rgba(255, 255, 255, 0.16), transparent 42%, rgba(255, 255, 255, 0.12));
  pointer-events: none;
}
.sidebar-panel::after {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: 9px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  pointer-events: none;
}
@media (prefers-reduced-transparency: reduce) {
  .sidebar-panel {
    background: rgba(255, 255, 255, 0.94);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}
.sidebar-header {
  padding: 16px 18px;
  border-bottom: 1px solid rgba(79, 110, 246, 0.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}
.sidebar-title {
  font-weight: 700;
  font-size: 15px;
  color: #232845;
  letter-spacing: -0.2px;
}
.close-btn {
  color: #8c95a8;
  font-size: 16px;
}
.close-btn:hover {
  color: #4f6ef6;
}
.sidebar-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 18px;
}
.sidebar-body::-webkit-scrollbar {
  width: 4px;
}
.sidebar-body::-webkit-scrollbar-thumb {
  background: rgba(79, 110, 246, 0.15);
  border-radius: 4px;
}
.year-picker {
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.year-picker .label {
  font-size: 13px;
  color: #6b7280;
  font-weight: 500;
}
.info-section {
  font-size: 13px;
  color: #556;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid rgba(79, 110, 246, 0.08);
}
.info-section p {
  margin: 4px 0;
  line-height: 1.5;
}
.detail-link {
  margin-top: 4px;
  color: #4f6ef6;
  font-weight: 500;
}
.detail-link:hover {
  color: #3b54d4;
}
.full-width {
  width: 100%;
  text-align: center;
}
.split-layout {
  display: flex;
  gap: 18px;
}
.split-left, .split-right {
  flex: 1;
  min-width: 0;
}
.split-left h4, .split-right h4 {
  font-size: 13px;
  margin: 0 0 10px;
  color: #454e6b;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}
.split-left h4 i, .split-right h4 i {
  color: #4f6ef6;
}
.meta {
  font-size: 12px;
  color: #8c95a8;
  margin-top: 8px;
}
.slide-enter-active, .slide-leave-active {
  transition: transform 0.3s cubic-bezier(0.25, 0.46, 0.45, 0.94), opacity 0.25s ease;
}
.slide-enter, .slide-leave-to {
  transform: translateX(-20px);
  opacity: 0;
}
</style>
