<template>
  <div class="top-toolbar">
    <!-- 第一排：筛选区 + 搜索 + 操作入口 -->
    <div class="tb-row tb-row-top">
      <div class="tb-filter-group">
        <el-select v-model="selectedCity" size="small" placeholder="贵州省" @change="onCityChange"
                   class="tb-select tb-select-city">
          <el-option label="贵州省" value="all" />
          <el-option v-for="c in cities" :key="c.properties.adcode"
                     :label="c.properties.name" :value="c.properties.adcode" />
        </el-select>
        <el-select v-if="selectedCity !== 'all'" v-model="selectedDistrict" size="small"
                   placeholder="区县" @change="onDistrictChange" class="tb-select tb-select-district">
          <el-option label="全部区县" value="all" />
          <el-option v-for="d in districts" :key="d.properties.adcode"
                     :label="d.properties.name" :value="d.properties.adcode" />
        </el-select>
        <el-select v-model="selectedBranch" size="small" placeholder="一级支行" @change="onBranchChange"
                   class="tb-select tb-select-branch">
          <el-option label="全部" value="all" />
          <el-option v-for="b in branchOptions" :key="b" :label="b" :value="b" />
        </el-select>
      </div>

      <div class="tb-search-wrap">
        <i class="el-icon-search tb-search-icon" />
        <input ref="searchInput" v-model="searchQuery" class="tb-search-input"
               placeholder="搜索网点..." @input="onSearchInput"
               @focus="searchFocused = true" @blur="onSearchBlur" />
        <div v-if="searchFocused && searchResults.length > 0" class="tb-search-dropdown">
          <div v-for="b in searchResults" :key="b.branchId" class="tb-search-item"
               @mousedown.prevent="selectBranch(b)">
            <div class="tb-search-item-top">
              <span class="tb-search-item-name">{{ b.secondaryBranch || b.primaryBranch || '未知网点' }}</span>
              <el-button v-if="compareActive" size="mini" type="text" class="tb-add-compare"
                         @mousedown.stop.prevent="addCompare(b)">
                + 对比
              </el-button>
            </div>
            <span class="tb-search-item-parent">{{ b.primaryBranch }}</span>
          </div>
        </div>
      </div>

      <div class="tb-actions">
        <el-button size="small" class="tb-action-btn" @click="$emit('goto-access')">
          <i class="el-icon-document" /> 我的申请
        </el-button>
        <el-button size="small" class="tb-action-btn tb-action-approve" @click="$emit('goto-approval')">
          <i class="el-icon-bell" /> 审批管理
          <el-badge v-if="pendingCount > 0" :value="pendingCount" class="tb-badge" />
        </el-button>
      </div>
    </div>

    <!-- 第二排：功能胶囊按钮 -->
    <div class="tb-row tb-row-bottom">
      <div class="tb-func-group">
        <button :class="['tb-func-btn', { active: false }]" @click="$emit('toggle-quadrant')">
          <i class="el-icon-s-data" /> 综合分析四象限
        </button>
        <button :class="['tb-func-btn', { active: false }]" @click="$emit('toggle-dim-stats')">
          <i class="el-icon-pie-chart" /> 统计
        </button>
        <button :class="['tb-func-btn', { active: heatmapActive }]" @click="$emit('toggle-heatmap')">
          <i class="el-icon-data-board" /> 网格热力图
        </button>
        <button :class="['tb-func-btn', { active: blankSpotActive }]" @click="$emit('toggle-blank-spot')">
          <i class="el-icon-view" /> 服务空白点
        </button>
        <button :class="['tb-func-btn', { active: peerBankActive }]" @click="$emit('toggle-peerbank')">
          <i class="el-icon-office-building" /> 同业网点
        </button>
        <button :class="['tb-func-btn', { active: rangeActive }]" @click="$emit('toggle-range')">
          <i class="el-icon-rank" /> 范围内POI
        </button>
        <button :class="['tb-func-btn', { active: rankingActive }]" @click="$emit('toggle-ranking')">
          <i class="el-icon-trophy" /> 网格/网点排名
        </button>
        <button :class="['tb-func-btn', { active: compareActive }]" @click="$emit('toggle-compare')">
          <i class="el-icon-data-analysis" /> {{ compareActive ? '退出对比' : '多网点对比分析' }}
        </button>
      </div>
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
      // 只模糊匹配网点名称
      this.searchResults = this.branchList
        .filter(b => {
          const name = (b.secondaryBranch || b.primaryBranch || '').toLowerCase()
          return name.includes(q)
        })
        .slice(0, 15)
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
/* ===== 容器 ===== */
.top-toolbar {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  isolation: isolate;
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.28);
  background: rgba(255,255,255,0.88);
  backdrop-filter: blur(18px) saturate(160%);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.44),
    0 8px 32px rgba(79,110,246,0.08),
    0 1px 4px rgba(0,0,0,0.04);
  padding: 8px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.top-toolbar::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  border-radius: inherit;
  background:
    radial-gradient(circle at 20% 0%, rgba(255,255,255,0.4), transparent 34%),
    linear-gradient(90deg, rgba(255,255,255,0.12), transparent 42%, rgba(255,255,255,0.08));
  pointer-events: none;
}

/* ===== 行 ===== */
.tb-row {
  display: flex;
  align-items: center;
}

/* ===== 第一排：三段式 ===== */
.tb-row-top {
  justify-content: space-between;
  gap: 12px;
}

/* 左侧：筛选区 */
.tb-filter-group {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.tb-select { width: min(110px, 14vw); }
.tb-select >>> .el-input__inner {
  height: 30px;
  line-height: 30px;
  border-radius: 8px;
  border-color: rgba(0,0,0,0.08);
  background: rgba(79,110,246,0.04);
  font-size: 13px;
}
.tb-select >>> .el-input__inner:focus {
  border-color: #4f6ef6;
  background: #fff;
}

/* 中间：搜索 */
.tb-search-wrap {
  position: relative;
  flex: 1;
  max-width: 320px;
  min-width: 140px;
}
.tb-search-icon {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: #666;
  font-size: 14px;
  pointer-events: none;
  z-index: 1;
}
.tb-search-input {
  width: 100%;
  height: 30px;
  border: 1px solid rgba(0,0,0,0.08);
  border-radius: 9px;
  padding: 0 10px 0 30px;
  font-size: 13px;
  outline: none;
  background: rgba(79,110,246,0.04);
  transition: border-color 0.2s, background 0.2s, box-shadow 0.2s;
}
.tb-search-input:focus {
  border-color: #4f6ef6;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(79,110,246,0.08);
}
.tb-search-input::placeholder { color: #aaa; }
.tb-search-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 6px 24px rgba(0,0,0,0.12);
  max-height: 260px;
  overflow-y: auto;
  z-index: 2000;
}
.tb-search-item {
  display: flex;
  flex-direction: column;
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
}
.tb-search-item:last-child { border-bottom: none; }
.tb-search-item:hover { background: #f0f7ff; }
.tb-search-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.tb-search-item-name {
  font-size: 13px;
  color: #333;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.tb-add-compare {
  font-size: 13px;
  color: #4f6ef6;
  flex-shrink: 0;
  padding: 0 4px;
  font-weight: 600;
}
.tb-add-compare:hover { color: #3b54d4; }
.tb-search-item-parent {
  font-size: 13px;
  color: #666;
  margin-top: 2px;
}

/* 右侧：操作按钮 */
.tb-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding-left: 12px;
  border-left: 1px solid rgba(0,0,0,0.06);
}
.tb-action-btn {
  height: 30px;
  padding: 0 12px;
  border-radius: 8px;
  font-size: 14px;
  border: 1px solid rgba(0,0,0,0.06);
  background: transparent;
  color: #444;
  transition: all 0.2s;
}
.tb-action-btn:hover {
  background: rgba(79,110,246,0.04);
  border-color: rgba(79,110,246,0.12);
  color: #4f6ef6;
}
.tb-action-btn i { margin-right: 4px; }
.tb-action-approve { color: #e6a23c; }
.tb-action-approve:hover {
  background: rgba(230,162,60,0.08);
  border-color: rgba(230,162,60,0.2);
  color: #d4941f;
}
.tb-badge >>> .el-badge__content {
  top: 2px;
  right: -4px;
  font-size: 12px;
}

/* ===== 第二排：功能胶囊栏 ===== */
.tb-row-bottom { justify-content: center; }
.tb-func-group {
  display: flex;
  align-items: center;
  gap: 6px;
}
.tb-func-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 14px;
  border: 1px solid rgba(0,0,0,0.08);
  border-radius: 15px;
  background: rgba(255,255,255,0.6);
  color: #444;
  font-size: 14px;
  cursor: pointer;
  outline: none;
  transition: all 0.2s ease;
  white-space: nowrap;
  font-family: inherit;
}
.tb-func-btn:hover {
  border-color: rgba(79,110,246,0.2);
  background: rgba(79,110,246,0.04);
  color: #4f6ef6;
}
.tb-func-btn.active {
  border-color: #4f6ef6;
  background: #4f6ef6;
  color: #fff;
  box-shadow: 0 2px 8px rgba(79,110,246,0.25);
}
.tb-func-btn.active:hover {
  background: #3b54d4;
  border-color: #3b54d4;
  color: #fff;
}
.tb-func-btn i { font-size: 14px; }

/* 媒体查询：小屏减间距 */
@media (max-width: 1200px) {
  .tb-func-btn { padding: 0 10px; font-size: 12px; }
  .tb-select { width: 90px; }
}
@media (max-width: 900px) {
  .tb-func-group { gap: 4px; }
  .tb-func-btn { padding: 0 8px; font-size: 11px; }
  .tb-func-btn i { font-size: 12px; }
}
</style>
