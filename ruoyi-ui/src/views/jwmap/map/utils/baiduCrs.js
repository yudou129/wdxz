/**
 * 百度地图瓦片 CRS —— 匹配 map.py 下载脚本的瓦片编号规则
 *
 * map.py 瓦片编号公式:
 *   tile_x = floor(lng * 20037508.34 / 180 / (256 * 2^(18-z)))
 *   tile_y = floor(log(tan((90+lat)*π/360)) / (π/180) * 20037508.34 / 180 / (256 * 2^(18-z)))
 *
 * 本质是 SphericalMercator 投影 + 以 2^18 为基准的瓦片网格。
 * 与标准 EPSG:3857 的区别：瓦片原点在本初子午线(0°)，而非180°W。
 *
 * 推导:
 *   Leaflet pixel = scale(z) * (projected * a + b)
 *   scale(z) = 256 * 2^z
 *   令 pixel / 256 = projected / (256 * 2^(18-z))
 *   => a = 1 / (256 * 2^18) = 1 / 67108864, b = 0
 */

export const BaiduCRS = Object.assign({}, L.CRS.Earth, {
  code: 'Baidu:3857',
  projection: L.Projection.SphericalMercator,

  transformation: new L.Transformation(
    1 / 67108864, 0,
    -1 / 67108864, 0
  ),

  // 禁止经度环绕 —— 负经度对应负瓦片坐标
  infinite: true,
  wrapLng: undefined,
  wrapLat: undefined
})
