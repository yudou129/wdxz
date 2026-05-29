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
export function importExternalWeight(data) {
  return request({ url: '/jwmap/import/externalWeight', method: 'post', data: data, timeout: 300000, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function importBranchEfficiencyWeight(data) {
  return request({ url: '/jwmap/import/branchEfficiencyWeight', method: 'post', data: data, timeout: 300000, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function importBranchInfo(data) {
  return request({ url: '/jwmap/import/branchInfo', method: 'post', data: data, timeout: 600000, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function importExistingBranch(data) {
  return request({ url: '/jwmap/import/existingBranch', method: 'post', data: data, timeout: 600000, headers: { 'Content-Type': 'multipart/form-data' } })
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
export function getIndicatorList(sourceTable) {
  return request({ url: '/jwmap/data/indicator/list', method: 'get', params: { sourceTable } })
}
export function getExternalWeightList() {
  return request({ url: '/jwmap/data/weight/external', method: 'get' })
}
export function getBranchEfficiencyWeightList() {
  return request({ url: '/jwmap/data/weight/branchEfficiency', method: 'get' })
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
