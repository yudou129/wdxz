/**
 * 百度地图 GL 地址搜索工具 — BMapGL.LocalSearch 封装
 *
 * 使用百度地图内置的本地搜索能力，搜索地址/POI/地名，
 * 无需额外 API 调用，已随 BMapGL SDK 一起加载。
 *
 * 使用方式：
 *   const tool = new BMapSearchTool(map)
 *   const results = await tool.search('贵阳火车站')
 *   tool.addResultMarkers(results)  // 在地图上显示搜索结果
 */
export class BMapSearchTool {
  constructor(map) {
    this.map = map
    this._localSearch = null
    this._resultMarkers = []
    this._resultLabels = []
  }

  /**
   * 执行地址/POI搜索
   * @param {string} keyword — 搜索关键词
   * @returns {Promise<Array<{name, address, point, uid}>>}
   */
  search(keyword) {
    return new Promise((resolve, reject) => {
      const BMapGL = window.BMapGL
      if (!BMapGL) {
        reject(new Error('BMapGL 未加载'))
        return
      }
      if (!this._localSearch) {
        this._localSearch = new BMapGL.LocalSearch(this.map, {
          onSearchComplete: (results) => {
            if (this._searchResolve) {
              this._searchResolve(results)
            }
          }
        })
      }

      this._searchResolve = resolve
      this._localSearch.search(keyword)
    })
  }

  /**
   * 解析搜索结果为标准格式
   * @returns {Array<{name, address, point, uid}>}
   */
  parseResults(localResult) {
    const results = []
    if (!localResult) return results

    // BMapGL.LocalSearch 回调中获得的是 LocalResult 对象
    // 包含 getCurrentNumPois() 返回当前页面结果数
    const count = localResult.getCurrentNumPois ? localResult.getCurrentNumPois() : 0
    for (let i = 0; i < count; i++) {
      const poi = localResult.getPoi(i)
      if (poi && poi.point) {
        results.push({
          name: poi.title || '',
          address: poi.address || '',
          point: poi.point,
          uid: poi.uid || '',
          phone: poi.phoneNumber || '',
          tags: poi.tags || ''
        })
      }
    }
    return results
  }

  /**
   * 在地图上显示搜索结果标记
   * @param {Array} results — parseResults 的输出
   */
  showResultMarkers(results) {
    this.clearResultMarkers()
    if (!results || !results.length) return

    const BMapGL = window.BMapGL
    if (!BMapGL) return

    for (const r of results) {
      // 红色圆形标记
      const marker = new BMapGL.Marker(r.point, {
        enableDragging: false,
        massClear: false
      })
      this.map.addOverlay(marker)
      this._resultMarkers.push(marker)

      // 名称标签
      const label = new BMapGL.Label(r.name, {
        position: r.point,
        offset: new BMapGL.Size(0, -28)
      })
      label.setStyle({
        padding: '2px 6px',
        background: 'rgba(255,255,255,0.92)',
        border: '1px solid #e74c3c',
        borderRadius: '3px',
        fontSize: '12px',
        color: '#e74c3c',
        fontWeight: '600',
        whiteSpace: 'nowrap',
        boxShadow: '0 1px 4px rgba(0,0,0,0.15)'
      })
      this.map.addOverlay(label)
      this._resultLabels.push(label)

      // 点击标记打开信息窗口
      marker.addEventListener('click', () => {
        const infoWin = new BMapGL.InfoWindow(
          '<div style="font-size:13px;line-height:1.6">' +
          '<b style="font-size:14px">' + this._escapeHtml(r.name) + '</b>' +
          '<div style="color:#555;margin-top:3px">' + this._escapeHtml(r.address) + '</div>' +
          '</div>',
          { width: 240 }
        )
        this.map.openInfoWindow(infoWin, r.point)
      })
    }
  }

  clearResultMarkers() {
    const map = this.map
    if (map) {
      this._resultMarkers.forEach(m => { try { map.removeOverlay(m) } catch(e) {} })
      this._resultLabels.forEach(l => { try { map.removeOverlay(l) } catch(e) {} })
    }
    this._resultMarkers = []
    this._resultLabels = []
  }

  _escapeHtml(str) {
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
  }
}
