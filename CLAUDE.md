# RuoYi-Vue + jw-map 网点布局优化系统

> **重要规则**: 除非用户明确要求，否则不要自动执行 `git push` 推送操作。创建 commit 后需等待用户指令再推送。
>
> **完整项目文档**: `docs/README.md` — 包含数据库表结构、API列表、计算流水线、百度地图集成详情等。

## 项目概述

基于 **RuoYi-Vue 3.9.2** (Spring Boot 2.5.15 + Vue 2.6.12) 的银行网点布局优化系统。通过地理网格分析和 TOPSIS 多准则决策算法，评估网点选址得分和经营效能。已集成 **AI 智能分析模块**（DeepSeek V4），提供选址建议、网点诊断、对比分析等 7 个 AI 功能。

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 2.5.15 |
| Java | JDK | 1.8 |
| ORM | MyBatis | - |
| 数据库连接池 | Druid | 1.2.28 |
| Excel处理 | Apache POI | 4.1.2 |
| HTTP 客户端 | OkHttp | 项目现有版本 (3.x)，调用 DeepSeek API |
| JSON | FastJSON | 1.x |
| 前端框架 | Vue 2 + Element UI | 2.6.12 / 2.15.14 |
| 地图库 | BMapGL (百度地图 WebGL 3.0) | 在线SDK |
| 图表 | ECharts | 5.4.0 |
| AI模型 | DeepSeek V4 Flash | 通过 OpenAI 兼容协议调用 |

## 模块结构

```
├── pom.xml                  # 父POM
├── ruoyi-admin/             # Spring Boot入口
├── ruoyi-common/            # 通用工具类（含 RuoYiConfig）
├── ruoyi-framework/         # 安全框架(JWT)
├── ruoyi-system/            # 系统管理（SysDeptMapper/SysUserMapper）
├── ruoyi-quartz/            # 定时任务
├── ruoyi-generator/         # 代码生成器
├── jw-map/                  # ★ 网点布局优化模块（核心）
├── ruoyi-ui/                # Vue前端
└── sql/                     # 数据库初始化脚本
```

## jw-map 模块 (后端)

### 包结构
```
com.ruoyi.jwmap/
├── controller/     # 11个Controller + AI控制器
├── domain/         # 15个实体类
├── mapper/         # Mapper接口 + XML
├── service/        # 服务接口 + 实现类
│   └── impl/
│       ├── GridComputeServiceImpl.java   # 网格选址5步计算流水线，完成后调用 aiService.invalidateByCity()
│       ├── BranchComputeServiceImpl.java # 网点效能6步计算流水线，完成后调用 aiService.invalidateByCity()
│       ├── ExcelExportService.java       # POI SXSSF导出
│       └── ExcelImportService.java       # Excel导入+自动建指标
├── ai/              # ★ AI 智能分析模块（7个功能）
│   ├── config/      # AiProperties(api-key/model/base-url), AiConfig(OkHttpClient Bean), AiAsyncConfig(SSE线程池)
│   ├── controller/  # AiController — SSE流式端点 + AjaxResult常规端点 + 存量查询端点
│   ├── service/     # IAiService 接口
│   │   └── impl/
│   │       ├── AiServiceImpl.java        # 核心：OkHttp调LLM + SSE流式代理 + 存储读写
│   │       ├── AiDataAggregator.java     # 数据聚合：查各Mapper组装结构化上下文
│   │       ├── AiPromptBuilder.java      # 7套中文Prompt模板 + 系统角色定义
│   │       ├── AiCacheService.java       # 持久化存储(jw_ai_analysis表) + 计算过期管理
│   │       └── WordReportGenerator.java  # 选址报告生成（待实现POI Word）
│   ├── model/      # AiChatRequest/Response, AiAnalysisRecord, AiConstants
│   └── mapper/     # AiAnalysisMapper + XML
├── config/
│   ├── JwMapConfig.java        # jw-map 模块本地配置（profile/report路径等）
│   └── JwMapSecurityConfig.java # 安全配置：/jwmap/** permitAll()
└── util/
    └── TopsisCalculator.java   # TOPSIS算法
```

### 关键设计
- **全量重算**: 每次触发计算先 `deleteByCity` 再重新生成
- **导入upsert**: 使用MySQL `ON DUPLICATE KEY UPDATE`
- **安全策略**: `/jwmap/**` 全部 `permitAll()`（无需JWT），数据访问审批在业务层控制
- **权重配置**: 权重存储在 `jw_indicator_config` 树中，通过 `calculation_weight` 递归累乘计算有效权重

### AI 模块（jw-map 新增本模块专属配置类）
- **模块本地配置**: `JwMapConfig.java` — `@ConfigurationProperties(prefix = "jwmap")`，管理 profile/report 路径等，不修改 ruoyi-common 中的 RuoYiConfig
- **AI 配置**: 在 `application.yml` 中使用 `ai:` 前缀，通过 `AiProperties.java` 读取
- **OkHttp 版本兼容**: 项目 classpath 中已有 OkHttp 3.x，新增依赖时不要指定高版本；`RequestBody.create()` 使用 OkHttp 3.x 签名 `create(MediaType, String)`（注意参数顺序）
- **SseEmitter**: 来自 `spring-webmvc`，已作为显式依赖添加到 `jw-map/pom.xml`

### AI 模块架构

**7 个功能：**

| # | 功能 | 存储 | 流式 | 过期策略 |
|---|------|------|------|----------|
| 1 | 选址建议 | 不存储 | SSE 流式 | 无需 |
| 2 | 网点分析 | jw_ai_analysis | 首次SSE/存量直返 | 计算触发过期+手动刷新 |
| 3 | 多网点对比 | 不存储 | SSE 流式 | 无需 |
| 4 | 网格分析 | jw_ai_analysis | 首次SSE/存量直返 | 计算触发过期+手动刷新 |
| 5 | 权重助手 | 不存储 | 非流式 | 无需 |
| 6 | 选址报告 | 不存储 | 非流式(文件下载) | 无需 |
| 7 | 四象限分析 | jw_ai_analysis | 首次SSE/存量直返 | 计算触发过期+手动刷新 |

**LLM 调用方式**：
- **流式**：后端通过 OkHttp 调 DeepSeek `stream=true` API → 逐 chunk 读取 → 通过 `SseEmitter.send()` 推给前端
- **非流式**：直接 OkHttp POST → 解析完整 response → 返回 AjaxResult
- **系统角色**：银行网点选址与运营策略专家
- **API 端点**：`https://api.deepseek.com/v1/chat/completions`（兼容 OpenAI 协议）

**持久化存储**：
- 表：`jw_ai_analysis`（UNIQUE KEY on `analysis_type + entity_key`）
- 查询：有记录 && expired=0 → 直接返回；无记录 || expired=1 → 调LLM流式生成
- 重新生成：用户点「重新分析」→ ON DUPLICATE KEY UPDATE 覆盖
- 满意度：用户点 👍/👎 → POST /feedback → `satisfied=1/0`

## 前端架构

### 目录结构
```
src/views/jwmap/
├── index.vue                  # 数据管理页面（导入/计算/导出/查看）
├── baidu-map/                 # ★ 百度地图可视化（主要地图页面）
│   ├── index.vue              # 主页面（8个组件 + 4个mixins + AI state/lifecycle）
│   ├── mixins/
│   │   ├── useMapLifecycle.js  # 地图核心（热力图/空白点/同业/高亮/范围统计/搜索/测距）
│   │   └── useRanking.js       # 网格/网点排名
│   └── utils/
│       ├── sdkLoader.js         # BMapGL SDK动态加载（含AK密钥）
│       ├── bMapBoundaryManager.js
│       ├── bMapHeatmapLayer.js  # 已弃用，改用Polygon方案
│       ├── bMapMeasureTool.js
│       └── bMapSearchTool.js
├── shared/                    # 共享模块
│   ├── mixins/                # useIndicatorTree, useBranchComparison
│   ├── utils/bankSvgMap.js
│   └── assets/                # bank-icons(166个SVG), branch-icon.css
├── map/components/            # 22个共享UI组件（含AiAnalysisCard）
│   ├── AiAnalysisCard.vue      # ★ AI分析结果复用卡片（流式渲染+满意度评价+重新分析）
│   ├── SidebarPanel.vue        # ★ 内嵌AI区域（gridAiState/branchAiState prop）
│   ├── ComparisonPanel.vue     # ★ AI对比分析（aiState prop）
│   └── QuadrantChart.vue       # ★ AI象限分析（aiAnalysis prop）
├── config/indicator.vue       # 指标管理（含AI权重助手入口，待实现WeightAssistantDrawer）
└── access/                    # 数据查看申请/审批
```

### 前端 AI 数据流
```
用户操作 → Vue emit → baidu-map/index.vue load方法
  → ai.js API 调用 → fetchSSE(URL, onChunk, onDone, onError)
  → fetch + ReadableStream 解析 SSE data: 行
  → onChunk 逐段追加 content → AiAnalysisCard 渲染
  → onDone 停止 loading → 显示满意度按钮
```

### 百度地图集成说明
地图页面强依赖百度在线SDK，内网迁移需重点处理。所有百度地图API调用的精确代码位置和迁移难度标注：
- **SDK加载**: `sdkLoader.js:5` — AK密钥 `g0o18DfiUGEiOg9QbZ6Cq4N5QgtHX4tr`，CDN在线加载
- **核心API**: `BMapGL.Map/Polygon/CustomOverlay/Point` 等25+个调用（`useMapLifecycle.js` 各处）
- **在线服务**: `BMapGL.LocalSearch`(地址搜索) 和 `BMapGL.Geocoder`(逆地理编码) 内网不可用
- **坐标**: 全系统使用 BD09 坐标系（数据库所有坐标字段、GeoJSON边界文件）
- **详细说明**: `docs/README.md` 第7章

### 前端路由（均hidden）
- `/jwmap/baidu-map` — 百度地图可视化
- `/jwmap/config/indicator` — 指标管理
- `/jwmap/access-request` — 数据查看申请
- `/jwmap/access-approval` — 数据审批管理

## 开发运行

### 前置依赖
- **MySQL**: localhost:3306 / ry-vue (root/admin123)
- **Redis**: localhost:6379

### 命令
```bash
# 后端
mvn clean install -DskipTests
cd ruoyi-admin && mvn spring-boot:run   # 8080端口

# 前端
cd ruoyi-ui
npm install && npm run dev               # 8099端口，代理到localhost:8080
```

### macOS 适配
| 文件 | 配置项 | 原始值 | macOS |
|---|---|---|---|
| `application.yml` | `profile` | `D:/ruoyi/uploadPath` | `./uploadPath` |
| `logback.xml` | `log.path` | `/home/ruoyi/logs` | `./logs` |

## GeoJSON边界数据
- 路径: `ruoyi-ui/public/data/map_data/{adcode}_full.json`
- 编码: 520000(贵州), 520100(贵阳), 520200(六盘水), 520300(遵义), ...

## 数据库表

### 业务表（18个）
`jw_grid_meta`、`jw_population_heat`、`jw_grid_data_raw`、`jw_grid_data_normalized`、`jw_grid_summary`、`jw_grid_score`、`jw_branch_info`、`jw_branch_indicator`、`jw_branch_summary`、`jw_branch_score`、`jw_indicator_config`、`jw_external_resource_weight`、`jw_branch_efficiency_weight`、`jw_score_category_config`、`jw_poi_info`、`jw_peer_bank_info`、`jw_data_access_request`

### AI 模块表
- **`jw_ai_analysis`** — AI分析结果持久化（DDL: `sql/jw_ai_analysis.sql`），字段：`analysis_type`, `entity_key`, `city`, `content`(MEDIUMTEXT), `satisfied`, `expired`

## 常见问题排错

### AI 功能
1. **点击网点诊断无响应**：检查日志中 `ai-sse-*` 线程异常 → 最常见原因是 OkHttp `RequestBody.create()` 参数顺序错误（必须 `create(MediaType, String)`，不能是 `create(String, MediaType)`）；检查 DeepSeek API Key 和网络可达
2. **SSE 有 200 但无数据**：说明 SseEmitter 返回但 @Async 线程卡住 → 检查 `buildBranchContextData` 中的 Mapper 调用是否正常工作
3. **编译期 import 错误**：AI 模块新增文件需确认 `JwMapConfig` 是 `com.ruoyi.jwmap.config`（不是 common）；`AiProperties` 来自 `ai.config` 包
4. **Mapper 方法不存在**：不要假设 Mapper 方法名（如 `selectByBranchIdAndYear` 不存在，实际是 `selectByBranchIdsAndYear`），务必查看 Mapper 接口源码确认
5. **SSE 端点编译错误**：确认 `spring-webmvc` 已在 `jw-map/pom.xml` 中声明；确认 `@RequestMapping("/jwmap/ai")` 路径被 security 的 `permitAll()` 覆盖

### 通用
- 先检查代码，而不是验证用户提出的问题是否存在
- 需要新增远程调用的场景使用已有的 OkHttp 客户端（版本 3.x），新增依赖时注意版本兼容
- 修改原有 service/controller 时，最小化变更范围，新增功能尽量在 ai/ 子包内独立实现