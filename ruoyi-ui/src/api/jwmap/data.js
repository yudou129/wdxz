import request from '@/utils/request'

// ===== 城市状态 =====
export function getAllCityStatus() {
  return request({ url: '/jwmap/compute/cityStatus', method: 'get' })
}
export function getCityStatus(city) {
  return request({ url: '/jwmap/compute/cityStatus/' + city, method: 'get' })
}
export function getBranchStatus(city) {
  return request({ url: '/jwmap/compute/branchStatus/' + city, method: 'get' })
}

// ===== 数据导入（超时10分钟，大数据量） =====
export function importPoi(data) {
  return request({ url: '/jwmap/import/poi', method: 'post', data: data, timeout: 600000, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function importPopulationHeat(data) {
  return request({ url: '/jwmap/import/populationHeat', method: 'post', data: data, timeout: 600000, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function importBranchInfo(data) {
  return request({ url: '/jwmap/import/branchInfo', method: 'post', data: data, timeout: 600000, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function importExistingBranch(data) {
  return request({ url: '/jwmap/import/existingBranch', method: 'post', data: data, timeout: 600000, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function importPeerBank(data) {
  return request({ url: '/jwmap/import/peerBank', method: 'post', data: data, timeout: 600000, headers: { 'Content-Type': 'multipart/form-data' } })
}

// ===== 计算（超时10分钟，数据量大） =====
export function computeGridData(city) {
  return request({ url: '/jwmap/compute/grid/' + city, method: 'post', timeout: 600000 })
}
export function computeBranchData(city, year) {
  return request({ url: '/jwmap/compute/branch/' + city + '/' + year, method: 'post', timeout: 600000 })
}
export function assignGridToBranch(city) {
  return request({ url: '/jwmap/compute/branch/assignGrid/' + city, method: 'post', timeout: 600000 })
}
export function computeGridScore(city) {
  return request({ url: '/jwmap/compute/grid/score/' + city, method: 'post', timeout: 600000 })
}

// ===== 导出（超时10分钟，大文件下载） =====
export function exportGridCombined(city) {
  return request({ url: '/jwmap/export/grid/' + city, method: 'get', responseType: 'blob', timeout: 600000 })
}
export function exportBranchCombined(city, year) {
  return request({ url: '/jwmap/export/branch/' + city + '/' + year, method: 'get', responseType: 'blob', timeout: 600000 })
}

// ===== 导出（单sheet，向后兼容） =====
export function exportGridRaw(city) {
  return request({ url: '/jwmap/export/gridRaw/' + city, method: 'get', responseType: 'blob', timeout: 600000 })
}
export function exportGridNormalized(city) {
  return request({ url: '/jwmap/export/gridNormalized/' + city, method: 'get', responseType: 'blob', timeout: 600000 })
}
export function exportBranchBase(city) {
  return request({ url: '/jwmap/export/branchBase/' + city, method: 'get', responseType: 'blob', timeout: 600000 })
}
export function exportBranchCalc(city, year) {
  return request({ url: '/jwmap/export/branchCalc/' + city + '/' + year, method: 'get', responseType: 'blob', timeout: 600000 })
}
export function exportBranchNormalized(city, year) {
  return request({ url: '/jwmap/export/branchNormalized/' + city + '/' + year, method: 'get', responseType: 'blob', timeout: 600000 })
}

// ===== 数据查看 =====
export function getPoiList(city) {
  return request({ url: '/jwmap/data/poi/list', method: 'get', params: { city } })
}
export function getGridList(city) {
  return request({ url: '/jwmap/data/grid/list', method: 'get', params: { city } })
}
export function getGridCities() {
  return request({ url: '/jwmap/data/grid/cities', method: 'get' })
}
export function getBranchList(city) {
  return request({ url: '/jwmap/data/branch/list', method: 'get', params: { city } })
}
export function getBranchScore(city, year) {
  return request({ url: '/jwmap/data/branch/score/' + city + '/' + year, method: 'get' })
}
export function getIndicatorList(params) {
  return request({ url: '/jwmap/data/indicator/list', method: 'get', params })
}
export function getPeerBankList(city) {
  return request({ url: '/jwmap/data/peerBank/list', method: 'get', params: { city } })
}

// ===== 地图可视化 =====
export function getGridScoreByCity(city) {
  return request({ url: '/jwmap/data/grid/score/byCity/' + city, method: 'get' })
}
export function getGridIndicators(gridCode) {
  return request({ url: '/jwmap/data/grid/indicators/' + gridCode, method: 'get' })
}
export function getBranchScoreDetail(branchId, year) {
  return request({ url: '/jwmap/data/branch/score/detail/' + branchId + '/' + year, method: 'get' })
}
export function getGridRanking(city, pageNum, pageSize) {
  return request({ url: '/jwmap/data/grid/ranking/' + city, method: 'get', params: { pageNum, pageSize } })
}
export function getBranchRanking(city, year, pageNum, pageSize) {
  return request({ url: '/jwmap/data/branch/ranking/' + city + '/' + year, method: 'get', params: { pageNum, pageSize } })
}
export function getGridBranches(gridCode) {
  return request({ url: '/jwmap/data/grid/branches/' + gridCode, method: 'get' })
}
export function getBranchIndicators(branchId, year) {
  return request({ url: '/jwmap/data/branch/indicators/' + branchId + '/' + year, method: 'get' })
}
export function getQuadrantData(city, year) {
  return request({ url: '/jwmap/data/quadrant/' + city + '/' + year, method: 'get' })
}
export function getBranchInternalRanking(branchId, year) {
  return request({ url: '/jwmap/data/branch/ranking/internal/' + branchId + '/' + year, method: 'get' })
}
export function getGridDistrictRanking(gridCode) {
  return request({ url: '/jwmap/data/grid/ranking/district/' + gridCode, method: 'get' })
}
export function getGridPillarScores(gridCode) {
  return request({ url: '/jwmap/data/grid/pillar/' + gridCode, method: 'get' })
}
export function getGridTopScore(city) {
  return request({ url: '/jwmap/data/grid/topScore/' + city, method: 'get' })
}
export function getDimensionStats(city, year, dimension) {
  return request({ url: '/jwmap/data/dimension/stats/' + city + '/' + year, method: 'get', params: { dimension } })
}
export function getThreeFocusRanking(city, year) {
  return request({ url: '/jwmap/data/ranking/threeFocus/' + city + '/' + year, method: 'get' })
}
export function getPeerBankDistance(branchId, radius) {
  return request({ url: '/jwmap/data/peerBank/distance/' + branchId, method: 'get', params: { radius: radius || 1 } })
}
export function getNearbyBranches(branchId, radius) {
  return request({ url: '/jwmap/data/branch/nearby/' + branchId, method: 'get', params: { radius: radius || 1 } })
}
export function getPillarGap(gridCode) {
  return request({ url: '/jwmap/data/grid/pillar/gap/' + gridCode, method: 'get' })
}

// ===== 指标配置管理 =====
export function getIndicatorTree(type) {
  return request({ url: '/jwmap/config/indicators/tree', method: 'get', params: { indicatorType: type } })
}
export function generateIndicatorCode(name, type) {
  return request({ url: '/jwmap/config/indicators/code/generate', method: 'get', params: { name, indicatorType: type } })
}
export function saveIndicator(data) {
  return request({ url: '/jwmap/config/indicators', method: 'post', data: data })
}
export function updateIndicator(id, data) {
  return request({ url: '/jwmap/config/indicators/' + id, method: 'put', data: data })
}
export function deleteIndicator(id) {
  return request({ url: '/jwmap/config/indicators/' + id, method: 'delete' })
}
export function batchDeleteIndicators(codes) {
  return request({ url: '/jwmap/config/indicators/batchDelete', method: 'post', data: { codes } })
}
