/**
 * WGS84 ↔ GCJ02 ↔ BD09 坐标转换
 *
 * 数据源是 GCJ-02（高德/DataV），百度离线瓦片使用 BD09，因此：
 *   加载 GeoJSON → GCJ-02 → BD09 → 传入 Leaflet CRS
 *
 * 所有算法均为公开的标准实现。
 */

const PI = Math.PI
const X_PI = (PI * 3000.0) / 180.0
const A = 6378245.0          // 长半轴
const EE = 0.00669342162296594323 // 扁率

// ---- 内部工具 ----

function _isOutOfChina(lng, lat) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function _transformLng(lng, lat) {
  let ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng + 0.1 * lng * lat + 0.1 * Math.sqrt(Math.abs(lng))
  ret += ((20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(lng * PI) + 40.0 * Math.sin((lng / 3.0) * PI)) * 2.0) / 3.0
  ret += ((150.0 * Math.sin((lng / 12.0) * PI) + 300.0 * Math.sin((lng / 30.0) * PI)) * 2.0) / 3.0
  return ret
}

function _transformLat(lng, lat) {
  let ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat + 0.1 * lng * lat + 0.2 * Math.sqrt(Math.abs(lng))
  ret += ((20.0 * Math.sin(2.0 * lng * PI) + 20.0 * Math.sin(4.0 * lng * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(lat * PI) + 40.0 * Math.sin((lat / 3.0) * PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((lat / 12.0) * PI) + 320.0 * Math.sin((lat / 30.0) * PI)) * 2.0) / 3.0
  return ret
}

// ---- 公开 API ----

/** WGS84 → GCJ02 */
export function wgs84ToGcj02(lng, lat) {
  if (_isOutOfChina(lng, lat)) {
    return { lng, lat }
  }
  let dLng = _transformLng(lng - 105.0, lat - 35.0)
  let dLat = _transformLat(lng - 105.0, lat - 35.0)
  const radLat = (lat / 180.0) * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLng = (dLng * 180.0) / ((A / sqrtMagic) * Math.cos(radLat) * PI)
  dLat = (dLat * 180.0) / (((A * (1 - EE)) / (magic * sqrtMagic)) * PI)
  return { lng: lng + dLng, lat: lat + dLat }
}

/** GCJ02 → BD09 */
export function gcj02ToBd09(lng, lat) {
  const z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * X_PI)
  const theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * X_PI)
  return {
    lng: z * Math.cos(theta) + 0.0065,
    lat: z * Math.sin(theta) + 0.006
  }
}

/** BD09 → GCJ02 */
export function bd09ToGcj02(lng, lat) {
  const x = lng - 0.0065
  const y = lat - 0.006
  const z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI)
  const theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI)
  return {
    lng: z * Math.cos(theta),
    lat: z * Math.sin(theta)
  }
}

/** WGS84 → BD09（一步到位） */
export function wgs84ToBd09(lng, lat) {
  const gcj = wgs84ToGcj02(lng, lat)
  return gcj02ToBd09(gcj.lng, gcj.lat)
}

/** GCJ-02 → BD09 单点（别名，语义化） */
export { gcj02ToBd09 as fromGcj02 }

// ---- 批量转换（GCJ-02 → BD09，原地修改） ----

function _convertRing(ring) {
  for (let i = 0; i < ring.length; i++) {
    const [lng, lat] = ring[i]
    const bd = gcj02ToBd09(lng, lat)
    ring[i] = [bd.lng, bd.lat]
  }
}

/** 转换单个 Feature 的全部坐标 GCJ-02 → BD09（原地修改） */
export function featureGcj02ToBd09(feature) {
  const geom = feature.geometry
  if (geom.type === 'Polygon') {
    geom.coordinates.forEach(_convertRing)
  } else if (geom.type === 'MultiPolygon') {
    geom.coordinates.forEach(poly => poly.forEach(_convertRing))
  }
  // 转换 center（用于跳转）
  if (feature.properties && feature.properties.center) {
    const [lng, lat] = feature.properties.center
    const bd = gcj02ToBd09(lng, lat)
    feature.properties.center = [bd.lng, bd.lat]
  }
  return feature
}

/** 转换整个 FeatureCollection 的坐标 GCJ-02 → BD09（原地修改） */
export function collectionGcj02ToBd09(geojson) {
  geojson.features.forEach(featureGcj02ToBd09)
  return geojson
}
