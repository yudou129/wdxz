<template>
  <div class="top-toolbar">
    <div class="toolbar-row toolbar-row-top">
      <span class="toolbar-label">选择分行/地市</span>
      <el-select v-model="selectedCity" size="small" placeholder="选择分行/地市" @change="onCityChange"
                 style="width:140px;">
        <el-option label="全省" value="all" />
        <el-option v-for="b in BRANCH_LIST" :key="b.adcode"
                   :label="b.label" :value="b.adcode" />
      </el-select>
      <el-select v-if="selectedCity !== 'all'" v-model="selectedDistrict" size="small"
                 placeholder="选择区县" @change="onDistrictChange" style="width:130px;margin-left:6px;">
        <el-option label="全部区县" value="all" />
        <el-option v-for="d in districts" :key="d.properties.adcode"
                   :label="d.properties.name" :value="d.properties.adcode" />
      </el-select>
      <span v-if="selectedCity !== 'all'" class="toolbar-label" style="margin-left:12px;">一级支行</span>
      <el-select v-if="selectedCity !== 'all'" v-model="selectedBranch" size="small" placeholder="全部" @change="onBranchChange">
        <el-option label="全部" value="all" />
        <el-option v-for="b in branchOptions" :key="b" :label="b" :value="b" />
      </el-select>
      <span class="row-divider" />

      <!-- 地址/POI搜索 -->
      <div class="toolbar-search addr-search">
        <i class="el-icon-map-location search-icon" style="font-size:14px" />
        <input ref="addrInput" v-model="addressSearchKeyword" class="search-input"
               placeholder="搜索地址/POI..." style="width:120px"
               @input="onAddressSearchInput"
               @focus="addressSearchFocused = true"
               @blur="onAddressSearchBlur"
               @keydown.enter="onAddressSearchEnter" />
        <i v-if="addressSearchKeyword && !addressSearchLoading"
           class="el-icon-close search-clear"
           @mousedown.prevent="clearAddressSearch" />
        <i v-if="addressSearchLoading" class="el-icon-loading search-loading" />
        <div v-if="addressSearchFocused && addressSearchResults.length" class="search-dropdown addr-dropdown">
          <div v-for="(item, idx) in addressSearchResults" :key="idx"
               class="search-item" @mousedown.prevent="selectAddressResult(item)">
            <div class="search-item-row1">
              <span class="search-item-name">{{ item.name }}</span>
            </div>
            <span class="search-item-parent">{{ item.address }}</span>
          </div>
        </div>
      </div>

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
      <span class="row-divider" />
      <el-button size="small" @click="$emit('goto-access')">
        <i class="el-icon-document" /> 我的申请
      </el-button>
      <el-button size="small" type="warning" @click="$emit('goto-approval')">
        <i class="el-icon-bell" /> 审批管理
        <el-badge v-if="pendingCount > 0" :value="pendingCount" class="toolbar-badge" />
      </el-button>
    </div>

    <div v-show="true" class="toolbar-row toolbar-row-bottom">
      <el-button size="small" :type="quadrantActive ? 'primary' : 'default'"
                 :class="{ 'btn-analysis': quadrantActive }"
                 @click="$emit('toggle-quadrant')">
        <i class="el-icon-s-data" /> 四象限
      </el-button>
      <!-- <el-button size="small" @click="$emit('toggle-dim-stats')">
        <i class="el-icon-pie-chart" /> 统计
      </el-button> -->
      <el-button size="small" :type="branchActive ? 'primary' : 'default'"
                 :class="{ 'branch-on': branchActive }"
                 @click="$emit('toggle-branch')">
        <i class="el-icon-location" /> 网点信息
      </el-button>
      <el-button size="small" :type="heatmapActive ? 'primary' : 'default'"
                 :class="{ 'btn-info': heatmapActive }"
                 @click="$emit('toggle-heatmap')">
        <i class="el-icon-data-board" /> 网格热力图
      </el-button>
      <el-button size="small" :type="blankSpotActive ? 'primary' : 'default'"
                 :class="{ 'btn-info': blankSpotActive }"
                 @click="$emit('toggle-blank-spot')">
        <i class="el-icon-view" /> 服务空白点
      </el-button>
      <span v-if="blankSpotActive" class="blank-spot-params">
        <el-select v-model="blankSpotLimit" size="mini"
                   @change="$emit('blank-spot-params', { limit: blankSpotLimit })">
          <el-option label="前50个" :value="50" />
          <el-option label="前100个" :value="100" />
          <el-option label="前200个" :value="200" />
          <el-option label="前500个" :value="500" />
        </el-select>
      </span>
      <el-button size="small" :type="peerBankActive ? 'primary' : 'default'"
                 :class="{ 'btn-brand': peerBankActive }"
                 @click="$emit('toggle-peerbank')">
        <i class="el-icon-office-building" /> 同业网点
      </el-button>
      <el-button size="small" :type="rangeActive ? 'primary' : 'default'"
                 :class="{ 'btn-ops': rangeActive }"
                 @click="$emit('toggle-range')">
        <i class="el-icon-rank" /> POI范围分析
      </el-button>
      <el-dropdown size="small" @command="v => $emit('toggle-ranking', v)" trigger="click">
        <el-button size="small" :type="rankingActive ? 'primary' : 'default'"
                   :class="{ 'btn-analysis': rankingActive }">
          <i class="el-icon-trophy" /> 排名<i class="el-icon-arrow-down el-icon--right" />
        </el-button>
        <el-dropdown-menu slot="dropdown">
          <el-dropdown-item command="grid"><i class="el-icon-trophy" /> 网格排名</el-dropdown-item>
          <el-dropdown-item command="branch"><i class="el-icon-medal" /> 网点排名</el-dropdown-item>
        </el-dropdown-menu>
      </el-dropdown>
      <el-button size="small" :type="compareActive ? 'primary' : 'default'"
                 :class="{ 'btn-analysis': compareActive }"
                 @click="$emit('toggle-compare')">
        <i class="el-icon-data-analysis" /> {{ compareActive ? '退出对比' : '对比' }}
      </el-button>
      <el-dropdown size="small" @command="v => $emit('tool-command', v)" trigger="click">
        <el-button size="small" :type="toolActive ? 'primary' : 'default'">
          <i class="el-icon-setting" /> 工具<i class="el-icon-arrow-down el-icon--right" />
        </el-button>
        <el-dropdown-menu slot="dropdown">
          <el-dropdown-item command="pick"><i class="el-icon-location" /> 标点（经纬度拾取）</el-dropdown-item>
          <el-dropdown-item command="measure"><i class="el-icon-connection" /> 测距</el-dropdown-item>
          <el-dropdown-item command="clear" divided><i class="el-icon-delete" /> 清除所有标记</el-dropdown-item>
        </el-dropdown-menu>
      </el-dropdown>
    </div>
  </div>
</template>

<script>
const BRANCH_LIST = [
  { label: '贵阳分行（贵阳市）', adcode: 520100, city: '贵阳市' },
  { label: '遵义分行（遵义市）', adcode: 520300, city: '遵义市' },
  { label: '六盘水分行（六盘水市）', adcode: 520200, city: '六盘水市' },
  { label: '安顺分行（安顺市）', adcode: 520400, city: '安顺市' },
  { label: '毕节分行（毕节市）', adcode: 520500, city: '毕节市' },
  { label: '铜仁分行（铜仁市）', adcode: 520600, city: '铜仁市' },
  { label: '凯里分行（黔东南）', adcode: 522600, city: '黔东南州' },
  { label: '都匀分行（黔南）', adcode: 522700, city: '黔南州' },
  { label: '兴义分行（黔西南）', adcode: 522300, city: '黔西南州' },
]

export default {
  name: 'TopToolbar',
  BRANCH_LIST,
  props: {

    toolActive: { type: Boolean, default: false },
    quadrantActive: { type: Boolean, default: false },
    branchActive: { type: Boolean, default: true },
    heatmapActive: { type: Boolean, default: false },
    peerBankActive: { type: Boolean, default: true },
    rangeActive: { type: Boolean, default: false },
    rankingActive: { type: Boolean, default: false },
    compareActive: { type: Boolean, default: false },
    blankSpotActive: { type: Boolean, default: false },
    blankSpotLimit: { type: Number, default: 100 },
    branchList: { type: Array, default: () => [] },
    pendingCount: { type: Number, default: 0 },
    searchTool: { type: Object, default: null },

  },
  data() {
    return {
      selectedCity: 'all', selectedDistrict: 'all',
      selectedBranch: 'all', branchOptions: [], districts: [],
      searchQuery: '', searchResults: [], searchFocused: false,
      // 地址搜索
      addressSearchKeyword: '',
      addressSearchFocused: false,
      addressSearchResults: [],
      addressSearchLoading: false,
      _addressSearchTimer: null,
    }
  },
  computed: {
    BRANCH_LIST() {
      return BRANCH_LIST
    }
  },
  watch: {
    branchList(list) {
      this.searchQuery = ''
      this.searchResults = []
      // 从 branchList prop 提取唯一的一级支行名称，填充"一级支行"下拉框
      this.branchOptions = [...new Set((list || []).map(b => b.primaryBranch).filter(Boolean))].sort()
    }
  },
  methods: {
    // ======== 地址/POI搜索 ========
    onAddressSearchInput() {
      const kw = (this.addressSearchKeyword || '').trim()
      if (kw.length < 2) {
        this.addressSearchResults = []
        return
      }
      if (this._addressSearchTimer) clearTimeout(this._addressSearchTimer)
      this._addressSearchTimer = setTimeout(() => {
        this.doAddressSearch(kw)
      }, 300)
    },

    onAddressSearchEnter() {
      const kw = (this.addressSearchKeyword || '').trim()
      if (kw.length >= 2) {
        if (this._addressSearchTimer) clearTimeout(this._addressSearchTimer)
        this.doAddressSearch(kw)
      }
    },

    onAddressSearchBlur() {
      setTimeout(() => { this.addressSearchFocused = false }, 200)
    },

    async doAddressSearch(keyword) {
      if (!this.searchTool) return
      this.addressSearchLoading = true
      try {
        const result = await this.searchTool.search(keyword)
        const parsed = this.searchTool.parseResults(result)
        this.addressSearchResults = parsed
      } catch (e) {
        this.addressSearchResults = []
      } finally {
        this.addressSearchLoading = false
      }
    },

    selectAddressResult(item) {
      this.addressSearchKeyword = item.name
      this.addressSearchResults = []
      this.addressSearchFocused = false
      this.$emit('address-select', item)
    },

    clearAddressSearch() {
      this.addressSearchKeyword = ''
      this.addressSearchResults = []
      if (this.searchTool) this.searchTool.clearResultMarkers()
    },

    // ======== 网点搜索 ========
    onSearchInput() {
      const q = this.searchQuery.trim().toLowerCase()
      if (!q || q.length < 2 || !this.branchList.length) { this.searchResults = []; return }
      this.searchResults = this.branchList.filter(b => {
        const name = (b.secondaryBranch || b.primaryBranch || '').toLowerCase()
        const parent = (b.primaryBranch || '').toLowerCase()
        return name.includes(q) || parent.includes(q)
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
        const found = this.BRANCH_LIST.find(b => b.adcode === val)
        if (found) {
          this.loadDistricts(found.adcode)
          this.$emit('select-city', found.city || found.label, found.adcode)
        }
      }
    },
    onDistrictChange(val) {
      // 第二个参数传区县名称（如"云岩区"），供后端筛选用
      const feature = val === 'all' ? null : this.districts.find(d => d.properties.adcode === val)
      const name = feature ? feature.properties.name : null
      this.$emit('select-district', val === 'all' ? null : val, name)
    },
    async loadDistricts(adcode) {
      try {
        const res = await fetch(`/data/map_data/${adcode}_full.json`)
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        this.districts = (await res.json()).features || []
      } catch (e) { this.districts = [] }
    },

    onBranchChange(val) {
      this.$emit('filter-branch', val === 'all' ? null : val)
    },

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
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  max-width: 98vw;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.32), rgba(255, 255, 255, 0.10)),
    rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(24px) saturate(180%) contrast(1.06);
  -webkit-backdrop-filter: blur(24px) saturate(180%) contrast(1.06);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.48),
    inset 0 -1px 0 rgba(255, 255, 255, 0.10),
    0 4px 24px rgba(79, 110, 246, 0.08),
    0 1px 4px rgba(0, 0, 0, 0.04);
  padding: 12px 20px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.top-toolbar::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  border-radius: inherit;
  background:
    radial-gradient(circle at 20% 0%, rgba(255,255,255,0.5), transparent 36%),
    linear-gradient(90deg, rgba(255,255,255,0.18), transparent 42%, rgba(255,255,255,0.10));
  pointer-events: none;
}
.top-toolbar::after {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: 11px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  pointer-events: none;
}
@media (prefers-reduced-transparency: reduce) {
  .top-toolbar { background: rgba(255,255,255,0.94); backdrop-filter: none; -webkit-backdrop-filter: none; }
}

.toolbar-row {
  display: flex;
  align-items: center;
  white-space: nowrap;
}
.toolbar-row-top {
  gap: 6px;
}
.toolbar-row-bottom {
  justify-content: center;
  gap: 6px;
}
/* 工具栏内按钮加宽调字体 */
.toolbar-row-bottom ::v-deep .el-button--small {
  padding: 8px 16px;
  font-size: 13px;
}
.toolbar-row ::v-deep .el-button--small {
  padding: 8px 14px;
}
.toolbar-row-bottom > * { flex-shrink: 0; }

.toolbar-label {
  font-size: 13px;
  color: #444;
  font-weight: 500;
  margin-right: 2px;
  white-space: nowrap;
}

/* 行内分隔线 */
.row-divider {
  display: inline-block;
  width: 1px;
  height: 18px;
  background: rgba(0,0,0,0.08);
  margin: 0 2px;
  flex-shrink: 0;
}

/* 网点搜索 */
.toolbar-search {
  position: relative;
  display: inline-flex;
  align-items: center;
  margin-left: 10px;
}
.search-icon {
  position: absolute;
  left: 8px;
  color: #888;
  font-size: 13px;
  pointer-events: none;
  z-index: 1;
}
.search-input {
  width: 160px;
  height: 30px;
  border: 1px solid rgba(0,0,0,0.08);
  border-radius: 7px;
  padding: 0 10px 0 28px;
  font-size: 13px;
  outline: none;
  background: rgba(255,255,255,0.55);
  transition: border-color 0.2s, width 0.2s, background 0.2s, box-shadow 0.2s;
}
.search-input:focus {
  width: 200px;
  border-color: #4f6ef6;
  background: rgba(255,255,255,0.92);
  box-shadow: 0 0 0 3px rgba(79,110,246,0.08);
}
.search-input::placeholder { color: #999; }
.search-dropdown {
  position: absolute;
  top: 100%;
  left: -12px;
  min-width: calc(100% + 60px);
  width: 340px;
  margin-top: 5px;
  background: rgba(255,255,255,0.97);
  border-radius: 10px;
  border: 1px solid rgba(0,0,0,0.06);
  box-shadow: 0 8px 28px rgba(0,0,0,0.10), 0 2px 8px rgba(0,0,0,0.04);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  max-height: 300px;
  overflow-y: auto;
  z-index: 2000;
}
.search-item {
  display: flex;
  flex-direction: column;
  padding: 9px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.12s;
}
.search-item:last-child { border-bottom: none; }
.search-item:hover { background: #f5f8ff; }
.search-item-row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.search-item-name {
  font-size: 13px;
  color: #232845;
  font-weight: 600;
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
.add-compare-btn:hover { color: #3b54d4; }
.search-item-parent {
  font-size: 12px;
  color: #888;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 激活态按钮配色体系 ===== */

/* 蓝色 — 数据层控制（网点、同业） */
.btn-brand { background: #4f6ef6; border-color: #4f6ef6; color: #fff; }
.btn-brand:hover { background: #3b54d4; border-color: #3b54d4; }

/* 紫色 — 综合分析（四象限、排名、对比） */
.btn-analysis { background: #7c3aed; border-color: #7c3aed; color: #fff; }
.btn-analysis:hover { background: #6d28d9; border-color: #6d28d9; }

/* 青色 — 网格覆盖层（热力图、空白点） */
.btn-info { background: #06b6d4; border-color: #06b6d4; color: #fff; }
.btn-info:hover { background: #0891b2; border-color: #0891b2; }

/* 橙色 — 工具/操作（POI、工具） */
.btn-ops { background: #e6a23c; border-color: #e6a23c; color: #fff; }
.btn-ops:hover { background: #d4941f; border-color: #d4941f; }

/* 按钮悬停通用效果 */
.el-button--default:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  transition: all 0.15s ease;
}
.el-button--default:active {
  transform: translateY(0);
}

/* 空白点数量下拉 */
.blank-spot-params {
  display: inline-flex;
  align-items: center;
}
.blank-spot-params .el-select { width: 90px; }

/* 地址搜索 */
.addr-search .search-input { width: 150px; }
.addr-search .search-input:focus { width: 220px; }
.search-clear {
  position: absolute;
  right: 8px;
  color: #999;
  cursor: pointer;
  font-size: 13px;
  z-index: 1;
}
.search-clear:hover { color: #666; }
.search-loading {
  position: absolute;
  right: 8px;
  color: #4f6ef6;
  font-size: 13px;
  z-index: 1;
}
.filter-hint {
  font-size: 11px;
  color: #999;
  margin-left: 4px;
  padding: 1px 6px;
  border-radius: 10px;
  background: rgba(0,0,0,0.04);
}
.filter-hint-active {
  color: #4f6ef6;
  background: rgba(79,110,246,0.08);
}
.toolbar-badge { margin-left: 2px; }
.toolbar-badge >>> .el-badge__content { top: 2px; right: -4px; }
</style>
