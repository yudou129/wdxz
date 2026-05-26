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

// ===== 计算 =====
export function computeGridData(city) {
  return request({ url: '/jwmap/compute/grid/' + city, method: 'post' })
}
export function computeBranchData(city, year) {
  return request({ url: '/jwmap/compute/branch/' + city + '/' + year, method: 'post' })
}
export function assignGridToBranch(city) {
  return request({ url: '/jwmap/compute/branch/assignGrid/' + city, method: 'post' })
}

// ===== 导出 =====
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
