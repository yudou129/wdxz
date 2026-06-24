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
        <!-- 无权限提示 -->
        <template v-if="mode === 'branch' && hasAccess === false">
          <div class="no-access">
            <i class="el-icon-lock" style="font-size:36px;color:#d9d9d9;margin-bottom:12px;"></i>
            <p>暂无查看该网点详细数据的权限</p>
            <el-button type="primary" size="small" @click="$emit('apply-access', branchId)">申请查看</el-button>
          </div>
        </template>

        <template v-else>
          <!-- 网点模式：按数据层级依次展示的动态树 -->
          <template v-if="mode === 'branch' && branchTree.length">
            <div v-for="(node, i) in branchTree" :key="i">
              <div v-if="node.type === 'cat'" :class="['tree-cat', 'depth-' + node.depth]">
                {{ node.name }}
              </div>
              <div v-else class="data-row" :style="{ paddingLeft: (node.depth * 14 + 4) + 'px' }">
                <span class="data-label">{{ node.name }}</span>
                <span class="data-value">{{ fmtVal(node.value) }}</span>
              </div>
            </div>
          </template>

          <!-- 网格模式：按叶子节点的直接父级分组，每组一个堆叠百分比条 -->
          <template v-if="mode === 'grid' && stackedGroups.length">
            <div v-for="l2 in stackedGroups" :key="l2.name" class="l1-section">
              <h5 class="l1-header">{{ l2.l1Name }} / {{ l2.name }}</h5>
              <StackedBar
                :label="l2.name"
                :segments="l2.subs[0].segments" />
            </div>
          </template>

          <div v-if="!hasContent" class="empty-hint">暂无详细指标数据</div>
        </template>
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
    leftPos: { type: Number, default: 420 },
    hasAccess: { type: Boolean, default: true },
    branchId: { type: Number, default: null }
  },
  computed: {
    hasContent() {
      if (this.mode === 'branch') return this.branchTree.length > 0
      if (this.mode === 'grid') return this.stackedGroups.length > 0
      return this.data.length > 0
    },
    // 网点模式：动态树——按每条数据的 ancestors 链构建任意深度层级
    branchTree() {
      if (this.mode !== 'branch' || !this.data.length) return []
      const root = { _c: {}, _items: [] }
      for (const item of this.data) {
        let node = root
        for (const name of (item.ancestors || [])) {
          if (!node._c[name]) node._c[name] = { _c: {}, _items: [] }
          node = node._c[name]
        }
        node._items.push({ name: item.name, value: item.value })
      }
      const flat = []
      const walk = (node, depth) => {
        for (const [name, child] of Object.entries(node._c)) {
          flat.push({ type: 'cat', name, depth })
          walk(child, depth + 1)
          for (const item of child._items) {
            flat.push({ type: 'leaf', name: item.name, value: item.value, depth: depth + 1 })
          }
        }
      }
      walk(root, 0)
      return flat
    },
    // 网格模式：按叶子节点的直接父级分组，每组独立一个 StackedBar
    stackedGroups() {
      if (this.mode !== 'grid' || !this.data.length) return []

      const l2Map = {}
      for (const item of this.data) {
        const v = parseFloat(item.value)
        // 跳过值为0或NaN的项
        if (isNaN(v) || v === 0) continue
        const l2 = item.categoryLevel2 || item.categoryLevel1 || '其他'
        if (!l2Map[l2]) {
          l2Map[l2] = { name: l2, l1Name: item.categoryLevel1 || '其他', items: [], total: 0 }
        }
        l2Map[l2].items.push({ name: item.name, value: item.value, raw: v })
        l2Map[l2].total += v
      }

      return Object.values(l2Map).map(g => {
        const total = g.total || 1
        return {
          name: g.name,
          l1Name: g.l1Name,
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
  },
  methods: {
    fmtVal(v) { return typeof v === 'number' ? v.toFixed(2) : (v != null ? String(v) : '-') }
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
  z-index: 1000; display: flex; flex-direction: column;
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
.no-access { text-align: center; padding: 60px 20px; color: #999; }
.no-access p { margin: 8px 0 16px; font-size: 14px; }
/* 动态树层级样式 */
.tree-cat { font-weight: 600; margin: 8px 0 3px; }
.tree-cat.depth-0 { font-size: 14px; color: #232845; border-left: 3px solid #4f6ef6; padding: 3px 0 3px 10px; background: linear-gradient(90deg, rgba(79,110,246,0.06), transparent); }
.tree-cat.depth-1 { font-size: 13px; color: #454e6b; border-left: 2px solid #a0b4f8; padding: 2px 0 2px 18px; }
.tree-cat.depth-2 { font-size: 12px; color: #6b7280; padding: 2px 0 2px 28px; }
.panel-expand-enter-active, .panel-expand-leave-active {
  transition: transform 0.3s cubic-bezier(0.22,1,0.36,1), opacity 0.25s;
}
.panel-expand-enter, .panel-expand-leave-to { transform: translateX(-20px); opacity: 0; }
</style>
