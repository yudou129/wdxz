<template>
  <transition name="rank-slide">
    <div class="ranking-panel" v-if="visible">
      <div class="ranking-header">
        <span class="ranking-title">
          <i :class="titleIcon" /> {{ title }}
        </span>
        <div class="header-right">
          <el-radio-group v-if="showTypeSwitch" v-model="localType" size="mini" class="tb-type-group" @change="$emit('type-change', localType)">
            <el-radio-button label="grid">网格</el-radio-button>
            <el-radio-button label="branch">网点</el-radio-button>
          </el-radio-group>
          <el-radio-group v-if="showFocusTabs" v-model="focusTab" size="mini" class="tb-focus-group" @change="$emit('focus-change', focusTab)">
            <el-radio-button label="population">人口</el-radio-button>
            <el-radio-button label="enterprise">企业</el-radio-button>
            <el-radio-button label="business">商圈</el-radio-button>
          </el-radio-group>
          <el-button type="text" icon="el-icon-close" size="mini" class="close-btn" @click="$emit('close')" />
        </div>
      </div>
      <div class="ranking-body">
        <div v-for="(item, i) in items" :key="item.id"
             :class="['ranking-item', { 'rank-podium': i < 3 }]"
             @click="$emit('item-click', item)">
          <span class="rn-medal" v-if="i < 3">{{ medals[i] }}</span>
          <span class="rank-num" :class="{ 'rank-top': i < 3 }">{{ padRank(i + 1 + (page - 1) * pageSize) }}</span>
          <span :class="['rank-name', { 'rank-name-focus': i < 3 }]">{{ item.name }}</span>
          <span :class="['rank-score', 'rank-score-' + (i + 1)]">{{ item.score.toFixed(4) }}</span>
        </div>
        <el-button v-if="hasMore" type="text" class="load-more" @click="$emit('load-more')" :loading="loading">
          加载更多
        </el-button>
        <div v-if="!loading && items.length === 0" class="empty-hint">
          <i class="el-icon-document" /> 暂无排名数据
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: 'RankingList',
  props: {
    visible: Boolean,
    title: { type: String, default: '排名列表' },
    items: { type: Array, default: () => [] },
    page: { type: Number, default: 1 },
    pageSize: { type: Number, default: 20 },
    hasMore: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
    type: { type: String, default: 'grid' },
    showFocusTabs: { type: Boolean, default: false },
    showTypeSwitch: { type: Boolean, default: false }
  },
  data() {
    return { focusTab: 'population', localType: this.type }
  },
  watch: {
    type(v) { this.localType = v }
  },
  computed: {
    titleIcon() {
      if (this.showFocusTabs) return 'el-icon-data-line'
      return this.type === 'branch' ? 'el-icon-medal' : 'el-icon-trophy'
    },
    medals() {
      return ['🥇', '🥈', '🥉']
    }
  },
  methods: {
    padRank(n) { return n <= 9 ? '0' + n : String(n) }
  }
}
</script>

<style scoped>
.ranking-panel {
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: min(320px, 88vw);
  max-height: 70vh;
  isolation: isolate;
  overflow: visible;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(18px) saturate(160%);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.44),
    0 8px 32px rgba(79, 110, 246, 0.08),
    0 1px 4px rgba(0, 0, 0, 0.04);
  z-index: 1000;
  display: flex;
  flex-direction: column;
}
.ranking-panel::before {
  content: '';
  position: absolute; inset: 0; z-index: -1; border-radius: inherit;
  background:
    radial-gradient(circle at 20% 0%, rgba(255,255,255,0.4), transparent 34%),
    linear-gradient(90deg, rgba(255,255,255,0.12), transparent 42%, rgba(255,255,255,0.08));
  pointer-events: none;
}
@media (prefers-reduced-transparency: reduce) {
  .ranking-panel { background: rgba(255,255,255,0.94); backdrop-filter: none; -webkit-backdrop-filter: none; }
}

/* ===== 头部 ===== */
.ranking-header {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(79,110,246,0.06);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}
.ranking-title {
  font-weight: 700; font-size: 15px; color: #232845;
  display: flex; align-items: center; gap: 6px;
}
.ranking-title i { color: #4f6ef6; }
.header-right {
  display: flex; align-items: center; gap: 6px;
}
.header-right .el-radio-group >>> .el-radio-button__inner {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 0;
}
.tb-type-group { margin-right: 4px; }
.tb-type-group >>> .el-radio-button:first-child .el-radio-button__inner {
  border-radius: 6px 0 0 6px;
}
.tb-type-group >>> .el-radio-button:last-child .el-radio-button__inner {
  border-radius: 0 6px 6px 0;
}
.tb-focus-group >>> .el-radio-button:first-child .el-radio-button__inner {
  border-radius: 6px 0 0 6px;
}
.tb-focus-group >>> .el-radio-button:last-child .el-radio-button__inner {
  border-radius: 0 6px 6px 0;
}
.close-btn {
  color: #666; padding: 2px 4px; font-size: 14px;
}
.close-btn:hover { color: #4f6ef6; }

/* ===== 列表体 ===== */
.ranking-body {
  flex: 1; overflow-y: auto; padding: 4px 0;
}
.ranking-body::-webkit-scrollbar { width: 4px; }
.ranking-body::-webkit-scrollbar-thumb { background: rgba(79,110,246,0.1); border-radius: 4px; }

/* ===== 排名条目 ===== */
.ranking-item {
  display: flex;
  align-items: center;
  padding: 8px 16px 8px 14px;
  cursor: pointer;
  font-size: 13px;
  border-left: 3px solid transparent;
  transition: background 0.15s, border-color 0.15s;
}
.ranking-item:hover {
  background: rgba(79,110,246,0.04);
  border-left-color: #4f6ef6;
}
.ranking-item.rank-podium {
  margin: 2px 8px;
  border-radius: 8px;
  padding: 10px 12px 10px 10px;
  border-left-width: 0;
}
.ranking-item.rank-podium:nth-child(2) { background: linear-gradient(135deg, rgba(255,215,0,0.08), rgba(255,243,205,0.2)); }
.ranking-item.rank-podium:first-child  { background: linear-gradient(135deg, rgba(255,215,0,0.12), rgba(255,243,205,0.35)); }
.ranking-item.rank-podium:nth-child(3) { background: linear-gradient(135deg, rgba(168,178,196,0.08), rgba(227,230,237,0.2)); }
.ranking-item.rank-podium:nth-child(4) { background: linear-gradient(135deg, rgba(205,127,50,0.08), rgba(237,201,171,0.2)); }

/* ===== 奖牌 ===== */
.rn-medal {
  font-size: 18px;
  line-height: 1;
  margin-right: 6px;
  flex-shrink: 0;
}

/* ===== 排名号 ===== */
.rank-num {
  width: 28px;
  color: #666;
  font-variant-numeric: tabular-nums;
  font-weight: 500;
  font-size: 13px;
  flex-shrink: 0;
}
.rank-num.rank-top { color: #4f6ef6; font-weight: 700; }

/* ===== 名称 ===== */
.rank-name {
  flex: 1;
  color: #303651;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-name-focus { font-weight: 600; }

/* ===== 得分 ===== */
.rank-score {
  width: 60px;
  text-align: right;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  color: #4f6ef6;
  flex-shrink: 0;
}
.rank-score-1 { color: #d4a017; font-size: 15px; }
.rank-score-2 { color: #6b7280; font-size: 14px; }
.rank-score-3 { color: #cd7f32; font-size: 14px; }

/* ===== 加载更多 ===== */
.load-more {
  width: 100%;
  text-align: center;
  font-size: 13px;
  color: #666;
  padding: 10px 0;
  border-top: 1px solid #f0f0f0;
  margin-top: 4px;
  border-radius: 0;
}
.load-more:hover { color: #4f6ef6; }

/* ===== 空态 ===== */
.empty-hint {
  text-align: center;
  color: #666;
  font-size: 13px;
  padding: 32px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.empty-hint i {
  font-size: 28px;
  color: #cdd5e6;
}

/* ===== 动画 ===== */
.rank-slide-enter-active,
.rank-slide-leave-active {
  transition: transform 0.28s cubic-bezier(0.25,0.46,0.45,0.94), opacity 0.2s ease;
}
.rank-slide-enter,
.rank-slide-leave-to {
  transform: translateY(12px);
  opacity: 0;
}
</style>
