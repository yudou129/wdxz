# 网点布局优化系统 — 地图数据展示功能设计文档

> 版本: v1.0
> 日期: 2026-05-28
> 状态: 设计已确认

## 1. 概述

在天地图离线瓦片地图基础上，实现网格热力展示、网点标注、行政边界定位、数据筛选排名等数据可视化功能。

## 2. 技术约束

| 项目 | 说明 |
|---|---|
| 地图引擎 | Leaflet 1.7.1 |
| 地图底图 | 天地图离线瓦片（EPSG:3857），通过 TiandituBd09Crs 实现 BD09 坐标透明转换 |
| 坐标体系 | 用户数据统一 BD09，瓦片 WGS84，CRS 层自动转换 |
| 前端框架 | Vue 2 + Element UI |
| 页面路径 | `/jwmap/tianditu` |
| 后端 | Spring Boot 2.5 / MyBatis |

## 3. 整体架构

```
tianditu.vue（页面容器，管理状态与事件分发）
├── TopToolbar.vue（顶部浮动工具栏）
├── HeatmapLayer（纯 JS 类，非 Vue 组件，Canvas 渲染热力图）
├── SidebarPanel.vue（浮动侧边栏，380px/600px）
├── RankingList.vue（排名列表，可复用网格/网点排名）
├── BoundaryManager（已有，复用）
└── MeasureTool（已有，复用）
```

所有组件浮动覆盖在地图上，不压缩地图区域。

## 4. 页面布局

### 4.1 顶部工具栏

```
┌──────────────────────────────────────────────────┐
│  行政区: [贵州省 ▼]  一级支行: [全部 ▼]         │
│                          [🔥 热力图]             │
└──────────────────────────────────────────────────┘
```

- 行政区下拉框：省→地市→区县三级联动（复用 BoundaryManager 数据）
- 一级支行下拉框：从 branchList API 的 `primaryBranch` 字段去重
- 热力图开关按钮：emit 事件到父组件

### 4.2 侧边栏

浮动覆盖在地图左侧，不压缩地图区域。支持 380px/600px 两种宽度：

| 模式 | 宽度 | 触发条件 |
|---|---|---|
| `grid-only` | 380px | 点击网格（无网点） |
| `branch-only` | 380px | 点击网点图标 |
| `split` | 600px | 点击网格（有网点），左右分栏 |

年份选择器仅网点模式下显示。

### 4.3 排名列表

位于侧边栏底部或右下角浮动面板，前 20 名 + 分页查看更多。

## 5. 功能设计

### 5.1 行政边界展示（已有功能，需确认完整可用）

- 页面加载后显示贵州省 + 9 个地市边界（不同颜色）
- 三级联动下拉框（省→地市→区县）定位

### 5.2 热力图展示

**数据接口**：`GET /jwmap/data/grid/score/byCity/{city}`
**配色方案**：绿(0) → 黄 → 红(1) — `#00ff00 → #ffff00 → #ff0000`
**实现方式**：Canvas 自绘（`heatmapLayer.js`）

双层结构：
- **下层**：Canvas 热力叠加层，以网格中心点为圆心、siteScore 为权重，用 `createRadialGradient` 绘制渐变圆，实现颜色插值过渡
- **上层**：透明 `L.rectangle` 点击热区，与网格包围盒对齐，绑定 click 事件

交互逻辑：
- 选择地市 → 点击热力图按钮 → 加载当前地市网格热力
- 再次点击 → 隐藏热力图
- 切换地市 → 自动清除热力图
- 点击网格（透明热区）→ 弹出侧边栏

### 5.3 侧边栏数据展示

**网格视图（grid-only, 380px）**：
- 综合得分卡片：选址得分 + 全市排名
- 分项指标：人口/商业/企业三类
- 网格位置：编码 + 区县 + 查看详情链接

**网点视图（branch-only, 380px）**：
- 综合得分卡片：综合得分 + 排名
- 5 类分项得分进度条：营收/指标/客户/运营
- 基础信息：名称、地址、面积、人数、柜台数
- "查看详情" → el-dialog 弹窗展示 22 项衍生指标

**对比视图（split, 600px）**：
- 左侧：网格资源（得分 + 指标 + 位置）
- 右侧：网点经营（得分 + 分项 + 基础信息）

### 5.4 网点标注

- 自定义 DivIcon：红底白"工" 24×24px
- `L.layerGroup` 统一管理，支持按支行筛选

### 5.5 排名与筛选

- 排名列表组件（网格/网点共用）：前 20 + 分页
- 点击排名项 → flyTo 定位 + 侧边栏更新
- 行政区/支行筛选联动

## 6. 后端 API 设计

| 接口 | 方法 | 说明 |
|---|---|---|
| `/jwmap/data/grid/score/byCity/{city}` | GET | 城市所有网格得分列表 |
| `/jwmap/data/grid/indicators/{gridCode}` | GET | 单个网格全部分项指标值 |
| `/jwmap/data/branch/score/detail/{branchId}/{year}` | GET | 单个网点 5 类分类得分 |
| `/jwmap/data/grid/ranking/{city}` | GET | 网格排名（分页） |
| `/jwmap/data/branch/ranking/{city}/{year}` | GET | 网点排名（分页） |
| `/jwmap/data/grid/branches/{gridCode}` | GET | 网格内的网点列表 |

分页参数遵循 RuoYi `PageDomain` 规范（pageNum, pageSize），城市参数使用城市名称。

## 7. 开发顺序

### Phase 1：后端 API（6 个新接口 + 3 个 Mapper 改动）
### Phase 2：顶部工具栏 + 网点标注（TopToolbar.vue + 网点 DivIcon）
### Phase 3：热力图（heatmapLayer.js + 集成）
### Phase 4：侧边栏（SidebarPanel.vue，3 种模式）
### Phase 5：排名列表 + 筛选联动（RankingList.vue + 事件联动）

Phase 2 和 Phase 3 可并行。

## 8. 涉及文件

**后端**：
- `jw-map/.../controller/JwDataController.java`（+6 接口）
- `jw-map/.../mapper/JwGridDataRawMapper.java/.xml`（+1 查询）
- `jw-map/.../mapper/JwBranchScoreMapper.java/.xml`（+2 查询）
- `jw-map/.../mapper/JwBranchInfoMapper.java/.xml`（+1 查询）

**前端**：
- `ruoyi-ui/src/api/jwmap/data.js`（+6 API 函数）
- `ruoyi-ui/src/views/jwmap/map/tianditu.vue`（集成所有组件）
- 新建 `components/TopToolbar.vue`
- 新建 `components/SidebarPanel.vue`
- 新建 `components/RankingList.vue`
- 新建 `utils/heatmapLayer.js`
- 新建 `assets/branch-icon.css`
- 拷贝 GeoJSON 到 `public/data/map_data/`

## 9. 非功能需求

- 3000 网格 Canvas 渲染流畅
- 热力图显示/隐藏响应 < 500ms
- 侧边栏滑入滑出 CSS transition 过渡
