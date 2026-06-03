<template>
  <div class="import-panel">
    <el-form inline size="small">
      <el-form-item v-if="!hideCity" label="城市">
        <el-select v-model="innerCity" placeholder="选择城市" style="width:140px" filterable allow-create :disabled="loading" :no-data-text="cityList.length ? '无匹配城市' : '暂无数据，请输入城市名'">
          <el-option v-for="c in cityList" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="showDataSource" label="数据来源">
        <el-select v-model="innerDataSource" style="width:120px" :disabled="loading">
          <el-option label="网点信息" value="网点信息" />
          <el-option label="存量网点" value="存量网点" />
        </el-select>
      </el-form-item>
      <el-form-item label="文件">
        <el-upload :auto-upload="false" :limit="1" :on-change="handleFileChange" accept=".xlsx,.xls" :disabled="loading">
          <el-button size="small" icon="el-icon-upload2">选择Excel文件</el-button>
        </el-upload>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" size="small" @click="handleSubmit" :loading="loading" icon="el-icon-upload">{{ loading ? '导入中...' : '开始导入' }}</el-button>
      </el-form-item>
    </el-form>
    <div class="tips-text">
      <i class="el-icon-info" /> <slot name="tips"></slot>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ImportPanel',
  props: {
    label: { type: String, default: '' },
    city: { type: String, default: '' },
    cityList: { type: Array, default: () => [] },
    hideCity: { type: Boolean, default: false },
    showDataSource: { type: Boolean, default: false },
    dataSource: { type: String, default: '网点信息' },
    loading: { type: Boolean, default: false }
  },
  data() {
    return {
      file: null,
      innerCity: this.city || '',
      innerDataSource: this.dataSource || '网点信息'
    }
  },
  watch: {
    city(v) { this.innerCity = v },
    dataSource(v) { this.innerDataSource = v }
  },
  methods: {
    handleFileChange(file) {
      this.file = file.raw || file
    },
    handleSubmit() {
      if (!this.file) { this.$message.warning('请选择文件'); return }
      if (!this.hideCity && !this.innerCity) { this.$message.warning('请选择城市'); return }
      this.$emit('import', { file: this.file, city: this.innerCity, dataSource: this.innerDataSource })
    }
  }
}
</script>

<style scoped>
.import-panel {
  padding: 8px 0;
}
.tips-text {
  color: #8c95a8;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.tips-text i {
  font-size: 13px;
  color: #4f6ef6;
}
</style>
