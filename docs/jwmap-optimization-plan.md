# jw-map 系统优化实施方案

> 依据 `待优化.txt` 文档，按顺序完善网点布局优化系统。
> 核心目标：将系统从"数据展示工具"升级为"选址决策分析平台"。

---

## Phase A: 前后端冗余代码整理

- [x] **A1** 清理 `map/index.vue` 调试代码（G1/G2 硬编码网格 + debug 导出）
- [x] **A2** 合并重复 Mapper 方法（8个 Mapper 中的 upsert/insert/update 重复方法）
- [x] **A3** 清理 `GridComputeServiceImpl` — 确认 `branchInfoMapper` 仍在 `selectDistinctCities()` 中使用，无需删除

---

## Phase B: 配置迁移到模块

- [x] **B1** 新建 `JwMapSecurityConfig.java`，从 `ruoyi-framework/SecurityConfig.java` 移除 jwmap 规则

---

## Phase C: 低代码灵活性整理

- [x] **C1** 新增 `jw_score_category_config` 表 + 种子数据（22个衍生指标 → 4个分类映射）
- [x] **C2** 新增实体 `JwScoreCategoryConfig` + Mapper + XML
- [x] **C3** 改造 `BranchComputeServiceImpl` 硬编码分类改为从 DB 动态加载
- [x] **C4** 新建 `JwConfigController`（分类/指标查询 API）

---

## Phase D: 四象限功能

- [x] **D1** 后端 API `GET /jwmap/data/quadrant/{city}/{year}`（三表 JOIN + 中位数分类）
- [x] **D2** 新增 `JwBranchInfoMapper` 象限数据查询 SQL
- [x] **D3** 新建 `QuadrantChart.vue`（ECharts 散点图，四象限颜色+中位线）
- [x] **D4** 集成到 `tianditu.vue` + `data.js` + `TopToolbar` 切换按钮

---

## Phase E: 前端展示优化

- [x] **E1** `SidebarPanel.vue` 完全重构（网点/网格两种模式新布局）
- [x] **E2** 新建 `BranchInfoCard.vue` + `GridInfoCard.vue`
- [x] **E3** 新建 `RankBadge.vue` + `QuadrantPosition.vue`
- [x] **E4** 新建 `DetailPanel.vue`（右侧滑出）
- [x] **E5** 新建 `ThreeColumnCards.vue`（人口/企业/商圈）
- [x] **E6** 新建 `PercentageBar.vue`（百分比+悬浮）
- [x] **E7** 改造 `BranchScores.vue` + `IndicatorSection.vue` + `RankingList.vue`（之前已完成）
- [x] **E8** 后端新增 4 个 API（internal ranking / district ranking / pillar score / topScore）

---

## Phase F: 分维度展示统计内容

- [x] **F1** 后端 API `GET /jwmap/data/dimension/stats/{city}/{year}`
- [x] **F2** 新建 `DimensionStats.vue` + TopToolbar 切换按钮

---

## Phase G: 三聚焦分类排名

- [x] **G1** 后端 API `GET /jwmap/data/ranking/threeFocus/{city}/{year}`
- [x] **G2** 三聚焦排名数据已集成到网格侧边栏（人口/企业/商圈）

---

## Phase H: 同业位置距离展示

- [x] **H1** 后端 API `GET /jwmap/data/peerBank/distance/{branchId}`（Haversine 距离）
- [x] **H2** 新建 `PeerBankSection.vue`，集成到网点侧边栏

---

## Phase I: 周围网点距离展示

- [x] **I1** 后端 API `GET /jwmap/data/branch/nearby/{branchId}`（Haversine 距离）
- [x] **I2** 侧边栏网点模式下展示周围网点距离列表

---

## 验证方式

1. **后端**: `mvn clean install -DskipTests` 成功
2. **前端**: `npm run build` 成功
3. **功能**: 导入数据 → 计算 → 地图页面各模块正确展示
