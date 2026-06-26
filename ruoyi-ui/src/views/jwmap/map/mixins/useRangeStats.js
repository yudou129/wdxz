import L from 'leaflet'

/**
 * 范围统计 mixin — 地图拖拽绘制圆形/方形范围、清除重绘
 */
export default {
  methods: {
    onToggleRangeStats() {
      if (!this.rangeModeActive && !this.currentCity) {
        this.$message.warning('请先在工具栏选择城市')
        return
      }
      this.rangeModeActive = !this.rangeModeActive
      if (this.rangeModeActive) {
        this.rangeStats.visible = true
        this.map.dragging.disable()
        this.map.getContainer().style.cursor = 'crosshair'
        this.map.on('mousedown', this.onRangeDrawStart)
        this.map.on('mousemove', this.onRangeDrawMove)
        this.map.on('mouseup', this.onRangeDrawEnd)
        this.$message.info('在地图上按住拖拽划定范围')
      } else {
        this.unbindRangeEvents()
        this.rangeStats.visible = false
        this.clearRangeShape()
        this.map.dragging.enable()
        this.map.getContainer().style.cursor = ''
      }
    },

    onCloseRangeStats() {
      this.rangeModeActive = false
      this.rangeStats.visible = false
      this.unbindRangeEvents()
      this.clearRangeShape()
      this.map.dragging.enable()
      this.map.getContainer().style.cursor = ''
    },

    unbindRangeEvents() {
      this.map.off('mousedown', this.onRangeDrawStart)
      this.map.off('mousemove', this.onRangeDrawMove)
      this.map.off('mouseup', this.onRangeDrawEnd)
      this._drawing = null
    },

    onRangeDrawStart(e) {
      if (!this.rangeModeActive) return
      this._drawing = { start: e.latlng, tempLayer: null }
    },

    onRangeDrawMove(e) {
      if (!this._drawing || !this._drawing.start) return
      const start = this._drawing.start
      const end = e.latlng
      if (!end) return
      const shapeType = this.$refs.rangeStats ? this.$refs.rangeStats.shapeType : 'circle'
      const previewStyle = { color: '#409eff', weight: 1, dashArray: '5,5', fillOpacity: 0.05 }
      if (shapeType === 'circle') {
        const r = start.distanceTo(end)
        if (this._drawing.tempLayer && this._drawing.tempLayer.setRadius) {
          this._drawing.tempLayer.setRadius(r)
        } else {
          if (this._drawing.tempLayer) this.map.removeLayer(this._drawing.tempLayer)
          this._drawing.tempLayer = L.circle([start.lat, start.lng], { radius: r, ...previewStyle }).addTo(this.map)
        }
      } else {
        const bounds = L.latLngBounds(start, end)
        if (this._drawing.tempLayer && this._drawing.tempLayer.setBounds) {
          this._drawing.tempLayer.setBounds(bounds)
        } else {
          if (this._drawing.tempLayer) this.map.removeLayer(this._drawing.tempLayer)
          this._drawing.tempLayer = L.rectangle(bounds, previewStyle).addTo(this.map)
        }
      }
    },

    onRangeDrawEnd(e) {
      if (!this._drawing || !this._drawing.start) return
      const start = this._drawing.start
      const end = e.latlng
      if (this._drawing.tempLayer) { this.map.removeLayer(this._drawing.tempLayer); this._drawing.tempLayer = null }
      this._drawing = null
      if (start.distanceTo(end) < 10) return

      const shapeType = this.$refs.rangeStats ? this.$refs.rangeStats.shapeType : 'circle'
      let centerLat, centerLng, radius

      if (shapeType === 'circle') {
        centerLat = start.lat; centerLng = start.lng
        radius = Math.round(start.distanceTo(end))
      } else {
        const bounds = L.latLngBounds(start, end)
        const sw = bounds.getSouthWest(), ne = bounds.getNorthEast()
        centerLat = (sw.lat + ne.lat) / 2
        centerLng = (sw.lng + ne.lng) / 2
        const halfWidth = start.distanceTo(L.latLng(start.lat, end.lng)) / 2
        const halfHeight = start.distanceTo(L.latLng(end.lat, start.lng)) / 2
        radius = Math.round(Math.max(halfWidth, halfHeight))
      }

      this.clearRangeShape()
      this.drawRangeShape(centerLat, centerLng, radius, shapeType)
      if (this.$refs.rangeStats) {
        this.$refs.rangeStats.setCenter(centerLat, centerLng, radius)
      }
    },

    drawRangeShape(lat, lng, radius, shapeType) {
      if (this.rangeShapeLayer) { this.map.removeLayer(this.rangeShapeLayer); this.rangeShapeLayer = null }
      const style = { color: '#409eff', weight: 2, fillOpacity: 0.1, opacity: 0.7 }
      if (shapeType === 'circle') {
        this.rangeShapeLayer = L.circle([lat, lng], { radius, ...style }).addTo(this.map)
      } else {
        const halfSide = radius
        const latDelta = halfSide / 111320.0
        const lngDelta = halfSide / (111320.0 * Math.cos(lat * Math.PI / 180))
        const bounds = [[lat - latDelta, lng - lngDelta], [lat + latDelta, lng + lngDelta]]
        this.rangeShapeLayer = L.rectangle(bounds, style).addTo(this.map)
      }
    },

    clearRangeShape() {
      if (this.rangeShapeLayer) { this.map.removeLayer(this.rangeShapeLayer); this.rangeShapeLayer = null }
    },

    onRangeItemLocate(latlng) {
      if (latlng && latlng.length === 2) { this.map.flyTo(latlng, 16) }
    },

    onRangeParamChange(params) {
      if (this.$refs.rangeStats && this.$refs.rangeStats.placed) {
        const lat = this.$refs.rangeStats.centerLat
        const lng = this.$refs.rangeStats.centerLng
        if (lat != null && lng != null) {
          this.drawRangeShape(lat, lng, params.radius, params.shapeType)
        }
      }
    }
  }
}
