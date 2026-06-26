import L from 'leaflet'
import { getPeerBankDistance, getPeerBankList, getNearbyBranches } from '@/api/jwmap/data'

// 同业银行品牌色映射表
const PEER_BANK_STYLE_MAP = {
  '建设银行': { css: 'peer-bank-ccb',  text: '建', color: '#1a73e8' },
  '建设银行股份有限公司': { css: 'peer-bank-ccb',  text: '建', color: '#1a73e8' },
  '农业银行': { css: 'peer-bank-abc',  text: '农', color: '#34a853' },
  '中国农业银行': { css: 'peer-bank-abc',  text: '农', color: '#34a853' },
  '中国银行': { css: 'peer-bank-boc',  text: '中', color: '#c41230' },
  '交通银行': { css: 'peer-bank-comm', text: '交', color: '#003366' },
  '招商银行': { css: 'peer-bank-cmb',  text: '招', color: '#d52b1e' },
  '邮储银行': { css: 'peer-bank-psbc', text: '邮', color: '#009a44' },
  '中国邮政储蓄银行': { css: 'peer-bank-psbc', text: '邮', color: '#009a44' },
  '中信银行': { css: 'peer-bank-citic', text: '信', color: '#e60012' },
  '浦发银行': { css: 'peer-bank-spdb', text: '浦', color: '#004098' },
  '民生银行': { css: 'peer-bank-cmbc', text: '民', color: '#2e6db4' },
  '兴业银行': { css: 'peer-bank-cib',  text: '兴', color: '#003399' },
  '光大银行': { css: 'peer-bank-ceb',  text: '光', color: '#7c3aed' },
  '平安银行': { css: 'peer-bank-pab',  text: '平', color: '#f37021' },
  '华夏银行': { css: 'peer-bank-hxb',  text: '华', color: '#dc2626' },
  '贵阳银行': { css: 'peer-bank-gyb',  text: '筑', color: '#1d6fa0' },
  '贵州银行': { css: 'peer-bank-gzb',  text: '黔', color: '#6b8e23' }
}
const PEER_BANK_AUTO_COLORS = [
  '#6366f1', '#ec4899', '#14b8a6', '#f97316',
  '#8b5cf6', '#06b6d4', '#84cc16', '#e11d48'
]

/**
 * 同业银行地标 mixin — 银行图标渲染、图例、附近银行
 */
export default {
  methods: {
    getBankStyle(bankName) {
      if (!bankName) return { css: '', text: '?', color: '#999' }
      const name = bankName.trim()
      if (PEER_BANK_STYLE_MAP[name]) return PEER_BANK_STYLE_MAP[name]
      for (const key of Object.keys(PEER_BANK_STYLE_MAP)) {
        if (name.includes(key)) return PEER_BANK_STYLE_MAP[key]
      }
      const firstChar = name.charAt(0)
      if (!this.peerBankAutoMap[name]) {
        this.peerBankAutoMap[name] = PEER_BANK_AUTO_COLORS[this.peerBankAutoIndex % PEER_BANK_AUTO_COLORS.length]
        this.peerBankAutoIndex++
      }
      return { css: '', text: firstChar, color: this.peerBankAutoMap[name], auto: true }
    },

    async loadPeerBankMarkers() {
      if (!this.currentCity) return
      this.peerBankLayer.clearLayers()
      try {
        const res = await getPeerBankList(this.currentCity)
        const list = res.data || []
        if (!list.length) return
        const bankNamesInCity = new Set()
        for (const p of list) {
          if (p.longitude == null || p.latitude == null) continue
          const style = this.getBankStyle(p.bankName)
          bankNamesInCity.add(p.bankName || '未知')
          const className = 'peer-bank-icon' + (style.css ? ' ' + style.css : '')
          const icon = L.divIcon({
            className,
            html: style.text,
            iconSize: [24, 24],
            iconAnchor: [12, 12]
          })
          const m = L.marker([p.latitude, p.longitude], { icon })
          m.bindPopup(this.buildPeerBankPopup(p), { closeButton: true, maxWidth: 280 })
          this.peerBankLayer.addLayer(m)
        }
        this.peerBankNames = Array.from(bankNamesInCity)
      } catch (e) {
        console.error('[jwmap] 加载同业银行地标失败:', e)
      }
    },

    buildPeerBankPopup(p) {
      const style = this.getBankStyle(p.bankName)
      const color = style.color || '#d40000'
      const name = p.bankName || ''
      return '<div style="font-size:13px;line-height:1.6;min-width:160px">'
        + '<div style="font-weight:700;font-size:14px;margin-bottom:4px;color:' + color + '">'
        + '<span style="display:inline-block;width:18px;height:18px;border-radius:3px;border:2px solid '
        + color + ';text-align:center;font-size:10px;line-height:18px;margin-right:6px">'
        + style.text + '</span>' + (name || '同业银行') + '</div>'
        + (p.orgName ? '<div style="color:#555;font-size:12px">' + p.orgName + '</div>' : '')
        + (p.orgAddress ? '<div style="color:#888;font-size:11px;margin-top:2px">' + p.orgAddress + '</div>' : '')
        + '</div>'
    },

    _showPeerBankLegend(bankNames) {
      this._hidePeerBankLegend()
      if (!bankNames || bankNames.length === 0) return
      const sorted = [...bankNames].sort()
      let itemsHtml = ''
      for (const name of sorted) {
        const style = this.getBankStyle(name)
        const dotColor = style.color || '#d40000'
        itemsHtml += '<div style="display:flex;align-items:center;gap:6px;padding:2px 0">'
          + '<span style="display:inline-flex;width:16px;height:16px;border-radius:3px;'
          + 'border:2px solid ' + dotColor + ';align-items:center;justify-content:center;'
          + 'font-size:8px;font-weight:700;color:' + dotColor + ';flex-shrink:0">'
          + style.text + '</span>'
          + '<span style="font-size:11px;color:#333;white-space:nowrap">' + name + '</span>'
          + '</div>'
      }
      const div = document.createElement('div')
      div.id = 'peer-bank-legend'
      div.style.cssText = 'background:rgba(255,255,255,0.92);border-radius:6px;padding:8px 10px;font-size:11px;box-shadow:0 1px 6px rgba(0,0,0,0.15);max-height:260px;overflow-y:auto'
      div.innerHTML = '<div style="font-weight:600;font-size:11px;color:#333;margin-bottom:4px;border-bottom:1px solid #eee;padding-bottom:4px">同业银行</div>'
        + itemsHtml
      const bottomRight = this.map._controlCorners && this.map._controlCorners.bottomright
      if (bottomRight) {
        bottomRight.appendChild(div)
        this.peerBankLegend = div
      }
    },

    _hidePeerBankLegend() {
      if (this.peerBankLegend) {
        const el = typeof this.peerBankLegend === 'object' && this.peerBankLegend.nodeType === 1
          ? this.peerBankLegend
          : document.getElementById('peer-bank-legend')
        if (el && el.parentNode) el.parentNode.removeChild(el)
        this.peerBankLegend = null
      }
    },

    onTogglePeerBank() {
      if (!this.peerBankLayer) return
      if (this.peerBankVisible) {
        this.map.removeLayer(this.peerBankLayer)
        this._hidePeerBankLegend()
      } else {
        this.map.addLayer(this.peerBankLayer)
        if (this.peerBankNames.length) this._showPeerBankLegend(this.peerBankNames)
      }
      this.peerBankVisible = !this.peerBankVisible
    },

    async loadPeerAndNearby(branchId) {
      try {
        const [peerRes, nearbyRes] = await Promise.all([
          getPeerBankDistance(branchId, 1),
          getNearbyBranches(branchId, 1)
        ])
        this.peerBanks = peerRes.data || []
        this.nearbyBranches = nearbyRes.data || []
      } catch (e) {
        this.peerBanks = []
        this.nearbyBranches = []
      }
    }
  }
}
