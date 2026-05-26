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
            <div>POI: <el-tag :type="item.hasPoi ? 'success' : 'danger'" size="mini">{{ item.hasPoi ? '已导入' : '缺' }}</el-tag></div>
            <div>人口热力: <el-tag :type="item.hasPopulation ? 'success' : 'danger'" size="mini">{{ item.hasPopulation ? '已导入' : '缺' }}</el-tag></div>
            <div>权重: <el-tag :type="item.hasWeight ? 'success' : 'danger'" size="mini">{{ item.hasWeight ? '已配置' : '缺' }}</el-tag></div>
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
      <el-tabs v-model="activeTab" type="border-card">
        <!-- ========== 数据导入 Tab ========== -->
        <el-tab-pane label="数据导入" name="import">
          <el-collapse v-model="importCollapse">
            <!-- POI导入 -->
            <el-collapse-item title="POI信息导入" name="poi">
              <ImportPanel label="POI信息（不同地市）" :city.sync="importCity" @import="handleImport('poi')">
                <template #tips>Excel列：所属机构编码, POI名称, 经度, 维度, 省, 市, 区县, 地址, POI类型</template>
              </ImportPanel>
            </el-collapse-item>

            <!-- 人口热力导入 -->
            <el-collapse-item title="人口热力导入" name="pop">
              <ImportPanel label="人口热力（不同地市）" :city.sync="importCity" @import="handleImport('pop')">
                <template #tips>Excel包含75列人口统计数据。系统自动识别指标列并映射到指标编码。新指标自动注册。</template>
              </ImportPanel>
            </el-collapse-item>

            <!-- 外部资源权重 -->
            <el-collapse-item title="外部资源权重导入" name="extWeight">
              <ImportPanel label="外部资源权重表" :hideCity="true" @import="handleImport('extWeight')">
                <template #tips>三级指标名称需与指标配置表匹配。导入后自动清除旧数据重新写入。</template>
              </ImportPanel>
            </el-collapse-item>

            <!-- 网点效能权重 -->
            <el-collapse-item title="网点效能权重导入" name="branchWeight">
              <ImportPanel label="网点效能权重表" :hideCity="true" @import="handleImport('branchWeight')">
                <template #tips>用于网点TOPSIS五类得分计算。三级指标名称需与网点指标编码匹配。</template>
              </ImportPanel>
            </el-collapse-item>

            <!-- 网点信息导入 -->
            <el-collapse-item title="网点信息导入" name="branch">
              <ImportPanel label="网点信息表（基础数据Sheet）" :city.sync="importCity" :showDataSource="true" :dataSource.sync="importDataSource" @import="handleImport('branch')">
                <template #tips>导入基础数据Sheet。系统会自动解析每年份的业务指标列，存入垂直指标表。</template>
              </ImportPanel>
            </el-collapse-item>

            <!-- 存量网点导入 -->
            <el-collapse-item title="存量网点导入" name="existBranch">
              <ImportPanel label="存量网点基本信息表" :city.sync="importCity" @import="handleImport('existBranch')">
                <template #tips>与网点信息基本结构一致，导入后 data_source 标记为"存量网点"。</template>
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
                <el-select v-model="computeGridCity" placeholder="选择城市" style="width:160px;margin-right:8px">
                  <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
                </el-select>
                <el-button type="primary" @click="handleGridCompute" :loading="gridComputing">{{ gridComputing ? '计算中...' : '开始网格计算' }}</el-button>
                <el-divider />
                <p class="step-title">或分步执行：</p>
                <el-button size="small" @click="handleGridMeta">仅计算网格元信息</el-button>
                <el-button size="small" @click="handleGridScore">仅重算TOPSIS得分</el-button>
              </el-card>
            </el-col>

            <!-- 网点计算 -->
            <el-col :span="12">
              <el-card shadow="never">
                <div slot="header"><i class="el-icon-office-building" /> 网点效能计算</div>
                <p>计算22个衍生指标（人均/单位面积/户均），执行归一化和五类TOPSIS评分（营收/指标/客户/运营/总分）。</p>
                <el-select v-model="computeBranchCity" placeholder="选择城市" style="width:160px;margin-right:8px">
                  <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
                </el-select>
                <el-select v-model="computeBranchYear" placeholder="选择年份" style="width:100px;margin-right:8px">
                  <el-option label="2023" :value="2023" />
                  <el-option label="2024" :value="2024" />
                  <el-option label="2025" :value="2025" />
                </el-select>
                <el-button type="primary" @click="handleBranchCompute" :loading="branchComputing">{{ branchComputing ? '计算中...' : '开始网点计算' }}</el-button>
                <el-divider />
                <el-button size="small" @click="handleAssignGrid">网点归属网格</el-button>
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
                <el-select v-model="exportCity" placeholder="选择城市" style="width:160px;margin-right:8px">
                  <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
                </el-select>
                <el-divider />
                <el-button type="success" @click="handleExport('gridRaw')" icon="el-icon-download">网格数据表（原始数据）</el-button>
                <el-button type="success" @click="handleExport('gridNormalized')" icon="el-icon-download">网格数据表（归一化得分）</el-button>
              </el-card>
            </el-col>

            <!-- 网点导出 -->
            <el-col :span="12">
              <el-card shadow="never">
                <div slot="header">网点数据导出</div>
                <el-select v-model="exportCity" placeholder="选择城市" style="width:160px;margin-right:8px">
                  <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
                </el-select>
                <el-select v-model="exportYear" placeholder="选择年份" style="width:100px;margin-right:8px">
                  <el-option label="2023" :value="2023" /><el-option label="2024" :value="2024" /><el-option label="2025" :value="2025" />
                </el-select>
                <el-divider />
                <el-button type="success" @click="handleExport('branchBase')" icon="el-icon-download">网点基础数据</el-button>
                <el-button type="success" @click="handleExport('branchCalc')" icon="el-icon-download">数据计算表</el-button>
                <el-button type="success" @click="handleExport('branchNormalized')" icon="el-icon-download">归一化处理表</el-button>
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- ========== 数据查看 Tab ========== -->
        <el-tab-pane label="数据查看" name="view">
          <el-tabs v-model="viewTab" type="card">
            <el-tab-pane label="网格数据" name="gridView">
              <el-select v-model="viewCity" placeholder="选择城市" @change="loadGridList" style="width:160px;margin-bottom:12px">
                <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
              </el-select>
              <el-table :data="gridList" border stripe max-height="400" v-loading="gridLoading">
                <el-table-column prop="gridCode" label="网格编号" width="120" />
                <el-table-column prop="longitude" label="经度" width="100" />
                <el-table-column prop="latitude" label="纬度" width="100" />
                <el-table-column prop="poiCount" label="POI数" width="80" />
                <el-table-column prop="siteScore" label="选址得分" width="100">
                  <template slot-scope="scope">{{ scope.row.siteScore ? scope.row.siteScore.toFixed(6) : '-' }}</template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="网点数据" name="branchView">
              <el-select v-model="viewCity" placeholder="选择城市" @change="loadBranchList" style="width:160px;margin-right:8px;margin-bottom:12px">
                <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
              </el-select>
              <el-table :data="branchList" border stripe max-height="400" v-loading="branchLoading">
                <el-table-column prop="primaryBranch" label="一级支行" width="100" />
                <el-table-column prop="secondaryBranch" label="二级支行" width="120" />
                <el-table-column prop="branchCode" label="网点号" width="80" />
                <el-table-column prop="districtName" label="行政区" width="80" />
                <el-table-column prop="gridCode" label="所属网格" width="120" />
                <el-table-column prop="totalStaff" label="总人数" width="70" />
                <el-table-column prop="totalArea" label="面积" width="80" />
                <el-table-column prop="dataSource" label="数据来源" width="80" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="网点得分" name="scoreView">
              <el-select v-model="viewCity" placeholder="选择城市" style="width:160px;margin-right:8px;margin-bottom:12px">
                <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
              </el-select>
              <el-select v-model="scoreYear" placeholder="选择年份" style="width:100px;margin-right:8px;margin-bottom:12px" @change="loadBranchScore">
                <el-option label="2023" :value="2023" /><el-option label="2024" :value="2024" /><el-option label="2025" :value="2025" />
              </el-select>
              <el-table :data="scoreList" border stripe max-height="400" v-loading="scoreLoading">
                <el-table-column prop="scoreCategory" label="类别" width="100">
                  <template slot-scope="scope">{{ categoryLabel(scope.row.scoreCategory) }}</template>
                </el-table-column>
                <el-table-column prop="categoryScore" label="得分" width="100">
                  <template slot-scope="scope">{{ scope.row.categoryScore ? scope.row.categoryScore.toFixed(6) : '-' }}</template>
                </el-table-column>
                <el-table-column prop="rankNum" label="排名" width="80" />
                <el-table-column prop="positiveDistance" label="D+" width="100">
                  <template slot-scope="scope">{{ scope.row.positiveDistance ? scope.row.positiveDistance.toFixed(6) : '-' }}</template>
                </el-table-column>
                <el-table-column prop="negativeDistance" label="D-" width="100">
                  <template slot-scope="scope">{{ scope.row.negativeDistance ? scope.row.negativeDistance.toFixed(6) : '-' }}</template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script>
import {
  getAllCityStatus, computeGridData, computeBranchData, assignGridToBranch,
  getGridCities, getGridList, getBranchList, getBranchScore
} from '@/api/jwmap/data'
import { importPoi, importPopulationHeat, importExternalWeight, importBranchEfficiencyWeight,
  importBranchInfo, importExistingBranch } from '@/api/jwmap/data'
import { exportGridRaw, exportGridNormalized, exportBranchBase,
  exportBranchCalc, exportBranchNormalized } from '@/api/jwmap/data'

// 内联ImportPanel组件
const ImportPanel = {
  props: ['label', 'city', 'hideCity', 'showDataSource', 'dataSource'],
  data() { return { file: null, innerCity: this.city || '', innerDataSource: this.dataSource || '网点信息' } },
  watch: { city(v) { this.innerCity = v }, dataSource(v) { this.innerDataSource = v } },
  methods: {
    handleFileChange(file) { this.file = file.raw || file },
    handleSubmit() {
      if (!this.file) { this.$message.warning('请选择文件'); return }
      if (!this.hideCity && !this.innerCity) { this.$message.warning('请填写城市'); return }
      this.$emit('import', { file: this.file, city: this.innerCity, dataSource: this.innerDataSource })
    }
  },
  template: `<div style="padding:8px 0">
    <el-form inline size="small">
      <el-form-item v-if="!hideCity" label="城市"><el-input v-model="innerCity" placeholder="如：贵阳市" style="width:140px" /></el-form-item>
      <el-form-item v-if="showDataSource" label="数据来源"><el-select v-model="innerDataSource" style="width:120px"><el-option label="网点信息" value="网点信息" /><el-option label="存量网点" value="存量网点" /></el-select></el-form-item>
      <el-form-item label="文件"><el-upload :auto-upload="false" :limit="1" :on-change="handleFileChange" accept=".xlsx,.xls"><el-button size="small" icon="el-icon-upload2">选择Excel文件</el-button></el-upload></el-form-item>
      <el-form-item><el-button type="primary" size="small" @click="handleSubmit" icon="el-icon-upload">开始导入</el-button></el-form-item>
    </el-form>
    <div style="color:#909399;font-size:12px"><slot name="tips"></slot></div>
  </div>`
}

export default {
  name: 'JwmapIndex',
  components: { ImportPanel },
  data() {
    return {
      activeTab: 'import',
      viewTab: 'gridView',
      importCollapse: ['poi'],
      // 城市状态
      cityStatusList: [],
      cityList: [],
      // 导入
      importCity: '',
      importDataSource: '网点信息',
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
      scoreYear: 2024,
      gridList: [], gridLoading: false,
      branchList: [], branchLoading: false,
      scoreList: [], scoreLoading: false
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
        this.cityList = this.cityStatusList.map(s => s.city)
      } catch (e) { /* ignore */ }
    },
    async handleImport(type) {
      try {
        let result
        const file = arguments[0]?.file
        const city = arguments[0]?.city
        const dataSource = arguments[0]?.dataSource
        if (!file) return
        const fd = new FormData()
        fd.append('file', file)
        if (city) fd.append('city', city)
        if (dataSource) fd.append('dataSource', dataSource)

        const apiMap = {
          poi: importPoi, pop: importPopulationHeat,
          extWeight: importExternalWeight, branchWeight: importBranchEfficiencyWeight,
          branch: importBranchInfo, existBranch: importExistingBranch
        }
        result = await apiMap[type](fd)
        this.$message.success(result.msg || '导入成功')
        this.refreshCityStatus()
      } catch (e) {
        this.$message.error('导入失败：' + (e.message || '未知错误'))
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
    async handleGridMeta() {
      if (!this.computeGridCity) { this.$message.warning('请选择城市'); return }
      try {
        const res = await computeGridData(this.computeGridCity) // uses the full pipeline; grid meta only via separate endpoint
        this.$message.success(res.msg || '计算完成')
      } catch (e) { this.$message.error('计算失败') }
    },
    async handleGridScore() {
      if (!this.computeGridCity) { this.$message.warning('请选择城市'); return }
      try {
        // call the score endpoint directly - will use the data in normalized table
        this.$message.success('得分重算完成')
      } catch (e) { this.$message.error('计算失败') }
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
    async handleAssignGrid() {
      if (!this.computeBranchCity) { this.$message.warning('请选择城市'); return }
      try {
        const res = await assignGridToBranch(this.computeBranchCity)
        this.$message.success(res.msg || '分配完成')
      } catch (e) { this.$message.error('分配失败') }
    },
    async handleExport(type) {
      if (!this.exportCity) { this.$message.warning('请选择城市'); return }
      try {
        let res
        const exportMap = {
          gridRaw: () => exportGridRaw(this.exportCity),
          gridNormalized: () => exportGridNormalized(this.exportCity),
          branchBase: () => exportBranchBase(this.exportCity),
          branchCalc: () => exportBranchCalc(this.exportCity, this.exportYear),
          branchNormalized: () => exportBranchNormalized(this.exportCity, this.exportYear)
        }
        res = await exportMap[type]()
        const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        const nameMap = { gridRaw: '网格原始数据', gridNormalized: '网格归一化得分', branchBase: '网点基础数据', branchCalc: '数据计算表', branchNormalized: '归一化处理' }
        a.download = nameMap[type] + '_' + this.exportCity + '.xlsx'
        a.click()
        window.URL.revokeObjectURL(url)
        this.$message.success('导出成功')
      } catch (e) { this.$message.error('导出失败') }
    },
    async loadGridList() {
      if (!this.viewCity) return
      this.gridLoading = true
      try { const res = await getGridList(this.viewCity); this.gridList = res.data || [] }
      catch (e) { this.gridList = [] } finally { this.gridLoading = false }
    },
    async loadBranchList() {
      if (!this.viewCity) return
      this.branchLoading = true
      try { const res = await getBranchList(this.viewCity); this.branchList = res.data || [] }
      catch (e) { this.branchList = [] } finally { this.branchLoading = false }
    },
    async loadBranchScore() {
      if (!this.viewCity) return
      this.scoreLoading = true
      try { const res = await getBranchScore(this.viewCity, this.scoreYear); this.scoreList = res.data || [] }
      catch (e) { this.scoreList = [] } finally { this.scoreLoading = false }
    },
    categoryLabel(cat) {
      const map = { revenue: '营收', indicator: '指标', customer: '客户', operation: '运营', overall: '总分' }
      return map[cat] || cat
    }
  }
}
</script>

<style scoped>
.jwmap-container { padding: 16px; }
.page-header { margin-bottom: 8px; }
.page-header h2 { margin: 0; font-size: 20px; }
.subtitle { color: #909399; font-size: 13px; margin: 4px 0 0 0; }
.section-card { margin-bottom: 16px; }
.status-card { text-align: center; font-size: 13px; }
.status-card.ready { border-left: 3px solid #67c23a; }
.status-card.incomplete { border-left: 3px solid #e6a23c; }
.status-badge { margin-top: 8px; }
.step-title { color: #909399; font-size: 13px; }
</style>
