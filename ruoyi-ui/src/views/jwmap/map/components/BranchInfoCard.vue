<template>
  <div class="branch-info-card">
    <!-- 头部：名称 + 标签 + 定位 -->
    <div class="bi-header">
      <div class="bi-title-group">
        <span class="bi-name">{{ branch.secondaryBranch || '-' }}</span>
        <el-tag size="mini" :type="branch.branchType === '综合网点' ? 'primary' : ''" effect="plain" class="bi-tag">
          {{ branch.branchType || '未知' }}
        </el-tag>
      </div>
      <el-button size="mini" icon="el-icon-zoom-in" circle class="bi-zoom-btn" @click="$emit('zoom')" title="定位到地图" />
    </div>

    <!-- 核心信息 2列布局 -->
    <div class="bi-grid">
      <div class="bi-cell">
        <span class="bi-cell-icon el-icon-location-outline" />
        <div class="bi-cell-body">
          <span class="bi-cell-label">地址</span>
          <span class="bi-cell-value addr">{{ branch.address || '-' }}</span>
        </div>
      </div>
      <div class="bi-cell">
        <span class="bi-cell-icon el-icon-user" />
        <div class="bi-cell-body">
          <span class="bi-cell-label">行长</span>
          <span class="bi-cell-value">{{ branch.branchManager || '-' }}</span>
        </div>
      </div>
      <div class="bi-cell">
        <span class="bi-cell-icon el-icon-s-home" />
        <div class="bi-cell-body">
          <span class="bi-cell-label">产权</span>
          <span class="bi-cell-value" :class="{ 'is-rent': branch.propertyRight === '租赁' }">
            {{ branch.propertyRight || '-' }}
            <i v-if="branch.propertyRight === '租赁'" class="el-icon-warning-outline rent-icon" />
          </span>
        </div>
      </div>
      <div class="bi-cell">
        <span class="bi-cell-icon el-icon-office-building" />
        <div class="bi-cell-body">
          <span class="bi-cell-label">所属支行</span>
          <span class="bi-cell-value">{{ branch.primaryBranch || '-' }}</span>
        </div>
      </div>
    </div>

    <!-- 详细信息折叠 -->
    <el-collapse v-model="moreOpen" class="bi-collapse">
      <el-collapse-item name="more">
        <template #title>
          <div class="bi-more-trigger">
            <span>详细信息</span>
            <span class="bi-more-badge">6项</span>
          </div>
        </template>
        <div class="bi-detail-grid">
          <div class="bi-detail-item">
            <span class="bd-label">面积</span>
            <span class="bd-value">{{ branch.totalArea || '-' }}<small>m²</small></span>
          </div>
          <div class="bi-detail-item">
            <span class="bd-label">区县</span>
            <span class="bd-value">{{ branch.districtName || '-' }}</span>
          </div>
          <div class="bi-detail-item">
            <span class="bd-label">个金经理</span>
            <span class="bd-value">{{ branch.personalManager || 0 }}<small>人</small></span>
          </div>
          <div class="bi-detail-item">
            <span class="bd-label">对公经理</span>
            <span class="bd-value">{{ branch.corporateManager || 0 }}<small>人</small></span>
          </div>
          <div class="bi-detail-item">
            <span class="bd-label">现金柜</span>
            <span class="bd-value">{{ branch.cashCounter || 0 }}<small>个</small></span>
          </div>
          <div class="bi-detail-item">
            <span class="bd-label">非现金柜</span>
            <span class="bd-value">{{ branch.nonCashCounter || 0 }}<small>个</small></span>
          </div>
          <div class="bi-detail-item" v-if="branch.lastRenovation">
            <span class="bd-label">最近装修</span>
            <span class="bd-value">{{ branch.lastRenovation }}</span>
          </div>
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
.branch-info-card {
  background: linear-gradient(135deg, #f0f4ff 0%, #f8faff 100%);
  border: 1px solid rgba(79, 110, 246, 0.12);
  border-radius: 10px;
  padding: 14px 14px 4px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(79, 110, 246, 0.06);
  transition: box-shadow 0.25s ease;
}
.branch-info-card:hover {
  box-shadow: 0 4px 16px rgba(79, 110, 246, 0.12);
}

/* ===== 头部 ===== */
.bi-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}
.bi-title-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}
.bi-name {
  font-size: 16px;
  font-weight: 700;
  color: #232845;
  line-height: 1.3;
}
.bi-tag {
  font-size: 11px;
  font-weight: 500;
  border-radius: 4px;
  line-height: 1.4;
}
.bi-zoom-btn {
  flex-shrink: 0;
  color: #4f6ef6;
  background: rgba(79, 110, 246, 0.06);
  border-color: rgba(79, 110, 246, 0.15);
  transition: background 0.2s, transform 0.2s;
}
.bi-zoom-btn:hover {
  background: rgba(79, 110, 246, 0.14);
  transform: scale(1.08);
}

/* ===== 信息网格 ===== */
.bi-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.bi-cell {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 4px 6px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.5);
  min-width: 0;
}
.bi-cell-icon {
  font-size: 14px;
  color: #4f6ef6;
  margin-top: 2px;
  flex-shrink: 0;
}
.bi-cell-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.bi-cell-label {
  font-size: 10px;
  color: #888;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  line-height: 1.3;
}
.bi-cell-value {
  font-size: 13px;
  color: #333;
  font-weight: 500;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bi-cell-value.addr {
  white-space: normal;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.bi-cell-value.is-rent {
  color: #e6a23c;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.rent-icon {
  font-size: 13px;
  color: #e6a23c;
}

/* ===== 折叠面板 ===== */
.bi-collapse {
  margin-top: 6px;
  border: none;
  background: transparent;
}
.bi-collapse >>> .el-collapse-item__header {
  border: none;
  height: 32px;
  line-height: 32px;
  padding: 0 4px;
  background: transparent;
}
.bi-collapse >>> .el-collapse-item__wrap {
  border: none;
  background: transparent;
}
.bi-collapse >>> .el-collapse-item__content {
  padding-bottom: 6px;
}
.bi-more-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: #4f6ef6;
  user-select: none;
}
.bi-more-badge {
  font-size: 11px;
  font-weight: 400;
  color: #888;
  background: rgba(79, 110, 246, 0.06);
  padding: 0 8px;
  border-radius: 10px;
  line-height: 1.6;
}

/* ===== 详细内容网格 ===== */
.bi-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 12px;
  padding: 6px 8px 2px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 8px;
}
.bi-detail-item {
  display: flex;
  justify-content: space-between;
  padding: 3px 0;
  font-size: 13px;
  border-bottom: 1px solid rgba(79, 110, 246, 0.04);
}
.bi-detail-item:last-child,
.bi-detail-item:nth-last-child(2) {
  border-bottom: none;
}
.bd-label {
  color: #888;
  flex-shrink: 0;
}
.bd-value {
  color: #333;
  font-weight: 500;
  text-align: right;
}
.bd-value small {
  font-weight: 400;
  color: #888;
  margin-left: 2px;
}
</style>
