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
        <!-- 无权限提示（查看详细指标数据时触发） -->
        <template v-if="mode === 'branch' && noAccess">
          <div class="no-access">
            <i class="el-icon-lock" style="font-size:36px;color:#d9d9d9;margin-bottom:12px;"></i>
            <p>无数据查看权限</p>
            <div style="display:flex;gap:10px;justify-content:center;margin-top:12px;">
              <el-button size="small" @click="$emit('close')">取消</el-button>
              <el-button type="primary" size="small" @click="$emit('apply-access', branchId)">申请</el-button>
            </div>
          </div>
        </template>

        <template v-else>
          <!-- 网点模式：卡片式分组展示 -->
          <template v-if="mode === 'branch' && branchTree.length">
            <div class="branch-detail">
              <div v-for="(card, ci) in groupedCards" :key="ci"
                :class="['detail-card', card.typeClass]">
                <div class="card-header">
                  <i :class="card.icon" />
                  <span class="card-title">{{ card.name }}</span>
                </div>
                <div class="card-body">
                  <div v-for="(sub, si) in card.subs" :key="si"
                    :class="['card-sub', sub.typeClass]">
                    <div class="sub-title">
                      <i :class="sub.icon" />
                      <span>{{ sub.name }}</span>
                    </div>
                    <div class="sub-items">
                      <div v-for="(item, li) in sub.items" :key="li" class="data-item">
                        <span class="item-label">{{ item.label }}</span>
                        <span class="item-value">{{ fmtVal(item.value) }}</span>
                      </div>
                    </div>
                  </div>
                  <div v-for="(item, di) in card.directs" :key="'d'+di" class="data-item">
                    <span class="item-label">{{ item.label }}</span>
                    <span class="item-value">{{ fmtVal(item.value) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- 网格模式：按叶子节点的直接父级分组，每组一个堆叠百分比条 -->
          <template v-if="mode === 'grid' && stackedGroups.length">
            <div v-for="l2 in stackedGroups" :key="l2.name" class="l1-section">
              <h5 class="l1-header">
                <span>{{ l2.l1Name }} / {{ l2.name }}</span>
                <span class="l1-count">{{ l2.subs[0].segments.length }} 项</span>
              </h5>
              <StackedBar :segments="l2.subs[0].segments" />
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

// 分段指示器逻辑排序表 — 使有自然顺序的指标按合理序列展示
const SEGMENT_SORT_ORDER = {
  // 年龄（从小到大）
  '18岁以下': 1, '18-25岁': 2, '18-30岁': 2,
  '25-30岁': 3, '30-35岁': 4, '30-45岁': 4,
  '35-45岁': 5, '45-50岁': 6, '45-60岁': 6,
  '50-55岁': 7, '55-60岁': 8, '60岁以上': 9, '60岁及以上': 9,
  // 收入水平（从低到高）
  '3000以下': 1, '3000以下/月': 1,
  '3000-5000': 2, '3000-5000/月': 2,
  '5000-8000': 3, '5000-10000': 3,
  '8000以上': 4, '10000以上': 4, '10000以上/月': 4,
  // 教育水平（从低到高）
  '小学及以下': 1, '初中': 2, '高中': 3,
  '高中及以下': 3, '中专': 3,
  '大专': 4, '本科': 5, '硕士': 6, '硕士及以上': 6,
  '博士': 7, '博士及以上': 7,
}

export default {
  name: 'DetailPanel',
  components: { StackedBar },
  props: {
    visible: Boolean,
    data: { type: Array, default: () => [] },
    mode: { type: String, default: 'branch' },
    leftPos: { type: Number, default: 420 },
    noAccess: { type: Boolean, default: false },
    branchId: { type: Number, default: null }
  },
  computed: {
    hasContent() {
      if (this.mode === 'branch') return this.branchTree.length > 0
      if (this.mode === 'grid') return this.stackedGroups.length > 0
      return this.data.length > 0
    },
    // 网点模式：将扁平 tree 按 depth-0 分组为卡片 → subs → items
    groupedCards() {
      if (this.mode !== 'branch' || !this.branchTree.length) return []
      const cards = []
      let cur = null
      for (const node of this.branchTree) {
        if (node.type === 'cat' && node.depth === 0) {
          cur = { name: node.name, subs: [], directs: [] }
          cards.push(cur)
        } else if (node.type === 'cat' && node.depth === 1) {
          if (cur) cur.subs.push({ name: node.name, items: [] })
        } else if (node.type === 'leaf') {
          const item = { label: node.name, value: node.value }
          if (cur) {
            if (cur.subs.length > 0) cur.subs[cur.subs.length - 1].items.push(item)
            else cur.directs.push(item)
          }
        }
      }
      return cards.map(c => ({
        ...c,
        icon: this.cardIcon(c.name),
        tag: /人均|效能/.test(c.name) ? '计算数据' : '原始数据',
        typeClass: /人均|效能/.test(c.name) ? 'card-computed' : 'card-raw',
        subs: c.subs.map(s => ({
          ...s,
          icon: /头部|中部/.test(s.name) ? 'el-icon-top' :
                (/底尾/.test(s.name) ? 'el-icon-bottom' : 'el-icon-minus'),
          typeClass: /头部|中部/.test(s.name) ? 'sub-head' :
                     (/底尾/.test(s.name) ? 'sub-tail' : '')
        }))
      }))
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

      // 对每个分组内的指标按逻辑顺序排序（年龄/收入/教育等）
      const sortItems = (items) => {
        const sorted = [...items]
        sorted.sort((a, b) => {
          const ka = SEGMENT_SORT_ORDER[a.name] || 999
          const kb = SEGMENT_SORT_ORDER[b.name] || 999
          return ka - kb
        })
        return sorted
      }

      return Object.values(l2Map).map(g => {
        const total = g.total || 1
        return {
          name: g.name,
          l1Name: g.l1Name,
          subs: [{
            name: g.name,
            segments: sortItems(g.items).map(item => ({
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
    fmtVal(v) { return typeof v === 'number' ? v.toFixed(2) : (v != null ? String(v) : '-') },
    cardIcon(name) {
      if (/人均/.test(name)) return 'el-icon-s-data'
      if (/客群|客户/.test(name)) return 'el-icon-user'
      if (/营收|收入/.test(name)) return 'el-icon-money'
      if (/业绩/.test(name)) return 'el-icon-trophy'
      if (/运营/.test(name)) return 'el-icon-s-operation'
      if (/资产/.test(name)) return 'el-icon-bank-card'
      if (/对公/.test(name)) return 'el-icon-office-building'
      if (/个人/.test(name)) return 'el-icon-user-solid'
      if (/成长|发展/.test(name)) return 'el-icon-s-promotion'
      return 'el-icon-document'
    }
  }
}
</script>
<style scoped>
.detail-panel {
  position: absolute; top: 100px; bottom: 12px; width: min(500px, 88vw);
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
.close-btn { color: #444; }
.close-btn:hover { color: #4f6ef6; }
.detail-body { flex: 1; overflow-y: auto; padding: 14px 18px; }
.detail-body::-webkit-scrollbar { width: 4px; }
.detail-body::-webkit-scrollbar-thumb { background: rgba(79,110,246,0.12); border-radius: 4px; }

.l1-section {
  margin-bottom: 14px;
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  border: 1px solid rgba(79,110,246,0.08);
  box-shadow: 0 1px 4px rgba(0,0,0,0.03);
}
.l1-header {
  font-size: 13px; font-weight: 700; color: #232845;
  margin: 0 0 8px; padding: 3px 0 3px 10px;
  border-left: 3px solid #4f6ef6;
  background: linear-gradient(90deg, rgba(79,110,246,0.04), transparent);
  display: flex; align-items: center; gap: 6px;
}
.l1-count {
  font-size: 12px; font-weight: 400; color: #666;
  margin-left: auto;
}

.branch-detail {
  display: flex; flex-direction: column; gap: 14px;
}
.detail-card {
  background: #fff; border-radius: 10px;
  border: 1px solid rgba(79,110,246,0.08);
  box-shadow: 0 2px 8px rgba(0,0,0,0.04); overflow: hidden;
}
.card-header {
  display: flex; align-items: center; gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid rgba(79,110,246,0.06);
}
.card-raw .card-header {
  background: linear-gradient(90deg, rgba(79,110,246,0.06), rgba(79,110,246,0.02));
}
.card-computed .card-header {
  background: linear-gradient(90deg, rgba(168,85,247,0.06), rgba(168,85,247,0.02));
}
.card-header i { font-size: 16px; }
.card-raw .card-header i { color: #4f6ef6; }
.card-computed .card-header i { color: #a855f7; }
.card-title { font-size: 14px; font-weight: 700; color: #232845; flex: 1; }
.card-tag {
  font-size: 12px; font-weight: 600; padding: 1px 8px; border-radius: 8px;
  letter-spacing: 0.3px; flex-shrink: 0;
}
.card-raw .card-tag { background: rgba(79,110,246,0.08); color: #4f6ef6; }
.card-computed .card-tag { background: rgba(168,85,247,0.08); color: #a855f7; }
.card-body { padding: 6px 0; }
.card-sub {
  padding: 8px 14px; border-left: 3px solid transparent;
}
.card-sub + .card-sub { border-top: 1px solid rgba(0,0,0,0.03); }
.sub-head { border-left-color: #4f6ef6; background: linear-gradient(90deg, rgba(79,110,246,0.03), transparent); }
.sub-tail { border-left-color: #f0a050; background: linear-gradient(90deg, rgba(240,160,80,0.03), transparent); }
.sub-title {
  font-size: 13px; font-weight: 600; color: #444; margin-bottom: 4px;
  display: flex; align-items: center; gap: 4px;
}
.sub-title i { font-size: 12px; }
.sub-head .sub-title i { color: #4f6ef6; }
.sub-tail .sub-title i { color: #f0a050; }
.sub-items { padding-left: 2px; }
.data-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 0 6px 10px; font-size: 13px;
  border-bottom: 1px solid #f5f6fa;
}
.data-item:last-child { border-bottom: none; }
.item-label { color: #444; flex: 1; padding-right: 12px; line-height: 1.4; }
.item-value {
  font-weight: 600; color: #232845;
  font-variant-numeric: tabular-nums; text-align: right; flex-shrink: 0;
  font-size: 14px;
}
.card-computed .item-value { color: #7c3aed; }
.empty-hint { text-align: center; color: #555; padding: 40px 0; font-size: 14px; }
.no-access { text-align: center; padding: 60px 20px; color: #555; }
.no-access p { margin: 8px 0 16px; font-size: 14px; }
.panel-expand-enter-active, .panel-expand-leave-active {
  transition: transform 0.3s cubic-bezier(0.22,1,0.36,1), opacity 0.25s;
}
.panel-expand-enter, .panel-expand-leave-to { transform: translateX(-20px); opacity: 0; }
</style>
