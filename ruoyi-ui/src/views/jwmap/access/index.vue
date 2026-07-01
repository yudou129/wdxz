<template>
  <div class="page-access">
    <div class="pa-header">
      <div class="pa-header-left">
        <h2 class="pa-title"><i class="el-icon-document" /> 数据查看申请</h2>
        <span class="pa-subtitle">申请查看支行级数据权限，由上级机构审核</span>
      </div>
      <div class="pa-header-right">
        <el-button type="primary" @click="showNewForm = true">
          <i class="el-icon-plus" /> 新建申请
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="pa-stats">
      <div class="pa-stat-card">
        <span class="pa-stat-label">待审批</span>
        <span class="pa-stat-num pending">{{ pendingCount }}</span>
      </div>
      <div class="pa-stat-card">
        <span class="pa-stat-label">已通过</span>
        <span class="pa-stat-num approved">{{ approvedCount }}</span>
      </div>
      <div class="pa-stat-card">
        <span class="pa-stat-label">已拒绝</span>
        <span class="pa-stat-num rejected">{{ rejectedCount }}</span>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="我的申请" name="myList">
        <el-table :data="requestList" v-loading="tableLoading" class="pa-table">
          <el-table-column label="目标支行" prop="targetDeptName" min-width="150" />
          <el-table-column label="事由" prop="reason" min-width="220" show-overflow-tooltip />
          <el-table-column label="有效期" min-width="170">
            <template slot-scope="{ row }">
              <span class="pa-cell" v-if="row.status === '1' && row.validDateFrom">
                {{ parseDate(row.validDateFrom) }} ~ {{ parseDate(row.validDateTo) }}
              </span>
              <span class="pa-cell" v-else-if="row.validDays">{{ row.validDays }}天</span>
              <span class="pa-cell" v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template slot-scope="{ row }">
              <span :class="['pa-pill', statusClass(row.status)]">{{ statusLabel(row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="审核人" prop="reviewerName" width="110" />
          <el-table-column label="审核时间" prop="reviewTime" width="160" />
          <el-table-column label="操作" width="90">
            <template slot-scope="{ row }">
              <el-button v-if="row.status === '0'" type="text" class="pa-act-cancel"
                         @click="handleCancel(row.requestId)">撤销</el-button>
              <el-button v-if="row.status === '1'" type="text" class="pa-act-export"
                         @click="handleExport(row)">导出</el-button>
              <el-button type="text" class="pa-act-detail"
                         @click="showDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <pagination v-show="total > 0" :total="total" :page.sync="pageNum" :limit.sync="pageSize"
                    @pagination="fetchList" />
      </el-tab-pane>
    </el-tabs>

    <!-- 新建申请对话框 -->
    <el-dialog title="新建数据查看申请" :visible.sync="showNewForm" width="600px"
               custom-class="pa-dialog">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" class="pa-form">
        <el-form-item label="目标支行" prop="targetDeptId">
          <treeselect
            v-model="form.targetDeptId"
            :options="deptTree"
            :normalizer="node => ({ id: node.id, label: node.label, children: node.children || [] })"
            placeholder="请选择目标支行（支行级节点）"
            style="width:100%"
          />
          <span class="pa-form-tip">可选市行或支行。审核人由目标机构的上级机构自动筛选</span>
        </el-form-item>
        <el-form-item label="申请事由" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3"
                    placeholder="请输入申请查看该支行数据的事由" maxlength="500" />
        </el-form-item>
        <el-form-item label="有效期" prop="validDays">
          <el-radio-group v-model="form.validDays">
            <el-radio :label="7">7天</el-radio>
            <el-radio :label="30">30天</el-radio>
            <el-radio :label="90">90天</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核人" prop="reviewerId">
          <el-select v-model="form.reviewerId" placeholder="请选择审核人" style="width:100%"
                     :loading="reviewersLoading" :disabled="reviewers.length === 0 && !reviewersLoading">
            <el-option v-for="r in reviewers" :key="r.userId || r.userid"
                       :label="r.nickName || r.nickname || r.userName || r.username"
                       :value="r.userId || r.userid" />
          </el-select>
          <span v-if="reviewers.length === 0 && !reviewersLoading" class="pa-form-tip pa-tip-warn">
            该机构无审核员，请联系贵阳分行科技部添加
          </span>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="showNewForm = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting"
                   :disabled="reviewers.length === 0">提交</el-button>
      </div>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog title="申请详情" :visible.sync="showDetailDialog" width="560px"
               custom-class="pa-dialog">
      <div v-if="detailData" class="pa-detail-card">
        <div class="pa-detail-row"><span class="pa-detail-lbl">目标支行</span><span class="pa-detail-val">{{ detailData.targetDeptName }}</span></div>
        <div class="pa-detail-row"><span class="pa-detail-lbl">申请事由</span><span class="pa-detail-val">{{ detailData.reason }}</span></div>
        <div class="pa-detail-row"><span class="pa-detail-lbl">状态</span><span :class="['pa-pill', statusClass(detailData.status)]">{{ statusLabel(detailData.status) }}</span></div>
        <div class="pa-detail-row"><span class="pa-detail-lbl">审核人</span><span class="pa-detail-val">{{ detailData.reviewerName || '-' }}</span></div>
        <div class="pa-detail-row"><span class="pa-detail-lbl">审核意见</span><span class="pa-detail-val">{{ detailData.reviewComment || '-' }}</span></div>
        <div class="pa-detail-row" v-if="detailData.validDateFrom"><span class="pa-detail-lbl">有效期</span><span class="pa-detail-val">{{ detailData.validDateFrom }} ~ {{ detailData.validDateTo }}</span></div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { submitAccessRequest, getMyRequestList, cancelRequest, getRequestDetail, resolveBranchDept, getReviewers, getAccessDeptTree, exportApprovedData } from '@/api/jwmap/data-access'
import Treeselect from '@riophae/vue-treeselect'
import '@riophae/vue-treeselect/dist/vue-treeselect.css'
import errorHandlerMixin from '../mixins/errorHandlerMixin'
import fetchStateMixin from '../mixins/fetchStateMixin'

export default {
  name: 'JwDataAccessRequest',
  components: { Treeselect },
  mixins: [fetchStateMixin, errorHandlerMixin],
  data() {
    return {
      activeTab: 'myList',
      requestList: [],
      total: 0,
      pageNum: 1,
      pageSize: 10,
      tableLoading: false,

      showNewForm: false,
      submitting: false,
      reviewers: [],
      reviewersLoading: false,
      form: { targetDeptId: null, reason: '', validDays: 30, reviewerId: null },
      rules: {
        targetDeptId: [{ required: true, message: '请选择目标支行', trigger: 'change' }],
        reason: [{ required: true, message: '请输入申请事由', trigger: 'blur' }],
        validDays: [{ required: true, message: '请选择有效期', trigger: 'change' }],
        reviewerId: [{ required: true, message: '请选择审核人', trigger: 'change' }]
      },

      showDetailDialog: false,
      detailData: null,
      deptTree: [],
      statusOptions: [
        { value: '0', label: '待审批', raw: { listClass: 'primary' } },
        { value: '1', label: '已通过', raw: { listClass: 'success' } },
        { value: '2', label: '已拒绝', raw: { listClass: 'danger' } },
        { value: '3', label: '已撤销', raw: { listClass: 'info' } },
        { value: '4', label: '已过期', raw: { listClass: 'warning' } }
      ]
    }
  },
  created() {
    this._active = true
    this.fetchList()
    this.loadDeptTree()
  },
  beforeDestroy() {
    this._active = false
    if (this._branchPollTimer) {
      clearTimeout(this._branchPollTimer)
      this._branchPollTimer = null
    }
  },
  watch: {
    '$route.query.branchId': {
      handler(branchId) {
        if (!branchId) return
        // 等 deptTree 加载完成后自动填充
        let retries = 0
        const tryFill = () => {
          if (!this._active) return
          if (this.deptTree.length > 0) {
            resolveBranchDept(branchId).then(res => {
              if (!this._active) return
              const data = res.data || {}
              if (data.deptId) {
                this.form.targetDeptId = data.deptId
                this.showNewForm = true
              }
            }).catch(() => {})
          } else if (retries++ < 120) {
            this._branchPollTimer = setTimeout(tryFill, 200)
          }
        }
        tryFill()
      },
      immediate: true
    },
    showNewForm(val) {
      if (val) {
        this.form.reviewerId = null
        this.loadReviewers(this.form.targetDeptId)
      }
    },
    'form.targetDeptId'(val) {
      if (this.showNewForm && val) {
        this.form.reviewerId = null
        this.loadReviewers(val)
      }
    }
  },
  computed: {
    pendingCount() { return this.requestList.filter(r => r.status === '0').length },
    approvedCount() { return this.requestList.filter(r => r.status === '1').length },
    rejectedCount() { return this.requestList.filter(r => r.status === '2').length }
  },
  methods: {
    async handleExport(row) {
      try {
        const blob = await exportApprovedData(row.requestId)
        const header = await blob.slice(0, 1).text()
        if (header === '{') {
          const errText = await blob.text()
          try { const err = JSON.parse(errText); this.$message.error(err.msg || '导出失败') } catch (e) { this.$message.error('导出失败') }
          return
        }
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = '网点数据_' + (row.targetDeptName || 'export') + '.xlsx'
        a.click()
        window.URL.revokeObjectURL(url)
        this.$message.success('导出成功')
      } catch (e) { this.$message.error('导出失败：' + (e.message || '未知错误')) }
    },
    statusClass(s) {
      const map = { '0': 'pill-pending', '1': 'pill-approved', '2': 'pill-rejected', '3': 'pill-cancelled', '4': 'pill-expired' }
      return map[s] || ''
    },
    statusLabel(s) {
      const map = { '0': '待审批', '1': '已通过', '2': '已拒绝', '3': '已撤销', '4': '已过期' }
      return map[s] || s
    },
    async fetchList() {
      this.tableLoading = true
      try {
        const res = await getMyRequestList({ pageNum: this.pageNum, pageSize: this.pageSize })
        this.requestList = res.rows || []
        this.total = res.total || 0
      } catch (e) {
        this.handleError(e, '加载申请列表')
      } finally {
        this.tableLoading = false
      }
    },
    async loadDeptTree() {
      try {
        const res = await getAccessDeptTree()
        this.deptTree = res.data || []
      } catch (e) {
        this.handleError(e, '加载部门树', true) // 静默失败
        this.deptTree = []
      }
    },
    async loadReviewers(targetDeptId) {
      this.reviewersLoading = true
      try {
        const res = await getReviewers(targetDeptId || null)
        this.reviewers = res.data || []
      } catch (e) {
        this.handleError(e, '加载审核人', true)
        this.reviewers = []
      } finally {
        this.reviewersLoading = false
      }
    },
    handleSubmit() {
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        this.submitting = true
        submitAccessRequest({ targetDeptId: this.form.targetDeptId, reason: this.form.reason, validDays: this.form.validDays, reviewerId: this.form.reviewerId }).then(res => {
          this.$message.success('申请已提交')
          this.showNewForm = false
          this.form = { targetDeptId: null, reason: '', validDays: 30, reviewerId: null }
          this.$nextTick(() => this.$refs.formRef.clearValidate())
          this.fetchList()
        }).catch(err => {
          this.handleError(err, '提交申请')
        }).finally(() => { this.submitting = false })
      })
    },
    handleCancel(requestId) {
      this.$confirm('确认撤销该申请吗？', '提示', { type: 'warning' }).then(() => {
        cancelRequest(requestId).then(() => {
          this.$message.success('已撤销')
          this.fetchList()
        })
      }).catch(() => {})
    },
    showDetail(row) {
      getRequestDetail(row.requestId).then(res => {
        this.detailData = res.data
        this.showDetailDialog = true
      })
    },
    /** 格式化为 yyyy-MM-dd HH:mm:ss */
    parseDate(date) {
      if (!date) return '-'
      const d = new Date(date)
      if (isNaN(d.getTime())) return date
      const pad = n => n.toString().padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    }
  }
}
</script>

<style scoped>
.page-access { padding: 0 20px; }
/* 头部 */
.pa-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 20px; padding: 20px 0 0;
}
.pa-header-left { display: flex; flex-direction: column; gap: 4px; }
.pa-title { font-size: 20px; font-weight: 700; color: #232845; margin: 0; display: flex; align-items: center; gap: 8px; }
.pa-title i { color: #4f6ef6; font-size: 22px; }
.pa-subtitle { font-size: 13px; color: #888; }
/* 统计卡片 */
.pa-stats { display: flex; gap: 14px; margin-bottom: 20px; }
.pa-stat-card {
  flex: 1; background: #fff; border-radius: 10px; padding: 16px 20px;
  border: 1px solid rgba(79,110,246,0.06); box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  display: flex; flex-direction: column; gap: 4px;
}
.pa-stat-label { font-size: 13px; color: #666; }
.pa-stat-num { font-size: 28px; font-weight: 800; line-height: 1.1; }
.pa-stat-num.pending { color: #409eff; }
.pa-stat-num.approved { color: #67c23a; }
.pa-stat-num.rejected { color: #f56c6c; }
/* 表格 */
.pa-table { width: 100%; }
.pa-table >>> .el-table__header th {
  background: #f8f9fd; color: #444; font-weight: 600; font-size: 14px;
}
.pa-table >>> .el-table__body td { font-size: 14px; padding: 10px 0; }
.pa-table >>> .el-table__row { height: 48px; }
.pa-cell { font-size: 14px; color: #303651; }
/* 状态标签 */
.pa-pill {
  display: inline-block; padding: 2px 12px; border-radius: 12px;
  font-size: 12px; font-weight: 600; line-height: 22px;
}
.pill-pending { background: rgba(64,158,255,0.08); color: #409eff; }
.pill-approved { background: rgba(103,194,58,0.08); color: #67c23a; }
.pill-rejected { background: rgba(245,108,108,0.08); color: #f56c6c; }
.pill-cancelled { background: rgba(144,147,153,0.08); color: #909399; }
.pill-expired { background: rgba(230,162,60,0.08); color: #e6a23c; }
/* 操作按钮 */
.pa-act-cancel { color: #f56c6c; font-size: 14px; }
.pa-act-cancel:hover { color: #e04040; }
.pa-act-detail { color: #4f6ef6; font-size: 14px; }
.pa-act-export { color: #52c41a; font-size: 14px; }
.pa-act-export:hover { color: #389e0d; }
/* 对话框 */
.pa-dialog >>> .el-dialog__header { padding: 20px 24px 16px; border-bottom: 1px solid #f0f0f0; }
.pa-dialog >>> .el-dialog__title { font-size: 17px; font-weight: 700; color: #232845; }
.pa-dialog >>> .el-dialog__body { padding: 20px 24px; }
.pa-dialog >>> .el-dialog__footer { padding: 12px 24px 20px; border-top: 1px solid #f0f0f0; }
.pa-form .el-form-item { margin-bottom: 20px; }
.pa-form >>> .el-form-item__label { font-size: 14px; color: #444; }
.pa-form-tip { display: block; font-size: 12px; color: #888; margin-top: 4px; line-height: 1.4; }
.pa-tip-warn { color: #e6a23c; }
/* 详情卡片 */
.pa-detail-card { display: flex; flex-direction: column; gap: 12px; }
.pa-detail-row {
  display: flex; align-items: baseline; gap: 12px;
  padding: 10px 14px; background: #f8f9fd; border-radius: 8px;
}
.pa-detail-lbl { font-size: 13px; color: #888; width: 80px; flex-shrink: 0; }
.pa-detail-val { font-size: 14px; color: #303651; font-weight: 500; }
</style>
