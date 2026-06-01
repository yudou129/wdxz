<template>
  <div class="top-toolbar">
    <span class="toolbar-label">行政区</span>
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

    <span class="toolbar-label" style="margin-left:12px;">一级支行</span>
    <el-select v-model="selectedBranch" size="small" placeholder="全部" @change="onBranchChange">
      <el-option label="全部" value="all" />
      <el-option v-for="b in branchOptions" :key="b" :label="b" :value="b" />
    </el-select>

    <div class="toolbar-right">
      <el-button size="small" :type="heatmapActive ? 'danger' : 'default'"
                 :class="{ 'heatmap-on': heatmapActive }"
                 @click="$emit('toggle-heatmap')">
        <i class="el-icon-data-board" /> 热力图
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
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
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
    0 4px 20px rgba(79, 110, 246, 0.06),
    0 1px 3px rgba(0, 0, 0, 0.04);
  padding: 10px 18px;
  display: flex;
  align-items: center;
  white-space: nowrap;
  gap: 2px;
}
.top-toolbar::before {
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
.top-toolbar::after {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: 9px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  pointer-events: none;
}
@media (prefers-reduced-transparency: reduce) {
  .top-toolbar {
    background: rgba(255, 255, 255, 0.94);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}
.toolbar-label {
  font-size: 13px;
  color: #556;
  font-weight: 500;
  margin-right: 2px;
}
.toolbar-right {
  margin-left: 20px;
  padding-left: 16px;
  border-left: 1px solid rgba(79, 110, 246, 0.1);
}
.heatmap-on {
  background: #f56c6c;
  border-color: #f56c6c;
  color: #fff;
}
.heatmap-on:hover {
  background: #e85b5b;
  border-color: #e85b5b;
}
</style>
