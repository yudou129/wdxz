<template>
  <transition name="panel-expand">
    <div v-if="visible" class="detail-panel" :style="{ left: leftPos + 'px' }">
      <div class="detail-header">
        <span class="detail-title">
          <i :class="mode === 'branch' ? 'el-icon-office-building' : 'el-icon-s-grid'" />
          {{ mode === 'branch' ? '网点详细指标数据' : '网格详细指标数据' }}
        </span>
        <el-button type="text" icon="el-icon-close" class="close-btn" @click="$emit('close')" />
      </div>
      <div class="detail-body">
        <!-- 网点模式 -->
        <template v-if="mode === 'branch' && groupedData.length">
          <div v-for="g in groupedData" :key="g.name" class="data-section">
            <h5 class="section-header">{{ g.name }}</h5>
            <div v-for="item in g.items" :key="item.name" class="data-row">
              <span class="data-label">{{ item.name }}</span>
              <span class="data-value" :class="{ highlight: item.highlight }">{{ item.value }}</span>
            </div>
          </div>
        </template>

        <!-- 网格模式：一级分类为标题，该分类下所有三级指标合并在一个堆叠百分比条上 -->
        <template v-if="mode === 'grid' && stackedGroups.length">
          <div v-for="l1 in stackedGroups" :key="l1.name" class="l1-section">
            <h5 class="l1-header">{{ l1.name }}</h5>
            <StackedBar v-for="l2 in l1.subs" :key="l2.name"
              :label="l1.name"
              :segments="l2.segments" />
          </div>
        </template>

        <div v-if="!hasContent" class="empty-hint">暂无详细指标数据</div>
      </div>
    </div>
  </transition>
</template>
<script>
import StackedBar from './StackedBar'

export default {
  name: 'DetailPanel',
  components: { StackedBar },
  props: {
    visible: Boolean,
    data: { type: Array, default: () => [] },
    mode: { type: String, default: 'branch' },
    leftPos: { type: Number, default: 420 }
  },
  computed: {
    hasContent() {
      if (this.mode === 'branch') return this.groupedData.length > 0
      if (this.mode === 'grid') return this.stackedGroups.length > 0
      return this.data.length > 0
    },
    groupedData() {
      if (this.mode !== 'branch' || !this.data.length) return []
      const map = {}
      for (const item of this.data) {
        const l1 = item.categoryLevel1 || '其他'
        if (!map[l1]) map[l1] = []
        map[l1].push({ name: item.name, value: item.value, highlight: item.highlight || false })
      }
      return Object.entries(map).map(([name, items]) => ({ name, items }))
    },
    // 网格模式：一级分类 → 该分类下所有三级指标合并在一个StackedBar上
    stackedGroups() {
      if (this.mode !== 'grid' || !this.data.length) return []

      const l1Map = {}
      for (const item of this.data) {
        const l1 = item.categoryLevel1 || '其他'
        const v = parseFloat(item.value)
        if (!l1Map[l1]) l1Map[l1] = { name: l1, items: [], total: 0 }
        l1Map[l1].items.push({ name: item.name, value: item.value, raw: v })
        if (!isNaN(v)) l1Map[l1].total += v
      }

      return Object.values(l1Map).map(g => {
        const total = g.total || 1
        return {
          name: g.name,
          subs: [{
            name: g.name,
            segments: g.items.map(item => ({
              name: item.name,
              value: item.value,
              pct: isNaN(item.raw) ? 0 : Math.max(Math.round(item.raw / total * 100), 1)
            }))
          }]
        }
      })
    }
  }
}
</script>
<style scoped>
.detail-panel {
  position: absolute; top: 60px; bottom: 12px; width: 420px;
  background: rgba(255,255,255,0.93);
  backdrop-filter: blur(18px); -webkit-backdrop-filter: blur(18px);
  border-radius: 10px;
  border: 1px solid rgba(79,110,246,0.14);
  box-shadow: 4px 0 24px rgba(79,110,246,0.08), 0 1px 3px rgba(0,0,0,0.03);
  z-index: 999; display: flex; flex-direction: column;
}
.detail-header {
  padding: 14px 18px; border-bottom: 1px solid rgba(79,110,246,0.08);
  display: flex; justify-content: space-between; align-items: center; flex-shrink: 0;
}
.detail-title { font-weight: 700; font-size: 14px; color: #232845; display: flex; align-items: center; gap: 6px; }
.detail-title i { color: #4f6ef6; }
.close-btn { color: #8c95a8; }
.close-btn:hover { color: #4f6ef6; }
.detail-body { flex: 1; overflow-y: auto; padding: 14px 18px; }
.detail-body::-webkit-scrollbar { width: 4px; }
.detail-body::-webkit-scrollbar-thumb { background: rgba(79,110,246,0.12); border-radius: 4px; }

.l1-section { margin-bottom: 18px; }
.l1-header {
  font-size: 13px; font-weight: 700; color: #232845;
  margin: 0 0 8px; padding: 4px 0 4px 10px;
  border-left: 3px solid #4f6ef6;
  background: linear-gradient(90deg, rgba(79,110,246,0.04), transparent);
}

.data-section { margin-bottom: 14px; }
.section-header { font-size: 13px; font-weight: 600; color: #454e6b; margin: 0 0 6px; padding-left: 8px; border-left: 3px solid #4f6ef6; }
.data-row { display: flex; justify-content: space-between; padding: 5px 0; font-size: 13px; border-bottom: 1px solid #f5f6fa; }
.data-label { color: #666; }
.data-value { color: #303651; font-weight: 500; font-variant-numeric: tabular-nums; }
.data-value.highlight { color: #4f6ef6; font-weight: 700; }
.empty-hint { text-align: center; color: #aaa; padding: 40px 0; font-size: 14px; }
.panel-expand-enter-active, .panel-expand-leave-active {
  transition: transform 0.3s cubic-bezier(0.22,1,0.36,1), opacity 0.25s;
}
.panel-expand-enter, .panel-expand-leave-to { transform: translateX(-20px); opacity: 0; }
</style>
