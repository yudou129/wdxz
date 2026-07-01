<template>
  <div class="score-progress">
    <!-- 得分标签行 -->
    <div class="sp-labels">
      <span class="sp-label-me">
        <span class="sp-dot-me"></span>得分 {{ fmtScore(myScore) }}
      </span>
      <span class="sp-label-district">
        <span class="sp-dot-district"></span>区县最高 {{ fmtScore(districtTopScore) }}
      </span>
      <span class="sp-label-city">
        <span class="sp-dot-city"></span>全市最高 {{ fmtScore(cityTopScore) }}
      </span>
    </div>
    <!-- 进度条 -->
    <div class="sp-track">
      <div class="sp-fill" :style="{ width: fillPct + '%' }"></div>
      <!-- 行政区最高标记线 -->
      <div v-if="districtTopScore > 0 && yieldPct(districtTopScore) > 0 && yieldPct(districtTopScore) < 100"
        class="sp-marker sp-marker-district"
        :style="{ left: yieldPct(districtTopScore) + '%' }">
      </div>
      <!-- 市最高终点标记 -->
      <div v-if="cityTopScore > 0"
        class="sp-marker sp-marker-city"
        :style="{ left: '100%' }">
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ScoreProgressBar',
  props: {
    myScore: { type: Number, default: 0 },
    districtTopScore: { type: Number, default: 0 },
    cityTopScore: { type: Number, default: 0 }
  },
  computed: {
    fillPct() {
      if (!this.cityTopScore || this.cityTopScore <= 0) return 0
      return Math.min(Math.max(this.myScore / this.cityTopScore, 0), 1) * 100
    }
  },
  methods: {
    fmtScore(v) { return typeof v === 'number' ? v.toFixed(3) : '0.000' },
    yieldPct(v) {
      if (!this.cityTopScore || this.cityTopScore <= 0) return 0
      return Math.min(Math.max(v / this.cityTopScore, 0), 1) * 100
    }
  }
}
</script>

<style scoped>
.score-progress {
  background: rgba(255,255,255,0.5);
  border-radius: 8px;
  padding: 10px 12px 12px;
  margin: 10px 0 6px;
}
/* 上方标签行 */
.sp-labels {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 500;
  gap: 6px;
}
.sp-label-me, .sp-label-district, .sp-label-city {
  display: flex;
  align-items: center;
  gap: 4px;
}
.sp-dot-me, .sp-dot-district, .sp-dot-city {
  display: inline-block;
  width: 8px; height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.sp-dot-me { background: #4f6ef6; }
.sp-dot-district { background: #52c41a; }
.sp-dot-city { background: #f0a050; }
.sp-label-district { color: #52c41a; }
.sp-label-city { color: #f0a050; }

/* 进度条 */
.sp-track {
  position: relative;
  height: 10px;
  background: rgba(0,0,0,0.06);
  border-radius: 5px;
  overflow: visible;
}
.sp-fill {
  height: 100%;
  border-radius: 5px;
  background: linear-gradient(90deg, #4f6ef6 0%, #6b8af8 100%);
  transition: width 0.4s cubic-bezier(0.25,0.46,0.45,0.94);
}
/* 标记竖线 */
.sp-marker {
  position: absolute;
  top: -3px;
  bottom: -3px;
  width: 2px;
  transform: translateX(-50%);
  border-radius: 1px;
  z-index: 2;
}
.sp-marker-district {
  background: #52c41a;
  box-shadow: 0 0 0 2px rgba(82,196,26,0.15);
}
.sp-marker-city {
  background: #f0a050;
  box-shadow: 0 0 0 2px rgba(240,160,80,0.15);
}
</style>
