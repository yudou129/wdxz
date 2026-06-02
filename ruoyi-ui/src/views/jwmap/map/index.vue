<template>
  <div class="jw-map-container">
    <div id="jwmap" ref="mapEl"></div>
  </div>
</template>

<script>
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { BaiduCRS } from './utils/baiduCrs.js'
import { BoundaryManager } from './utils/boundaryManager.js'
import { MeasureTool } from './utils/measureTool.js'

export default {
  name: 'JwMapView',
  data() {
    return {
      map: null,
      boundaryMgr: null,
      measureTool: null
    }
  },
  mounted() {
    this.$nextTick(() => {
      this.initMap()
    })
  },
  beforeDestroy() {
    if (this.measureTool) {
      this.measureTool._deactivate()
      this.measureTool._clear()
    }
    if (this.map) {
      this.map.remove()
      this.map = null
    }
  },
  methods: {
    initMap() {
      this.map = L.map(this.$refs.mapEl, {
        center: [26.5807, 106.7238],
        zoom: 10,
        minZoom: 9,
        maxZoom: 17,
        zoomControl: true,
        attributionControl: false,
        crs: BaiduCRS,
        zoomAnimation: true,
        fadeAnimation: true
      })

      // 百度离线瓦片层
      // BaiduCRS 的 tile_x 直接匹配 map.py 编号, tile_y 为负值需取反 (-coords.y - 1)
      const tileLayer = L.tileLayer('/tiles/{z}/{x}/{ty}.png', {
        minZoom: 9,
        maxZoom: 17,
        tileSize: 256,
        errorTileUrl: ''
      })

      tileLayer.getTileUrl = function (coords) {
        // CRS y 轴朝上(负值), map.py y 轴朝下; 转换: map.py_y = -crs_y - 1
        const ty = -coords.y - 1
        return `/tiles/${coords.z}/${coords.x}/${ty}.png`
      }

      tileLayer.on('tileerror', () => { /* 静默处理 404 */ })
      tileLayer.addTo(this.map)

      // 边界管理器
      this.boundaryMgr = new BoundaryManager(this.map)
      this.boundaryMgr.init()
        .catch(err => console.error('[jwmap] 边界加载失败:', err))

      // 测量工具
      this.measureTool = new MeasureTool(this.map)
      this.measureTool.init()
    }
  }
}
</script>

<style scoped>
.jw-map-container {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
}
#jwmap {
  width: 100%;
  height: 100%;
}
</style>

<style>
/* 全局样式（不加 scoped，Leaflet 控件需要） */

/* 边界选择器 */
.boundary-control {
  background: rgba(255,255,255,0.92);
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.18);
  padding: 6px 8px;
}
.boundary-select {
  font-size: 14px;
  padding: 6px 8px;
  border: 1px solid #d0d0d0;
  border-radius: 4px;
  background: #fff;
  min-width: 200px;
  cursor: pointer;
  outline: none;
}
.boundary-select:focus {
  border-color: #4a90d9;
}

/* 边界标签 */
.boundary-label {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  font-size: 13px;
  font-weight: bold;
  color: #333;
  text-shadow: 0 0 3px #fff, 0 0 3px #fff;
  white-space: nowrap;
}

/* 测量工具 */
.measure-control {
  background: rgba(255,255,255,0.92);
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.18);
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.measure-btn {
  font-size: 13px;
  padding: 5px 10px;
  border: 1px solid #d0d0d0;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  white-space: nowrap;
}
.measure-btn:hover { background: #f0f0f0; }
.measure-btn.active { background: #4a90d9; color: #fff; border-color: #4a90d9; }
.measure-label span {
  font-size: 12px;
  background: rgba(0,0,0,0.7);
  color: #fff;
  padding: 2px 6px;
  border-radius: 3px;
  white-space: nowrap;
}
.measure-label.total span {
  background: rgba(41,128,185,0.9);
  font-weight: bold;
}
</style>
