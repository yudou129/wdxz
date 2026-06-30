<template>
  <div class="top-toolbar">
    <div class="toolbar-row toolbar-row-top">
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

      <div class="toolbar-search">
        <i class="el-icon-search search-icon" />
        <input ref="searchInput" v-model="searchQuery" class="search-input"
               placeholder="搜索网点..." @input="onSearchInput"
               @focus="searchFocused = true" @blur="onSearchBlur" />
        <div v-if="searchFocused && searchResults.length > 0" class="search-dropdown">
          <div v-for="b in searchResults" :key="b.branchId" class="search-item"
               @mousedown.prevent="selectBranch(b)">
            <div class="search-item-row1">
              <span class="search-item-name">{{ b.secondaryBranch || b.primaryBranch || '未知网点' }}</span>
              <el-button v-if="compareActive" size="mini" type="text" class="add-compare-btn"
                         @mousedown.stop.prevent="addCompare(b)">
                加入对比
              </el-button>
            </div>
            <span class="search-item-parent">{{ b.primaryBranch }}</span>
          </div>
        </div>
      </div>
      <span style="margin-left:8px;border-left:1px solid rgba(0,0,0,0.1);padding-left:8px" />
      <el-button size="small" @click="$emit('goto-access')">
        <i class="el-icon-document" /> 我的申请
      </el-button>
      <el-button size="small" type="warning" @click="$emit('goto-approval')">
        <i class="el-icon-bell" /> 审批管理
        <el-badge v-if="pendingCount > 0" :value="pendingCount" class="toolbar-badge" />
      </el-button>
    </div>

    <div class="toolbar-row toolbar-row-bottom">
      <el-button size="small" @click="$emit('toggle-quadrant')">
        <i class="el-icon-s-data" /> 四象限综合分析
      </el-button>
      <!-- <el-button size="small" @click="$emit('toggle-dim-stats')">
        <i class="el-icon-pie-chart" /> 统计
      </el-button> -->
      <el-button size="small" :type="heatmapActive ? 'danger' : 'default'"
                 :class="{ 'heatmap-on': heatmapActive }"
                 @click="$emit('toggle-heatmap')">
        <i class="el-icon-data-board" /> 网格热力图
      </el-button>
      <el-button size="small" :type="blankSpotActive ? 'info' : 'default'"
                 :class="{ 'blankspot-on': blankSpotActive }"
                 @click="$emit('toggle-blank-spot')">
        <i class="el-icon-view" /> 服务空白点
      </el-button>
      <el-button size="small" :type="peerBankActive ? 'primary' : 'default'"
                 :class="{ 'peerbk-on': peerBankActive }"
                 @click="$emit('toggle-peerbank')">
        <i class="el-icon-office-building" /> 同业网点
      </el-button>
      <el-button size="small" :type="rangeActive ? 'warning' : 'default'"
                 :class="{ 'range-on': rangeActive }"
                 @click="$emit('toggle-range')">
        <i class="el-icon-rank" /> POI范围分析
      </el-button>
      <el-button size="small" :type="rankingActive ? 'success' : 'default'"
                 :class="{ 'ranking-on': rankingActive }"
                 @click="$emit('toggle-ranking')">
        <i class="el-icon-trophy" /> 网格/网点排名
      </el-button>
      <el-button size="small" :type="compareActive ? 'warning' : 'default'"
                 :class="{ 'compare-on': compareActive }"
                 @click="$emit('toggle-compare')">
        <i class="el-icon-data-analysis" /> {{ compareActive ? '退出对比' : '多网点对比分析' }}
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
    heatmapActive: { type: Boolean, default: false },
    peerBankActive: { type: Boolean, default: true },
    rangeActive: { type: Boolean, default: false },
    rankingActive: { type: Boolean, default: false },
    compareActive: { type: Boolean, default: false },
    blankSpotActive: { type: Boolean, default: false },
    branchList: { type: Array, default: () => [] },
    pendingCount: { type: Number, default: 0 }
  },
  data() {
    return {
      selectedCity: 'all', selectedDistrict: 'all',
      selectedBranch: 'all', branchOptions: [], districts: [],
      searchQuery: '', searchResults: [], searchFocused: false
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
  watch: {
    branchList() {
      this.searchQuery = ''
      this.searchResults = []
    }
  },
  methods: {
    onSearchInput() {
      const q = this.searchQuery.trim().toLowerCase()
      if (!q || q.length < 2 || !this.branchList.length) { this.searchResults = []; return }
      this.searchResults = this.branchList.filter(b => {
        const name = (b.secondaryBranch || b.primaryBranch || '').toLowerCase()
        const parent = (b.primaryBranch || '').toLowerCase()
        const addr = (b.address || '').toLowerCase()
        const district = (b.districtName || '').toLowerCase()
        return name.includes(q) || parent.includes(q) || addr.includes(q) || district.includes(q)
      }).slice(0, 20) // 最多显示20条
    },
    selectBranch(b) {
      this.searchQuery = b.secondaryBranch || b.primaryBranch || ''
      this.searchResults = []
      this.searchFocused = false
      this.$refs.searchInput.blur()
      this.$emit('search-branch', b)
    },
    addCompare(b) {
      this.$emit('add-compare-branch', b)
    },
    onSearchBlur() {
      // 延迟隐藏，让点击选项有机会触发
      setTimeout(() => { this.searchFocused = false }, 200)
    },
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
  padding: 8px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.toolbar-row {
  display: flex;
  align-items: center;
  white-space: nowrap;
}
.toolbar-row-top {
  gap: 2px;
}
.toolbar-row-bottom {
  justify-content: center;
  gap: 4px;
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
  color: #444;
  font-weight: 500;
  margin-right: 2px;
}
/* 网点搜索 */
.toolbar-search {
  position: relative;
  display: inline-flex;
  align-items: center;
  margin-left: 8px;
}
.search-icon {
  position: absolute;
  left: 8px;
  color: #666;
  font-size: 13px;
  pointer-events: none;
  z-index: 1;
}
.search-input {
  width: 140px;
  height: 28px;
  border: 1px solid rgba(0,0,0,0.1);
  border-radius: 6px;
  padding: 0 8px 0 26px;
  font-size: 13px;
  outline: none;
  background: rgba(255,255,255,0.7);
  transition: border-color 0.2s, width 0.2s;
}
.search-input:focus {
  width: 180px;
  border-color: #409eff;
  background: rgba(255,255,255,0.95);
}
.search-input::placeholder {
  color: #888;
}
.search-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  max-height: 260px;
  overflow-y: auto;
  z-index: 2000;
}
.search-item {
  display: flex;
  flex-direction: column;
  padding: 7px 10px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
}
.search-item:last-child {
  border-bottom: none;
}
.search-item:hover {
  background: #f0f7ff;
}
.search-item-row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.search-item-name {
  font-size: 13px;
  color: #333;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.add-compare-btn {
  font-size: 12px;
  color: #4f6ef6;
  flex-shrink: 0;
  padding: 0 4px;
}
.add-compare-btn:hover {
  color: #3b54d4;
}
.search-item-parent {
  font-size: 13px;
  color: #555;
  margin-top: 1px;
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
.peerbk-on {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}
.peerbk-on:hover {
  background: #3a8ee6;
  border-color: #3a8ee6;
}
.range-on {
  background: #e6a23c;
  border-color: #e6a23c;
  color: #fff;
}
.range-on:hover {
  background: #d4941f;
  border-color: #d4941f;
}
.ranking-on {
  background: #67c23a;
  border-color: #67c23a;
  color: #fff;
}
.ranking-on:hover {
  background: #5daf34;
  border-color: #5daf34;
}
.compare-on {
  background: #e6a23c;
  border-color: #e6a23c;
  color: #fff;
}
.compare-on:hover {
  background: #d4941f;
  border-color: #d4941f;
}
.blankspot-on {
  background: #06b6d4;
  border-color: #06b6d4;
  color: #fff;
}
.blankspot-on:hover {
  background: #0891b2;
  border-color: #0891b2;
}
.toolbar-badge {
  margin-left: 2px;
}
.toolbar-badge >>> .el-badge__content {
  top: 2px;
  right: -4px;
}
</style>
