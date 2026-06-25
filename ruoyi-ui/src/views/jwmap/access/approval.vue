<template>
  <div class="app-container">
    <el-alert v-if="notReviewer" title="暂无审批权限"
             description="当前用户不是数据审核员，请联系管理员分配 data_reviewer 角色"
             type="warning" show-icon style="margin-bottom:16px;" />
    <el-tabs v-model="activeTab">
      <el-tab-pane label="待审批" name="pending">
        <el-table :data="pendingList" v-loading="pendingLoading" stripe size="small">
          <el-table-column label="申请人" prop="applicantName" width="100" />
          <el-table-column label="目标支行" prop="targetDeptName" min-width="140" />
          <el-table-column label="事由" prop="reason" min-width="200" show-overflow-tooltip />
          <el-table-column label="提交时间" prop="createTime" width="150" />
          <el-table-column label="有效期" width="80">
            <template slot-scope="{ row }">{{ row.validDays }}天</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template slot-scope="{ row }">
              <el-button type="text" size="small" @click="handleReview(row)">审核</el-button>
            </template>
          </el-table-column>
        </el-table>
        <pagination v-show="pendingTotal > 0" :total="pendingTotal"
                    :page.sync="pendingPageNum" :limit.sync="pendingPageSize"
                    @pagination="fetchPendingList" />
      </el-tab-pane>

      <el-tab-pane label="已审批" name="reviewed">
        <el-table :data="reviewedList" v-loading="reviewedLoading" stripe size="small">
          <el-table-column label="申请人" prop="applicantName" width="100" />
          <el-table-column label="目标支行" prop="targetDeptName" min-width="140" />
          <el-table-column label="事由" prop="reason" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template slot-scope="{ row }">
              <dict-tag :options="statusOptions" :value="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="审批意见" prop="reviewComment" width="150" show-overflow-tooltip />
          <el-table-column label="审批时间" prop="reviewTime" width="150" />
        </el-table>
        <pagination v-show="reviewedTotal > 0" :total="reviewedTotal"
                    :page.sync="reviewedPageNum" :limit.sync="reviewedPageSize"
                    @pagination="fetchReviewedList" />
      </el-tab-pane>
    </el-tabs>

    <!-- 审核对话框 -->
    <el-dialog title="审批申请" :visible.sync="showReviewDialog" width="520px">
      <div v-if="reviewItem">
        <el-descriptions :column="1" border size="small" style="margin-bottom:16px;">
          <el-descriptions-item label="申请人">{{ reviewItem.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="目标支行">{{ reviewItem.targetDeptName }}</el-descriptions-item>
          <el-descriptions-item label="申请事由">{{ reviewItem.reason }}</el-descriptions-item>
          <el-descriptions-item label="有效期">{{ reviewItem.validDays }}天</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ reviewItem.createTime }}</el-descriptions-item>
        </el-descriptions>
        <el-form :model="reviewForm" ref="reviewFormRef">
          <el-form-item label="审批意见" prop="reviewComment">
            <el-input v-model="reviewForm.reviewComment" type="textarea" :rows="3"
                      placeholder="请输入审批意见（可选）" maxlength="500" />
          </el-form-item>
        </el-form>
      </div>
      <div slot="footer">
        <el-button @click="showReviewDialog = false">取消</el-button>
        <el-button type="danger" @click="handleReject" :loading="reviewLoading">拒绝</el-button>
        <el-button type="primary" @click="handleApprove" :loading="reviewLoading">通过</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getPendingRequestList, getReviewedRequestList, approveRequest, rejectRequest } from '@/api/jwmap/data-access'
import { checkIsReviewer } from '@/api/jwmap/data-access'

export default {
  name: 'JwDataAccessApproval',
  data() {
    return {
      notReviewer: false,
      activeTab: 'pending',

      pendingList: [],
      pendingTotal: 0,
      pendingPageNum: 1,
      pendingPageSize: 10,
      pendingLoading: false,

      reviewedList: [],
      reviewedTotal: 0,
      reviewedPageNum: 1,
      reviewedPageSize: 10,
      reviewedLoading: false,

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
      this.notReviewer = true
    })
  },
  methods: {
    fetchPendingList() {
      this.pendingLoading = true
      getPendingRequestList({ pageNum: this.pendingPageNum, pageSize: this.pendingPageSize }).then(res => {
        this.pendingList = res.rows || []
        this.pendingTotal = res.total || 0
      }).finally(() => { this.pendingLoading = false })
    },
    fetchReviewedList() {
      this.reviewedLoading = true
      getReviewedRequestList({ pageNum: this.reviewedPageNum, pageSize: this.reviewedPageSize }).then(res => {
        this.reviewedList = res.rows || []
        this.reviewedTotal = res.total || 0
      }).finally(() => { this.reviewedLoading = false })
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
          this.$message.error(err.msg || '操作失败')
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
          this.$message.error(err.msg || '操作失败')
        }).finally(() => { this.reviewLoading = false })
      }).catch(() => {})
    }
  }
}
</script>
