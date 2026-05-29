/**
 * 边界管理器
 *
 * 数据源：public/data/map_data/*.json，BD09 坐标（已转换好）
 * 无需再做坐标转换，直接渲染为 Leaflet 矢量图层
 */

import L from 'leaflet'

// 城市色板
const CITY_COLORS = [
  '#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6',
  '#1abc9c', '#e67e22', '#2980b9', '#c0392b'
]

const CITY_CODES = [520100, 520200, 520300, 520400, 520500, 520600, 522300, 522600, 522700]

export class BoundaryManager {
  constructor(map) {
    this.map = map

    this.cityLayer = null
    this.districtLayer = null
    this.highlightLayer = null

    this.cities = null
    this.districtCache = {}

    this.selectEl = null
  }

  // ---- 初始化 ----

  async init(options = {}) {
    await this._loadProvince()
    this._createCityLayer()
    if (options.createControl !== false) this._createControl()
    this.showAllCities()
  }

  async _loadProvince() {
    const res = await fetch('/data/map_data/520000_full.json')
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    this.cities = (await res.json()).features
  }

  // ---- 图层 ----

  _createCityLayer() {
    const fc = { type: 'FeatureCollection', features: this.cities }
    this.cityLayer = L.geoJSON(fc, {
      style: (f) => ({
        color: CITY_COLORS[this._cityIdx(f.properties.adcode)],
        weight: 2,
        opacity: 0.85,
        fillColor: CITY_COLORS[this._cityIdx(f.properties.adcode)],
        fillOpacity: 0.08
      }),
      onEachFeature: (f, layer) => {
        layer.bindTooltip(f.properties.name, {
          permanent: true, direction: 'center', className: 'boundary-label'
        })
      }
    })
  }

  _makeDistrictLayer(features) {
    return L.geoJSON({ type: 'FeatureCollection', features }, {
      style: () => ({ color: '#555', weight: 1.2, opacity: 0.75, fillOpacity: 0 }),
      onEachFeature: (f, layer) => {
        layer.bindTooltip(f.properties.name, { permanent: false, direction: 'center' })
      }
    })
  }

  _makeHighlightLayer(feature) {
    return L.geoJSON({ type: 'FeatureCollection', features: [feature] }, {
      style: () => ({ color: '#ff0000', weight: 3, opacity: 1, fillColor: '#ff6666', fillOpacity: 0.15 })
    })
  }

  // ---- 控制面板 ----

  _createControl() {
    const BoundaryControl = L.Control.extend({
      onAdd: () => {
        const div = L.DomUtil.create('div', 'boundary-control')
        div.innerHTML = this._buildHTML()
        this.selectEl = div.querySelector('select')
        L.DomEvent.disableClickPropagation(div)
        L.DomEvent.disableScrollPropagation(div)
        L.DomEvent.on(this.selectEl, 'change', this._onSelect, this)
        return div
      }
    })
    new BoundaryControl({ position: 'topleft' }).addTo(this.map)
  }

  _buildHTML() {
    let html = '<select class="boundary-select">'
    html += '<option value="all">📍 贵州省</option>'
    html += '<optgroup label="── 市 / 州 ──">'
    for (const c of this.cities) {
      html += `<option value="c_${c.properties.adcode}">${c.properties.name}</option>`
    }
    html += '</optgroup>'
    html += '</select>'
    return html
  }

  async _loadDistrictOptions(adcode) {
    if (this.selectEl) {
      const old = this.selectEl.querySelectorAll('optgroup.district-group')
      old.forEach(g => g.remove())
    }

    if (this.districtCache[adcode]) return

    const res = await fetch(`/data/map_data/${adcode}_full.json`)
    this.districtCache[adcode] = (await res.json()).features

    if (!this.selectEl) return  // No native dropdown, only caching needed

    const city = this.cities.find(c => c.properties.adcode === adcode)
    if (!city || !this.districtCache[adcode].length) return

    let g = `<optgroup class="district-group" label="── ${city.properties.name} 区县 ──">`
    const sorted = [...this.districtCache[adcode]].sort((a, b) => a.properties.adcode - b.properties.adcode)
    for (const d of sorted) {
      g += `<option value="d_${d.properties.adcode}">&nbsp;&nbsp;${d.properties.name}</option>`
    }
    g += '</optgroup>'

    const cg = this.selectEl.querySelector('optgroup:last-of-type')
    if (cg) cg.insertAdjacentHTML('afterend', g)
  }

  async _onSelect(e) {
    const v = e.target.value
    if (v === 'all') { this.showAllCities(); return }
    const [type, code] = v.split('_')
    const adcode = +code
    if (type === 'c') {
      await this._loadDistrictOptions(adcode)
      this.showCity(adcode)
    } else if (type === 'd') {
      this.showDistrict(adcode)
    }
  }

  // ---- 显示 / 跳转 ----

  showAllCities() {
    this._clearHL()
    this._removeDL()
    if (!this.map.hasLayer(this.cityLayer)) this.cityLayer.addTo(this.map)
    this.map.setView([26.5807, 106.7238], 10)
  }

  async showCity(adcode) {
    this._clearHL()
    this._removeDL()
    if (!this.map.hasLayer(this.cityLayer)) this.cityLayer.addTo(this.map)

    const feature = this.cities.find(c => c.properties.adcode === adcode)
    if (!feature) return

    // Auto-load districts if not cached yet (supports TopToolbar path)
    if (!this.districtCache[adcode]) {
      await this._loadDistrictOptions(adcode)
    }

    this.highlightLayer = this._makeHighlightLayer(feature).addTo(this.map)

    const districts = this.districtCache[adcode]
    if (districts) {
      this.districtLayer = this._makeDistrictLayer(districts).addTo(this.map)
    }

    const [lng, lat] = feature.properties.center
    this.map.setView([lat, lng], 11)
  }

  showDistrict(adcode) {
    let dFeature = null
    let parentCode = null
    for (const [code, features] of Object.entries(this.districtCache)) {
      dFeature = features.find(f => f.properties.adcode === adcode)
      if (dFeature) { parentCode = +code; break }
    }
    if (!dFeature) return

    if (!this.map.hasLayer(this.cityLayer)) this.cityLayer.addTo(this.map)

    this._clearHL()
    this._removeDL()

    const districts = this.districtCache[parentCode]
    if (districts) {
      this.districtLayer = this._makeDistrictLayer(districts).addTo(this.map)
    }

    this.highlightLayer = this._makeHighlightLayer(dFeature).addTo(this.map)

    const [lng, lat] = dFeature.properties.center
    this.map.setView([lat, lng], 13)
  }

  // ---- 清理 ----

  _removeDL() {
    if (this.districtLayer) { this.map.removeLayer(this.districtLayer); this.districtLayer = null }
  }
  _clearHL() {
    if (this.highlightLayer) { this.map.removeLayer(this.highlightLayer); this.highlightLayer = null }
  }

  _cityIdx(adcode) {
    const i = CITY_CODES.indexOf(adcode)
    return i === -1 ? 0 : i
  }
}
