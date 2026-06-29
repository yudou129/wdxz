<template>
  <div class="jwmap-container">
    <!-- 顶部标题 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <div class="page-header">
          <h2>网点布局优化 — 数据管理</h2>
          <p class="subtitle">贵州省 · 地图网格热力图数据预处理</p>
        </div>
      </el-col>
    </el-row>

    <!-- 城市数据就绪状态 -->
    <el-card class="section-card" shadow="hover">
      <div slot="header"><i class="el-icon-data-analysis" /> 城市数据就绪状态</div>
      <el-row :gutter="12">
        <el-col :span="6" v-for="item in cityStatusList" :key="item.city">
          <el-card :class="['status-card', item.ready ? 'ready' : 'incomplete']" shadow="never">
            <h4>{{ item.city }}</h4>
            <div>POI: <el-tag :type="item.hasPoi ? 'success' : 'danger'" size="small">{{ item.hasPoi ? '已导入' : '缺' }}</el-tag></div>
            <div>人口热力: <el-tag :type="item.hasPopulation ? 'success' : 'danger'" size="small">{{ item.hasPopulation ? '已导入' : '缺' }}</el-tag></div>
            <div>权重: <el-tag :type="item.hasWeight ? 'success' : 'danger'" size="small">{{ item.hasWeight ? '已配置' : '缺' }}</el-tag></div>
            <div>网格数: <b>{{ item.gridCount || 0 }}</b> | 得分: <b>{{ item.hasScore ? '已计算' : '未计算' }}</b></div>
            <div class="status-badge">
              <el-tag :type="item.ready ? 'success' : 'warning'">{{ item.ready ? '就绪' : '待完善' }}</el-tag>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6" v-if="cityStatusList.length === 0">
          <el-empty description="暂无数据，请先导入" />
        </el-col>
      </el-row>
      <el-button type="primary" size="small" @click="refreshCityStatus" style="margin-top:12px">刷新状态</el-button>
    </el-card>

    <!-- Tab 区域 -->
    <el-card class="section-card" shadow="hover">
      <el-tabs v-model="activeTab" class="dm-tabs">
        <!-- ========== 数据导入 Tab ========== -->
        <el-tab-pane label="数据导入" name="import">
          <el-collapse v-model="importCollapse">
            <!-- POI导入 -->
            <el-collapse-item title="POI信息导入" name="poi">
              <ImportPanel label="POI信息（不同地市）" :city.sync="importCity" :cityList="cityList" :loading="importingPoi" @import="handleImport('poi', $event)">
                <template #tips>Excel列：所属机构编码, POI名称, 经度, 维度, 省, 市, 区县, 地址, POI类型</template>
              </ImportPanel>
            </el-collapse-item>

            <!-- 人口热力导入 -->
            <el-collapse-item title="人口热力导入" name="pop">
              <ImportPanel label="人口热力（不同地市）" :city.sync="importCity" :cityList="cityList" :loading="importingPop" @import="handleImport('pop', $event)">
                <template #tips>Excel包含75列人口统计数据。系统自动识别指标列并映射到指标编码。新指标自动注册。</template>
              </ImportPanel>
            </el-collapse-item>

            <!-- 网点信息导入 -->
            <el-collapse-item title="网点信息导入" name="branch">
              <ImportPanel label="网点信息表（基础数据Sheet）" :city.sync="importCity" :cityList="cityList" :showDataSource="true" :dataSource.sync="importDataSource" :loading="importingBranch" @import="handleImport('branch', $event)">
                <template #tips>导入基础数据Sheet。系统会自动解析每年份的业务指标列，存入垂直指标表。</template>
              </ImportPanel>
            </el-collapse-item>

            <!-- 同业银行导入 -->
            <el-collapse-item title="同业银行导入" name="peerBank">
              <ImportPanel label="同业银行数据" :city.sync="importCity" :cityList="cityList" :loading="importingPeerBank" @import="handleImport('peerBank', $event)">
                <template #tips>Excel列：机构编码, 机构名称, 机构地址, 经度, 纬度, 银行名称, 省, 市, 区县, 乡镇。自动跳过"其他银行"和"工商银行"数据，并根据经纬度自动计算所属网格。</template>
              </ImportPanel>
            </el-collapse-item>
          </el-collapse>
        </el-tab-pane>

        <!-- ========== 数据计算 Tab ========== -->
        <el-tab-pane label="数据计算" name="compute">
          <el-row :gutter="20">
            <!-- 网格计算 -->
            <el-col :span="12">
              <el-card shadow="never">
                <div slot="header"><i class="el-icon-s-grid" /> 网格数据计算</div>
                <p>将POI信息和人口热力数据按1KM×1KM网格聚合，计算四至坐标、POI数量、TOPSIS选址得分。</p>
                <el-select v-model="computeGridCity" placeholder="选择城市" class="dm-select">
                  <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
                </el-select>
                <el-button type="primary" @click="handleGridCompute" :loading="gridComputing">{{ gridComputing ? '计算中...' : '开始网格计算' }}</el-button>
              </el-card>
            </el-col>

            <!-- 网点计算 -->
            <el-col :span="12">
              <el-card shadow="never">
                <div slot="header"><i class="el-icon-office-building" /> 网点效能计算</div>
                <p>计算22个衍生指标（人均/单位面积/户均），执行归一化和五类TOPSIS评分（营收/指标/客户/运营/总分）。</p>
                <el-select v-model="computeBranchCity" placeholder="选择城市" class="dm-select">
                  <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
                </el-select>
                <el-select v-model="computeBranchYear" placeholder="选择年份" class="dm-select dm-select-sm">
                  <el-option label="2023" :value="2023" />
                  <el-option label="2024" :value="2024" />
                  <el-option label="2025" :value="2025" />
                </el-select>
                <el-button type="primary" @click="handleBranchCompute" :loading="branchComputing">{{ branchComputing ? '计算中...' : '开始网点计算' }}</el-button>
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- ========== 数据导出 Tab ========== -->
        <el-tab-pane label="数据导出" name="export">
          <el-row :gutter="16">
            <!-- 网格导出 -->
            <el-col :span="12">
              <el-card shadow="never">
                <div slot="header">网格数据导出</div>
                <el-select v-model="exportCity" placeholder="选择城市" class="dm-select">
                  <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
                </el-select>
                <div class="step-gap"></div>
                <el-button type="success" @click="handleExport('grid')" icon="el-icon-download">网格导出（原始数据+归一化得分）</el-button>
              </el-card>
            </el-col>

            <!-- 网点导出 -->
            <el-col :span="12">
              <el-card shadow="never">
                <div slot="header">网点数据导出</div>
                <el-select v-model="exportCity" placeholder="选择城市" class="dm-select">
                  <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
                </el-select>
                <el-select v-model="exportYear" placeholder="选择年份" class="dm-select dm-select-sm">
                  <el-option label="2023" :value="2023" /><el-option label="2024" :value="2024" /><el-option label="2025" :value="2025" />
                </el-select>
                <div class="step-gap"></div>
                <el-button type="success" @click="handleExport('branch')" icon="el-icon-download">网点导出（基础数据+数据计算表+归一化）</el-button>
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- ========== 数据查看 Tab ========== -->
        <el-tab-pane label="数据查看" name="view">
          <el-tabs v-model="viewTab" type="card">
            <el-tab-pane label="网格数据" name="gridView">
              <el-select v-model="viewCity" placeholder="选择城市" class="dm-select" style="margin-bottom:12px">
                <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
              </el-select>
              <div style="height:calc(100vh - 380px);min-height:400px">
                <GridDetailView :city="viewCity" />
              </div>
            </el-tab-pane>
            <el-tab-pane label="网点数据" name="branchView">
              <div style="margin-bottom:12px">
                <el-select v-model="viewCity" placeholder="选择城市" class="dm-select">
                  <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
                </el-select>
                <el-select v-model="branchViewYear" placeholder="选择年份" class="dm-select dm-select-sm">
                  <el-option label="2023" :value="2023" />
                  <el-option label="2024" :value="2024" />
                  <el-option label="2025" :value="2025" />
                </el-select>
              </div>
              <div style="height:calc(100vh - 380px);min-height:400px">
                <BranchDetailView :city="viewCity" :year="branchViewYear" />
              </div>
            </el-tab-pane>
            <el-tab-pane label="同业银行" name="peerBankView">
              <el-select v-model="viewCity" placeholder="选择城市" @change="loadPeerBankList" class="dm-select" style="margin-bottom:12px">
                <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
              </el-select>
              <el-table :data="peerBankList" class="dm-table" v-loading="peerBankLoading">
                <el-table-column prop="orgCode" label="机构编码" width="160" />
                <el-table-column prop="orgName" label="机构名称" min-width="200" show-overflow-tooltip />
                <el-table-column prop="bankName" label="银行名称" width="100" />
                <el-table-column prop="longitude" label="经度" width="100" />
                <el-table-column prop="latitude" label="纬度" width="100" />
                <el-table-column prop="district" label="区县" width="80" />
                <el-table-column prop="town" label="乡镇" width="100" />
                <el-table-column prop="gridCode" label="所属网格" width="120" />
                <el-table-column prop="orgAddress" label="机构地址" min-width="200" show-overflow-tooltip />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>

        <!-- ========== 指标配置 Tab ========== -->
        <el-tab-pane label="指标配置" name="config">
          <IndicatorConfig />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script>
// 贵州省工行二级分行对应地市列表
const GUIZHOU_CITIES = [
  '贵阳市', '遵义市', '六盘水市', '安顺市', '毕节市', '铜仁市',
  '兴义', '凯里', '都匀'
]

import {
  getAllCityStatus, computeGridData, computeBranchData,
} from '@/api/jwmap/data'
import { importPoi, importPopulationHeat,
  importBranchInfo, importPeerBank } from '@/api/jwmap/data'
import { exportGridCombined, exportBranchCombined } from '@/api/jwmap/data'
import { getPeerBankList } from '@/api/jwmap/data'
import ImportPanel from './components/ImportPanel'
import IndicatorConfig from './config/indicator'
import GridDetailView from './components/GridDetailView'
import BranchDetailView from './components/BranchDetailView'

export default {
  name: 'JwmapIndex',
  components: { ImportPanel, IndicatorConfig, GridDetailView, BranchDetailView },
  data() {
    return {
      activeTab: 'import',
      viewTab: 'gridView',
      importCollapse: ['poi'],
      // 城市状态
      cityStatusList: [],
      cityList: [...GUIZHOU_CITIES],
      // 导入
      importCity: '',
      importDataSource: '网点信息',
      importingPoi: false,
      importingPop: false,
      importingBranch: false,
      importingPeerBank: false,
      // 计算
      computeGridCity: '',
      computeBranchCity: '',
      computeBranchYear: 2024,
      gridComputing: false,
      branchComputing: false,
      // 导出
      exportCity: '',
      exportYear: 2024,
      // 查看
      viewCity: '',
      branchViewYear: 2024,
      branchList: [], branchLoading: false,
      peerBankList: [], peerBankLoading: false
    }
  },
  created() {
    this.refreshCityStatus()
  },
  methods: {
    async refreshCityStatus() {
      try {
        const res = await getAllCityStatus()
        this.cityStatusList = res.data || []
        // 合并静态列表与API返回的城市，保留静态列表且去重
        const apiCities = this.cityStatusList.map(s => s.city)
        const seen = new Set(GUIZHOU_CITIES)
        this.cityList = [...GUIZHOU_CITIES]
        apiCities.forEach(c => { if (!seen.has(c)) { seen.add(c); this.cityList.push(c) } })
      } catch (e) { /* ignore */ }
    },
    async handleImport(type, eventData) {
      const loadingKey = 'importing' + type.charAt(0).toUpperCase() + type.slice(1)
      this[loadingKey] = true
      try {
        const file = eventData?.file
        const city = eventData?.city
        const dataSource = eventData?.dataSource
        if (!file) return
        let result
        const fd = new FormData()
        fd.append('file', file)
        if (city) fd.append('city', city)
        if (dataSource) fd.append('dataSource', dataSource)

        const apiMap = {
          poi: importPoi, pop: importPopulationHeat,
          branch: importBranchInfo, peerBank: importPeerBank
        }
        result = await apiMap[type](fd)
        this.$message.success(result.msg || '导入成功')
        this.refreshCityStatus()
      } catch (e) {
        this.$message.error('导入失败：' + (e.message || '未知错误'))
      } finally {
        this[loadingKey] = false
      }
    },
    async handleGridCompute() {
      if (!this.computeGridCity) { this.$message.warning('请选择城市'); return }
      this.gridComputing = true
      try {
        const res = await computeGridData(this.computeGridCity)
        this.$message.success(res.msg || '计算完成')
        this.refreshCityStatus()
      } catch (e) { this.$message.error('计算失败：' + (e.message || '未知错误')) }
      finally { this.gridComputing = false }
    },
    async handleBranchCompute() {
      if (!this.computeBranchCity) { this.$message.warning('请选择城市'); return }
      this.branchComputing = true
      try {
        const res = await computeBranchData(this.computeBranchCity, this.computeBranchYear)
        this.$message.success(res.msg || '计算完成')
      } catch (e) { this.$message.error('计算失败：' + (e.message || '未知错误')) }
      finally { this.branchComputing = false }
    },
    async handleExport(type) {
      if (!this.exportCity) { this.$message.warning('请选择城市'); return }
      try {
        let blob
        let fileName
        if (type === 'grid') {
          blob = await exportGridCombined(this.exportCity)
          fileName = '网格数据_' + this.exportCity + '.xlsx'
        } else if (type === 'branch') {
          blob = await exportBranchCombined(this.exportCity, this.exportYear)
          fileName = '网点数据_' + this.exportCity + '_' + this.exportYear + '.xlsx'
        } else {
          this.$message.error('未知导出类型')
          return
        }
        // 检查是否为后端错误JSON（xlsx文件以PK开头，JSON以{开头）
        const header = await blob.slice(0, 1).text()
        if (header === '{') {
          const errText = await blob.text()
          try {
            const err = JSON.parse(errText)
            this.$message.error(err.msg || '导出失败')
          } catch (e) {
            this.$message.error('导出失败')
          }
          return
        }
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = fileName
        a.click()
        window.URL.revokeObjectURL(url)
        this.$message.success('导出成功')
      } catch (e) { this.$message.error('导出失败：' + (e.message || '未知错误')) }
    },
    async loadBranchList() {
      if (!this.viewCity) return
      this.branchLoading = true
      try { const res = await getBranchList(this.viewCity); this.branchList = res.data || [] }
      catch (e) { this.branchList = [] } finally { this.branchLoading = false }
    },
    async loadPeerBankList() {
      if (!this.viewCity) return
      this.peerBankLoading = true
      try { const res = await getPeerBankList(this.viewCity); this.peerBankList = res.data || [] }
      catch (e) { this.peerBankList = [] } finally { this.peerBankLoading = false }
    },
  }
}
</script>

<style scoped>
.jwmap-container {
  padding: 20px 24px;
  max-width: 1280px;
  margin: 0 auto;
}
.page-header { margin-bottom: 16px; }
.page-header h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #232845;
  letter-spacing: -0.3px;
}
.subtitle {
  color: #888;
  font-size: 14px;
  margin: 4px 0 0 0;
}
/* 卡片 */
.section-card {
  margin-bottom: 18px;
  border-radius: 12px;
  border: 1px solid rgba(79, 110, 246, 0.06);
  box-shadow: 0 2px 12px rgba(79, 110, 246, 0.04);
  transition: box-shadow 0.25s ease;
}
.section-card:hover {
  box-shadow: 0 4px 20px rgba(79, 110, 246, 0.08);
}
/* Tab 容器 */
.dm-tabs >>> .el-tabs__header {
  margin: 0;
  border-bottom: 1px solid rgba(79,110,246,0.06);
}
.dm-tabs >>> .el-tabs__nav-wrap { padding-left: 8px; }
.dm-tabs >>> .el-tabs__item {
  font-size: 14px;
  font-weight: 600;
  color: #666;
  padding: 0 18px;
  height: 44px;
  line-height: 44px;
}
.dm-tabs >>> .el-tabs__item.is-active {
  color: #4f6ef6;
}
.dm-tabs >>> .el-tabs__active-bar {
  background: #4f6ef6;
  height: 3px;
  border-radius: 2px 2px 0 0;
}
.dm-tabs >>> .el-tabs__content { padding: 20px 0; }
/* 城市状态卡 */
.status-card {
  text-align: center;
  font-size: 14px;
  border-radius: 8px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  padding: 4px 0;
}
.status-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 14px rgba(79, 110, 246, 0.1);
}
.status-card.ready {
  border-left: 3px solid #4f6ef6;
  background: linear-gradient(135deg, #f8faff 0%, #f0f4ff 100%);
}
.status-card.incomplete {
  border-left: 3px solid #f0a050;
  background: linear-gradient(135deg, #fffaf5 0%, #fff7ed 100%);
}
.status-badge { margin-top: 10px; }
/* 选择器统一 */
.dm-select { width: 160px; }
.dm-select-sm { width: 110px; }
/* 表格 */
.dm-table { width: 100%; }
.dm-table >>> .el-table__header th {
  background: #f8f9fd; color: #444; font-weight: 600; font-size: 14px;
}
.dm-table >>> .el-table__body td { font-size: 14px; padding: 10px 0; }
/* 计算 Tab */
.step-divider {
  margin: 14px 0 10px; font-size: 13px; color: #888;
  position: relative; padding-left: 10px; border-left: 2px solid #4f6ef6;
}
.step-gap { margin-top: 10px; }
</style>
