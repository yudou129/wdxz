<template>
  <div class="branch-detail-view" style="height:100%">
    <el-row :gutter="16" style="height:100%">
      <!-- 左侧：网点排名列表 -->
      <el-col :span="8" style="height:100%">
        <el-card shadow="never" class="side-card" style="height:100%">
          <div slot="header" class="list-header">
            <span>网点排名</span>
            <el-select v-model="districtFilter" size="mini" placeholder="全部区县" clearable filterable style="width:130px">
              <el-option v-for="d in districtList" :key="d" :label="d" :value="d" />
            </el-select>
          </div>
          <el-table :data="pagedBranches" stripe size="small" highlight-current-row
            @current-change="selectBranch" height="calc(100vh - 420px)" style="width:100%">
            <el-table-column label="#" width="45" align="center">
              <template slot-scope="scope">{{ rankIndex(scope.row) }}</template>
            </el-table-column>
            <el-table-column prop="secondaryBranch" label="网点名称" min-width="140" show-overflow-tooltip />
            <el-table-column label="得分" width="80" align="right">
              <template slot-scope="scope">{{ scope.row.categoryScore ? scope.row.categoryScore.toFixed(4) : '-' }}</template>
            </el-table-column>
          </el-table>
          <el-pagination small layout="prev, pager, next" :total="filteredBranches.length"
            :page-size="pageSize" :current-page.sync="currentPage" style="margin-top:8px;text-align:center" />
        </el-card>
      </el-col>

      <!-- 右侧：网点详情 -->
      <el-col :span="16" style="height:100%">
        <el-card shadow="never" v-if="selectedBranch" class="detail-card" style="height:100%;overflow-y:auto">
          <div class="detail-header">
            <h3 style="margin:0;font-size:18px">{{ selectedBranch.secondaryBranch || selectedBranch.branchCode }}</h3>
            <div>
              <el-tag type="success" size="medium" style="margin-right:6px">
                得分 {{ selectedBranch.categoryScore ? selectedBranch.categoryScore.toFixed(6) : '-' }}
              </el-tag>
              <el-tag type="primary" size="medium">
                排名 {{ selectedBranch.rankNum || '-' }}
              </el-tag>
            </div>
          </div>
          <div class="meta-row">
            <span>网点号 {{ branchDetail.branchCode || '-' }}</span>
            <span>行政区 {{ branchDetail.districtName || '-' }}</span>
            <span>地址 {{ branchDetail.address || '-' }}</span>
            <span>经度 {{ branchDetail.longitude }}</span>
            <span>纬度 {{ branchDetail.latitude }}</span>
            <span>总人数 {{ branchDetail.totalStaff || '-' }}</span>
            <span>面积 {{ branchDetail.totalArea || '-' }}</span>
          </div>

          <!-- 指标数据（三级树：根→二级→叶子，逐级展开） -->
          <div v-if="indicatorTree.length > 0" class="section">
            <h4 class="section-title">指标数据</h4>
            <el-collapse v-model="activeCategories">
              <el-collapse-item v-for="(root, idx) in indicatorTree" :key="idx" :name="String(idx)">
                <template slot="title">
                  <span class="level-root">{{ root.name }}</span>
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
          <el-empty v-else description="该网点暂无指标数据" :image-size="80" style="padding:20px 0" />

          <!-- 分类得分（一级指标的 TOPSIS 得分） -->
          <div v-if="pillarList.length > 0" class="section">
            <h4 class="section-title">分类得分</h4>
            <el-row :gutter="12">
              <el-col :span="Math.floor(24 / pillarList.length)" v-for="s in pillarList" :key="s.key">
                <div class="pillar-card">
                  <div class="pillar-label">{{ s.label }}</div>
                  <div class="pillar-value">{{ s.score }}</div>
                </div>
              </el-col>
            </el-row>
          </div>

          <!-- 排名信息 -->
          <div v-if="internalRanking" class="section">
            <h4 class="section-title">排名信息</h4>
            <el-row :gutter="16">
              <el-col :span="12">
                <div class="rank-card">
                  <div class="rank-label">全市排名</div>
                  <div class="rank-value">{{ internalRanking.cityRank }} / {{ internalRanking.cityTotal }}</div>
                </div>
              </el-col>
              <el-col :span="12">
                <div class="rank-card">
                  <div class="rank-label">一级支行排名</div>
                  <div class="rank-value">{{ internalRanking.branchRank }} / {{ internalRanking.branchTotal }}</div>
                </div>
              </el-col>
            </el-row>
          </div>
        </el-card>
        <el-empty v-else description="请从左侧选择一个网点" style="padding:80px 0" :image-size="120" />
      </el-col>
    </el-row>
  </div>
</template>

<script>
import {
  getBranchScore, getBranchList, getBranchIndicators,
  getBranchInternalRanking, getIndicatorList,
  getBranchPillarScores
} from '@/api/jwmap/data'
import detailViewMixin from '../mixins/detailViewMixin'

export default {
  mixins: [detailViewMixin],
  props: {
    city: { type: String, default: '' },
    year: { type: Number, default: 2024 }
  },
  data() {
    return {
      allBranches: [],
      branchInfoMap: {},
      selectedBranch: null,
      branchDetail: {},
      branchIndicators: [],
      internalRanking: null
    }
  },
  computed: {
    indicators() { return this.branchIndicators },
    districtList() {
      const set = new Set()
      this.allBranches.forEach(b => {
        const info = this.branchInfoMap[b.branchId]
        if (info && info.districtName) set.add(info.districtName)
      })
      return [...set].sort()
    },
    filteredBranches() {
      let list = [...this.allBranches]
      if (this.districtFilter) {
        list = list.filter(b => {
          const info = this.branchInfoMap[b.branchId]
          return info && info.districtName === this.districtFilter
        })
      }
      return list
    },
    pagedBranches() {
      const start = (this.currentPage - 1) * this.pageSize
      return this.filteredBranches.slice(start, start + this.pageSize)
    }
  },
  watch: {
    city: {
      immediate: true,
      handler(val) {
        if (val) this.loadBranchList()
      }
    },
    year() {
      if (this.city) this.loadBranchList()
    }
  },
  methods: {
    async loadBranchList() {
      this.allBranches = []
      this.selectedBranch = null
      this.branchDetail = {}
      this.branchIndicators = []
      this.pillarScores = null
      this.internalRanking = null
      try {
        const [scoreRes, infoRes, indRes] = await Promise.all([
          getBranchScore(this.city, this.year),
          getBranchList(this.city),
          getIndicatorList()
        ])
        // 排名列表（已按得分排序）
        this.allBranches = scoreRes.data || []

        // 网点详情映射
        const infoList = infoRes.data || []
        this.branchInfoMap = {}
        infoList.forEach(b => { this.branchInfoMap[b.branchId] = b })

        // 指标名称映射
        const indList = indRes.data || []
        this.indicatorMap = {}
        this.categoryMap = {}
        for (const item of indList) {
          this.indicatorMap[item.indicatorCode] = {
            name: item.indicatorName,
            parentCode: item.parentCode
          }
        }
        for (const item of indList) {
          if (item.parentCode && this.indicatorMap[item.parentCode]) {
            this.categoryMap[item.parentCode] = this.indicatorMap[item.parentCode].name
          }
        }
        for (const item of indList) {
          if (!item.parentCode) {
            this.categoryMap[item.indicatorCode] = item.indicatorName
          }
        }
      } catch (e) {
        this.allBranches = []
      }
    },
    async selectBranch(branch) {
      this.selectedBranch = branch
      this.branchDetail = this.branchInfoMap[branch.branchId] || {}
      this.branchIndicators = []
      this.pillarScores = null
      this.internalRanking = null
      try {
        const [indRes, pillarRes, rankRes] = await Promise.all([
          getBranchIndicators(branch.branchId, this.year),
          getBranchPillarScores(branch.branchId, this.year),
          getBranchInternalRanking(branch.branchId, this.year)
        ])
        this.branchIndicators = indRes.data || []
        this.pillarScores = pillarRes.data || null
        this.internalRanking = rankRes.data || null
      } catch (e) {
        this.branchIndicators = []
        this.pillarScores = null
        this.internalRanking = null
      }
    },
    rankIndex(row) {
      return this.filteredBranches.indexOf(row) + 1
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
.rank-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px 16px;
  text-align: center;
}
.rank-label {
  font-size: 12px;
  color: #8c95a8;
  margin-bottom: 4px;
}
.rank-value {
  font-size: 16px;
  font-weight: 700;
  color: #232845;
}
.level-root { font-size: 15px; font-weight: 700; color: #232845; }
.level-category { font-size: 13px; font-weight: 600; color: #5a6276; }
</style>
