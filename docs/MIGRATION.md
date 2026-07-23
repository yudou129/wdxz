# 网点布局优化系统 — 项目迁移文档

> 将 `jw-map` 网点布局优化模块迁移到内网已有的若依框架中。
> 基于 RuoYi-Vue 3.9.2（Spring Boot 2.5.15 + Vue 2.6.12）

---

## 目录

1. [项目概述](#1-项目概述)
2. [模块结构与代码映射](#2-模块结构与代码映射)
3. [数据库与表结构](#3-数据库与表结构)
4. [配置文件清单](#4-配置文件清单)
5. [迁移步骤](#5-迁移步骤)
6. [迁移后需要修改的地方](#6-迁移后需要修改的地方)
7. [常见问题](#7-常见问题)

---

## 1. 项目概述

### 功能总览

| # | 功能 | 前端入口 | 后端位置 |
|---|------|---------|---------|
| 1 | 网格选址计算（TOPSIS） | 数据管理页 → 导入/计算 | GridComputeServiceImpl.java |
| 2 | 网点效能计算（TOPSIS） | 数据管理页 → 导入/计算 | BranchComputeServiceImpl.java |
| 3 | 百度地图可视化 | `/jwmap/baidu-map` 路由 | — |
| 4 | AI 选址建议 | 网格 AI 入口（SidebarPanel） | ai/service/impl/AiServiceImpl.java |
| 5 | AI 网点诊断 | 网点 AI 入口（SidebarPanel） | 同上 |
| 6 | AI 多网点对比 | ComparisonPanel | 同上 |
| 7 | AI 网格分析 | 网格 AI 入口（SidebarPanel） | 同上 |
| 8 | AI 权重助手 | 网格/网点 → ⚖️ 按钮 | 同上 |
| 9 | AI 选址报告 | 空白网格 → 📄 按钮 | 同上 |
| 10 | AI 四象限深度分析 | 网点 → 四象限卡片 → 🤖 按钮 | 同上 |
| 11 | 指标管理 | `/jwmap/config/indicator` | JwIndicatorConfig 相关 |
| 12 | 数据查看申请/审批 | `/jwmap/access-request` / `access-approval` | JwDataAccessRequest 相关 |

### 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 2.5.15 |
| JDK | Java | 1.8 |
| 数据库 | MySQL | 5.7+ / 8.0 |
| ORM | MyBatis | Spring Boot 内置 |
| 连接池 | Druid | 1.2.28 |
| Excel | Apache POI (SXSSF) | 4.1.2 |
| HTTP 客户端 | OkHttp (调用 DeepSeek API) | 4.12.0 (jw-map 模块) |
| JSON | FastJSON2 | 2.0.61 (框架)；1.2.83 (jw-map AI) |
| 前端框架 | Vue 2 + Element UI | 2.6.12 / 2.15.14 |
| 地图库 | BMapGL (百度地图 WebGL) | 在线 SDK |
| 图表 | ECharts | 5.4.0 |
| AI 模型 | DeepSeek V4 Flash | 通过 OpenAI 协议 |

---

## 2. 模块结构与代码映射

### 2.1 整体结构

若依标准项目结构 + `jw-map` 自定义模块：

```
ruoyi 根目录/
├── pom.xml                      # 父 POM
├── ruoyi-admin/                 # Spring Boot 入口
│   └── src/main/resources/
│       ├── application.yml      # ★ 主配置（需迁移修改）
│       └── application-druid.yml # 数据库连接（需迁移修改）
├── ruoyi-common/                # 通用工具
├── ruoyi-framework/             # 安全框架
│   └── SecurityConfig.java      # ★ 安全配置（需添加/确认 jwmap 路由放行）
├── ruoyi-system/                # 系统管理
├── ruoyi-quartz/                # 定时任务（数据访问过期用）
├── ruoyi-generator/             # 代码生成器
├── jw-map/                      # ★ 网点布局优化模块（核心，需要整个迁移）
│   ├── pom.xml                  # 模块依赖
│   └── src/main/java/com/ruoyi/jwmap/
│       ├── ai/                  # AI 智能分析（7个功能）
│       ├── config/              # 模块配置（JwMapConfig + JwMapSecurityConfig）
│       ├── constant/            # 常量
│       ├── controller/          # 11个数据 Controller + AI Controller
│       ├── domain/              # 15个实体类
│       ├── mapper/              # Mapper接口
│       ├── service/             # 业务服务
│       ├── task/                # 定时任务（数据访问过期）
│       └── util/                # 工具类（TOPSIS算法）
├── ruoyi-ui/                    # ★ 前端（需要整个迁移）
│   └── src/views/jwmap/         # 所有地图相关页面
└── sql/                         # ★ 数据库初始化脚本
```

### 2.2 关键代码文件

#### 后端 — `jw-map` 模块

| 功能 | 文件 | 说明 |
|------|------|------|
| **TOPSIS 网格选址计算** | `service/impl/GridComputeServiceImpl.java` | 5步计算流水线：归一化→加权→正负理想解→得分→排名，完成后调 aiService.invalidateByCity() |
| **TOPSIS 网点效能计算** | `service/impl/BranchComputeServiceImpl.java` | 6步计算流水线，同上 |
| **Excel 导出** | `service/impl/ExcelExportService.java` | POI SXSSF 流式导出大数据量 Excel |
| **Excel 导入** | `service/impl/ExcelImportService.java` | 导入+自动建指标 |
| **TOPSIS 算法** | `util/TopsisCalculator.java` | 正负理想解 + 相似度计算 |
| **AI 控制器** | `ai/controller/AiController.java` | SSE 流式端点 + AjaxResult 端点 |
| **AI 服务** | `ai/service/impl/AiServiceImpl.java` | LLM 调用 + SSE 流式代理 + 存储 |
| **AI 数据聚合** | `ai/service/impl/AiDataAggregator.java` | 从各 Mapper 读取数据组装为上下文 |
| **AI 提示词** | `ai/service/impl/AiPromptBuilder.java` | 7套提示词模板 |
| **AI 缓存** | `ai/service/impl/AiCacheService.java` | `jw_ai_analysis` 表读写 + 过期管理 |
| **Web 报告** | `ai/service/impl/WordReportGenerator.java` | （待实现，目前返回 HTML） |
| **AI 模型** | `ai/model/AiChatRequest.java` | DeepSeek/OpenAI 兼容请求体 |
| **模块配置** | `config/JwMapConfig.java` | `jwmap.profile` 报告路径 |
| **安全配置** | `config/JwMapSecurityConfig.java` | `/jwmap/** permitAll()` |
| **数据访问审批** | `controller/JwAccessController.java` | 申请/审批 |
| **指标管理** | `controller/JwIndicatorConfigController.java` | 权重树 CRUD |
| **数据 Controller** | `controller/JwBranchInfoController.java` | 网点 CRUD + 批量导入 |
| **网格 Controller** | `controller/JwGridDataController.java` | 网格数据导入/计算 |
| **分析数据** | `controller/JwAnalysisController.java` | 象限数据、排名数据 |
| **定时任务** | `task/JwDataAccessExpiryTask.java` | 每天2am过期数据访问申请 |
| **地图数据** | `controller/JwMapDataController.java` | 网格/网点/同业 POI 等地图 Marker 数据 |

#### 前端 — `jw-map` 模块

| 文件 | 功能 |
|------|------|
| `views/jwmap/index.vue` | 数据管理页面（导入/计算/导出/查看） |
| `views/jwmap/baidu-map/index.vue` | ★ 百度地图主页面（8个组件 + 4个 mixins） |
| `views/jwmap/baidu-map/mixins/useMapLifecycle.js` | ★ 地图核心：热力图、空白点、同业、高亮、范围统计、搜索、测距 |
| `views/jwmap/baidu-map/mixins/useRanking.js` | 网格/网点排名 |
| `views/jwmap/baidu-map/utils/sdkLoader.js` | BMapGL SDK 动态加载（含 AK 密钥） |
| `views/jwmap/map/components/` | 22个共享 UI 组件（SidebarPanel、QuadrantChart、AiDrawer 等） |
| `views/jwmap/map/components/AiDrawer.vue` | ★ AI 分析结果的右侧抽屉 |
| `views/jwmap/map/components/AiAnalysisCard.vue` | AI 结果卡片（流式Markdown渲染） |
| `views/jwmap/map/components/SidebarPanel.vue` | 侧边栏（包含多数据展示 + AI 入口） |
| `views/jwmap/shared/` | 共享 mixins 和 SVG图标 |
| `views/jwmap/config/indicator.vue` | 指标管理页面 |
| `views/jwmap/access/` | 数据查看申请/审批 |
| `api/jwmap/` | 所有后端 API 封装 |
| `api/jwmap/ai.js` | AI 功能 API（fetchSSE流式 + 常规请求） |
| `router/index.js` | 路由配置（需要添加 `jwmap` 路由） |

### 2.3 AI 功能代码映射

| 功能 | 前端触发 | API 函数 | 后端端点 | 提示词方法 |
|------|---------|---------|---------|-----------|
| 选址建议 | SidebarPanel AI按钮 | getSiteSuggestionStream | site-suggestion/stream/{gridCode} | buildSiteSuggestionUserMessage |
| 网点诊断 | SidebarPanel AI按钮 | getBranchAnalysisStream | branch-analysis/stream/{branchId}/{year} | buildBranchAnalysisUserMessage |
| 多网点对比 | ComparisonPanel AI按钮 | getBranchComparisonStream | branch-comparison/stream?branchIds=... | buildBranchComparisonUserMessage |
| 网格分析 | SidebarPanel AI按钮 | getGridAnalysisStream | grid-analysis/stream/{gridCode} | buildGridAnalysisUserMessage |
| 权重助手 | SidebarPanel ⚖️ 按钮 | getWeightSuggestion | weight-suggestion (POST非流式) | buildWeightSuggestionUserMessage |
| 选址报告 | SidebarPanel 📄 按钮 | generateSiteReport | site-report/{gridCode} (POST非流式) | buildSiteReportUserMessage |
| 四象限深度分析 | 网点四象限卡 🤖 按钮 | getPerBranchQuadrantStream | quadrant-analysis/stream/per-branch | buildPerBranchQuadrantAnalysisUserMessage |

---

## 3. 数据库与表结构

### 3.1 若依标准表（已有，无需额外迁移）

- `sys_user`, `sys_dept`, `sys_role`, `sys_menu`, `sys_role_menu`, `sys_user_role`
- `sys_job`, `sys_job_log`（Quartz 定时任务）
- `qrtz_*` 系列（Quartz 调度器，可选）

### 3.2 jw-map 业务表（18个表，需要迁移）

#### 网格体系（6个表）

**`jw_grid_meta`** — 网格元数据
```sql
CREATE TABLE `jw_grid_meta` (
  `grid_code` varchar(50) NOT NULL COMMENT '网格编码',
  `longitude` decimal(10,6) NOT NULL COMMENT '中心点经度',
  `latitude` decimal(10,6) NOT NULL COMMENT '中心点纬度',
  `west_longitude` decimal(10,6) NOT NULL COMMENT '西边经度',
  `east_longitude` decimal(10,6) NOT NULL COMMENT '东边经度',
  `north_latitude` decimal(10,6) NOT NULL COMMENT '北边纬度',
  `south_latitude` decimal(10,6) NOT NULL COMMENT '南边纬度',
  `province` varchar(50) DEFAULT NULL,
  `city` varchar(50) DEFAULT NULL,
  `district` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`grid_code`),
  KEY `idx_city` (`city`),
  KEY `idx_district` (`district`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网格元数据(1km×1km)';
```

**`jw_population_heat`** — 人口热力（垂直存储）
```sql
CREATE TABLE `jw_population_heat` (
  `heat_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `grid_code` varchar(50) NOT NULL COMMENT '网格编码',
  `indicator_code` varchar(50) NOT NULL COMMENT '指标编码',
  `indicator_value` decimal(20,2) DEFAULT NULL COMMENT '指标值',
  PRIMARY KEY (`heat_id`),
  KEY `idx_grid_code` (`grid_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人口热力数据';
```

**`jw_grid_data_raw`** — 网格原始指标数据（垂直存储）
```sql
CREATE TABLE `jw_grid_data_raw` (
  `data_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `grid_code` varchar(50) NOT NULL,
  `indicator_code` varchar(50) NOT NULL,
  `indicator_value` decimal(20,2) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`data_id`),
  KEY `idx_grid_code` (`grid_code`),
  KEY `idx_grid_indicator` (`grid_code`,`indicator_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网格原始指标数据';
```

**`jw_grid_data_normalized`** — 网格归一化数据（垂直存储）
```sql
CREATE TABLE `jw_grid_data_normalized` (
  `data_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `grid_code` varchar(50) NOT NULL,
  `indicator_code` varchar(50) NOT NULL,
  `normalized_value` decimal(20,4) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`data_id`),
  KEY `idx_grid_code` (`grid_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网格归一化数据';
```

**`jw_grid_summary`** — 网格汇总（最大/最小值 + 权重）
```sql
CREATE TABLE `jw_grid_summary` (
  `summary_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `city` varchar(50) NOT NULL,
  `indicator_code` varchar(50) NOT NULL,
  `actual_weight` decimal(10,4) DEFAULT NULL COMMENT '有效权重',
  `max_raw` decimal(20,2) DEFAULT NULL COMMENT '指标最大值',
  `min_raw` decimal(20,2) DEFAULT NULL COMMENT '指标最小值',
  `max_norm` decimal(20,4) DEFAULT NULL,
  `min_norm` decimal(20,4) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`summary_id`),
  KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网格汇总(权重+MAX/MIN)';
```

**`jw_grid_score`** — 网格 TOPSIS 得分
```sql
CREATE TABLE `jw_grid_score` (
  `grid_code` varchar(50) NOT NULL,
  `city` varchar(50) DEFAULT NULL,
  `score_category` varchar(50) NOT NULL COMMENT '类别:pop/business/ent/overall',
  `positive_distance` decimal(20,4) DEFAULT NULL,
  `negative_distance` decimal(20,4) DEFAULT NULL,
  `site_score` decimal(20,4) DEFAULT NULL COMMENT '选址得分',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`grid_code`,`score_category`),
  KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网格TOPSIS得分';
```

#### 网点体系（4个表）

**`jw_branch_info`** — 网点基本信息
```sql
CREATE TABLE `jw_branch_info` (
  `branch_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '网点ID',
  `primary_branch` varchar(100) DEFAULT NULL COMMENT '一级支行',
  `secondary_branch` varchar(100) DEFAULT NULL COMMENT '网点名称',
  `branch_code` varchar(50) DEFAULT NULL COMMENT '网点编码',
  `grid_code` varchar(50) DEFAULT NULL COMMENT '所属网格',
  `district_name` varchar(50) DEFAULT NULL COMMENT '所属区县',
  `street` varchar(200) DEFAULT NULL,
  `address` varchar(500) DEFAULT NULL,
  `longitude` decimal(10,6) DEFAULT NULL,
  `latitude` decimal(10,6) DEFAULT NULL,
  `total_staff` int(11) DEFAULT NULL COMMENT '总人数',
  `personal_manager` int(11) DEFAULT NULL COMMENT '个人客户经理',
  `corporate_manager` int(11) DEFAULT NULL COMMENT '对公客户经理',
  `counter_staff` int(11) DEFAULT NULL COMMENT '柜员',
  `lobby_staff` int(11) DEFAULT NULL COMMENT '大堂人员',
  `branch_manager` varchar(50) DEFAULT NULL COMMENT '行长',
  `manager_tenure` int(11) DEFAULT NULL COMMENT '行长任期(月)',
  `manager_history` text COMMENT '行长履历',
  `total_area` decimal(10,2) DEFAULT NULL COMMENT '总面积(㎡)',
  `other_floor_area` decimal(10,2) DEFAULT NULL,
  `cash_counter` int(11) DEFAULT NULL COMMENT '现金柜台',
  `non_cash_counter` int(11) DEFAULT NULL,
  `manager_seat` int(11) DEFAULT NULL,
  `property_right` varchar(50) DEFAULT NULL COMMENT '产权类型',
  `lease_expire` date DEFAULT NULL COMMENT '租约到期',
  `last_renovation` date DEFAULT NULL COMMENT '最近装修',
  `branch_type` varchar(50) DEFAULT NULL COMMENT '网点类型',
  `relocation` varchar(10) DEFAULT NULL,
  `city` varchar(50) DEFAULT NULL,
  `data_source` varchar(50) DEFAULT NULL,
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志',
  `create_time` datetime DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`branch_id`),
  KEY `idx_city` (`city`),
  KEY `idx_grid_code` (`grid_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网点信息';
```

**`jw_branch_indicator`** — 网点经营指标（垂直存储）
```sql
CREATE TABLE `jw_branch_indicator` (
  `indicator_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `branch_id` bigint(20) NOT NULL,
  `data_year` int(11) NOT NULL,
  `sheet_type` varchar(50) DEFAULT NULL COMMENT '工作表类型',
  `indicator_code` varchar(50) NOT NULL,
  `indicator_value` decimal(20,2) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`indicator_id`),
  UNIQUE KEY `uk_branch_year_code` (`branch_id`,`data_year`,`indicator_code`),
  KEY `idx_year` (`data_year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网点经营指标';
```

**`jw_branch_summary`** — 网点汇总
```sql
CREATE TABLE `jw_branch_summary` (
  `summary_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `city` varchar(50) NOT NULL,
  `data_year` int(11) NOT NULL,
  `indicator_code` varchar(50) NOT NULL,
  `actual_weight` decimal(10,4) DEFAULT NULL,
  `max_value` decimal(20,2) DEFAULT NULL,
  `min_value` decimal(20,2) DEFAULT NULL,
  `max_norm` decimal(20,4) DEFAULT NULL,
  `min_norm` decimal(20,4) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`summary_id`),
  KEY `idx_city_year` (`city`,`data_year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网点汇总(权重+MAX/MIN)';
```

**`jw_branch_score`** — 网点 TOPSIS 得分
```sql
CREATE TABLE `jw_branch_score` (
  `score_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `branch_id` bigint(20) NOT NULL,
  `data_year` int(11) NOT NULL,
  `city` varchar(50) DEFAULT NULL,
  `score_category` varchar(50) NOT NULL COMMENT '维度:overall/revenue/indicator/customer/operation',
  `positive_distance` decimal(20,4) DEFAULT NULL,
  `negative_distance` decimal(20,4) DEFAULT NULL,
  `category_score` decimal(20,4) DEFAULT NULL COMMENT '维度得分',
  `rank_num` int(11) DEFAULT NULL COMMENT '排名',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`score_id`),
  UNIQUE KEY `uk_branch_year_cat` (`branch_id`,`data_year`,`score_category`),
  KEY `idx_city_year` (`city`,`data_year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网点TOPSIS得分';
```

#### 配置表（4个表）

**`jw_indicator_config`** — ★ 指标配置（权重树）
```sql
CREATE TABLE `jw_indicator_config` (
  `indicator_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `indicator_code` varchar(100) NOT NULL COMMENT '指标编码',
  `indicator_name` varchar(200) DEFAULT NULL COMMENT '指标名称',
  `indicator_type` varchar(50) DEFAULT NULL COMMENT '类型:grid/branch_raw/branch',
  `parent_code` varchar(100) DEFAULT NULL COMMENT '父级编码',
  `is_derived` char(1) DEFAULT 'N' COMMENT '是否派生(计算)指标',
  `computation_pattern` varchar(500) DEFAULT NULL COMMENT '计算模式',
  `input_codes` text COMMENT '输入指标编码',
  `calculation_weight` decimal(10,4) DEFAULT NULL COMMENT '权重',
  `sort_order` int(11) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`indicator_id`),
  UNIQUE KEY `uk_indicator_code` (`indicator_code`),
  KEY `idx_parent_code` (`parent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标配置(权重树)';
```

**`jw_score_category_config`** — 评分分类映射
```sql
CREATE TABLE `jw_score_category_config` (
  `config_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `category_code` varchar(50) NOT NULL COMMENT '分类编码',
  `category_name` varchar(100) DEFAULT NULL COMMENT '分类名称',
  `indicator_code` varchar(100) NOT NULL COMMENT '指标编码',
  `sort_order` int(11) DEFAULT NULL,
  `is_active` char(1) DEFAULT 'Y',
  PRIMARY KEY (`config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分分类配置';
```

**`jw_external_resource_weight`** — 网格权重（已废弃，权重已迁移到 jw_indicator_config）
```sql
CREATE TABLE `jw_external_resource_weight` (
  `weight_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `indicator_code` varchar(100) NOT NULL,
  `indicator_name` varchar(200) DEFAULT NULL,
  `weight_value` decimal(10,4) DEFAULT NULL,
  PRIMARY KEY (`weight_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网格权重(已迁移到indicator_config)';
```

**`jw_branch_efficiency_weight`** — 网点权重（已废弃，权重已迁移到 jw_indicator_config）
```sql
CREATE TABLE `jw_branch_efficiency_weight` (
  `weight_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `indicator_code` varchar(100) NOT NULL,
  `indicator_name` varchar(200) DEFAULT NULL,
  `weight_value` decimal(10,4) DEFAULT NULL,
  PRIMARY KEY (`weight_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网点权重(已迁移到indicator_config)';
```

#### 业务辅助表（4个表）

**`jw_poi_info`** — POI 信息
```sql
CREATE TABLE `jw_poi_info` (
  `poi_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `org_code` varchar(50) DEFAULT NULL,
  `poi_name` varchar(200) DEFAULT NULL,
  `longitude` decimal(10,6) DEFAULT NULL,
  `latitude` decimal(10,6) DEFAULT NULL,
  `province` varchar(50) DEFAULT NULL,
  `city` varchar(50) DEFAULT NULL,
  `district` varchar(50) DEFAULT NULL,
  `address` varchar(500) DEFAULT NULL,
  `poi_type` varchar(50) DEFAULT NULL COMMENT 'POI类型',
  `del_flag` char(1) DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`poi_id`),
  KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='POI信息';
```

**`jw_peer_bank_info`** — 同业银行信息
```sql
CREATE TABLE `jw_peer_bank_info` (
  `peer_bank_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `org_code` varchar(50) DEFAULT NULL,
  `org_name` varchar(200) DEFAULT NULL,
  `org_address` varchar(500) DEFAULT NULL,
  `longitude` decimal(10,6) DEFAULT NULL,
  `latitude` decimal(10,6) DEFAULT NULL,
  `bank_name` varchar(100) DEFAULT NULL COMMENT '所属银行',
  `province` varchar(50) DEFAULT NULL,
  `city` varchar(50) DEFAULT NULL,
  `district` varchar(50) DEFAULT NULL,
  `town` varchar(50) DEFAULT NULL,
  `grid_code` varchar(50) DEFAULT NULL,
  `del_flag` char(1) DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`peer_bank_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同业银行信息';
```

**`jw_data_access_request`** — 数据查看申请审批
```sql
CREATE TABLE `jw_data_access_request` (
  `request_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `applicant_id` bigint(20) NOT NULL,
  `target_dept_id` bigint(20) NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `valid_days` int(11) DEFAULT 7,
  `status` char(1) DEFAULT '0' COMMENT '0待审/1通过/2驳回/3取消/4过期',
  `reviewer_id` bigint(20) DEFAULT NULL,
  `review_comment` varchar(500) DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) DEFAULT '0',
  PRIMARY KEY (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据查看申请';
```

#### AI 分析表（1个表）

**`jw_ai_analysis`** — AI 分析结果持久化
```sql
CREATE TABLE `jw_ai_analysis` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `analysis_type` varchar(50) NOT NULL COMMENT '类型:branch/grid/quadrant',
  `entity_key` varchar(200) NOT NULL COMMENT '实体标识',
  `city` varchar(50) DEFAULT NULL COMMENT '城市',
  `content` mediumtext COMMENT 'AI分析内容(JSON/Markdown)',
  `satisfied` tinyint(4) DEFAULT NULL COMMENT '满意度',
  `expired` tinyint(4) DEFAULT 0 COMMENT '是否过期',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_entity` (`analysis_type`,`entity_key`),
  KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析结果';
```

### 3.3 若依基础表补充

需要确保以下若依标准表存在，并添加 `jw-map` 所需的菜单/部门数据：

- `sys_menu` — 需执行 `sql/jw_baidu_map_menu.sql` 添加菜单项
- `sys_dept` — 需执行 `sql/jw_outlet_depts.sql` 添加网点部门（可选）
- `sys_job` — 需执行 `sql/jw_quartz_access_expiry.sql` 添加过期任务

### 3.4 数据库初始化顺序

```
1. ry_20260417.sql (或内网已有若依基础表) → 若依框架基础表
2. jw_map_20250525.sql → 18个业务表
3. jw_score_category_config.sql → 评分分类映射
4. jw_data_access_request.sql → 数据访问审批表
5. jw_ai_analysis.sql → AI分析结果表
6. jw_peer_bank_info.sql → 同业银行表（可跳过，已包含在2中）
7. migrate_indicator_redesign.sql → 指标树迁移（仅在新装时需要）
8. jw_baidu_map_menu.sql → 菜单权限
9. jw_outlet_depts.sql → 网点部门（可选）
10. quartz.sql → Quartz调度表（若内网已有则跳过）
11. jw_quartz_access_expiry.sql → 定时任务注册
```

**数据导入顺序**（业务数据）：
```
1. jw_indicator_config → 指标配置树（含权重）
2. jw_score_category_config → 评分分类
3. jw_grid_meta → 网格元数据
4. jw_population_heat → 人口热力
5. jw_poi_info → POI数据
6. jw_peer_bank_info → 同业银行
7. jw_branch_info → 网点信息
8. jw_branch_indicator → 网点指标
```

---

## 4. 菜单、角色与权限配置

### 4.1 系统菜单树

jw-map 模块需要注册以下菜单到 `sys_menu` 表：

| 菜单ID | 菜单名称 | 父ID | 排序 | 路由 | 组件路径 | 权限标识 | 类型 |
|--------|---------|------|------|------|---------|---------|------|
| 1061 | 网点布局 | 0 (根) | — | — | — | — | 目录 |
| 1065 | 百度地图 | 1061 | 2 | baidu-map | jwmap/baidu-map/index | jwmap:map:view | 菜单 |
| — | 指标管理 | 1061 | — | indicator | jwmap/config/indicator | — | 菜单 |
| — | 数据查看申请 | 1061 | — | access-request | jwmap/access/request | — | 菜单 |
| — | 数据审批管理 | 1061 | — | access-approval | jwmap/access/approval | — | 菜单 |

> **注意**：上述菜单中 `1061`（网点布局目录）和 `1065`（百度地图）有固定 ID，其他菜单建议在内网迁移时重新生成。

### 4.2 菜单 SQL 脚本

```sql
-- ============ 1. 网点布局目录（若不存在） ============
INSERT IGNORE INTO `sys_menu` 
VALUES (1061, '网点布局', 0, 10, 'jwmap', NULL, NULL, NULL, 1, 0, 'M', '0', '0', '', 'tree', 'admin', sysdate(), '', NULL, '网点布局目录');

-- ============ 2. 子菜单 ============
-- 百度地图可视化（主页面）
INSERT IGNORE INTO `sys_menu` 
VALUES (1065, '百度地图', 1061, 2, 'baidu-map', 'jwmap/baidu-map/index', '', '', 1, 0, 'C', '0', '0', 'jwmap:map:view', 'map', 'admin', sysdate(), '', NULL, '百度地图可视化页面');

-- 指标管理（若需要独立入口）
INSERT IGNORE INTO `sys_menu` 
VALUES (1066, '指标管理', 1061, 3, 'indicator', 'jwmap/config/indicator', '', '', 1, 0, 'C', '0', '0', 'jwmap:config:indicator', 'tree', 'admin', sysdate(), '', NULL, '指标权重配置');

-- 数据查看申请（若需要独立入口）
INSERT IGNORE INTO `sys_menu` 
VALUES (1067, '数据查看申请', 1061, 4, 'access-request', 'jwmap/access/request', '', '', 1, 0, 'C', '0', '0', 'jwmap:access:request', 'edit', 'admin', sysdate(), '', NULL, '数据查看申请');

-- 数据审批管理
INSERT IGNORE INTO `sys_menu` 
VALUES (1068, '数据审批管理', 1061, 5, 'access-approval', 'jwmap/access/approval', '', '', 1, 0, 'C', '0', '0', 'jwmap:access:approve', 'eye', 'admin', sysdate(), '', NULL, '数据审批管理');

-- ============ 3. 角色权限 ============
-- 赋予 admin 角色（role_id=100）所有 jw-map 菜单权限
INSERT IGNORE INTO `sys_role_menu` VALUES (100, 1061);
INSERT IGNORE INTO `sys_role_menu` VALUES (100, 1065);
INSERT IGNORE INTO `sys_role_menu` VALUES (100, 1066);
INSERT IGNORE INTO `sys_role_menu` VALUES (100, 1067);
INSERT IGNORE INTO `sys_role_menu` VALUES (100, 1068);

-- ============ 4. 清理旧菜单（若迁移时有冲突） ============
-- 若内网已有旧版本 jwmap 菜单（1063、1064），需要清理
DELETE FROM `sys_role_menu` WHERE menu_id IN (1063, 1064);
DELETE FROM `sys_menu` WHERE menu_id IN (1063, 1064);
```

### 4.3 权限标识说明

| 权限标识 | 对应功能 | 说明 |
|---------|---------|------|
| `jwmap:map:view` | 百度地图可视化 | 访问地图主页面的基础权限 |
| `jwmap:config:indicator` | 指标管理 | 配置指标权重树 |
| `jwmap:access:request` | 数据查看申请 | 提交数据查看申请 |
| `jwmap:access:approve` | 数据审批管理 | 审批数据查看申请 |
| — | AI 分析功能 | 不需要独立权限，地图页面内直接可用 |

> **注意**：jw-map 的 `/jwmap/**` 路由在安全配置中全部 `permitAll()`，不经过 JWT 验证。菜单权限与 `sys_menu.perms` 相关联是若依标准的前端路由控制，如果不需要前端菜单控制，可以不配置权限标识。

### 4.4 部门（网点）组织架构

jw-map 使用四级组织架构：**省行 → 市行 → 一级支行 → 网点（二级支行）**。

若依标准 `sys_dept` 表结构：
- `dept_id`: 部门ID
- `parent_id`: 父部门ID
- `ancestors`: 祖先ID链（如 `0,200,201,210`）
- `order_num`: 排序
- `leader`: 负责人
- `status`: 状态（0正常 1停用）
- `del_flag`: 删除标志（0正常 2删除）

**说明**：jw-map 模块中，`sys_dept` 主要用于**数据访问申请审批**的数据权限控制，**不是**百度地图功能必需的。如果内网不需要审批功能，可以跳过此表的数据导入。

网点（二级支行）部门数据见 `sql/jw_outlet_depts.sql`：

| dept_id | 部门名称 | parent_id | 所属市行/支行 | ancestros |
|---------|---------|-----------|-------------|-----------|
| 232~241 | 清镇市支行等10个 | 210 | 贵阳分行 | 0,200,201,210 |
| 242~246 | 遵义分行营业部等5个 | 220 | 遵义分行 | 0,200,202,220 |
| 247~249 | 仁怀市支行营业部等3个 | 221 | 仁怀市支行 | 0,200,202,221 |
| 250~251 | 赤水市支行营业部等2个 | 222 | 赤水市支行 | 0,200,202,222 |
| 252~253 | 正安县支行营业部等2个 | 223 | 正安县支行 | 0,200,202,223 |
| 254~256 | 六盘水分行营业部等3个 | 230 | 六盘水分行 | 0,200,203,230 |
| 257~258 | 盘州市支行营业部等2个 | 231 | 盘州市支行 | 0,200,203,231 |

### 4.5 角色配置

jw-map 模块涉及的角色和权限分配：

| role_id | 角色名称 | 角色键 | 数据范围 | 说明 |
|---------|---------|--------|---------|------|
| 100 | 管理员 | admin | 1（全部） | 拥有所有权限，包括菜单和 AI 功能 |
| 1 | 普通角色 | common | 5（按自定义） | 可在若依后台分配具体权限 |
| 2 | 数据查看员 | viewer | 4（仅本部门及以下） | 仅查看本地区网点数据 |

> 如果管理员角色 ID 不是 100，需要修改 `sql/jw_baidu_map_menu.sql` 中的 `INSERT INTO sys_role_menu VALUES (角色ID, 菜单ID)`。

---

## 5. 测试用户数据

以下数据用于迁移后快速验证功能。密码加密使用 BCrypt，默认密码均为 `admin123`。

```sql
-- ======================================================================
-- 测试用户数据（sys_user）
-- 密码统一为 admin123（BCrypt 加密）
-- ======================================================================

-- 1. 各分行/支行的数据查看员用户（用于数据访问申请审批流程测试）
INSERT IGNORE INTO `sys_user` 
VALUES (30, 'gy_viewer', '0', '贵阳数据员', 'gy_viewer@test.com', '13800001001', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', '0', '210', NULL, NULL, NULL, NULL, NULL, NULL, 'admin', sysdate(), '', NULL, '贵阳分行数据查看员');
INSERT IGNORE INTO `sys_user`
VALUES (31, 'zy_viewer', '0', '遵义数据员', 'zy_viewer@test.com', '13800001002', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', '0', '220', NULL, NULL, NULL, NULL, NULL, NULL, 'admin', sysdate(), '', NULL, '遵义分行数据查看员');
INSERT IGNORE INTO `sys_user`
VALUES (32, 'lp_viewer', '0', '六盘水数据员', 'lp_viewer@test.com', '13800001003', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', '0', '230', NULL, NULL, NULL, NULL, NULL, NULL, 'admin', sysdate(), '', NULL, '六盘水分行数据查看员');

-- 2. 支行级审批人用户
INSERT IGNORE INTO `sys_user`
VALUES (33, 'gy_approver', '0', '贵阳审批人', 'gy_approver@test.com', '13800002001', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', '0', '210', NULL, NULL, NULL, NULL, NULL, NULL, 'admin', sysdate(), '', NULL, '贵阳分行数据审批人');
INSERT IGNORE INTO `sys_user`
VALUES (34, 'zy_approver', '0', '遵义审批人', 'zy_approver@test.com', '13800002002', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', '0', '220', NULL, NULL, NULL, NULL, NULL, NULL, 'admin', sysdate(), '', NULL, '遵义分行数据审批人');

-- 3. 超级管理员（独立于 admin，用于测试多角色场景）
INSERT IGNORE INTO `sys_user`
VALUES (35, 'map_admin', '0', '地图管理员', 'map_admin@test.com', '13800003001', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', '0', '100', NULL, NULL, NULL, NULL, NULL, NULL, 'admin', sysdate(), '', NULL, '地图系统管理员（测试用）');

-- ======================================================================
-- 用户-角色关联（sys_user_role）
-- ======================================================================
INSERT IGNORE INTO `sys_user_role` VALUES (30, 2);   -- gy_viewer → 普通角色
INSERT IGNORE INTO `sys_user_role` VALUES (31, 2);   -- zy_viewer → 普通角色
INSERT IGNORE INTO `sys_user_role` VALUES (32, 2);   -- lp_viewer → 普通角色
INSERT IGNORE INTO `sys_user_role` VALUES (33, 1);   -- gy_approver → 普通角色（可分配审批权限）
INSERT IGNORE INTO `sys_user_role` VALUES (34, 1);   -- zy_approver → 普通角色
INSERT IGNORE INTO `sys_user_role` VALUES (35, 100); -- map_admin → admin角色

-- ======================================================================
-- 用户-岗位关联（sys_user_post，若不需要可以跳过）
-- ======================================================================
INSERT IGNORE INTO `sys_user_post` VALUES (30, 2);   -- 数据员岗位
INSERT IGNORE INTO `sys_user_post` VALUES (31, 2);
INSERT IGNORE INTO `sys_user_post` VALUES (32, 2);
INSERT IGNORE INTO `sys_user_post` VALUES (33, 1);   -- 经理岗位
INSERT IGNORE INTO `sys_user_post` VALUES (34, 1);
INSERT IGNORE INTO `sys_user_post` VALUES (35, 1);
```

### 测试用户登录信息

| 用户名 | 密码 | 所属部门 | 角色 | 测试用途 |
|--------|------|---------|------|---------|
| admin | admin123 | 总公司 | 管理员 | 所有功能可用 |
| gy_viewer | admin123 | 贵阳分行(210) | 普通用户 | 测试数据查看申请 |
| zy_viewer | admin123 | 遵义分行(220) | 普通用户 | 测试数据查看申请 |
| lp_viewer | admin123 | 六盘水分行(230) | 普通用户 | 测试数据查看申请 |
| gy_approver | admin123 | 贵阳分行(210) | 普通用户 | 测试数据审批 |
| zy_approver | admin123 | 遵义分行(220) | 普通用户 | 测试数据审批 |
| map_admin | admin123 | 总公司 | 管理员 | 测试多角色场景 |

---

## 6. 配置文件清单

### 4.1 后端配置

| 配置文件 | 路径 | 需要迁移修改 |
|---------|------|------------|
| `application.yml` | `ruoyi-admin/src/main/resources/application.yml` | ★ 数据库、Redis、AI配置、server.port |
| `application-druid.yml` | `ruoyi-admin/src/main/resources/application-druid.yml` | ★ 数据库连接串、用户名、密码 |
| `logback.xml` | `ruoyi-admin/src/main/resources/logback.xml` | △ 日志路径 |
| `mybatis/mybatis-config.xml` | `ruoyi-admin/src/main/resources/mybatis/mybatis-config.xml` | 通常不需改 |

### 4.2 关键配置项说明

```yaml
# application.yml — 需要根据内网环境修改的部分

server:
  port: 8080                    # ★ 改为内网端口

spring:
  redis:
    host: localhost              # ★ 改为内网 Redis 地址
    port: 6379
    password: null               # ★ 内网 Redis 密码
  # 数据源配置已移到 application-druid.yml

# ★ 文件上传/报告存储路径（常用配置）
ruoyi:
  profile: ./uploadPath          # 上传文件根目录
# jw-map 模块配置（使用同一路径）
jwmap:
  profile: ./uploadPath          # 报告存储目录（默认同 ruoyi.profile）

# ★ AI 配置（迁移到内网后需要更新）
ai:
  api-key: sk-xxxx               # ★ 改为内网可用的 API Key
  base-url: https://api.deepseek.com/v1  # ★ 改为内网 LLM API 地址
  model: deepseek-v4-flash       # ★ 模型名称（可改）
```

```yaml
# application-druid.yml — 必须修改
spring:
  datasource:
    druid:
      master:
        url: jdbc:mysql://内网IP:3306/ry-vue?useUnicode=true&...  # ★ 数据库地址
        username: root           # ★ 用户名
        password: your_password  # ★ 密码
```

### 4.3 前端配置

| 文件 | 路径 | 需要修改 |
|-----|------|---------|
| `.env.development` | `ruoyi-ui/.env.development` | △ `VUE_APP_BASE_API` 代理路径 |
| `.env.production` | `ruoyi-ui/.env.production` | ★ 内网后端地址 |
| `vue.config.js` | `ruoyi-ui/vue.config.js` | △ 代理目标地址 |
| `.env.staging` | `ruoyi-ui/.env.staging` | △ 测试环境地址 |

**前端代理配置**（`vue.config.js` 关键部分）：
```javascript
devServer: {
  host: '0.0.0.0',
  port: 8099,                     // 前端开发端口
  proxy: {
    '/dev-api': {
      target: 'http://localhost:8080',  // ★ 改为内网后端地址
      pathRewrite: { '^/dev-api': '' }
    }
  }
}
```

---

## 5. 迁移步骤

### 第一阶段：数据库

1. 在内网 MySQL 中创建数据库 `ry-vue`（或已有则用现有）
2. 按[3.4节数据库初始化顺序](#34-数据库初始化顺序)依次执行 SQL 脚本
3. 确认所有表创建成功：`SHOW TABLES LIKE 'jw_%'` 应返回18个业务表

### 第二阶段：后端

1. 将以下代码复制到内网项目：
   - `jw-map/` 整个模块目录
   - 在父 `pom.xml` 中添加：`<module>jw-map</module>`
   - 在 `ruoyi-admin/pom.xml` 中添加依赖：
     ```xml
     <dependency>
         <groupId>com.ruoyi</groupId>
         <artifactId>jw-map</artifactId>
     </dependency>
     ```
2. 复制 `application.yml` 和 `application-druid.yml` 中的 jw-map 相关配置
3. 复制 `src/main/resources/mapper/jwmap/` 到内网项目的相同位置
4. 确认 SecurityConfig 中 `/jwmap/**` 被放行（或直接复制 JwMapSecurityConfig.java）
5. 修改数据库连接配置
6. 编译验证：`mvn clean install -DskipTests`

### 第三阶段：前端

1. 复制 `ruoyi-ui/src/views/jwmap/` 到内网项目
2. 复制 `ruoyi-ui/src/api/jwmap/` 到内网项目
3. 在路由配置文件 `router/index.js` 中添加：
   ```javascript
   {
     path: '/jwmap',
     hidden: true,
     component: Layout,
     children: [
       { path: 'baidu-map', component: () => import('@/views/jwmap/baidu-map/index'), name: 'JwMapBaidu' },
       { path: 'config/indicator', component: () => import('@/views/jwmap/config/indicator'), name: 'IndicatorConfig' },
       { path: 'access-request', component: () => import('@/views/jwmap/access/request'), name: 'AccessRequest' },
       { path: 'access-approval', component: () => import('@/views/jwmap/access/approval'), name: 'AccessApproval' }
     ]
   }
   ```
4. 安装前端依赖（如 ECharts 等）
5. 配置前端代理

### 第四阶段：百度地图

> 百度地图是项目中**最需要关注的迁移点**，因为依赖在线 SDK

1. 确认百度地图 AK 密钥可用或在百度地图开放平台申请新密钥
2. AK 密钥位置：`ruoyi-ui/src/views/jwmap/baidu-map/utils/sdkLoader.js:5`
3. 内网环境限制：
   - 若机房有外网访问权限 → 在线 SDK 直接可用
   - 若完全内网隔离 → 需要下载百度地图离线 SDK 包（详情见 7.3）

---

## 6. 迁移后需要修改的地方

### 6.1 必须修改（否则无法运行）

| 项目 | 文件 | 配置项 | 说明 |
|------|------|--------|------|
| 数据库 | `application-druid.yml` | `url`、`username`、`password` | 改为内网数据库信息 |
| Redis | `application.yml` | `spring.redis.host`、`spring.redis.password` | 改为内网 Redis |
| 父 POM | `pom.xml` | 添加 `<module>jw-map</module>` | 否则 jw-map 模块不会被编译 |
| admin POM | `ruoyi-admin/pom.xml` | 添加 jw-map 依赖 | 否则启动时找不到 jw-map |
| 前端代理 | `vue.config.js` | `target` | 改为内网后端地址 |
| 前端 API | `.env.production` | `VUE_APP_BASE_API` | 改为内网 API 前缀 |

### 6.2 需要确认修改

| 项目 | 位置 | 说明 |
|------|------|------|
| 端口 | `application.yml` → `server.port` | 内网端口可能不是 8080 |
| 上传路径 | `application.yml` → `ruoyi.profile` | 改为内网绝对路径 |
| Redis 密码 | `application.yml` → `spring.redis.password` | 内网 Redis 可能要求密码 |
| 日志路径 | `logback.xml` | macOS 用 `./logs`，内网需改为绝对路径 `/data/logs` |
| AI API Key | `application.yml` → `ai.api-key` | 内网可用其他 LLM 密钥 |
| AI Base URL | `application.yml` → `ai.base-url` | 内网可能是其他 LLM 网关地址（如 OpenAI 协议兼容） |
| OkHttp 版本 | `jw-map/pom.xml` | 若内网已有 OkHttp 依赖，需确认版本兼容 |
| 百度地图 AK | `sdkLoader.js` | 内网环境可能需要新 AK 或离线方案 |

### 6.3 可选修改

| 项目 | 说明 |
|------|------|
| Token 密钥 | `application.yml` → `token.secret` 建议改为内网专用 |
| Token 过期时间 | `application.yml` → `token.expireTime` 按需调整 |
| Druid 监控 | `application-druid.yml` → 统计页面账号密码 |
| 数据源连接池 | `application-druid.yml` → 最大连接数按内网规模调整 |

---

## 7. 常见问题

### 7.1 百度地图 SDK 加载失败

**表现**：地图区域空白，控制台报 `BMapGL is not defined`。

**排查**：
1. 检查 `sdkLoader.js` 中的 AK 是否有效
2. 检查内网服务器是否能访问 `https://api.map.baidu.com/`
3. 若不能访问，需要申请内网专用地图方案

### 7.2 AI 功能无响应

**表现**：点击 AI 按钮没有反应或一直 loading。

**排查**：
1. 控制台查看网络请求 → 确认 SSE 端点是否返回 200
2. 后端日志搜索 `ai-sse-*` 线程 → 检查是否调用 LLM API
3. `ai.api-key` 和 `ai.base-url` 是否正确

### 7.3 百度地图内网离线方案

如果内网完全不能访问外网，有两种替代方案：

**方案 A：离线瓦片 + 本地 SDK**
1. 从百度地图开放平台下载离线 SDK 包（部分版本支持）
2. 将 SDK JS 文件部署到内网静态资源服务器
3. 修改 `sdkLoader.js` 指向本地 SDK 地址

**方案 B：替换为 Leaflet**
1. 项目中已有 Leaflet 的一些代码痕迹（`public/data/map_data/` 下 GeoJSON 边界文件）
2. 需要重写地图渲染层，替换 BMapGL API 为 Leaflet
3. 需将 BD09 坐标转为 WGS84 坐标
4. 在线地图服务（LocalSearch、Geocoder）需要替换为离线或内网地址编码方案

### 7.4 SSE 流式连接中断

AI 分析使用 SSE 流式输出，如果内网有反向代理/负载均衡（如 Nginx），需要配置：

```nginx
# 确保 Nginx 不缓冲 SSE
proxy_buffering off;
proxy_cache off;
proxy_read_timeout 180s;
proxy_send_timeout 180s;
```

### 7.5 定时任务

数据访问申请过期任务使用 Quartz，需要确认内网数据库有 Quartz 调度表：
```sql
-- 11个 QRTZ_ 表
SHOW TABLES LIKE 'QRTZ_%';
```
若不存在，执行 `sql/quartz.sql`。

### 7.6 跨域问题

若内网前端和后端不同源（例如前端 Nginx 8080 → 后端 8080），需要确认跨域配置：

```yaml
# application.yml
referer:
  allowed-domains: localhost,127.0.0.1,内网域名或IP
```

或在前端代理中处理。

---

## 附录

### A. 文件清单总览

| 类型 | 数量 | 位置 |
|------|------|------|
| Java 源文件 | ~50+ | `jw-map/src/main/java/com/ruoyi/jwmap/` |
| Mapper XML | 15+ | `jw-map/src/main/resources/mapper/jwmap/` |
| Vue 页面 | ~30+ | `ruoyi-ui/src/views/jwmap/` |
| JS API | 8 | `ruoyi-ui/src/api/jwmap/` |
| SQL 脚本 | 11 | `sql/` |
| GeoJSON 边界 | ~10 | `ruoyi-ui/public/data/map_data/` |

### B. 依赖汇总（jw-map 模块特有）

```xml
<!-- jw-map/pom.xml 中特有的依赖（非若依标准） -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>fastjson</artifactId>
    <version>1.2.83</version>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webmvc</artifactId>
    <!-- SseEmitter 所在，Spring Boot 已包含 -->
</dependency>
```

### C. 前端特有依赖

```json
// ruoyi-ui/package.json 中 jw-map 需要的依赖
{
  "echarts": "^5.4.0",
  "clipboard": "^2.0.8",
  // 以下为若依标准依赖，jw-map 同样需要
  "element-ui": "^2.15.14",
  "vue": "^2.6.12",
  "highlight.js": "^9.18.5"
}
```
