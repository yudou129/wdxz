<template>
  <div class="ranking-panel" v-if="visible">
    <div class="ranking-header">
      <span class="ranking-title">{{ title }}</span>
      <el-button type="text" icon="el-icon-close" size="mini" @click="$emit('close')" />
    </div>
    <div class="ranking-body">
      <div v-for="(item, i) in items" :key="item.id"
           class="ranking-item" @click="$emit('item-click', item)">
        <span class="rank-num">#{{ i + 1 + (page - 1) * pageSize }}</span>
        <span class="rank-name">{{ item.name }}</span>
        <span class="rank-score">{{ item.score.toFixed(2) }}</span>
      </div>
      <el-button v-if="hasMore" type="text" class="load-more" @click="$emit('load-more')" :loading="loading">
        查看更多 →
      </el-button>
    </div>
  </div>
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
    loading: { type: Boolean, default: false }
  }
}
</script>

<style scoped>
.ranking-panel {
  position: absolute; right: 12px; bottom: 12px; width: 260px; max-height: 360px;
  background: #fff; border-radius: 8px; box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  z-index: 1000; display: flex; flex-direction: column; overflow: hidden;
}
.ranking-header { padding: 10px 12px; border-bottom: 1px solid #f0f0f0; display: flex; justify-content: space-between; align-items: center; }
.ranking-title { font-weight: 600; font-size: 13px; color: #333; }
.ranking-body { flex: 1; overflow-y: auto; padding: 4px 0; }
.ranking-item { display: flex; align-items: center; padding: 6px 12px; cursor: pointer; font-size: 13px; transition: background 0.15s; }
.ranking-item:hover { background: #f5f5f5; }
.rank-num { width: 36px; color: #888; font-variant-numeric: tabular-nums; }
.rank-name { flex: 1; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-score { width: 48px; text-align: right; color: #52c41a; font-weight: 600; }
.load-more { width: 100%; text-align: center; font-size: 12px; }
</style>
