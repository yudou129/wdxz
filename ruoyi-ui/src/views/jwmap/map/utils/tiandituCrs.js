/**
 * 天地图 CRS —— 支持 BD09 坐标输入，自动转为 WGS84 适配天地图瓦片
 *
 * 原理：天地图 vec_w/cva_w 使用 EPSG:3857 Web Mercator + WGS84 坐标系，
 * 但用户数据（网点/网格/边界）来自百度地图，使用 BD09 坐标系。
 * 此 CRS 拦截 project/unproject，在投影时自动做 BD09 ↔ WGS84 转换，
 * 上层 Leaflet 代码统一使用 BD09 坐标即可。
 */
import L from 'leaflet'
import { bd09ToWgs84, wgs84ToBd09 } from './coordConvert.js'

export const TiandituBd09Crs = L.extend({}, L.CRS.EPSG3857, {
  code: 'Tianditu:BD09',

  project(latlng) {
    // BD09 → WGS84 → Web Mercator 像素坐标
    const wgs = bd09ToWgs84(latlng.lng, latlng.lat)
    return L.Projection.SphericalMercator.project(new L.LatLng(wgs.lat, wgs.lng))
  },

  unproject(point) {
    // Web Mercator → WGS84 → BD09
    const wgs = L.Projection.SphericalMercator.unproject(point)
    const bd = wgs84ToBd09(wgs.lng, wgs.lat)
    return new L.LatLng(bd.lat, bd.lng)
  }
})
