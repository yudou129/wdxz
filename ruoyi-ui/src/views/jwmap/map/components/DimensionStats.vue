<template>
  <transition name="quad-slide">
    <div v-if="visible" class="dim-panel">
      <div class="dim-header">
        <span class="dim-title"><i class="el-icon-pie-chart" /> 维度统计</span>
        <el-button type="text" icon="el-icon-close" size="mini" class="close-btn" @click="$emit('close')" />
      </div>
      <div class="dim-body">
        <el-radio-group v-model="curDim" size="mini" @change="onDimChange">
          <el-radio-button label="district">区县</el-radio-button>
          <el-radio-button label="branchType">业态</el-radio-button>
          <el-radio-button label="propertyRight">产权</el-radio-button>
        </el-radio-group>
        <div class="dim-list">
          <div v-for="item in data" :key="item.dimension" class="dim-item">
            <div class="dim-name">{{ item.dimension }}</div>
            <div class="dim-bar-wrap">
              <div class="dim-bar" :style="{ width: barPct(item.avgScore) + '%' }"></div>
            </div>
            <div class="dim-info">
              <span class="dim-count">{{ item.count }}网点</span>
              <span class="dim-avg">{{ fmt(item.avgScore) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>
<script>
export default {
  name: 'DimensionStats',
  props: { visible: Boolean, data: { type: Array, default: () => [] } },
  data() { return { curDim: 'district' } },
  methods: {
    onDimChange(v) { this.$emit('dim-change', v) },
    barPct(v) { return Math.min((v || 0) * 100, 100) },
    fmt(v) { return typeof v === 'number' ? v.toFixed(4) : '-' }
  }
}
</script>
<style scoped>
.dim-panel {
  position: absolute; right: 12px; top: 60px; width: 300px;
  isolation: isolate; overflow: hidden; border-radius: 10px;
  border: 1px solid rgba(255,255,255,0.28);
  background: linear-gradient(135deg, rgba(255,255,255,0.28), rgba(255,255,255,0.08)), rgba(255,255,255,0.10);
  backdrop-filter: blur(22px) saturate(170%) contrast(1.04);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.44), 0 8px 32px rgba(79,110,246,0.08), 0 1px 4px rgba(0,0,0,0.05);
  z-index: 1000;
}
.dim-header { padding: 10px 14px; border-bottom: 1px solid rgba(79,110,246,0.08); display: flex; justify-content: space-between; align-items: center; }
.dim-title { font-weight: 700; font-size: 13px; color: #232845; }
.dim-title i { color: #4f6ef6; margin-right: 4px; }
.close-btn { color: #8c95a8; }
.dim-body { padding: 10px 14px; max-height: 320px; overflow-y: auto; }
.dim-list { margin-top: 10px; }
.dim-item { display: flex; align-items: center; gap: 8px; padding: 5px 0; }
.dim-name { width: 48px; font-size: 12px; color: #666; flex-shrink: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dim-bar-wrap { flex: 1; height: 6px; background: #eef0f5; border-radius: 3px; overflow: hidden; }
.dim-bar { height: 100%; border-radius: 3px; background: linear-gradient(90deg, #4f6ef6, #8fabff); }
.dim-info { display: flex; gap: 8px; font-size: 11px; }
.dim-count { color: #aaa; }
.dim-avg { color: #4f6ef6; font-weight: 600; width: 50px; text-align: right; }
</style>
