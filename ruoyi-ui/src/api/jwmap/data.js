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

// ===== 数据导入 =====
export function importPoi(data) {
  return request({ url: '/jwmap/import/poi', method: 'post', data: data, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function importPopulationHeat(data) {
  return request({ url: '/jwmap/import/populationHeat', method: 'post', data: data, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function importExternalWeight(data) {
  return request({ url: '/jwmap/import/externalWeight', method: 'post', data: data, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function importBranchEfficiencyWeight(data) {
  return request({ url: '/jwmap/import/branchEfficiencyWeight', method: 'post', data: data, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function importBranchInfo(data) {
  return request({ url: '/jwmap/import/branchInfo', method: 'post', data: data, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function importExistingBranch(data) {
  return request({ url: '/jwmap/import/existingBranch', method: 'post', data: data, headers: { 'Content-Type': 'multipart/form-data' } })
}

// ===== 计算（超时5分钟，数据量大） =====
export function computeGridData(city) {
  return request({ url: '/jwmap/compute/grid/' + city, method: 'post', timeout: 300000 })
}
export function computeBranchData(city, year) {
  return request({ url: '/jwmap/compute/branch/' + city + '/' + year, method: 'post', timeout: 300000 })
}
export function assignGridToBranch(city) {
  return request({ url: '/jwmap/compute/branch/assignGrid/' + city, method: 'post', timeout: 300000 })
}
export function computeGridScore(city) {
  return request({ url: '/jwmap/compute/grid/score/' + city, method: 'post', timeout: 300000 })
}

// ===== 导出（组合式，一个文件多个Sheet） =====
export function exportGridCombined(city) {
  return request({ url: '/jwmap/export/grid/' + city, method: 'get', responseType: 'blob' })
}
export function exportBranchCombined(city, year) {
  return request({ url: '/jwmap/export/branch/' + city + '/' + year, method: 'get', responseType: 'blob' })
}

// ===== 导出（单sheet，向后兼容） =====
export function exportGridRaw(city) {
  return request({ url: '/jwmap/export/gridRaw/' + city, method: 'get', responseType: 'blob' })
}
export function exportGridNormalized(city) {
  return request({ url: '/jwmap/export/gridNormalized/' + city, method: 'get', responseType: 'blob' })
}
export function exportBranchBase(city) {
  return request({ url: '/jwmap/export/branchBase/' + city, method: 'get', responseType: 'blob' })
}
export function exportBranchCalc(city, year) {
  return request({ url: '/jwmap/export/branchCalc/' + city + '/' + year, method: 'get', responseType: 'blob' })
}
export function exportBranchNormalized(city, year) {
  return request({ url: '/jwmap/export/branchNormalized/' + city + '/' + year, method: 'get', responseType: 'blob' })
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
