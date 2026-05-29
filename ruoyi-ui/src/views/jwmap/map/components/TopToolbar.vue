<template>
  <div class="top-toolbar">
    <span class="toolbar-label">行政区：</span>
    <el-select v-model="selectedCity" size="small" placeholder="贵州省" @change="onCityChange"
               style="width:110px;">
      <el-option label="贵州省" value="all" />
      <el-option v-for="c in cities" :key="c.properties.adcode"
                 :label="c.properties.name" :value="c.properties.adcode" />
    </el-select>
    <el-select v-if="selectedCity !== 'all'" v-model="selectedDistrict" size="small"
               placeholder="选择区县" @change="onDistrictChange" style="width:120px;margin-left:6px;">
      <el-option label="全部区县" value="all" />
      <el-option v-for="d in districts" :key="d.properties.adcode"
                 :label="d.properties.name" :value="d.properties.adcode" />
    </el-select>

    <span class="toolbar-label" style="margin-left:12px;">一级支行：</span>
    <el-select v-model="selectedBranch" size="small" placeholder="全部" @change="onBranchChange">
      <el-option label="全部" value="all" />
      <el-option v-for="b in branchOptions" :key="b" :label="b" :value="b" />
    </el-select>

    <div class="toolbar-right">
      <el-button size="small" :type="heatmapActive ? 'danger' : 'default'"
                 @click="$emit('toggle-heatmap')">
        🔥 热力图
      </el-button>
    </div>
  </div>
</template>

<script>
import { getBranchList } from '@/api/jwmap/data'

export default {
  name: 'TopToolbar',
  props: {
    cities: { type: Array, default: () => [] },
    heatmapActive: { type: Boolean, default: false }
  },
  data() {
    return {
      selectedCity: 'all', selectedDistrict: 'all',
      selectedBranch: 'all', branchOptions: [], districts: []
    }
  },
  computed: {
    // adcode(520100) → 中文名(贵阳市) 映射
    cityNameMap() {
      const map = {}
      for (const c of this.cities) {
        if (c.properties && c.properties.adcode != null) {
          map[c.properties.adcode] = c.properties.name
        }
      }
      return map
    }
  },
  methods: {
    onCityChange(val) {
      this.selectedDistrict = 'all'
      if (val === 'all') {
        this.branchOptions = []; this.selectedBranch = 'all'; this.districts = []
        this.$emit('select-city', null)
      } else {
        this.loadBranches(val)
        this.loadDistricts(val)
        this.$emit('select-city', val)
      }
    },
    onDistrictChange(val) {
      this.$emit('select-district', val === 'all' ? null : val)
    },
    async loadDistricts(adcode) {
      try {
        const res = await fetch(`/data/map_data/${adcode}_full.json`)
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        this.districts = (await res.json()).features || []
      } catch (e) { this.districts = [] }
    },
    async loadBranches(adcode) {
      try {
        // adcode(520100) → 中文名(贵阳市)，后端 city 列存中文名
        const cityName = this.cityNameMap[adcode]
        if (!cityName) { this.branchOptions = []; return }
        const res = await getBranchList(cityName)
        const branches = [...new Set((res.data || []).map(b => b.primaryBranch).filter(Boolean))]
        this.branchOptions = branches.sort()
      } catch (e) { this.branchOptions = [] }
    },
    onBranchChange(val) {
      this.$emit('filter-branch', val === 'all' ? null : val)
    }
  }
}
</script>

<style scoped>
.top-toolbar {
  position: absolute; top: 10px; left: 50%; transform: translateX(-50%);
  z-index: 1000; background: rgba(255,255,255,0.95);
  padding: 8px 16px; border-radius: 6px; box-shadow: 0 2px 8px rgba(0,0,0,0.15);
  display: flex; align-items: center; white-space: nowrap;
}
.toolbar-label { font-size: 13px; color: #555; }
.toolbar-right { margin-left: 24px; }
</style>
