<template>
  <div class="branch-info-card">
    <div class="card-header">
      <span class="branch-name">{{ branch.secondaryBranch || '-' }}</span>
      <el-tag size="mini" effect="plain" type="info">{{ branch.branchType || '未知' }}</el-tag>
    </div>
    <div class="info-row"><span class="ilabel">行长</span><span class="ivalue">{{ branch.branchManager || '-' }}</span></div>
    <div class="info-row"><span class="ilabel">地址</span><span class="ivalue">{{ branch.address || '-' }}</span></div>
    <div class="info-row"><span class="ilabel">产权</span>
      <span class="ivalue" :class="{ warn: branch.propertyRight === '租赁' }">{{ branch.propertyRight || '-' }}</span>
    </div>
    <el-button type="text" size="mini" icon="el-icon-zoom-in" class="zoom-btn" @click="$emit('zoom')">定位</el-button>

    <el-collapse v-model="moreOpen" class="more-collapse">
      <el-collapse-item name="more">
        <template #title><span class="more-title">更多信息</span></template>
        <div class="more-grid">
          <div><span class="mlabel">面积</span><span class="mvalue">{{ branch.totalArea }}m&sup2;</span></div>
          <div><span class="mlabel">个金经理</span><span class="mvalue">{{ branch.personalManager }}人</span></div>
          <div><span class="mlabel">对公经理</span><span class="mvalue">{{ branch.corporateManager }}人</span></div>
          <div><span class="mlabel">现金柜</span><span class="mvalue">{{ branch.cashCounter }}个</span></div>
          <div><span class="mlabel">非现金柜</span><span class="mvalue">{{ branch.nonCashCounter }}个</span></div>
          <div><span class="mlabel">最近装修</span><span class="mvalue">{{ branch.lastRenovation || '-' }}</span></div>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>
<script>
export default {
  name: 'BranchInfoCard',
  props: { branch: Object },
  data() { return { moreOpen: [] } }
}
</script>
<style scoped>
.branch-info-card { margin-bottom: 12px; }
.card-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.branch-name { font-size: 14px; font-weight: 700; color: #232845; }
.info-row { display: flex; align-items: baseline; padding: 2px 0; font-size: 12px; }
.ilabel { color: #999; width: 32px; flex-shrink: 0; }
.ivalue { color: #444; }
.ivalue.warn { color: #e6a23c; }
.zoom-btn { margin: 4px 0 0; color: #4f6ef6; }
.more-collapse { margin-top: 4px; border: none; }
.more-collapse >>> .el-collapse-item__header { border: none; height: 28px; line-height: 28px; font-size: 12px; }
.more-collapse >>> .el-collapse-item__wrap { border: none; }
.more-title { color: #4f6ef6; font-size: 12px; }
.more-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 2px 12px; }
.mlabel { font-size: 11px; color: #999; }
.mvalue { font-size: 12px; color: #444; margin-left: 4px; font-weight: 500; }
</style>
