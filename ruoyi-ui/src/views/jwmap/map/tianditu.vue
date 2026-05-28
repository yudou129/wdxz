<template>
  <div class="jw-map-container">
    <div id="jwmap-tianditu" ref="mapEl"></div>
  </div>
</template>

<script>
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { TiandituBd09Crs } from './utils/tiandituCrs.js'
import { BoundaryManager } from './utils/boundaryManager.js'
import { MeasureTool } from './utils/measureTool.js'

export default {
  name: 'JwMapTianditu',
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
      // 天地图瓦片使用 EPSG:3857，但用户坐标统一用 BD09（由 TiandituBd09Crs 自动转换）
      this.map = L.map(this.$refs.mapEl, {
        crs: TiandituBd09Crs,
        center: [26.5807, 106.7238],
        zoom: 10,
        minZoom: 9,
        maxZoom: 17,
        zoomControl: true,
        attributionControl: false,
        zoomAnimation: true,
        fadeAnimation: true
      })

      // 天地图矢量底图（vec）
      const vecLayer = L.tileLayer('/tiles_tianditu/vec/{z}/{x}/{y}.png', {
        minZoom: 9,
        maxZoom: 17,
        tileSize: 256,
        errorTileUrl: ''
      })
      vecLayer.on('tileerror', () => { /* 静默处理 404 */ })
      vecLayer.addTo(this.map)

      // 天地图注记层（cva），叠加在底图上
      const cvaLayer = L.tileLayer('/tiles_tianditu/cva/{z}/{x}/{y}.png', {
        minZoom: 9,
        maxZoom: 17,
        tileSize: 256,
        errorTileUrl: ''
      })
      cvaLayer.on('tileerror', () => { /* 静默处理 404 */ })
      cvaLayer.addTo(this.map)

      // 边界管理器
      this.boundaryMgr = new BoundaryManager(this.map)
      this.boundaryMgr.init()
        .then(() => {
          // 调试网格 1（红色）
          const G1 = { lng1: 106.617339, lng2: 106.627389, lat1: 26.639653, lat2: 26.648637 }
          L.rectangle([[G1.lat1, G1.lng1], [G1.lat2, G1.lng2]], {
            color: '#ff0000', weight: 3, fillColor: '#ff0000', fillOpacity: 0.1
          }).addTo(this.map)
            .bindTooltip(`网格1<br>${G1.lat1}~${G1.lat2}<br>${G1.lng1}~${G1.lng2}`, {
              permanent: true, direction: 'top', className: 'grid-label'
            })

          // 调试网格 2（蓝色）
          const G2 = { lng1: 106.778747, lng2: 106.788789, lat1: 26.549563, lat2: 26.558547 }
          L.rectangle([[G2.lat1, G2.lng1], [G2.lat2, G2.lng2]], {
            color: '#0000ff', weight: 3, fillColor: '#0000ff', fillOpacity: 0.1
          }).addTo(this.map)
            .bindTooltip(`网格2<br>${G2.lat1}~${G2.lat2}<br>${G2.lng1}~${G2.lng2}`, {
              permanent: true, direction: 'top', className: 'grid-label'
            })

          this.map.setView([(G2.lat1 + G2.lat2) / 2, (G2.lng1 + G2.lng2) / 2], 14)
        })
        .catch(err => console.error('[jwmap-tianditu] 边界加载失败:', err))

      // 测量工具
      this.measureTool = new MeasureTool(this.map)
      this.measureTool.init()

      // 暴露调试接口
      window.__jwmap_tianditu = this.map
      window.__boundaryMgr_tianditu = this.boundaryMgr
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
#jwmap-tianditu {
  width: 100%;
  height: 100%;
}
</style>
