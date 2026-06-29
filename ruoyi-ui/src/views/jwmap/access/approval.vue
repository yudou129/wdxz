<template>
  <div class="page-approval">
    <div class="pa-header">
      <div class="pa-header-left">
        <h2 class="pa-title"><i class="el-icon-bell" /> 审批管理</h2>
        <span class="pa-subtitle">审核用户的数据查看申请</span>
      </div>
    </div>

    <el-alert v-if="notReviewer" title="暂无审批权限"
             description="当前用户不是数据审核员，请联系管理员分配 data_reviewer 角色"
             type="warning" show-icon :closable="false" class="pa-alert" />

    <!-- 待审批统计 -->
    <div class="pa-stats" v-if="!notReviewer">
      <div class="pa-stat-card">
        <span class="pa-stat-label">待审批</span>
        <span class="pa-stat-num pending">{{ pendingTotal }}</span>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="待审批" name="pending">
        <el-table :data="pendingList" v-loading="loading.pendingList" class="pa-table">
          <el-table-column label="申请人" prop="applicantName" width="120" />
          <el-table-column label="目标支行" prop="targetDeptName" min-width="150" />
          <el-table-column label="事由" prop="reason" min-width="220" show-overflow-tooltip />
          <el-table-column label="提交时间" prop="createTime" width="160" />
          <el-table-column label="有效期" width="80">
            <template slot-scope="{ row }">{{ row.validDays }}天</template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template slot-scope="{ row }">
              <el-button type="text" class="pa-act-review" @click="handleReview(row)">审核</el-button>
            </template>
          </el-table-column>
        </el-table>
        <pagination v-show="pendingTotal > 0" :total="pendingTotal"
                    :page.sync="pendingPageNum" :limit.sync="pendingPageSize"
                    @pagination="fetchPendingList" />
      </el-tab-pane>

      <el-tab-pane label="已审批" name="reviewed">
        <el-table :data="reviewedList" v-loading="loading.reviewedList" class="pa-table">
          <el-table-column label="申请人" prop="applicantName" width="120" />
          <el-table-column label="目标支行" prop="targetDeptName" min-width="150" />
          <el-table-column label="事由" prop="reason" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template slot-scope="{ row }">
              <span :class="['pa-pill', statusClass(row.status)]">{{ statusLabel(row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="审批意见" prop="reviewComment" width="160" show-overflow-tooltip />
          <el-table-column label="审批时间" prop="reviewTime" width="160" />
        </el-table>
        <pagination v-show="reviewedTotal > 0" :total="reviewedTotal"
                    :page.sync="reviewedPageNum" :limit.sync="reviewedPageSize"
                    @pagination="fetchReviewedList" />
      </el-tab-pane>
    </el-tabs>

    <!-- 审核对话框 -->
    <el-dialog title="审批申请" :visible.sync="showReviewDialog" width="560px"
               custom-class="pa-dialog">
      <div v-if="reviewItem" class="pa-review-wrap">
        <div class="pa-review-card">
          <div class="pa-review-row"><span class="pa-review-lbl">申请人</span><span class="pa-review-val">{{ reviewItem.applicantName }}</span></div>
          <div class="pa-review-row"><span class="pa-review-lbl">目标支行</span><span class="pa-review-val">{{ reviewItem.targetDeptName }}</span></div>
          <div class="pa-review-row"><span class="pa-review-lbl">申请事由</span><span class="pa-review-val">{{ reviewItem.reason }}</span></div>
          <div class="pa-review-row"><span class="pa-review-lbl">有效期</span><span class="pa-review-val">{{ reviewItem.validDays }}天</span></div>
          <div class="pa-review-row"><span class="pa-review-lbl">提交时间</span><span class="pa-review-val">{{ reviewItem.createTime }}</span></div>
        </div>
        <el-form :model="reviewForm" ref="reviewFormRef">
          <el-form-item label="审批意见" prop="reviewComment" class="pa-form-item">
            <el-input v-model="reviewForm.reviewComment" type="textarea" :rows="3"
                      placeholder="请输入审批意见（可选）" maxlength="500" />
          </el-form-item>
        </el-form>
      </div>
      <div slot="footer">
        <el-button @click="showReviewDialog = false">取消</el-button>
        <el-button type="danger" @click="handleReject" :loading="reviewLoading" icon="el-icon-close">拒绝</el-button>
        <el-button type="primary" @click="handleApprove" :loading="reviewLoading" icon="el-icon-check">通过</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getPendingRequestList, getReviewedRequestList, approveRequest, rejectRequest, checkIsReviewer } from '@/api/jwmap/data-access'
import fetchStateMixin from '../mixins/fetchStateMixin'
import errorHandlerMixin from '../mixins/errorHandlerMixin'

export default {
  name: 'JwDataAccessApproval',
  mixins: [fetchStateMixin, errorHandlerMixin],
  data() {
    return {
      notReviewer: false,
      activeTab: 'pending',

      pendingList: [],
      pendingTotal: 0,
      pendingPageNum: 1,
      pendingPageSize: 10,

      reviewedList: [],
      reviewedTotal: 0,
      reviewedPageNum: 1,
      reviewedPageSize: 10,

      showReviewDialog: false,
      reviewItem: null,
      reviewForm: { reviewComment: '' },
      reviewLoading: false,
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
    this.checkReviewer()
  },
  beforeDestroy() {
    this._active = false
    if (this._reviewerTimer) { clearTimeout(this._reviewerTimer); this._reviewerTimer = null }
  },
  methods: {
    statusClass(s) {
      const map = { '0': 'pill-pending', '1': 'pill-approved', '2': 'pill-rejected', '3': 'pill-cancelled', '4': 'pill-expired' }
      return map[s] || ''
    },
    statusLabel(s) {
      const map = { '0': '待审批', '1': '已通过', '2': '已拒绝', '3': '已撤销', '4': '已过期' }
      return map[s] || s
    },
    checkReviewer() {
      checkIsReviewer().then(res => {
        if (res.data) {
          this.notReviewer = false
          this.fetchPendingList()
          this.fetchReviewedList()
        } else {
          this.notReviewer = true
          this.$message.warning('当前用户不是数据审核员，无审批权限')
        }
      }).catch(() => {
        if (!this._active) return
        this.handleError({ message: '无法验证审核员身份' }, '验证身份', false)
        this._reviewerTimer = setTimeout(() => this.checkReviewer(), 3000)
      })
    },
    async fetchPendingList() {
      const res = await this.withAsyncState('pendingList', getPendingRequestList,
        { pageNum: this.pendingPageNum, pageSize: this.pendingPageSize }, '加载待审批列表')
      if (res) {
        this.pendingList = res.rows || []
        this.pendingTotal = res.total || 0
      }
    },
    async fetchReviewedList() {
      const res = await this.withAsyncState('reviewedList', getReviewedRequestList,
        { pageNum: this.reviewedPageNum, pageSize: this.reviewedPageSize }, '加载已审批列表')
      if (res) {
        this.reviewedList = res.rows || []
        this.reviewedTotal = res.total || 0
      }
    },
    handleReview(row) {
      this.reviewItem = row
      this.reviewForm = { reviewComment: '' }
      this.showReviewDialog = true
    },
    handleApprove() {
      this.$confirm('确认通过该申请？', '提示', { type: 'info' }).then(() => {
        this.reviewLoading = true
        approveRequest({ requestId: this.reviewItem.requestId, reviewComment: this.reviewForm.reviewComment }).then(() => {
          this.$message.success('已通过')
          this.showReviewDialog = false
          this.fetchPendingList()
          this.fetchReviewedList()
          this.$root.$emit('pending-count-changed')
        }).catch(err => {
          this.handleError(err, '审批通过')
        }).finally(() => { this.reviewLoading = false })
      }).catch(() => {})
    },
    handleReject() {
      this.$confirm('确认拒绝该申请？', '提示', { type: 'warning' }).then(() => {
        this.reviewLoading = true
        rejectRequest({ requestId: this.reviewItem.requestId, reviewComment: this.reviewForm.reviewComment }).then(() => {
          this.$message.success('已拒绝')
          this.showReviewDialog = false
          this.fetchPendingList()
          this.fetchReviewedList()
          this.$root.$emit('pending-count-changed')
        }).catch(err => {
          this.handleError(err, '审批拒绝')
        }).finally(() => { this.reviewLoading = false })
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.page-approval { padding: 0 20px; }
.pa-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 20px; padding: 20px 0 0;
}
.pa-header-left { display: flex; flex-direction: column; gap: 4px; }
.pa-title { font-size: 20px; font-weight: 700; color: #232845; margin: 0; display: flex; align-items: center; gap: 8px; }
.pa-title i { color: #e6a23c; font-size: 22px; }
.pa-subtitle { font-size: 13px; color: #888; }
.pa-alert { border-radius: 8px; margin-bottom: 16px; }
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
/* 表格 */
.pa-table { width: 100%; }
.pa-table >>> .el-table__header th {
  background: #f8f9fd; color: #444; font-weight: 600; font-size: 14px;
}
.pa-table >>> .el-table__body td { font-size: 14px; padding: 10px 0; }
.pa-table >>> .el-table__row { height: 48px; }
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
.pa-act-review { color: #4f6ef6; font-size: 14px; }
/* 对话框 */
.pa-dialog >>> .el-dialog__header { padding: 20px 24px 16px; border-bottom: 1px solid #f0f0f0; }
.pa-dialog >>> .el-dialog__title { font-size: 17px; font-weight: 700; color: #232845; }
.pa-dialog >>> .el-dialog__body { padding: 20px 24px; }
.pa-dialog >>> .el-dialog__footer { padding: 12px 24px 20px; border-top: 1px solid #f0f0f0; }
.pa-review-card {
  background: #f8f9fd; border-radius: 8px; padding: 4px 14px; margin-bottom: 16px;
}
.pa-review-row { display: flex; align-items: baseline; gap: 12px; padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
.pa-review-row:last-child { border-bottom: none; }
.pa-review-lbl { font-size: 13px; color: #888; width: 80px; flex-shrink: 0; }
.pa-review-val { font-size: 14px; color: #303651; font-weight: 500; }
.pa-form-item { margin-bottom: 0; }
</style>
