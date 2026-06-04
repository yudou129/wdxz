<template>
  <div class="grid-detail-view" style="height:100%">
    <el-row :gutter="16" style="height:100%">
      <!-- 左侧：网格排名列表 -->
      <el-col :span="8" style="height:100%">
        <el-card shadow="never" class="side-card" style="height:100%">
          <div slot="header" class="list-header">
            <span>网格排名</span>
            <el-select v-model="districtFilter" size="mini" placeholder="全部区县" clearable filterable style="width:130px">
              <el-option v-for="d in districtList" :key="d" :label="d" :value="d" />
            </el-select>
          </div>
          <el-table :data="pagedGrids" stripe size="small" highlight-current-row
            @current-change="selectGrid" height="calc(100vh - 420px)" style="width:100%">
            <el-table-column label="#" width="45" align="center">
              <template slot-scope="scope">{{ rankIndex(scope.row) }}</template>
            </el-table-column>
            <el-table-column prop="gridCode" label="网格" min-width="130" show-overflow-tooltip />
            <el-table-column prop="siteScore" label="得分" width="80" align="right">
              <template slot-scope="scope">{{ scope.row.siteScore ? scope.row.siteScore.toFixed(4) : '-' }}</template>
            </el-table-column>
          </el-table>
          <el-pagination small layout="prev, pager, next" :total="filteredGrids.length"
            :page-size="pageSize" :current-page.sync="currentPage" style="margin-top:8px;text-align:center" />
        </el-card>
      </el-col>

      <!-- 右侧：网格详情 -->
      <el-col :span="16" style="height:100%">
        <el-card shadow="never" v-if="selectedGrid" class="detail-card" style="height:100%;overflow-y:auto">
          <div class="detail-header">
            <h3 style="margin:0;font-size:18px">{{ selectedGrid.gridCode }}</h3>
            <div>
              <el-tag type="success" size="medium" style="margin-right:6px">
                得分 {{ selectedGrid.siteScore ? selectedGrid.siteScore.toFixed(6) : '-' }}
              </el-tag>
              <el-tag type="primary" size="medium">
                排名 {{ gridRank(selectedGrid.gridCode) }}
              </el-tag>
            </div>
          </div>
          <div class="meta-row">
            <span>经度 {{ selectedGrid.longitude }}</span>
            <span>纬度 {{ selectedGrid.latitude }}</span>
            <span>区县 {{ selectedGrid.district || '-' }}</span>
          </div>

          <!-- 指标数据（三级树：根→二级→叶子，逐级展开） -->
          <div v-if="indicatorTree.length > 0" class="section">
            <h4 class="section-title">指标数据</h4>
            <el-collapse v-model="activeCategories">
              <el-collapse-item v-for="(root, idx) in indicatorTree" :key="idx" :name="String(idx)">
                <template slot="title">
                  <span class="level-root">{{ root.name }}</span>
                  <span v-if="pillarScores && pillarScores[root.code]" class="pillar-hint">
                    TOPSIS: {{ (pillarScores[root.code].score || 0).toFixed(4) }}
                  </span>
                </template>
                <!-- 直接子节点：直属于根，无中间分类标题 -->
                <div v-for="ind in root.direct" :key="ind.indicatorCode" class="indicator-row">
                  <span class="ind-name">{{ ind.indicatorName || ind.indicatorCode }}</span>
                  <span class="ind-value">{{ formatValue(ind.indicatorValue) }}</span>
                </div>
                <!-- 中间分类节点：有中间父节点的，按分类折叠展示 -->
                <el-collapse v-if="root.categories.length" v-model="activeL2[idx]" class="inner-collapse">
                  <el-collapse-item v-for="(cat, cidx) in root.categories" :key="cidx" :name="String(cidx)">
                    <template slot="title">
                      <span class="level-category">{{ cat.name }}</span>
                    </template>
                    <div v-for="ind in cat.indicators" :key="ind.indicatorCode" class="indicator-row">
                      <span class="ind-name">{{ ind.indicatorName || ind.indicatorCode }}</span>
                      <span class="ind-value">{{ formatValue(ind.indicatorValue) }}</span>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </el-collapse-item>
            </el-collapse>
          </div>
          <el-empty v-else description="该网格暂无指标数据" :image-size="80" style="padding:20px 0" />

          <!-- 三聚集得分（按指标配置根节点动态分组） -->
          <div v-if="pillarList.length > 0" class="section">
            <h4 class="section-title">三聚集得分</h4>
            <el-row :gutter="12">
              <el-col :span="Math.floor(24 / pillarList.length)" v-for="p in pillarList" :key="p.key">
                <div class="pillar-card">
                  <div class="pillar-label">{{ p.label }}</div>
                  <div class="pillar-value">{{ p.score }}</div>
                </div>
              </el-col>
            </el-row>
          </div>

          <!-- 关联网点 -->
          <div v-if="gridBranches.length > 0" class="section">
            <h4 class="section-title">关联网点 ({{ gridBranches.length }})</h4>
            <el-table :data="gridBranches" size="small" stripe>
              <el-table-column prop="secondaryBranch" label="网点名称" min-width="140" />
              <el-table-column prop="branchCode" label="网点号" width="100" />
              <el-table-column prop="districtName" label="行政区" width="80" />
              <el-table-column prop="totalStaff" label="总人数" width="70" />
            </el-table>
          </div>
        </el-card>
        <el-empty v-else description="请从左侧选择一个网格" style="padding:80px 0" :image-size="120" />
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { getGridList, getGridIndicators, getGridBranches, getGridPillarScores, getIndicatorList } from '@/api/jwmap/data'
import detailViewMixin from '../mixins/detailViewMixin'

export default {
  mixins: [detailViewMixin],
  props: {
    city: { type: String, default: '' }
  },
  data() {
    return {
      allGrids: [],
      selectedGrid: null,
      gridIndicators: [],
      gridBranches: []
    }
  },
  computed: {
    indicators() { return this.gridIndicators },
    districtList() {
      const set = new Set()
      this.allGrids.forEach(g => { if (g.district) set.add(g.district) })
      return [...set].sort()
    },
    filteredGrids() {
      let list = [...this.allGrids].sort((a, b) => (b.siteScore || 0) - (a.siteScore || 0))
      if (this.districtFilter) {
        list = list.filter(g => g.district === this.districtFilter)
      }
      return list
    },
    pagedGrids() {
      const start = (this.currentPage - 1) * this.pageSize
      return this.filteredGrids.slice(start, start + this.pageSize)
    }
  },
  watch: {
    city: {
      immediate: true,
      handler(val) {
        if (val) this.loadGridList(val)
      }
    }
  },
  methods: {
    async loadGridList(city) {
      this.allGrids = []
      this.selectedGrid = null
      try {
        const [gridRes, indRes] = await Promise.all([
          getGridList(city),
          getIndicatorList()  // 加载全部类型，使 categoryMap 能正确解析各级父节点名称
        ])
        this.allGrids = gridRes.data || []

        // 构建指标code→name映射
        const indList = indRes.data || []
        this.indicatorMap = {}
        this.categoryMap = {}
        for (const item of indList) {
          this.indicatorMap[item.indicatorCode] = {
            name: item.indicatorName,
            parentCode: item.parentCode
          }
        }
        // 构建父节点code→name映射（用于分组标题）
        for (const item of indList) {
          if (item.parentCode && this.indicatorMap[item.parentCode]) {
            this.categoryMap[item.parentCode] = this.indicatorMap[item.parentCode].name
          }
        }
        // 根节点也加入categoryMap（自身作为类目标题）
        for (const item of indList) {
          if (!item.parentCode) {
            this.categoryMap[item.indicatorCode] = item.indicatorName
          }
        }
      } catch (e) {
        this.allGrids = []
      }
    },
    async selectGrid(grid) {
      this.selectedGrid = grid
      this.gridIndicators = []
      this.gridBranches = []
      this.pillarScores = null
      try {
        const [indRes, branchRes, pillarRes] = await Promise.all([
          getGridIndicators(grid.gridCode),
          getGridBranches(grid.gridCode),
          getGridPillarScores(grid.gridCode)
        ])
        this.gridIndicators = indRes.data || []
        this.gridBranches = branchRes.data || []
        this.pillarScores = pillarRes.data || null
      } catch (e) {
        this.gridIndicators = []
        this.gridBranches = []
        this.pillarScores = null
      }
    },
    rankIndex(row) {
      return this.filteredGrids.indexOf(row) + 1
    },
    gridRank(gridCode) {
      const idx = this.filteredGrids.findIndex(g => g.gridCode === gridCode)
      return idx >= 0 ? idx + 1 : '-'
    }
  }
}
</script>

<style scoped>
.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
}
.side-card >>> .el-card__body {
  padding: 10px 12px;
}
.detail-card >>> .el-card__body {
  padding: 16px 20px;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.meta-row {
  margin: 8px 0 16px;
  color: #8c95a8;
  font-size: 13px;
}
.meta-row span {
  margin-right: 16px;
}
.section {
  margin-top: 16px;
}
.section-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #232845;
}
.indicator-row {
  display: flex;
  justify-content: space-between;
  padding: 5px 0;
  border-bottom: 1px solid #f0f2f5;
  font-size: 13px;
}
.indicator-row:hover {
  background: #f8faff;
}
.ind-name {
  color: #5a6276;
}
.ind-value {
  font-weight: 600;
  color: #232845;
}
.inner-collapse >>> .el-collapse-item__header {
  padding-left: 16px;
  font-size: 13px;
  font-weight: 500;
  color: #5a6276;
  height: 32px;
  line-height: 32px;
  background: #fafbfc;
  border-radius: 4px;
}
.inner-collapse >>> .el-collapse-item__wrap {
  border-bottom: none;
}
.inner-collapse >>> .el-collapse-item__content {
  padding: 2px 18px 6px;
}
.pillar-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px;
  text-align: center;
  transition: background 0.2s;
}
.pillar-card:hover {
  background: #eef1f6;
}
.pillar-label {
  font-size: 12px;
  color: #8c95a8;
  margin-bottom: 4px;
}
.pillar-value {
  font-size: 20px;
  font-weight: 700;
  color: #232845;
}
.level-root { font-size: 15px; font-weight: 700; color: #232845; }
.level-category { font-size: 13px; font-weight: 600; color: #5a6276; }
.pillar-hint { font-size: 12px; font-weight: 400; color: #8c95a8; margin-left: 8px; }
</style>
