<template>
  <div class="app-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="我的申请" name="myList">
        <el-row style="margin-bottom:12px;">
          <el-button type="primary" size="small" @click="showNewForm = true">新建申请</el-button>
        </el-row>
        <el-table :data="requestList" v-loading="loading" stripe size="small">
          <el-table-column label="目标支行" prop="targetDeptName" min-width="140" />
          <el-table-column label="事由" prop="reason" min-width="200" show-overflow-tooltip />
          <el-table-column label="有效期" min-width="160">
            <template slot-scope="{ row }">
              <span v-if="row.status === '1' && row.validDateFrom">
                {{ row.validDateFrom }} ~ {{ row.validDateTo }}
              </span>
              <span v-else-if="row.validDays">{{ row.validDays }}天</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template slot-scope="{ row }">
              <dict-tag :options="statusOptions" :value="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="审核人" prop="reviewerName" width="100" />
          <el-table-column label="审核时间" prop="reviewTime" width="150" />
          <el-table-column label="操作" width="100">
            <template slot-scope="{ row }">
              <el-button v-if="row.status === '0'" type="text" size="small"
                         @click="handleCancel(row.requestId)">撤销</el-button>
              <el-button v-else type="text" size="small"
                         @click="showDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <pagination v-show="total > 0" :total="total" :page.sync="pageNum" :limit.sync="pageSize"
                    @pagination="fetchList" />
      </el-tab-pane>
    </el-tabs>

    <!-- 新建申请对话框 -->
    <el-dialog title="新建数据查看申请" :visible.sync="showNewForm" width="520px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="目标支行" prop="targetDeptId">
          <treeselect
            v-model="form.targetDeptId"
            :options="deptTree"
            :normalizer="node => ({ id: node.id, label: node.label, children: node.children || [] })"
            placeholder="请选择目标支行（支行级节点）"
            style="width:100%"
          />
          <span class="form-tip">可选市行或支行。审核人由目标机构的上级机构自动筛选</span>
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
                     :loading="reviewersLoading">
            <el-option v-for="r in reviewers" :key="r.userId || r.userid"
                       :label="r.nickName || r.nickname || r.userName || r.username"
                       :value="r.userId || r.userid" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="showNewForm = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">提交</el-button>
      </div>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog title="申请详情" :visible.sync="showDetailDialog" width="500px">
      <div v-if="detailData">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="目标支行">{{ detailData.targetDeptName }}</el-descriptions-item>
          <el-descriptions-item label="申请事由">{{ detailData.reason }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <dict-tag :options="statusOptions" :value="detailData.status" />
          </el-descriptions-item>
          <el-descriptions-item label="审核人">{{ detailData.reviewerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核意见">{{ detailData.reviewComment || '-' }}</el-descriptions-item>
          <el-descriptions-item label="有效期" v-if="detailData.validDateFrom">
            {{ detailData.validDateFrom }} ~ {{ detailData.validDateTo }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { submitAccessRequest, getMyRequestList, cancelRequest, getRequestDetail, resolveBranchDept, getReviewers } from '@/api/jwmap/data-access'
import { getDeptTree } from '@/api/system/dept'
import Treeselect from '@riophae/vue-treeselect'
import '@riophae/vue-treeselect/dist/vue-treeselect.css'

export default {
  name: 'JwDataAccessRequest',
  components: { Treeselect },
  dicts: ['jw_access_status'],
  data() {
    return {
      activeTab: 'myList',
      requestList: [],
      total: 0,
      pageNum: 1,
      pageSize: 10,
      loading: false,

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
      deptTree: []
    }
  },
  created() {
    this.fetchList()
    this.loadDeptTree()
  },
  watch: {
    '$route.query.branchId': {
      handler(branchId) {
        if (!branchId) return
        // 等 deptTree 加载完成后自动填充
        let retries = 0
        const tryFill = () => {
          if (this.deptTree.length > 0) {
            resolveBranchDept(branchId).then(res => {
              const data = res.data || {}
              if (data.deptId) {
                this.form.targetDeptId = data.deptId
                this.showNewForm = true
              }
            }).catch(() => {})
          } else if (retries++ < 30) {
            setTimeout(tryFill, 100)
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
  methods: {
    fetchList() {
      this.loading = true
      getMyRequestList({ pageNum: this.pageNum, pageSize: this.pageSize }).then(res => {
        this.requestList = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadDeptTree() {
      // 用部门树接口获取省行-市行-支行的树形结构
      return getDeptTree().then(res => {
        this.deptTree = res.data || []
      }).catch(() => {
        this.deptTree = []
      })
    },
    loadReviewers(targetDeptId) {
      this.reviewersLoading = true
      getReviewers(targetDeptId || null).then(res => {
        this.reviewers = res.data || []
      }).catch(() => {
        this.reviewers = []
      }).finally(() => { this.reviewersLoading = false })
    },
    handleSubmit() {
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        this.submitting = true
        submitAccessRequest({ targetDeptId: this.form.targetDeptId, reason: this.form.reason, validDays: this.form.validDays, reviewerId: this.form.reviewerId }).then(res => {
          this.$message.success('申请已提交')
          this.showNewForm = false
          this.form = { targetDeptId: null, reason: '', validDays: 30, reviewerId: null }
          this.fetchList()
        }).catch(err => {
          this.$message.error(err.msg || '提交失败')
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
    }
  }
}
</script>
