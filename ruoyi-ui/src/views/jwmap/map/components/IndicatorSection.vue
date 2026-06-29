<template>
  <div class="indicator-section">
    <h4 v-if="title" class="indicator-title">{{ title }}</h4>

    <template v-if="grouped">
      <div v-for="(g, gi) in groups" :key="gi" class="category-group">
        <h5 class="category-title-l1">{{ g.name }}</h5>
        <div v-for="(sg, si) in g.subs" :key="si" class="category-subgroup">
          <span v-if="sg.name" class="category-title-l2">{{ sg.name }}</span>
          <div v-for="item in sg.items" :key="item.code" class="indicator-row" :class="{ compact }">
            <PercentageBar v-if="showBars && isNumeric(item.value)"
              :name="item.name" :pct="toPct(item.value, item.code)" :value="item.value" />
            <template v-else>
              <span class="indicator-name">{{ item.name }}</span>
              <span class="indicator-value">{{ item.value }}</span>
            </template>
          </div>
        </div>
      </div>
    </template>

    <template v-else>
      <div v-for="item in items" :key="item.code" class="indicator-row" :class="{ compact }">
        <PercentageBar v-if="showBars && isNumeric(item.value)"
          :name="item.name" :pct="toPct(item.value, item.code)" :value="item.value" />
        <template v-else>
          <span class="indicator-name">{{ item.name }}</span>
          <span class="indicator-value">{{ item.value }}</span>
        </template>
      </div>
    </template>
  </div>
</template>
<script>
import PercentageBar from './PercentageBar'

// 用于计算百分比的全局最大值缓存（粗糙近似：前端的指标最大值）
const PCT_MAX = {}

export default {
  name: 'IndicatorSection',
  components: { PercentageBar },
  props: {
    title: String,
    items: Array,
    compact: Boolean,
    grouped: Boolean,
    showBars: { type: Boolean, default: false }
  },
  computed: {
    groups() {
      if (!this.grouped) return []
      const l1Map = {}
      for (const item of this.items) {
        const l1 = item.categoryLevel1 || '其他'
        if (!l1Map[l1]) l1Map[l1] = { name: l1, subMap: {} }
        const l2 = item.categoryLevel2 || ''
        if (!l1Map[l1].subMap[l2]) l1Map[l1].subMap[l2] = { name: l2, items: [] }
        l1Map[l1].subMap[l2].items.push(item)
      }
      return Object.values(l1Map).map(g => ({
        name: g.name,
        subs: Object.values(g.subMap)
      }))
    }
  },
  methods: {
    isNumeric(v) {
      return typeof v === 'number' || (typeof v === 'string' && !isNaN(parseFloat(v)))
    },
    toPct(val, code) {
      const v = parseFloat(val)
      if (isNaN(v)) return 0
      const max = PCT_MAX[code] || v
      if (v > max) PCT_MAX[code] = v
      return max > 0 ? Math.min(Math.round(v / max * 100), 100) : 0
    }
  }
}
</script>
<style scoped>
.indicator-title {
  font-size: 13px;
  font-weight: 600;
  color: #454e6b;
  margin: 12px 0 8px;
  letter-spacing: 0.1px;
}
.category-group { margin-bottom: 2px; }
.category-title-l1 {
  font-size: 13px; font-weight: 600; color: #444;
  margin: 10px 0 4px; padding-left: 8px;
  border-left: 3px solid #4f6ef6; line-height: 1.3;
}
.category-title-l2 {
  font-size: 12px; color: #555; display: block;
  margin: 6px 0 2px 8px; font-weight: 500;
  letter-spacing: 0.3px;
}
.indicator-row { display: flex; align-items: baseline; padding: 5px 0; font-size: 13px; }
.indicator-row.compact { font-size: 12px; padding: 3px 0; }
.indicator-name { color: #444; flex: 1; padding-right: 8px; }
.indicator-value { color: #303651; font-weight: 600; font-variant-numeric: tabular-nums; flex-shrink: 0; }
</style>
