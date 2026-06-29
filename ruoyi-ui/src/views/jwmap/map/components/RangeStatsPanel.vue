<template>
  <div class="range-stats-panel" v-show="visible">
    <div class="rsp-header">
      <span class="rsp-title">范围统计</span>
      <el-button size="mini" icon="el-icon-close" circle @click="$emit('close')" />
    </div>

    <div class="rsp-body">
      <!-- 形状 + 半径 选择 -->
      <div class="rsp-controls">
        <div class="rsp-control-row">
          <span class="rsp-label">形状</span>
          <el-radio-group v-model="shapeType" size="mini" @change="onParamChange">
            <el-radio-button label="circle">圆形</el-radio-button>
            <el-radio-button label="square">方形</el-radio-button>
          </el-radio-group>
        </div>
        <div class="rsp-control-row">
          <span class="rsp-label">范围</span>
          <el-radio-group v-model="radius" size="mini" @change="onParamChange">
            <el-radio-button :label="100">100m</el-radio-button>
            <el-radio-button :label="200">200m</el-radio-button>
            <el-radio-button :label="500">500m</el-radio-button>
            <el-radio-button :label="1000">1km</el-radio-button>
            <el-radio-button :label="2000">2km</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <!-- 放置提示 / 统计结果 -->
      <div v-if="!placed" class="rsp-place-hint">
        <i class="el-icon-map-location" />
        <span>在地图上按住拖拽划定范围</span>
      </div>

      <template v-if="placed">
        <!-- 加载中 -->
        <div v-if="loading" class="rsp-loading">
          <i class="el-icon-loading" /> 查询中...
        </div>

        <!-- 错误提示 -->
        <div v-if="errMsg && !loading" class="rsp-err-msg">
          <i class="el-icon-warning" /> {{ errMsg }}
        </div>

        <!-- 统计结果 -->
        <template v-if="!loading">
          <div class="rsp-summary">
            共 <strong>{{ totalCount }}</strong> 个POI点
          </div>

          <!-- 分类统计卡片 -->
          <div class="rsp-type-cards" v-if="typeStats.length > 0">
            <div class="rsp-type-card" v-for="st in typeStats" :key="st.type"
                 :style="{ borderLeftColor: st.color }">
              <div class="rsp-type-card-name">{{ st.type || '未知' }}</div>
              <div class="rsp-type-card-count">{{ st.count }}</div>
            </div>
          </div>

          <!-- 明细列表 -->
          <div class="rsp-detail-section">
            <div class="rsp-detail-header" @click="detailExpanded = !detailExpanded">
              <span>明细列表 ({{ poiList.length }})</span>
              <i :class="detailExpanded ? 'el-icon-arrow-up' : 'el-icon-arrow-down'" />
            </div>
            <div v-show="detailExpanded" class="rsp-detail-list">
              <div v-for="(item, idx) in poiList" :key="idx" class="rsp-detail-item"
                   @click="onItemClick(item)">
                <span class="rsp-detail-type-tag" :style="{ background: getTypeColor(item.poiType) }">
                  {{ item.poiType || '?' }}
                </span>
                <span class="rsp-detail-name">{{ item.poiName }}</span>
                <span class="rsp-detail-addr">{{ item.address }}</span>
              </div>
              <div v-if="poiList.length === 0" class="rsp-empty">该范围内暂无POI数据</div>
            </div>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<script>
import { getPoiWithinRange } from '@/api/jwmap/data'

// 分类色盘
const TYPE_COLORS = [
  '#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399',
  '#9b59b6', '#1abc9c', '#e74c3c', '#3498db', '#2ecc71',
  '#f39c12', '#e91e63', '#00bcd4', '#ff9800', '#795548'
]

export default {
  name: 'RangeStatsPanel',
  props: {
    visible: { type: Boolean, default: false },
    currentCity: { type: String, default: '' }
  },
  data() {
    return {
      shapeType: 'circle',
      radius: 500,
      placed: false,
      centerLat: null,
      centerLng: null,
      loading: false,
      poiList: [],
      typeStats: [],
      detailExpanded: true,
      typeColorMap: {},
      errMsg: ''
    }
  },
  computed: {
    totalCount() {
      return this.poiList.length
    }
  },
  watch: {
    visible(v) {
      if (!v) this.reset()
    },
    currentCity() {
      this.reset()
    }
  },
  methods: {
    reset() {
      this.placed = false
      this.centerLat = null
      this.centerLng = null
      this.loading = false
      this.poiList = []
      this.typeStats = []
      this.typeColorMap = {}
      this.errMsg = ''
      this.detailExpanded = true
    },
    // 设置中心点和半径（拖拽绘制后调用）
    setCenter(lat, lng, drawnRadius) {
      this.centerLat = lat
      this.centerLng = lng
      if (drawnRadius != null) {
        this.radius = drawnRadius
      }
      this.placed = true
      this.loadData()
    },
    onParamChange() {
      if (this.placed && this.centerLat != null) {
        this.loadData()
        this.$emit('param-change', { shapeType: this.shapeType, radius: this.radius })
      }
    },
    async loadData() {
      if (!this.currentCity || this.centerLat == null || this.centerLng == null) {
        if (!this.currentCity) console.warn('[jwmap] 范围统计：currentCity 为空，请先选择城市')
        return
      }
      this.loading = true
      this.errMsg = ''
      try {
        const res = await getPoiWithinRange({
          city: this.currentCity,
          shapeType: this.shapeType,
          centerLng: this.centerLng,
          centerLat: this.centerLat,
          radius: this.radius
        })
        this.poiList = res.data || []
        this.aggregateByType()
        if (this.poiList.length === 0) {
          this.errMsg = '该范围内暂无POI数据'
        }
      } catch (e) {
        console.error('[jwmap] 范围统计查询失败:', e)
        this.poiList = []
        this.typeStats = []
        this.errMsg = '查询失败: ' + (e.message || '未知错误')
      }
      this.loading = false
    },
    aggregateByType() {
      const map = {}
      for (const p of this.poiList) {
        const t = p.poiType || '未知'
        map[t] = (map[t] || 0) + 1
      }
      // 按数量降序排列
      const entries = Object.entries(map).sort((a, b) => b[1] - a[1])
      let ci = 0
      this.typeStats = entries.map(([type, count]) => {
        if (!this.typeColorMap[type]) {
          this.typeColorMap[type] = TYPE_COLORS[ci % TYPE_COLORS.length]
          ci++
        }
        return { type, count, color: this.typeColorMap[type] }
      })
    },
    getTypeColor(type) {
      return this.typeColorMap[type] || '#909399'
    },
    onItemClick(item) {
      // 点击定位到 POI 位置
      if (item.latitude && item.longitude) {
        this.$emit('locate', [item.latitude, item.longitude])
      }
    }
  }
}
</script>

<style scoped>
.range-stats-panel {
  position: absolute;
  top: 100px;
  right: 12px;
  bottom: 12px;
  z-index: 1000;
  width: min(320px, 90vw);
  background: rgba(255, 255, 255, 0.95);
  border-radius: 10px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  overflow: visible;
}
.rsp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #eee;
}
.rsp-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}
.rsp-body {
  padding: 10px 12px;
  overflow-y: auto;
  flex: 1;
}
.rsp-controls {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rsp-control-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rsp-label {
  font-size: 13px;
  color: #444;
  white-space: nowrap;
  min-width: 32px;
}
.rsp-place-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 20px 0;
  color: #444;
  font-size: 13px;
  cursor: default;
}
.rsp-place-hint i {
  font-size: 18px;
  color: #409eff;
}
.rsp-loading {
  text-align: center;
  padding: 16px 0;
  color: #555;
  font-size: 13px;
}
.rsp-summary {
  font-size: 13px;
  color: #444;
  padding: 8px 0 6px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 8px;
}
.rsp-type-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.rsp-type-card {
  flex: 1 0 calc(50% - 3px);
  min-width: 0;
  border-radius: 6px;
  padding: 8px 10px;
  background: #f8f9fa;
  border-left: 3px solid #409eff;
}
.rsp-type-card-name {
  font-size: 14px;
  color: #444;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.rsp-type-card-count {
  font-size: 20px;
  font-weight: 700;
  color: #333;
  line-height: 1.2;
}
.rsp-detail-section {
  border-top: 1px solid #f0f0f0;
  padding-top: 6px;
}
.rsp-detail-header {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #444;
  cursor: pointer;
  padding: 4px 0;
  user-select: none;
}
.rsp-detail-header:hover {
  color: #409eff;
}
.rsp-detail-list {
  max-height: 260px;
  overflow-y: auto;
}
.rsp-detail-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 4px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.rsp-detail-item:hover {
  background: #f0f7ff;
}
.rsp-detail-type-tag {
  font-size: 12px;
  color: #fff;
  border-radius: 3px;
  padding: 1px 5px;
  white-space: nowrap;
  flex-shrink: 0;
}
.rsp-detail-name {
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}
.rsp-detail-addr {
  color: #555;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 80px;
  flex-shrink: 0;
}
.rsp-empty {
  color: #555;
  font-size: 13px;
  text-align: center;
  padding: 12px 0;
}
.rsp-err-msg {
  color: #e6a23c;
  font-size: 13px;
  text-align: center;
  padding: 12px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
</style>
