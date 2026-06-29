<template>
  <div class="score-card" :class="size">
    <div class="sc-top">
      <span class="sc-label">{{ label }}</span>
      <span class="sc-gap" v-if="gap != null && gap > 0">距最高 {{ fmtGap(gap) }}</span>
    </div>
    <div class="sc-value">{{ typeof score === 'number' ? score.toFixed(2) : '-' }}</div>
    <div class="sc-badges" v-if="branchRank != null || cityRank != null">
      <RankBadge label="支行排名" :rank="branchRank" :total="branchTotal" />
      <RankBadge label="分行排名" :rank="cityRank" :total="cityTotal" />
    </div>
  </div>
</template>

<script>
import RankBadge from './RankBadge'

export default {
  name: 'ScoreCard',
  components: { RankBadge },
  props: {
    score: Number, rank: Number, label: String, gap: Number,
    size: { type: String, default: 'normal' },
    branchRank: Number, branchTotal: Number, cityRank: Number, cityTotal: Number
  },
  methods: {
    fmtGap(v) { return typeof v === 'number' ? v.toFixed(2) : '-' }
  }
}
</script>

<style scoped>
.score-card {
  background: linear-gradient(135deg, #f0f4ff 0%, #f8faff 100%);
  border: 1px solid rgba(79, 110, 246, 0.12);
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(79, 110, 246, 0.06);
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}
.score-card:hover {
  box-shadow: 0 4px 16px rgba(79, 110, 246, 0.12);
}
.score-card.small {
  padding: 10px 14px;
  border-radius: 8px;
}
.sc-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.sc-label {
  font-size: 13px;
  color: #444;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
.sc-gap {
  font-size: 12px;
  color: #e6a23c;
  font-weight: 500;
  background: rgba(230, 162, 60, 0.1);
  padding: 1px 8px;
  border-radius: 4px;
  font-variant-numeric: tabular-nums;
}
.sc-value {
  font-size: 32px;
  font-weight: 700;
  color: #4f6ef6;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.5px;
}
.score-card.small .sc-value {
  font-size: 24px;
}
.sc-rank {
  font-size: 14px;
  color: #444;
  margin-top: 4px;
}
.rank-num {
  color: #4f6ef6;
  font-weight: 600;
}
.sc-badges {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.sc-badges .rank-badge {
  background: rgba(255, 255, 255, 0.8);
}
</style>
