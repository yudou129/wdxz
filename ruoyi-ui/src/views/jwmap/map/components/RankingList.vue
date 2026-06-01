<template>
  <transition name="rank-slide">
    <div class="ranking-panel" v-if="visible">
      <div class="ranking-header">
        <span class="ranking-title">
          <i :class="titleIcon" /> {{ title }}
        </span>
        <el-button type="text" icon="el-icon-close" size="mini" class="close-btn" @click="$emit('close')" />
      </div>
      <div class="ranking-body">
        <div v-for="(item, i) in items" :key="item.id"
             class="ranking-item" @click="$emit('item-click', item)">
          <span class="rank-num" :class="{ 'rank-top': i < 3 }">{{ padRank(i + 1 + (page - 1) * pageSize) }}</span>
          <span class="rank-name">{{ item.name }}</span>
          <span class="rank-score">{{ item.score.toFixed(2) }}</span>
        </div>
        <el-button v-if="hasMore" type="text" class="load-more" @click="$emit('load-more')" :loading="loading">
          加载更多
        </el-button>
        <div v-if="!loading && items.length === 0" class="empty-hint">暂无排名数据</div>
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
    type: { type: String, default: 'grid' }
  },
  computed: {
    titleIcon() {
      return this.type === 'branch' ? 'el-icon-medal' : 'el-icon-trophy'
    }
  },
  methods: {
    padRank(n) {
      return n <= 9 ? '0' + n : String(n)
    }
  }
}
</script>

<style scoped>
.ranking-panel {
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: 272px;
  max-height: 380px;
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
}
.ranking-panel::before {
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
.ranking-panel::after {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: 9px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  pointer-events: none;
}
@media (prefers-reduced-transparency: reduce) {
  .ranking-panel {
    background: rgba(255, 255, 255, 0.94);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}
.ranking-header {
  padding: 12px 14px;
  border-bottom: 1px solid rgba(79, 110, 246, 0.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}
.ranking-title {
  font-weight: 700;
  font-size: 13px;
  color: #232845;
  display: flex;
  align-items: center;
  gap: 6px;
}
.ranking-title i {
  color: #4f6ef6;
}
.close-btn {
  color: #8c95a8;
  padding: 2px 4px;
}
.close-btn:hover {
  color: #4f6ef6;
}
.ranking-body {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}
.ranking-body::-webkit-scrollbar {
  width: 4px;
}
.ranking-body::-webkit-scrollbar-thumb {
  background: rgba(79, 110, 246, 0.15);
  border-radius: 4px;
}
.ranking-item {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.18s ease, padding-left 0.2s ease;
  border-left: 3px solid transparent;
}
.ranking-item:hover {
  background: rgba(79, 110, 246, 0.04);
  border-left-color: #4f6ef6;
}
.rank-num {
  width: 28px;
  color: #a0a8ba;
  font-variant-numeric: tabular-nums;
  font-weight: 500;
  font-size: 12px;
}
.rank-num.rank-top {
  color: #4f6ef6;
  font-weight: 700;
}
.rank-name {
  flex: 1;
  color: #303651;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-score {
  width: 50px;
  text-align: right;
  color: #4f6ef6;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  font-size: 13px;
}
.load-more {
  width: 100%;
  text-align: center;
  font-size: 12px;
  color: #4f6ef6;
  padding: 8px;
}
.empty-hint {
  text-align: center;
  color: #a0a8ba;
  font-size: 13px;
  padding: 24px 0;
}
.rank-slide-enter-active, .rank-slide-leave-active {
  transition: transform 0.28s cubic-bezier(0.25, 0.46, 0.45, 0.94), opacity 0.2s ease;
}
.rank-slide-enter, .rank-slide-leave-to {
  transform: translateY(12px);
  opacity: 0;
}
</style>
