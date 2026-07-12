# jw-map 网点布局优化系统 — 项目完整说明文档

> 基于 **RuoYi-Vue 3.9.2** 的银行网点布局优化模块
> 用于内网迁移参考，记录所有功能、技术实现、数据库、配置项

---

## 一、项目架构概览

```
├── pom.xml                          # 父 POM（Spring Boot 2.5.15）
├── ruoyi-admin/                     # Spring Boot 入口，application.yml / application-druid.yml
├── ruoyi-common/                    # 通用工具类、BaseEntity、MyBatis
├── ruoyi-framework/                 # 安全框架（JWT/Shiro → 已改为JWT）
├── ruoyi-system/                    # 系统管理（用户/角色/菜单/部门）
├── ruoyi-quartz/                    # 定时任务
├── ruoyi-generator/                 # 代码生成器
├── jw-map/                          # ★ 网点布局优化模块（核心业务）
│   ├── src/main/java/com/ruoyi/jwmap/
│   │   ├── config/
│   │   │   ├── JwMapSecurityConfig.java    # 安全配置——开放 /jwmap/** 全部接口
│   │   │   └── ...
│   │   ├── constant/
│   │   │   └── AccessStatus.java           # 数据访问状态常量
│   │   ├── controller/                     # 11个Controller（详见下方）
│   │   ├── domain/                         # 15个实体类
│   │   ├── mapper/                         # 15个Mapper接口
│   │   ├── service/                        # 3接口 + 6实现类
│   │   └── util/
│   │       ├── JwGeoUtils.java             # Haversine距离公式
│   │       ├── JwIndicatorUtils.java       # 指标树追踪
│   │       └── TopsisCalculator.java       # TOPSIS算法引擎
│   └── src/main/resources/mapper/jwmap/    # 15个MyBatis XML
├── ruoyi-ui/                        # Vue 2.6 前端
│   └── src/views/jwmap/
│       ├── index.vue                # 数据管理页面（导入/计算/导出/查看4个Tab）
│       ├── baidu-map/               # ★ 百度地图可视化（主要地图页面）
│       ├── map/components/          # 共享UI组件（21个）
│       ├── shared/                  # 共享mixin/工具/资产
│       ├── config/indicator.vue     # 指标管理页面
│       └── access/                  # 数据查看申请/审批页面
└── sql/
    ├── jw_dump_full.sql             # 完整数据库dump（含非jw表）
    └── jw_baidu_map_menu.sql        # 百度地图菜单SQL
```

---

## 二、数据库 — 完整表结构

数据库名：`ry-vue`，共 **16张 jw_ 表**。

### 2.1 网格选址数据（5表）

#### `jw_grid_meta` — 网格元数据
| 字段 | 类型 | 说明 |
|---|---|---|
| grid_code | varchar(64) PK | 网格编码 |
| longitude/latitude | decimal(12,8) | 网格中心坐标 |
| west_longitude / east_longitude / north_latitude / south_latitude | decimal(12,8) | 1km×1km 包围盒 |
| province / city / district | varchar(32) | 行政区划 |
| create_by / create_time / update_by / update_time | | 审计字段 |

#### `jw_population_heat` — 人口热力（垂直存储）
| 字段 | 类型 | 说明 |
|---|---|---|
| heat_id | bigint PK | |
| grid_code | varchar(64) | 网格编码 |
| indicator_code | varchar(64) | 指标编码（如 `population_density`） |
| indicator_value | decimal(16,4) | 指标值 |
| create_time | datetime | |

#### `jw_grid_data_raw` — 网格原始指标值（垂直存储）
| 字段 | 类型 | 说明 |
|---|---|---|
| data_id | bigint PK | |
| grid_code | varchar(64) | 网格编码 |
| indicator_code | varchar(64) | 指标编码 |
| indicator_value | decimal(16,4) | 原始值 |
| create_time | datetime | |

#### `jw_grid_data_normalized` — 网格归一化值（垂直存储）
| 字段 | 类型 | 说明 |
|---|---|---|
| data_id | bigint PK | |
| grid_code | varchar(64) | 网格编码 |
| indicator_code | varchar(64) | 指标编码 |
| normalized_value | decimal(16,10) | 归一化结果 |
| create_time | datetime | |

#### `jw_grid_summary` — 网格指标汇总
| 字段 | 类型 | 说明 |
|---|---|---|
| summary_id | bigint PK | |
| city | varchar(32) | 城市 |
| indicator_code | varchar(64) | 指标编码 |
| actual_weight | decimal(16,10) | 实际权重 |
| max_raw / min_raw | decimal(16,4) | 原始值最大/最小 |
| max_norm / min_norm | decimal(16,10) | 归一化最大/最小 |

#### `jw_grid_score` — 网格 TOPSIS 得分
| 字段 | 类型 | 说明 |
|---|---|---|
| grid_code | varchar(64) PK | 网格编码 |
| city | varchar(32) | 城市 |
| positive_distance / negative_distance | decimal(16,10) | 正/负理想解距离 |
| site_score | decimal(16,10) | 综合选址评分 |
| score_category | varchar(32) | 评分类别（默认"overall"） |
| create_time | datetime | |

**存储策略**：`score_category` 支持多类别评分，当前只用了 `overall`。

---

### 2.2 网点效能数据（5表）

#### `jw_branch_info` — 网点基础信息
| 字段 | 类型 | 说明 |
|---|---|---|
| branch_id | bigint PK | |
| primary_branch | varchar(100) | 一级支行 |
| secondary_branch | varchar(100) | 二级支行（网点名） |
| branch_code | varchar(32) | 网点编码 |
| city | varchar(32) | 城市 |
| grid_code | varchar(64) | 所属网格编码（空间关联结果） |
| district_name | varchar(64) | 区县 |
| street / address | varchar | 地址 |
| longitude / latitude | decimal(12,8) | BD09坐标 |
| total_staff / personal_manager / corporate_manager / counter_staff / lobby_staff | int | 人员配置 |
| branch_manager / manager_tenure / manager_resume / manager_history | varchar/text | 行长信息 |
| total_area / other_floor_area | decimal(10,2) | 面积 |
| cash_counter / non_cash_counter / manager_seat | int | 柜台配置 |
| property_right | varchar(32) | 产权性质（自有/租赁） |
| lease_expire / last_renovation | varchar(32) | 租赁到期/最近装修 |
| branch_type | varchar(64) | 网点类型 |
| relocation | varchar(100) | 搬迁计划 |
| data_source | varchar(32) | 数据来源（默认"网点信息"） |
| del_flag | char(1) | 删除标记（0正常） |

#### `jw_branch_indicator` — 网点业务指标（垂直存储）
| 字段 | 类型 | 说明 |
|---|---|---|
| indicator_id | bigint PK | |
| branch_id | bigint | 网点ID |
| data_year | int | 年份 |
| sheet_type | varchar(32) | Sheet类型（基础数据/计算指标/归一化） |
| indicator_code | varchar(64) | 指标编码 |
| indicator_value | decimal(16,4) | 指标值 |
| create_time | datetime | |

#### `jw_branch_summary` — 网点指标汇总
| 字段 | 类型 | 说明 |
|---|---|---|
| summary_id | bigint PK | |
| city | varchar(32) | |
| data_year | int | 年份 |
| indicator_code | varchar(64) | |
| actual_weight | decimal(16,10) | 权重 |
| max_value / min_value | decimal(16,4) | 最大/最小值 |
| max_norm / min_norm | decimal(16,10) | 归一化最大/最小 |
| create_time | datetime | |

#### `jw_branch_score` — 网点 TOPSIS 得分
| 字段 | 类型 | 说明 |
|---|---|---|
| score_id | bigint PK | |
| branch_id | bigint | 网点ID |
| data_year | int | 年份 |
| city | varchar(32) | |
| score_category | varchar(32) | 评分类别（revenue/indicator/customer/operation/overall） |
| positive_distance / negative_distance | decimal(16,10) | 正/负理想解距离 |
| category_score | decimal(16,10) | 评分 |
| rank_num | int | 排名 |
| create_time | datetime | |

---

### 2.3 权重配置表（2表）

#### `jw_external_resource_weight` — 外部资源权重（网格选址TOPSIS权重）
#### `jw_branch_efficiency_weight` — 网点效能权重（网点TOPSIS权重）

两者结构相同：
| 字段 | 类型 | 说明 |
|---|---|---|
| weight_id | bigint PK | |
| level1_name / level2_name / level3_name | varchar(100) | 三级分类名称 |
| level1_ratio / level2_ratio / level3_ratio | decimal(10,6) | 分类权重 |
| total_weight | decimal(10,6) | 总权重（=level3_ratio） |
| indicator_code | varchar(64) | 关联指标编码 |

---

### 2.4 其他表（4表）

#### `jw_indicator_config` — 指标配置（元数据表）
| 字段 | 类型 | 说明 |
|---|---|---|
| indicator_id | bigint PK | |
| indicator_code | varchar(64) UNIQUE | 指标编码 |
| indicator_name | varchar(200) | 指标名称 |
| sort_order | int | 排序 |
| indicator_type | varchar(16) | 类型（grid=网格, branch=网点） |
| parent_code | varchar(64) | 父级指标编码 |
| is_derived | char(1) | 是否衍生指标（0=原始, 1=计算） |
| computation_pattern | varchar(32) | 计算模式（见下方4.2节） |
| input_codes | varchar(512) | 输入指标编码列表 |
| calculation_weight | double | 权重（已废弃，用jw_external_resource_weight） |

#### `jw_poi_info` — POI点位数据
#### `jw_peer_bank_info` — 同业银行信息
#### `jw_data_access_request` — 数据查看申请表

---

## 三、后端 API 完整列表

### 3.1 计算接口 — `JwComputeController` (`/jwmap/compute/`)
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/cityStatus` | 所有城市网格数据就绪状态 |
| GET | `/cityStatus/{city}` | 单个城市网格就绪状态 |
| GET | `/branchStatus/{city}` | 网点数据就绪状态 |
| POST | `/grid/{city}` | 触发网格全量计算流水线（5步） |
| POST | `/branch/{city}/{year}` | 触发网点全量计算流水线（6步） |

### 3.2 数据查询 — `JwGridDataController` (`/jwmap/data/`)
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/grid/list` | 列表网格（含siteScore） |
| GET | `/grid/cities` | 有数据的城市列表 |
| GET | `/grid/score/byCity/{city}` | 城市网格得分排序（驱动热力图） |
| GET | `/grid/indicators/{gridCode}` | 网格指标明细 |
| GET | `/grid/ranking/{city}` | 网格排名（分页） |
| GET | `/grid/ranking/district/{gridCode}` | 网格区内排名 |
| GET | `/grid/branches/{gridCode}` | 网格内网点（权限过滤） |
| GET | `/grid/pillar/{gridCode}` | 网格支柱得分（人口/企业/商户） |
| GET | `/grid/pillar/gap/{gridCode}` | 网格差距分析 |
| GET | `/grid/topScore/{city}` | 城市最高分 |
| GET | `/grid/topWithoutBranch/{city}` | 无网点高分网格（空白点） |
| GET | `/grid/nearestBranch/{gridCode}` | 最近网点 |

### 3.3 数据查询 — `JwBranchDataController` (`/jwmap/data/`)
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/branch/list` | 网点列表 |
| GET | `/branch/score/{city}/{year}` | 网点得分 |
| GET | `/branch/summary/{city}/{year}` | 网点汇总 |
| GET | `/branch/score/detail/{branchId}/{year}` | 网点得分明细 |
| GET | `/branch/indicators/{branchId}/{year}` | 网点指标（权限检查） |
| GET | `/branch/ranking/{city}/{year}` | 网点排名 |
| GET | `/branch/ranking/internal/{branchId}/{year}` | 网点内部排名 |
| GET | `/branch/topScores/{city}/{year}` | 各维度最高分 |
| GET | `/branch/nearby/{branchId}` | 附近网点 |

### 3.4 其他数据 — 5个Controller
| Controller | 前缀 | 主要用途 |
|---|---|---|
| `JwPeerBankController` | `/jwmap/data/peerBank` | 同业查询 |
| `JwPoiController` | `/jwmap/data/poi` | POI查询+范围分析 |
| `JwPopulationController` | `/jwmap/data/population` | 人口数据网格 |
| `JwAnalysisController` | `/jwmap/data/` | 四象限/维度统计/三方聚焦排名 |
| `JwConfigController` | `/jwmap/config` | 指标CRUD |

### 3.5 导入/导出 — 600s超时
| Controller | 路径 | 说明 |
|---|---|---|
| `JwImportController` | `/jwmap/import/` | POI/人口热力/网点信息/同行银行（POST multipart） |
| `JwExportController` | `/jwmap/export/` | 网格/网点数据导出（GET blob, SXSSF透视表） |

### 3.6 数据查看审批 — `JwDataAccessController` (`/jwmap/access/`)
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/request/submit` | 提交查看申请 |
| GET | `/request/myList` | 我的申请列表 |
| POST | `/request/cancel/{requestId}` | 撤销申请 |
| GET | `/request/pendingList` | 待审批列表 |
| POST | `/request/approve` | 审批通过 |
| POST | `/request/reject` | 拒绝 |
| GET | `/checkBranch/{branchId}` | 查看权限检查 |
| GET | `/export/{requestId}` | 导出已审批数据 |

---

## 四、核心计算流水线

### 4.1 网格选址评分（`GridComputeServiceImpl`）

**数据来源**：jw_population_heat（人口热力）、jw_poi_info（POI数据）

**5步流水线**：

1. **computeGridMeta** — 从人口热力提取唯一网格→计算包围盒（经纬度偏移量约0.005度，约500m）→ 统计网格内POI数量 → 写入 `jw_grid_meta`
2. **computeGridRawData** — 将人口热力值+POI计数写入 `jw_grid_data_raw`（按 `indicator_code` 字段区分）
3. **computeGridSummary** — 对每个指标计算 max/min/weight → `jw_grid_summary`。权重取自 `jw_external_resource_weight`（未配置则默认1）
4. **computeGridNormalized** — 归一化：`value / SQRT(SUMSQ)` → `jw_grid_data_normalized`
5. **computeGridScore** — TOPSIS评分（按root类别）→ `jw_grid_score`

**计算公式**：
- 归一化：`norm = value / sqrt(Σ(value²))`
- TOPSIS正距离：`D⁺ = sqrt(Σ(weight × (norm - ideal⁺)²))`
- TOPSIS负距离：`D⁻ = sqrt(Σ(weight × (norm - ideal⁻)²))`
- 得分：`Score = D⁻ / (D⁺ + D⁻)`

**策略**：全量重算——先 `deleteByCity`，再重新生成

### 4.2 网点效能评分（`BranchComputeServiceImpl`）

**数据来源**：`jw_branch_info`（基础信息）+ 导入的基础Excel指标数据

**6步流水线**：

1. **assignGridToBranch** — 空间关联：地图上坐标点到最近的网格
2. **computeBranchIndicators** — 基于 `jw_indicator_config` 中的衍生指标配置计算22个指标：
   - **计算模式**（`computation_pattern` 字段）：
     - `per_capita` = value / staff count
     - `per_area` = value / total_area
     - `per_customer` = value / personal_manager
     - `growth_rate` = (y₁ - y₀) / y₀
     - `sum_per_capita` / `sum_per_area`
3. **computeBranchSummary** — 每个指标统计 max/min/weight
4. **computeBranchNormalized** — 归一化：`value * weight / SQRT(SUMSQ)`
5. **computeBranchScore** — 5个类别TOPSIS得分：revenue / indicator / customer / operation / overall
6. **computeRankings** — 按得分排序

**权重来源**：`jw_branch_efficiency_weight`（按 `level1_name → level2_name → level3_name` 三级分类 -> `indicator_code`-> `total_weight`）

---

## 五、前端架构

### 5.1 技术栈
- Vue 2.6 + Element UI 2.15
- **BMapGL（百度地图 WebGL 3.0）** — 通过 `sdkLoader.js` 动态异步加载
- ECharts 5.4（四象限/维度统计/雷达图）
- Axios（api/jwmap/data.js封装全部后端API）

### 5.2 前端目录结构
```
src/views/jwmap/
├── index.vue                    # 数据管理页面（4个Tab：导入/计算/导出/查看）
├── baidu-map/                   # ★ 百度地图可视化页面
│   ├── index.vue                # 主页面（引用8个共享组件 + 4个mixins）
│   ├── mixins/
│   │   ├── useMapLifecycle.js   # 地图核心：初始化/热力图/空白点/同业/高亮/范围统计/搜索/测距
│   │   └── useRanking.js        # 网格/网点排名加载+分页
│   └── utils/
│       ├── sdkLoader.js          # BMapGL SDK动态加载（含AK密钥）
│       ├── bMapBoundaryManager.js# 行政区划边界管理
│       ├── bMapHeatmapLayer.js   # 热力图层（已弃用，改用Polygon方案）
│       ├── bMapMeasureTool.js    # 测距工具
│       └── bMapSearchTool.js     # 地址/POI搜索工具（BMapGL.LocalSearch封装）
├── shared/                      # 共享模块
│   ├── mixins/
│   │   ├── useIndicatorTree.js   # 指标名称→编码映射
│   │   └── useBranchComparison.js# 网点对比（最多4个）
│   ├── utils/
│   │   └── bankSvgMap.js         # 银行SVG图标映射（23家银行）
│   └── assets/
│       ├── bank-icons/           # 166个银行SVG
│       └── branch-icon.css       # 网点标记样式
├── map/components/               # 22个共享UI组件
│   ├── TopToolbar.vue           # 工具栏（行政区切换/功能按钮/网点搜索/地址搜索）
│   ├── SidebarPanel.vue         # 侧边栏（网格/网点详情）
│   ├── RankingList.vue          # 排名列表
│   ├── DetailPanel.vue          # 详情弹窗
│   ├── QuadrantChart.vue        # 四象限图
│   ├── DimensionStats.vue       # 维度统计
│   ├── RangeStatsPanel.vue      # 范围统计面板
│   ├── ComparisonPanel.vue      # 网点对比
│   ├── BranchInfoCard.vue       # 网点信息卡片
│   ├── BranchScores.vue         # 网点得分
│   ├── GridInfoCard.vue         # 网格信息卡片
│   ├── IndicatorSection.vue     # 指标分区
│   ├── PeerBankSection.vue      # 同业展示
│   ├── PercentageBar.vue        # 进度条
│   ├── PillarRadar.vue          # 雷达图
│   ├── QuadrantPosition.vue     # 象限位置
│   ├── RankBadge.vue            # 排名徽章
│   ├── ScoreCard.vue            # 得分卡片
│   ├── ScoreProgressBar.vue     # 得分进度条
│   ├── StackedBar.vue           # 堆叠柱状图
│   └── ThreeColumnCards.vue     # 三列卡片
├── config/
│   └── indicator.vue            # 指标管理配置页
├── access/
│   ├── index.vue                # 数据查看申请
│   └── approval.vue             # 数据审批管理
└── mixins/                      # 废弃（旧Leaflet时代混入）
```

### 5.3 地图功能详解

| 功能 | 数据结构 | 核心逻辑 | 文件 |
|---|---|---|---|
| **行政区边界** | GeoJSON `/data/map_data/{adcode}_full.json` | BMapGL.Polygon，按adcode切换市/区 | `bMapBoundaryManager.js` |
| **本行网点** | `jw_branch_info` → `BMapGL.CustomOverlay` | 圆形蓝色气泡（28px），蓝色边框，内嵌SVG logo | `useMapLifecycle.js:loadBranches()` |
| **同业银行** | `jw_peer_bank_info` → `BMapGL.CustomOverlay` | 彩色方块（20px），各银行专有颜色，SVG/文字回退 | `useMapLifecycle.js:loadPeerBankMarkers()` |
| **热力图** | `jw_grid_score` → `BMapGL.Polygon` | 1km网格多边形，绿→黄→红色渐变，BMapGL自动跟随地图 | `useMapLifecycle.js:loadHeatmapData()` |
| **空白点** | `grid/topWithoutBranch` → `BMapGL.Polygon` | 蓝色渐变半透明多边形，得分越高越深，可点击 | `useMapLifecycle.js:loadBlankSpotData()` |
| **高亮** | `BMapGL.Polygon/Circle/Label` | 网格：金色虚线框+标签；网点：三层同心圆+红点+标签 | `useMapLifecycle.js:highlightGrid/Branch()` |
| **范围统计** | `poi/withinRange` (BMapGL.Polygon) | 拖拽/中心点击绘制圆或矩形→统计POI | `useMapLifecycle.js:onToggleRangeStats()` |
| **地址搜索** | BMapGL.LocalSearch | 工具栏搜索框→下拉列表→flyTo+标记 | `bMapSearchTool.js` / `TopToolbar.vue` |
| **测距** | BMapGL.Polyline + Marker | 点选坐标→折线→像素计算距离 | `bMapMeasureTool.js` |

### 5.4 前端路由（所有hidden）
- `/jwmap/baidu-map` — 百度地图可视化
- `/jwmap/config/indicator` — 指标管理
- `/jwmap/access-request` — 数据查看申请
- `/jwmap/access-approval` — 数据审批管理

### 5.5 环境配置
| 环境 | API Base | 代理目标 |
|---|---|---|
| 开发 (.env.development) | `/dev-api` | localhost:8080 |
| 生产 (.env.production) | `/prod-api` | 生产服务器 |
| 预发 (.env.staging) | `/stage-api` | 预发服务器 |

前端 devServer 端口：8099

---

## 六、安全配置

### 6.1 接口权限
**`JwMapSecurityConfig.java`**：
- `/jwmap/**` 所有路径 = **`.permitAll()`**（无需JWT认证！）
- 数据查看权限在 `JwDataAccessServiceImpl` 逻辑层用 `getUserId()` 控制

**设计原因**：地图页是公开页面，不要求登录就能查看地图。但网点指标明细需要通过数据审批流程授权。

### 6.2 数据审批流程
**5种状态**：PENDING(0) → APPROVED(1) / REJECTED(2) / CANCELLED(3) / EXPIRED(4)
- 用户提交 → 对应部门审批人审核 → 通过后可查看网点指标值
- `role_key = 'data_reviewer'` 角色可审批
- 审批通过有有效期（默认30天）

---

## 七、百度地图集成说明 ★（内网迁移重点关注）

### 7.1 依赖关系

整个地图页面**强依赖百度在线服务**，内网环境无法直接使用。以下逐一列出所有使用百度地图 API 的代码位置、用途和迁移影响。

---

### 7.2 BMapGL SDK 加载

| 项目 | 内容 |
|---|---|
| **文件** | `ruoyi-ui/src/views/jwmap/baidu-map/utils/sdkLoader.js` |
| **当前AK** | `g0o18DfiUGEiOg9QbZ6Cq4N5QgtHX4tr` |
| **加载方式** | 动态创建 `<script>` 标签请求 `https://api.map.baidu.com/api?v=3.0&type=webgl&ak={AK}&callback=onBMapGLReady` |
| **加载时机** | 页面 mounted 时 `loadBMapGL()` → JSONP 回调 → 初始化地图 |
| **SDK版本** | v3.0, WebGL 版（type=webgl） |
| **全局暴露** | `window.BMapGL`（整个项目所有地图操作都通过 `window.BMapGL.xxx` 调用） |
| **内网影响** | **脚本加载失败则整个地图无法初始化** |

---

### 7.3 使用的百度地图 API 完整清单

#### 核心地图（必需）

| API 调用 | 用途 | 代码位置 | 内网替代难度 |
|---|---|---|---|
| `new BMapGL.Map(container)` | 创建地图实例 | `useMapLifecycle.js:initMap()` | 高 |
| `map.centerAndZoom(point, zoom)` | 设置中心/缩放 | `useMapLifecycle.js` | 高 |
| `map.setMinZoom() / setMaxZoom()` | 缩放范围 9-16 | `useMapLifecycle.js` | 高 |
| `map.addEventListener('click')` | 地图点击事件 | `useMapLifecycle.js` | 高 |
| `map.addEventListener('zoomend')` | 缩放结束事件 | `useMapLifecycle.js` | 高 |
| `map.panTo(point)` | 飞至某个位置 | `useMapLifecycle.js:onAddressSelect` | 高 |
| `map.flyTo(point, zoom)` | 平滑飞行 | `useMapLifecycle.js:onRankingItemClick/onSearchBranch` | 高 |
| `map.getCenter()` | 获取地图中心点 | `useMapLifecycle.js` | 高 |
| `map.getZoom()` | 获取当前缩放级别 | `useMapLifecycle.js` | 高 |
| `map.setZoom(zoom)` | 设置缩放级别 | `useMapLifecycle.js:onAddressSelect` | 高 |
| `map.pointToPixel(point)` | 经纬度→屏幕坐标 | `useMapLifecycle.js:rangeStats` | 高 |
| `map.getContainer()` | 获取地图DOM容器 | `useMapLifecycle.js`多处 | 高 |

#### 覆盖物（地图要素渲染）

| API 调用 | 用途 | 代码位置 | 内网替代难度 |
|---|---|---|---|
| `new BMapGL.Polygon(points, opts)` | 渲染网格热力图 | `useMapLifecycle.js:loadHeatmapData()` | 中 |
| `new BMapGL.Polygon(points, opts)` | 渲染空白点 | `useMapLifecycle.js:loadBlankSpotData()` | 中 |
| `new BMapGL.Polygon(points, opts)` | 渲染边界/选择形状 | `bMapBoundaryManager.js`, `useMapLifecycle.js:rangeStats` | 中 |
| `new BMapGL.Polyline(points, opts)` | 测距折线 | `bMapMeasureTool.js` | 中 |
| `new BMapGL.Circle(center, radius)` | 高亮网点/测距圆 | `useMapLifecycle.js:highlightBranch()` | 中 |
| `new BMapGL.CustomOverlay(func, opts)` | 自定义网点标记(本行) | `useMapLifecycle.js:loadBranches()` | 中 |
| `new BMapGL.CustomOverlay(func, opts)` | 自定义同业标记 | `useMapLifecycle.js:loadPeerBankMarkers()` | 中 |
| `new BMapGL.Marker(point, opts)` | 搜索/测距坐标点 | `bMapSearchTool.js`, `bMapMeasureTool.js` | 中 |
| `new BMapGL.Label(str, opts)` | 文字标签 | `bMapSearchTool.js`, `useMapLifecycle.js:highlight*` | 中 |
| `new BMapGL.InfoWindow(html, opts)` | 信息弹窗 | `useMapLifecycle.js:peerBank/branch click` | 中 |
| `new BMapGL.Point(lng, lat)` | 坐标点(几乎每个API都用到) | 各处 | 中 |
| `map.addOverlay(overlay)` | 添加覆盖物到地图 | 各处 | 高 |
| `map.removeOverlay(overlay)` | 移除覆盖物 | 各处 | 高 |

#### 搜索与地理编码（依赖在线服务）

| API 调用 | 用途 | 代码位置 | 内网替代难度 |
|---|---|---|---|
| `new BMapGL.LocalSearch(map, opts)` | 地址/POI搜索 | `bMapSearchTool.js:search()` | **高——纯在线服务，无离线方案** |
| `LocalSearch.search(keyword)` | 执行搜索 | `bMapSearchTool.js` | 同上 |
| `LocalResult.getCurrentNumPois()` | 获取搜索结果数量 | `bMapSearchTool.js:parseResults()` | 同上 |
| `LocalResult.getPoi(i)` | 获取单个结果 | `bMapSearchTool.js` | 同上 |
| `new BMapGL.Geocoder()` | 逆地理编码（坐标→地址） | `useMapLifecycle.js:reverseGeocode()` | **高——纯在线服务，无离线方案** |
| `Geocoder.getLocation(point, cb)` | 执行逆地理编码 | `useMapLifecycle.js` | 同上 |

#### 工具类

| API 调用 | 用途 | 代码位置 | 内网替代难度 |
|---|---|---|---|
| `new BMapGL.NavigationControl()` | 地图缩放控件 | `useMapLifecycle.js:initMap()` | 低（可自建按钮） |

#### 依赖百度地图的坐标基准

| 事项 | 说明 | 涉及位置 |
|---|---|---|
| **坐标系** | 所有坐标数据使用 **BD09**（百度坐标系） | 数据库 `jw_branch_info.longitude/latitude`, `jw_peer_bank_info`, `jw_grid_meta` 等全部字段 |
| **数据来源** | Excel 导入的坐标已通过第三方工具转为 BD09 | 导入流程 |
| **边界数据** | `public/data/map_data/*.json` 中的 GeoJSON 边界也是 BD09 | `bMapBoundaryManager.js` |

> **坑点**：如果内网迁移换用非百度地图方案，数据库中所有坐标字段都需要从 BD09 转换为目标坐标系（如 WGS84）。转换算法可在百度开放文档中找到。

---

### 7.4 SDK 加载失败的影响

| 影响范围 | 表现 | 是否阻止页面渲染 |
|---|---|---|
| 地图初始化 | 白屏，无法显示地图底图 | **是** |
| 热力图 | 无法叠加网格 | 级联失效 |
| 网点标记 | 无法显示本行/同业网点 | 级联失效 |
| 搜索 | 地址搜索不可用 | 级联失效 |
| 测距 | 测距工具不可用 | 级联失效 |
| 边界 | 行政区边界不可用 | 级联失效 |
| 其他功能 | 侧边栏/面板等纯UI组件可正常使用（但无地图可操作） | 部分可用 |

> `sdkLoader.js` 有 15 秒超时机制，超时后会报错但不阻塞页面其他非地图组件渲染。

---

### 7.5 内网迁移方案对比

| 方案 | 复杂度 | 说明 |
|---|---|---|
| **A. 申请内网AK + 内网CDN** | 低 | 联系百度地图商务申请私有化部署或内网加速AK | 低 |
| **B. 离线地图底图+自研坐标转换** | 高 | 替换BMapGL为Leaflet/OpenLayers+离线瓦片，需重写所有 `window.BMapGL.xxx` 调用 | 高（数百处） |
| **C. 保留BMapGL加载，地址搜索降级** | 中 | 保留核心地图功能，`LocalSearch` 和 `Geocoder` 改为自建接口查询 | 中 |

**推荐方案**：先在百度地图控制台申请新AK测试在线连接，不通则考虑方案C——地图底图使用 BMapGL 的内网代理，搜索功能改为后端POI数据库查询。

---

### 7.6 密钥与配置

```
AK 文件位置: ruoyi-ui/src/views/jwmap/baidu-map/utils/sdkLoader.js (第5行)
当前AK值:    g0o18DfiUGEiOg9QbZ6Cq4N5QgtHX4tr
API版本:     v3.0 WebGL
加载URL:     https://api.map.baidu.com/api?v=3.0&type=webgl&ak={AK}&callback=onBMapGLReady
超时时间:     15秒 (sdkLoader.js 中 hardcoded)
地图中心:     贵州省 26.5807, 106.7238
缩放范围:     9-16
坐标系:       BD09


---

## 八、内网迁移修改清单

### 8.1 必须修改
| 序号 | 事项 | 涉及文件 | 说明 |
|---|---|---|---|
| 1 | **数据库连接** | `application-druid.yml` | `url/username/password` 改为内网MySQL |
| 2 | **Redis连接** | `application.yml` | `host/port/password` 改为内网Redis |
| 3 | **百度地图AK** | `ruoyi-ui/.../sdkLoader.js:5` | 改为内网可用AK（详见第七章全部说明） |
| 4 | **后端接口地址** | `.env.development / .env.production` | 改 `VUE_APP_BASE_API` |
| 5 | **后端端口** | `application.yml` | `server.port` 改为内网端口 |
| 6 | **BMapGL CDN加载** | `sdkLoader.js:6` | 在线CDN `api.map.baidu.com` 内网不可达，需配置内网代理或替换地图方案（详见7.5节） |
| 7 | **地址搜索(在线服务)** | `bMapSearchTool.js` | 依赖百度在线搜索API，内网不可用，需下线此功能或改用后端自建POI搜索 |
| 8 | **逆地理编码(在线服务)** | `useMapLifecycle.js:reverseGeocode()` | 依赖百度在线Geocoder，内网不可用，可移除或替换 |
| 9 | **坐标转换(内迁换坐标系时)** | 数据库全部坐标字段 | 所有坐标为BD09，换非百度地图方案需批量转换坐标系 |

### 8.2 建议修改
| 序号 | 事项 | 涉及文件 | 说明 |
|---|---|---|---|
| 6 | 后端文档路径 | `application.yml` | `pathMapping: /dev-api` 改为对应环境路径 |
| 7 | 文件上传路径 | `application.yml` | `profile: ./uploadPath` |
| 8 | 日志路径 | `logback.xml` | `log.path: ./logs` |
| 9 | Swagger开关 | `application.yml` | 生产环境可关闭 |

### 8.3 数据迁移
- SQL文件：`sql/jw_dump_full.sql`（完整数据库）
- 也可用 `j_baidu_map_menu.sql` 单独部署菜单（配合已有ry-vue库）
- GeoJSON边界数据：`ruoyi-ui/public/data/map_data/` 下的 `{adcode}_full.json` 文件（贵州省及各城市）

### 8.4 编译运行
```bash
# 后端
mvn clean install -DskipTests
cd ruoyi-admin
mvn spring-boot:run    # 默认8080端口，4核16G服务器约30秒启动

# 前端
cd ruoyi-ui
npm install
npm run dev            # 开发：8099端口，代理到localhost:8080
npm run build:prod     # 生产：输出到dist/
```

### 8.5 前端部署
- 生产构建产物在 `ruoyi-ui/dist/`
- 需要将 `dist` 内容 + `public/data/map_data/` 下的 GeoJSON 文件部署到 Web 服务器
- Nginx 配置需要代理 `/prod-api` 到后端地址

---

## 九、数据流摘要

```
Excel导入
  │
  ├─ jw_poi_info ──→ GridCompute ──→ jw_grid_data_raw → normalize → jw_grid_score (热力图+空白点)
  ├─ jw_population_heat ──→ GridCompute ──→ jw_grid_data_raw → ...
  ├─ jw_branch_info ──→ BranchCompute ──→ jw_branch_indicator → normalize → jw_branch_score (网点排名)
  └─ jw_peer_bank_info ──→ 直接查询 → 地图显示

前端访问：
  getGridScoreByCity(city) → 热力图/空白点渲染
  getBranchList(city) → 本行网点标记
  getPeerBankList(city) → 同业银行标记
  getGridTopWithoutBranch(city) → 服务空白点分析
  数据权限 → 网点指标明细查看
```

---

## 十、菜单与权限

| menu_id | 菜单名 | 路由 | 权限标识 |
|---|---|---|---|
| 1061 | 网点布局 | — | —（顶级菜单） |
| 1062 | 数据管理 | jwmap/index | jwmap:data:list |
| 1065 | 百度地图 | jwmap/baidu-map/index | jwmap:map:view |
| — | 指标管理 | jwmap/config/indicator | （通过路由） |
| — | 数据查看申请 | jwmap/access-request | （通过路由） |
| — | 数据审批管理 | jwmap/access-approval | （通过路由） |

> 指标管理、数据申请、审批 三条路由在 `dynamicRoutes` 中定义，`hidden: true`，由后端通过菜单权限控制显示。
> 菜单1063（原"地图可视化"指向Leaflet版）和1064（天地图）已删除。
