<template>
  <div class="indicator-section">
    <h4 v-if="title" class="indicator-title">{{ title }}</h4>

    <template v-if="grouped">
      <div v-for="(g, gi) in groups" :key="gi" class="category-group">
        <h5 class="category-title-l1">{{ g.name }}</h5>
        <div v-for="(sg, si) in g.subs" :key="si" class="category-subgroup">
          <span v-if="sg.name" class="category-title-l2">{{ sg.name }}</span>
          <div v-for="item in sg.items" :key="item.code" class="indicator-row" :class="{ compact }">
            <span class="indicator-name">{{ item.name }}</span>
            <span class="indicator-value">{{ item.value }}</span>
          </div>
        </div>
      </div>
    </template>

    <template v-else>
      <div v-for="item in items" :key="item.code" class="indicator-row" :class="{ compact }">
        <span class="indicator-name">{{ item.name }}</span>
        <span class="indicator-value">{{ item.value }}</span>
      </div>
    </template>
  </div>
</template>
<script>
export default {
  name: 'IndicatorSection',
  props: { title: String, items: Array, compact: Boolean, grouped: Boolean },
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
  }
}
</script>
<style scoped>
.indicator-title { font-size: 13px; font-weight: 600; color: #555; margin: 12px 0 8px; }
.category-group { margin-bottom: 4px; }
.category-title-l1 { font-size: 12px; font-weight: 600; color: #666; margin: 10px 0 4px; padding-left: 2px; border-left: 3px solid #409eff; padding-left: 6px; }
.category-title-l2 { font-size: 11px; color: #999; display: block; margin: 6px 0 2px 6px; }
.indicator-row { display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; }
.indicator-row.compact { font-size: 12px; padding: 2px 0; }
.indicator-name { color: #666; }
.indicator-value { color: #333; font-weight: 500; }
</style>
