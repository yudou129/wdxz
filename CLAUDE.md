# RuoYi-Vue + jw-map 网点布局优化系统

> **重要规则**: 除非用户明确要求，否则不要自动执行 `git push` 推送操作。创建 commit 后需等待用户指令再推送。

## 项目概述

基于 **RuooYi-Vue 3.9.2** (Spring Boot 2.5.15 + Vue 2.6.12) 的银行网点布局优化系统。通过地理网格分析和 TOPSIS 多准则决策算法，评估网点选址得分和经营效能。

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 2.5.15 |
| Java | JDK | 1.8 |
| ORM | MyBatis (通过 ruoyi-common) | - |
| 数据库连接池 | Druid | 1.2.28 |
| Excel处理 | Apache POI | 4.1.2 |
| 前端框架 | Vue 2 + Element UI | 2.6.12 / 2.15.14 |
| 地图库 | Leaflet | 1.7.1 |
| 图表 | ECharts | 5.4.0 |
| 构建工具 | Maven (后端) / Vue CLI 4 (前端) | - |

## 模块结构

```
RuoYi-Vue-springboot2/
├── pom.xml                  # 父POM，管理7个子模块
├── ruoyi-admin/             # Spring Boot入口 + 通用Controller
├── ruoyi-common/            # 通用工具类、BaseEntity、MyBatis配置
├── ruoyi-framework/         # 安全框架、Shiro配置
├── ruoyi-system/            # 系统管理（用户、角色、菜单等）
├── ruoyi-quartz/            # 定时任务
├── ruoyi-generator/         # 代码生成器
├── jw-map/                  # ★ 网点布局优化模块（核心业务模块）
├── ruoyi-ui/                # Vue前端
└── sql/                     # 数据库初始化脚本
```

## jw-map 模块架构 (后端)

### 包结构
```
com.ruoyi.jwmap/
├── controller/
│   ├── JwComputeController.java   # /jwmap/compute - 计算编排
│   ├── JwDataController.java      # /jwmap/data - 数据查询
│   ├── JwImportController.java    # /jwmap/import - Excel导入
│   └── JwExportController.java    # /jwmap/export - Excel导出
├── domain/        # 13个实体类（见下方数据表映射）
├── mapper/        # 14个MyBatis Mapper接口
├── service/
│   ├── IGridComputeService.java   # 网格计算接口
│   ├── IBranchComputeService.java # 网点计算接口
│   └── impl/
│       ├── GridComputeServiceImpl.java  # 网格5步计算流水线
│       ├── BranchComputeServiceImpl.java # 网点6步计算流水线
│       ├── ExcelExportService.java      # Excel导出（POI SXSSF/XSSF）
│       └── ExcelImportService.java      # Excel导入+自动建指标
└── util/
    └── TopsisCalculator.java      # TOPSIS算法（静态工具类）
```

### 数据库表 (14张)

| 表名 | 实体类 | 用途 |
|---|---|---|
| `jw_poi_info` | JwPoiInfo | POI点位数据（银行、商圈等） |
| `jw_population_heat` | JwPopulationHeat | 人口热力网格数据 |
| `jw_indicator_config` | JwIndicatorConfig | 指标配置（分类、权重标记） |
| `jw_external_resource_weight` | (JwWeightConfig) | 外部资源权重配置 |
| `jw_branch_efficiency_weight` | (JwWeightConfig) | 网点效能权重配置 |
| `jw_grid_meta` | JwGridMeta | 1km网格元数据（坐标、POI数） |
| `jw_grid_data_raw` | JwGridDataRaw | 网格原始指标值 |
| `jw_grid_data_normalized` | JwGridDataNormalized | 网格归一化指标值 |
| `jw_grid_summary` | JwGridSummary | 网格指标统计（max/min/weight） |
| `jw_grid_score` | JwGridScore | 网格TOPSIS选址得分 |
| `jw_branch_info` | JwBranchInfo | 网点基础信息（30+字段） |
| `jw_branch_indicator` | JwBranchIndicator | 网点指标数据（基础/计算/归一化） |
| `jw_branch_summary` | JwBranchSummary | 网点指标统计 |
| `jw_branch_score` | JwBranchScore | 网点TOPSIS评分（5个类别） |

### 核心计算流水线

#### 网格选址评估 (GridComputeServiceImpl, 5步)
1. **computeGridMeta** — 从人口热力数据取唯一网格→计算1km包围盒→统计每个网格内POI数
2. **computeGridRawData** — 将人口热力值+POI计数写入 `jw_grid_data_raw`
3. **computeGridSummary** — 每个指标计算 max/min/weight → `jw_grid_summary`
4. **computeGridNormalized** — 归一化: `value / SQRT(SUMSQ)` → `jw_grid_data_normalized`
5. **computeGridScore** — TOPSIS评分 → `jw_grid_score`

#### 网点效能评估 (BranchComputeServiceImpl, 6步)
1. **assignGridToBranch** — 空间关联：网点坐标是否落在网格包围盒内
2. **computeBranchIndicators** — 从导入的基础数据计算22个衍生指标：
   - 营收类(2): 人均/单位面积营收
   - 指标类(10): 各项资产/储蓄/对公存款人均值+增长率
   - 客户类(7): 个人/对公/机构客户人均值
   - 运营类(3): 单位面积柜面/终端/ATM交易量
3. **computeBranchSummary** — 每个指标计算 max/min/weight
4. **computeBranchNormalized** — 归一化: `value * weight / SQRT(SUMSQ)`
5. **computeBranchScore** — 5个分项TOPSIS得分: revenue/indicator/customer/operation/overall
6. **computeRankings** — 按得分排序生成排名

### API接口汇总

| 前缀 | 方法 | 用途 |
|---|---|---|
| `/jwmap/compute/**` | GET/POST | 城市状态查询、触发网格/网点计算 |
| `/jwmap/data/**` | GET | 查询POI/网格/网点/指标/权重数据 |
| `/jwmap/import/**` | POST (multipart) | 导入POI/人口热力/权重/网点Excel |
| `/jwmap/export/**` | GET (blob) | 导出网格/网点数据为Excel透视表 |

### TOPSIS算法 (TopsisCalculator)
- 核心公式: Score = D⁻ / (D⁺ + D⁻)
- D⁺ = 到正理想解的距离; D⁻ = 到负理想解的距离
- 网格模式: 归一化不带权重，TOPSIS带权重
- 网点模式: 归一化带权重，TOPSIS带权重
- 所有运算为纯静态方法

## 前端架构 (ruoyi-ui)

### 目录结构
```
src/
├── api/jwmap/data.js       # ★ 所有jw-map后端API调用（唯一对接文件）
├── views/jwmap/
│   ├── index.vue           # 数据管理页面（导入/计算/导出/查看4个Tab）
│   └── map/
│       ├── index.vue       # 地图可视化页面（Leaflet + 百度离线瓦片）
│       └── utils/
│           ├── baiduCrs.js        # 百度BD09自定义CRS投影
│           ├── coordConvert.js    # WGS84/GCJ02/BD09坐标转换
│           ├── boundaryManager.js # 行政区划边界管理
│           └── measureTool.js     # 标点+测距工具
├── router/index.js         # 路由配置（JwMap作为dynamicRoutes）
├── store/modules/          # Vuex模块（无jwmap专用模块）
├── utils/request.js        # Axios封装（baseURL=/dev-api, 代理到localhost:8080）
└── components/             # 通用组件
```

### 地图技术细节
- **离线瓦片**: 开发服务器通过 `/tiles/{z}/{x}/{y}.png` 提供本地百度瓦片（标准 XYZ 格式）
- **瓦片路径**: 环境变量 `MAP_TILES_DIR` 默认 `E:/coding/wangdianxuanzhi/mapfile/baidu_road/baidu_road-z9-17`
- **瓦片下载工具**: `E:/coding/wangdianxuanzhi/tiler-master` (Go项目)，使用 `conf_baidu_road.toml` 配置下载
- **坐标系**: BD09坐标通过 EPSG:3857 投影公式映射（`baiduCrs.js`使用标准 `R=6378137`和`HALF=π*R`），与 tiler 瓦片编号精确对齐
- **CRS设计**: 不另做8点校准——tiler 和前端对 BD09 坐标使用相同的 EPSG:3857 投影，瓦片编号天然一致
- **地图中心**: 贵州省 (26.5807, 106.7238)，缩放范围 9-16

### 前端路由
- `/jwmap/map` — 地图可视化（dynamicRoutes，不依赖后端菜单）
- 数据管理页面 — 通过后端动态菜单树渲染

### 环境配置
- 开发环境: `VUE_APP_BASE_API=/dev-api` → 代理到 `http://localhost:8080`
- 生产环境: `VUE_APP_BASE_API=/prod-api`

## 开发运行方式

### 前置依赖
- **MySQL**: 需要运行在 `localhost:3306`，数据库 `ry-vue`（配置见 `application-druid.yml`）
- **Redis**: 需要运行在 `localhost:6379`

### 后端
```bash
# 在项目根目录
mvn clean install -DskipTests
cd ruoyi-admin
mvn spring-boot:run
# 默认端口 8080
```

### macOS 适配说明
项目原始配置针对 Windows/Linux 环境，macOS 上运行需修改以下路径：

| 文件 | 配置项 | 原始值 | macOS 适配值 |
|---|---|---|---|
| `ruoyi-admin/.../logback.xml` | `log.path` | `/home/ruoyi/logs` | `./logs` |
| `ruoyi-admin/.../application.yml` | `profile` (文件上传路径) | `D:/ruoyi/uploadPath` | `./uploadPath` |
| `ruoyi-ui/vue.config.js` | `TILES_DIR` (百度瓦片) | `E:/.../mapfile/guizhou_baidu_tiles` | `../mapfile/guizhou_baidu_tiles` |
| `ruoyi-ui/vue.config.js` | `TIANDITU_VEC_DIR` | `E:/.../mapfile/tianditu_vec/...` | `../mapfile/tianditu_vec/vec_guizhou-z9-17` |
| `ruoyi-ui/vue.config.js` | `TIANDITU_CVA_DIR` | `E:/.../mapfile/tianditu_cva/...` | `../mapfile/tianditu_cva/cva_guizhou-z9-17` |

### 前端
```bash
cd ruoyi-ui
npm install
npm run dev
# 默认端口 80，代理后端到 localhost:8080
```

### 地图瓦片
- 使用 tiler-master 从百度地图下载，配置见 `E:/coding/wangdianxuanzhi/tiler-master/conf_baidu_road.toml`
- 瓦片格式: 标准 XYZ 目录结构 `{z}/{x}/{y}.png`，层级 9-17
- 下载时 tiler 将 WGS84 边界转为 BD09 后，用 EPSG:3857 公式生成瓦片编号
- 已下载瓦片放在 `E:/coding/wangdianxuanzhi/mapfile/baidu_road/baidu_road-z9-17/`

### GeoJSON边界数据
- `boundaryManager.js` 引用 `/data/map_data/{adcode}_full.json`
- 文件需准备好放到 `ruoyi-ui/public/data/map_data/` 下
- 城市编码参考: 520100(贵阳), 520200(六盘水), 520300(遵义), 520400(安顺), 520500(毕节), 520600(铜仁), 522300(黔西南), 522600(黔东南), 522700(黔南)



## 外部工具

### tiler-master (百度瓦片下载)
- 路径: `E:/coding/wangdianxuanzhi/tiler-master/`
- 语言: Go
- 配置文件: `conf_baidu_road.toml`
- 下载范围: 贵州省 (通过 `geojson/guizhou.geojson` 边界)
- 输出: 标准 XYZ 瓦片 → `E:/coding/wangdianxuanzhi/mapfile/baidu_road/baidu_road-z9-17/`
- 关键逻辑: WGS84→BD09 坐标转换后，用 `maptile` 包 (EPSG:3857公式) 生成瓦片编号，请求百度服务器时做 `x/4.516, y/9.312` 修正

## 关键设计决策

1. **jw-map不引入新框架依赖** — 仅依赖 `ruoyi-common`，复用MyBatis/POI/Spring
2. **计算采用"全量重算"策略** — 每次触发计算都会先 `deleteByCity` 再重新生成
3. **导入采用upsert** — 使用MySQL `ON DUPLICATE KEY UPDATE` 支持重复导入
4. **评分分5个类别** — 网点TOPSIS除总分外，还分业务维度计算营收/指标/客户/运营得分
5. **地图全离线运行** — 百度瓦片本地提供，无需在线地图API密钥
6. **前端无专用Vuex模块** — jwmap数据状态在组件内管理，未抽取到store

## Excel结构逆向方法

当需要分析参考Excel表格结构（合并单元格、表头层级、列顺序）时：

1. **用Python openpyxl**（不要用Java POI写程序），因为openpyxl可以直接在REPL中交互式探索
2. **Windows注意**：`python3` 是Windows Store哑存根（返回exit code 49），用 `python` 命令


## 排错流程
应该先检查代码。而不是去验证用户提出的问题是否存在。