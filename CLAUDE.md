# RuoYi-Vue + jw-map 网点布局优化系统

> **重要规则**: 除非用户明确要求，否则不要自动执行 `git push` 推送操作。创建 commit 后需等待用户指令再推送。
>
> **完整项目文档**: `docs/README.md` — 包含数据库表结构、API列表、计算流水线、百度地图集成详情等。

## 项目概述

基于 **RuoYi-Vue 3.9.2** (Spring Boot 2.5.15 + Vue 2.6.12) 的银行网点布局优化系统。通过地理网格分析和 TOPSIS 多准则决策算法，评估网点选址得分和经营效能。

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 2.5.15 |
| Java | JDK | 1.8 |
| ORM | MyBatis | - |
| 数据库连接池 | Druid | 1.2.28 |
| Excel处理 | Apache POI | 4.1.2 |
| 前端框架 | Vue 2 + Element UI | 2.6.12 / 2.15.14 |
| 地图库 | BMapGL (百度地图 WebGL 3.0) | 在线SDK |
| 图表 | ECharts | 5.4.0 |
| 构建工具 | Maven (后端) / Vue CLI 4 (前端) | - |

## 模块结构

```
├── pom.xml                  # 父POM
├── ruoyi-admin/             # Spring Boot入口
├── ruoyi-common/            # 通用工具类
├── ruoyi-framework/         # 安全框架(JWT)
├── ruoyi-system/            # 系统管理
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
├── controller/   # 11个Controller（详见 docs/README.md 第三章）
├── domain/       # 15个实体类
├── mapper/       # 15个Mapper接口 + XML
├── service/      # 3接口 + 6实现类
│   └── impl/
│       ├── GridComputeServiceImpl.java   # 网格选址5步计算流水线
│       ├── BranchComputeServiceImpl.java # 网点效能6步计算流水线
│       ├── ExcelExportService.java       # POI SXSSF导出
│       └── ExcelImportService.java       # Excel导入+自动建指标
└── util/
    └── TopsisCalculator.java      # TOPSIS算法
```

### 关键设计
- **全量重算**: 每次触发计算先 `deleteByCity` 再重新生成
- **导入upsert**: 使用MySQL `ON DUPLICATE KEY UPDATE`
- **安全策略**: `/jwmap/**` 全部 `permitAll()`（无需JWT），数据访问审批在业务层控制
- **权重配置**: 两种权重表 `jw_external_resource_weight`(网格) 和 `jw_branch_efficiency_weight`(网点)

## 前端架构

### 目录结构
```
src/views/jwmap/
├── index.vue                  # 数据管理页面（导入/计算/导出/查看）
├── baidu-map/                 # ★ 百度地图可视化（主要地图页面）
│   ├── index.vue              # 主页面（8个共享组件 + 4个mixins）
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
├── map/components/            # 22个共享UI组件
├── config/indicator.vue       # 指标管理
└── access/                    # 数据查看申请/审批
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
- **MySQL**: localhost:3306 / ry-vue
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

## 排错流程
先检查代码，而不是验证用户提出的问题是否存在。
