/**
 * 百度地图 GL 边界管理器
 *
 * 数据源：public/data/map_data/*.json，BD09 坐标（已转换好）
 * 使用 BMapGL.Polygon + BMapGL.Label 渲染
 */
const CITY_COLORS = [
  '#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6',
  '#1abc9c', '#e67e22', '#2980b9', '#c0392b'
]

const CITY_CODES = [520100, 520200, 520300, 520400, 520500, 520600, 522300, 522600, 522700]

export class BMapBoundaryManager {
  constructor(map) {
    this.map = map
    this.cityOverlays = []      // { polygons: [], label: null }
    this.districtCache = {}     // adcode -> features[]
    this.cities = null
    this.selectEl = null
    this.controlEl = null
    this._visibleOverlays = []  // 当前显示的覆盖物引用
  }

  async init(options = {}) {
    await this._loadProvince()
    this._createAllCityOverlays()
    if (options.createControl !== false) this._createControl()
    this.showAllCities()
  }

  async _loadProvince() {
    const res = await fetch('/data/map_data/520000_full.json')
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    this.cities = (await res.json()).features
  }

  // ---- 创建城市覆盖物 ----

  _createAllCityOverlays() {
    const BMapGL = window.BMapGL
    if (!BMapGL) return

    this.cityOverlays = []
    for (const feature of this.cities) {
      const adcode = feature.properties.adcode
      const color = CITY_COLORS[this._cityIdx(adcode)]
      const overlays = { polygons: [], label: null }

      // 解析 GeoJSON 坐标
      const geom = feature.geometry
      const rings = this._extractRings(geom)

      for (const ring of rings) {
        const points = ring.map(([lng, lat]) => new BMapGL.Point(lng, lat))
        const polygon = new BMapGL.Polygon(points, {
          strokeColor: color,
          strokeWeight: 2,
          strokeOpacity: 0.85,
          fillColor: color,
          fillOpacity: 0.08,
          enableEditing: false,
          enableMassClear: false
        })
        overlays.polygons.push(polygon)
      }

      // 城市名称标签
      const center = feature.properties.center
      if (center) {
        const [lng, lat] = center
        const label = new BMapGL.Label(feature.properties.name, {
          position: new BMapGL.Point(lng, lat),
          offset: new BMapGL.Size(0, 0)
        })
        label.setStyle({
          color: '#333',
          fontSize: '13px',
          fontWeight: 'bold',
          fontFamily: '"Microsoft YaHei", sans-serif',
          border: 'none',
          backgroundColor: 'transparent',
          textShadow: '0 0 4px rgba(255,255,255,1), 0 0 4px rgba(255,255,255,1)',
          pointerEvents: 'none'
        })
        overlays.label = label
      }

      this.cityOverlays.push(overlays)
    }
  }

  _extractRings(geometry) {
    const rings = []
    if (geometry.type === 'Polygon') {
      rings.push(...geometry.coordinates)
    } else if (geometry.type === 'MultiPolygon') {
      for (const polyCoords of geometry.coordinates) {
        rings.push(...polyCoords)
      }
    }
    return rings
  }

  // ---- 创建区县覆盖物 ----

  _createDistrictOverlays(features) {
    const BMapGL = window.BMapGL
    if (!BMapGL) return []

    const overlays = []
    for (const feature of features) {
      const rings = this._extractRings(feature.geometry)
      for (const ring of rings) {
        const points = ring.map(([lng, lat]) => new BMapGL.Point(lng, lat))
        const polygon = new BMapGL.Polygon(points, {
          strokeColor: '#555',
          strokeWeight: 1.2,
          strokeOpacity: 0.75,
          fillOpacity: 0,
          enableEditing: false,
          enableMassClear: false
        })
        overlays.push(polygon)
      }
    }
    return overlays
  }

  // ---- 控制面板（纯 HTML，绝对定位） ----

  _createControl() {
    const div = document.createElement('div')
    div.className = 'boundary-control'
    div.innerHTML = this._buildHTML()
    this.selectEl = div.querySelector('select')
    this.controlEl = div

    // 阻止地图事件穿透
    div.addEventListener('click', e => e.stopPropagation())
    div.addEventListener('dblclick', e => e.stopPropagation())
    div.addEventListener('wheel', e => e.stopPropagation())
    div.addEventListener('touchstart', e => e.stopPropagation())

    this.selectEl.addEventListener('change', (e) => this._onSelect(e))

    // 插入到地图容器
    const container = this.map.getContainer()
    container.appendChild(div)
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
    // 移除旧的区县选项
    if (this.selectEl) {
      const old = this.selectEl.querySelectorAll('optgroup.district-group')
      old.forEach(g => g.remove())
    }

    if (this.districtCache[adcode]) return

    const res = await fetch(`/data/map_data/${adcode}_full.json`)
    this.districtCache[adcode] = (await res.json()).features

    if (!this.selectEl) return

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

  _clearAllVisible() {
    for (const ov of this._visibleOverlays) {
      try { this.map.removeOverlay(ov) } catch (e) { /* ignore */ }
    }
    this._visibleOverlays = []
  }

  showAllCities() {
    this._clearAllVisible()

    for (const city of this.cityOverlays) {
      for (const p of city.polygons) {
        this.map.addOverlay(p)
        this._visibleOverlays.push(p)
      }
      if (city.label) {
        this.map.addOverlay(city.label)
        this._visibleOverlays.push(city.label)
      }
    }
    this.map.centerAndZoom(new BMapGL.Point(106.7238, 26.5807), 10)
  }

  async showCity(adcode) {
    this._clearAllVisible()

    const city = this.cityOverlays[this._cityIdx(adcode)]
    if (!city) return

    for (const p of city.polygons) {
      this.map.addOverlay(p)
      this._visibleOverlays.push(p)
    }
    if (city.label) {
      this.map.addOverlay(city.label)
      this._visibleOverlays.push(city.label)
    }

    // 自动加载区县缓存
    if (!this.districtCache[adcode]) {
      await this._loadDistrictOptions(adcode)
    }

    const feature = this.cities.find(c => c.properties.adcode === adcode)
    if (feature && feature.properties.center) {
      const [lng, lat] = feature.properties.center
      this.map.centerAndZoom(new BMapGL.Point(lng, lat), 11)
    }
  }

  showDistrict(adcode) {
    this._clearAllVisible()

    let dFeature = null
    let parentCode = null
    for (const [code, features] of Object.entries(this.districtCache)) {
      dFeature = features.find(f => f.properties.adcode === adcode)
      if (dFeature) { parentCode = +code; break }
    }
    if (!dFeature) return

    // 显示父城市
    const pCode = this._findParentCityCode(adcode)
    if (pCode !== null) {
      const city = this.cityOverlays[this._cityIdx(pCode)]
      if (city) {
        for (const p of city.polygons) {
          this.map.addOverlay(p)
          this._visibleOverlays.push(p)
        }
      }
    }

    // 显示区县多边形
    const overlays = this._createDistrictOverlays([dFeature])
    for (const ov of overlays) {
      this.map.addOverlay(ov)
      this._visibleOverlays.push(ov)
    }

    if (dFeature.properties.center) {
      const [lng, lat] = dFeature.properties.center
      this.map.centerAndZoom(new BMapGL.Point(lng, lat), 13)
    }
  }

  highlightCity(adcode) {
    // 无需高亮，已有颜色
  }

  // ---- 清理 ----

  destroy() {
    this._clearAllVisible()
    if (this.controlEl && this.controlEl.parentNode) {
      this.controlEl.parentNode.removeChild(this.controlEl)
    }
    this.controlEl = null
    this.selectEl = null
    this.cities = null
    this.cityOverlays = []
    this.districtCache = {}
  }

  // ---- 工具 ----

  _findParentCityCode(districtAdcode) {
    for (const [code, features] of Object.entries(this.districtCache)) {
      const found = features.find(f => f.properties.adcode === districtAdcode)
      if (found) return +code
    }
    return null
  }

  _cityIdx(adcode) {
    const i = CITY_CODES.indexOf(adcode)
    return i === -1 ? 0 : i
  }
}
