# 项目修改变更记录

> 汇总近期所有代码和数据库的修改，方便迁移到内网时参照。

---

## 1. 指标编码重命名（jw_indicator_config）

### 修改内容

对 `jw_indicator_config` 表中无意义的纯数字/`grid_XXXX` 格式 `indicator_code` 重命名为有意义的英文名。

### 涉及数据库表

| 表 | 字段 | 修改内容 |
|----|------|---------|
| `jw_indicator_config` | `indicator_code` | 约 60 条记录从 `grid_XXXX`/纯数字改为有意义的英文编码 |
| `jw_indicator_config` | `parent_code` | 对应更新所有子节点的父编码引用 |
| `jw_grid_score` | `score_category` | `grid_3243` → `grid_biz`（400条） |
| `jw_branch_score` | `score_category` | `branch_raw_5913`→`revenue`, `branch_4568`→`indicator`, `branch_170`→`operation`, `branch_7584`→`customer`（250条） |

### 重命名映射

#### Grid 一级节点

| 旧编码 | 新编码 | 名称 |
|--------|-------|------|
| `grid_3243` | `grid_biz` | 商业聚集 |

#### Grid 二级节点（人口 pillar）

| 旧编码 | 新编码 | 名称 |
|--------|-------|------|
| `pop_2` | `grid_pop_resident` | 常住、流动人口 |
| `grid_8696` | `grid_pop_age` | 年龄 |
| `grid_9133` | `grid_pop_income` | 收入水平 |
| `grid_9881` | `grid_pop_education` | 教育水平 |
| `grid_7744` | `grid_pop_industry` | 所在行业 |
| `grid_1637` | `grid_pop_job` | 职业类别 |

#### Grid 二级节点（商业 pillar）

| 旧编码 | 新编码 | 名称 |
|--------|-------|------|
| `grid_48` | `grid_biz_lifecycle` | 人生阶段 |
| `grid_5543` | `grid_biz_asset` | 资产状况 |
| `grid_7683` | `grid_biz_consume` | 消费水平 |
| `grid_poi` | `grid_biz_poi` | POI |

#### Grid 二级节点（企业 pillar）

| 旧编码 | 新编码 | 名称 |
|--------|-------|------|
| `grid_8498` | `grid_ent_company` | 公司 |
| `grid_9293` | `grid_ent_office` | 写字楼 |

#### Grid 三级节点（行业）

`grid_363`→`grid_ind_agriculture`, `grid_1012`→`grid_ind_education`, `grid_157`→`grid_ind_legal`, `grid_2035`→`grid_ind_chemical`, `grid_3747`→`grid_ind_finance`, `grid_4469`→`grid_ind_food`, `grid_45`→`grid_ind_transport`, `grid_5393`→`grid_ind_construction`, `grid_5511`→`grid_ind_public`, `grid_6026`→`grid_ind_automotive`, `grid_6514`→`grid_ind_advertising`, `grid_654`→`grid_ind_tourism`, `grid_7221`→`grid_ind_home`, `grid_7225`→`grid_ind_sports`, `grid_7265`→`grid_ind_appliance`, `grid_8008`→`grid_ind_energy`, `grid_8114`→`grid_ind_medical`, `grid_8268`→`grid_ind_textile`, `grid_9145`→`grid_ind_service`, `grid_9209`→`grid_ind_machinery`, `grid_9997`→`grid_ind_catering`, `it`→`grid_ind_it`

#### Grid 三级节点（年龄/收入/教育/职业/居住等）

| 旧编码 | 新编码 |
|--------|-------|
| `2534/3544/4554/55` | `grid_age_25_34/grid_age_35_44/grid_age_45_54/grid_age_55plus` |
| `20000/800019999/40007999` | `grid_income_high/grid_income_mid/grid_income_low` |
| `grid_2817/grid_8856` | `grid_edu_college/grid_edu_junior` |
| `ent_2/grid_2332/grid_3115/grid_8067/grid_9134` | `grid_job_manager/grid_job_worker/grid_job_selfemployed/grid_job_professional/grid_job_clerk` |
| `grid_2310/grid_871` | `grid_resident_home/grid_resident_work` |

#### Grid 三级节点（人生阶段/资产/消费/POI）

| 旧编码 | 新编码 |
|--------|-------|
| `01/13/36/grid_7600/grid_5800/grid_704/grid_8287` | `grid_life_baby/grid_life_toddler/grid_life_child36/grid_life_pregnant/grid_life_highschool/grid_life_middleschool/grid_life_primaryschool` |
| `grid_9265/grid_6405` | `grid_asset_car/grid_asset_nocar` |
| `grid_3843/grid_4258/grid_6205` | `grid_consume_high/grid_consume_mid/grid_consume_low` |
| `grid_1849/grid_2741/grid_3301/grid_5067/grid_5367/grid_8868/grid_6458/grid_9458/grid_9896` | `grid_poi_mall/grid_poi_supermarket/grid_poi_food/grid_poi_market/grid_poi_pharmacy/grid_poi_hotel/grid_poi_convenience/grid_poi_gym/grid_poi_media` |

#### Branch Raw 根节点

| 旧编码 | 新编码 | 名称 |
|--------|-------|------|
| `branch_raw_5913` | `branch_raw_revenue` | 经营情况 |
| `branch_4568` | `branch_raw_performance` | 业绩表现 |
| `branch_170` | `branch_raw_operation` | 业务运营 |
| `branch_7584` | `branch_raw_customer` | 客户发展 |

#### Branch Raw 子节点

`branch_1201`→`branch_raw_savings`, `corp_dep`→`branch_raw_corp_dep`, `inst_dep`→`branch_raw_inst_dep`, `total_asset`→`branch_raw_total_asset`, `inclusive_loan`→`branch_raw_incl_loan`, `personal_loan`→`branch_raw_personal_loan`, `branch_1512`→`branch_raw_personal_cust`, `branch_4963`→`branch_raw_inst_cust`, `branch_9876`→`branch_raw_corp_cust`, `branch_raw_8804`→`branch_raw_incl_cust`, `interest_income`→`branch_raw_interest_income`, `branch_raw_1596`→`branch_raw_fee_income`, `branch_1274`→`branch_raw_cust_total`

#### Branch（派生）根节点

| 旧编码 | 新编码 | 名称 |
|--------|-------|------|
| `branch_raw_5913`(type=branch) | `branch_revenue` | 经营情况 |
| `branch_4568`(type=branch) | `branch_performance` | 业绩表现 |
| `branch_170`(type=branch) | `branch_operation` | 业务运营 |
| `branch_7584`(type=branch) | `branch_customer` | 客户发展 |

#### Branch（派生）子节点

`avg_balance_4`~`avg_balance_7`, `avg_growth_4`~`avg_growth_7` 等派生指标的 `parent_code` 同步更新。

### 涉及后段代码

| 文件 | 方法 | 修改内容 |
|------|------|---------|
| `AiDataAggregator.java` | `buildGridContextData()` | pillars, popRootChildren, bizRootChildren, enterprise pillar 中的旧编码改为新编码 |
| `AiDataAggregator.java` | `buildWeightContextData()` | weightTypes 和 typeNames 中的旧编码改为新编码 |
| `AiDataAggregator.java` | `buildPerBranchQuadrantContextData()` | pillars 中的旧编码改为新编码 |
| `AiDataAggregator.java` | `getScoreCategoryName()` | 删除多余的4行旧映射 |

### 涉及前端代码

| 文件 | 修改内容 |
|------|---------|
| `BranchScores.vue` | label 映射中的简称改为全称（`营收`→`经营情况` 等），CAT_COLORS 顺序调整 |
| `ComparisonPanel.vue` | `CATEGORY_NAME_FALLBACK` 中的简称改为全称 |

---

## 2. 审批人管辖逻辑修改

### 修改内容

`JwDataAccessServiceImpl.getReviewerDeptIds()` 方法重写。

### 旧逻辑

```java
// 通过 sys_role_dept 查角色关联的部门
List<Long> deptIds = sysDeptMapper.selectDeptListByRoleId(role.getRoleId(), false);
// 展开所有子孙部门
for (Long deptId : deptIds) {
    List<SysDept> children = sysDeptMapper.selectChildrenDeptById(deptId);
    children.forEach(d -> expanded.add(d.getDeptId()));
}
```

### 新逻辑

```java
// 审批人所在部门的直接子部门
SysDept query = new SysDept();
query.setParentId(user.getDeptId());
List<SysDept> children = sysDeptMapper.selectDeptList(query);
return children.stream().map(SysDept::getDeptId).collect(Collectors.toList());
```

### 效果

| 审批人所在部门 | 管辖的部门 |
|---------------|-----------|
| 省行(201) | 贵阳分行(210)、遵义分行(220)、六盘水分行(230)... |
| 贵阳分行(210) | 清镇市支行(232)、乌当区支行(233)...共10个 |
| 遵义分行(220) | 遵义分行营业部(242)、红花岗区支行(243)...共5个 |

### 涉及文件

| 文件 | 修改内容 |
|------|---------|
| `JwDataAccessServiceImpl.java` | 重写 `getReviewerDeptIds()` 方法 |

---

## 3. AI 功能 UX 优化

### 3.1 新增 AiDrawer 组件

将 AI 分析内容从 SidebarPanel/ComparisonPanel/QuadrantChart 的内嵌卡片改为**右侧独立抽屉**

| 文件 | 操作 |
|------|------|
| `map/components/AiDrawer.vue` | **新建** — 右侧抽屉 + Tab 管理 + 事件转发 |

### 3.2 重写 AiAnalysisCard

| 文件 | 操作 |
|------|------|
| `map/components/AiAnalysisCard.vue` | **重写** — rAF 节流渲染、3阶段思考动画、增强Markdown渲染、复制功能、5种类型配色、反馈栏时机修正 |

### 3.3 移除内嵌卡片，改用抽屉

| 文件 | 操作 |
|------|------|
| `baidu-map/index.vue` | 集成 AiDrawer、新增 `aiTabsData` 统一管理AI状态、`updateAiTab()` 响应式更新 |
| `map/components/SidebarPanel.vue` | 移除 AiAnalysisCard 内嵌，改为 emit 触发抽屉，AI 按钮移到醒目位置，Split模式支持 AI |
| `map/components/ComparisonPanel.vue` | 移除 AiAnalysisCard 内嵌，AI 按钮醒目化 |
| `map/components/QuadrantChart.vue` | 移除 AI 分析入口，散点图保留 |

### 3.4 Bug 修复

| 问题 | 修复 |
|------|------|
| 网格AI分析错误调用选址建议 | `index.vue:256` 用 `blankSpot` 标记替代 `nearestBranch` 判断 |
| 切换网格/网点后状态不重置 | `closeSidebar()` 重置 `gridAiState`/`branchAiState` |
| site 和 grid 作为两个独立 Tab | 统一为 `grid` Tab，通过 mode 字段区分选址/分析 |
| 选址报告按钮事件链路断裂 | AiDrawer 转发 `generateReport` 事件到父组件 |
| 权重助手无入口 | SidebarPanel 加权重助手按钮 + AiDrawer weight Tab + `loadWeightAi()` |
| 选址报告下载被浏览器拦截 | `window.open` 替换为 `<a>` 标签点击下载 |
| 效能得分展示 code 而非 name | `BranchScores.vue` 和 `ComparisonPanel.vue` 中的映射改用全称 |

### 涉及前端文件

| 文件 | 操作 |
|------|------|
| `api/jwmap/ai.js` | 新增 `getPerBranchQuadrantStream()`、`getPerBranchQuadrantCached()` API |

---

## 4. 新功能：单网点四象限深度分析

### 后端新增

| 文件 | 操作 |
|------|------|
| `IAiService.java` | 新增 `perBranchQuadrantAnalysisStream()`, `getPerBranchQuadrantCached()` |
| `AiController.java` | 新增 `GET /quadrant-analysis/stream/per-branch` + 存量查询端点 |
| `AiServiceImpl.java` | 新增 `perBranchQuadrantAnalysisStream()` 实现 |
| `AiDataAggregator.java` | 新增 `buildPerBranchQuadrantContextData()` |
| `AiPromptBuilder.java` | 新增 `buildPerBranchQuadrantAnalysisUserMessage()` |

### 前端新增

| 文件 | 操作 |
|------|------|
| `api/jwmap/ai.js` | 新增 `getPerBranchQuadrantStream()`、`getPerBranchQuadrantCached()` |
| `baidu-map/index.vue` | 新增 `loadPerBranchQuadrantAi()` 方法 |
| `map/components/SidebarPanel.vue` | 四象限卡片内新增 "AI象限深度分析" 按钮 |

---

## 5. 文档更新

| 文件 | 操作 |
|------|------|
| `docs/MIGRATION.md` | **新建** — 完整迁移文档（数据库DDL、配置文件、菜单/角色/权限、测试用户数据） |
| `docs/ai_prompts.md` | **新建** — AI 提示词文档（7个功能完整提示词模板） |
| `docs/indicator_code_rename.md` | **新建** — 指标编码重命名映射表 |
| `docs/changes_summary.md` | **当前文档** |
| `AiPromptBuilder.java` | 类注释增加指向 `docs/ai_prompts.md` 的引用 |

---

## 6. 数据库初始化注意事项

迁移到内网时，需要在 SQL 导入后执行以下额外脚本：

```sql
-- 1. 更新 jw_grid_score（grid_3243 → grid_biz）
UPDATE jw_grid_score SET score_category = 'grid_biz' WHERE score_category = 'grid_3243';

-- 2. 更新 jw_branch_score（旧编码 → 标准分类）
UPDATE jw_branch_score SET score_category = 'revenue' WHERE score_category = 'branch_raw_5913';
UPDATE jw_branch_score SET score_category = 'indicator' WHERE score_category = 'branch_4568';
UPDATE jw_branch_score SET score_category = 'customer' WHERE score_category = 'branch_7584';
UPDATE jw_branch_score SET score_category = 'operation' WHERE score_category = 'branch_170';

-- 3. jw_indicator_config 的编码重命名（使用完整的 rename_indicators.sql + rename_grid_fix.sql）
```

---

## 7. Excel 导入支持 .xls 旧格式

### 修改内容

`ExcelImportService.java` 中所有 5 个导入方法原硬编码 `XSSFWorkbook`（仅支持 .xlsx），改为使用 POI 的 `WorkbookFactory.create(inputStream)` 自动检测文件格式，同时兼容 .xls（OLE2）和 .xlsx（OOXML）。

### 涉及文件

| 文件 | 修改 |
|------|------|
| `jw-map/src/main/java/com/ruoyi/jwmap/service/impl/ExcelImportService.java` | 移除 `XSSFWorkbook` import；5 处 `new XSSFWorkbook(inputStream)` → `WorkbookFactory.create(inputStream)` |
| `jw-map/src/main/java/com/ruoyi/jwmap/controller/JwImportController.java` | 无需修改（已允许 `.xls` 扩展名） |

### 变更详情

| 方法 | 行号 |
|------|------|
| `importPoiInfo()` | `try-with-resources` 处 |
| `importPopulationHeat()` | `try-with-resources` 处 |
| `importBranchInfo()` | `try-with-resources` 处 |
| `importExistingBranch()` | `try-with-resources` 处 |
| `importPeerBank()` | `try-with-resources` 处 |

### 技术要点

- `WorkbookFactory.create()` 根据文件魔数自动判断格式，返回 `HSSFWorkbook`（.xls）或 `XSSFWorkbook`（.xlsx）
- 代码中所有行列单元格操作已使用 `org.apache.poi.ss.usermodel.*` 通用接口，无需强制类型转换
- POI 4.1.2 的 `poi-ooxml` 依赖已传递依赖 `poi` 基础包，无需新增 Maven 依赖
- 前端导入页面无需修改（文件选择框已支持 `.xls` 选择）

---

## 8. Excel 导入性能优化（批量插入）

### 问题

所有导入方法都是逐行单条 INSERT/upsert，对于大数据量（如 2000 网格 × 30 指标 = 60000 条人口热力数据），每次一条 SQL 往返，耗时可长达数分钟。

### 优化策略

全量覆盖场景（导入前已 `deleteByCity`）下，将逐行 upsert 改为积累后调用 `batchInsert` 批量插入，每批 500 条。

### 涉及文件

| 文件 | 修改 |
|------|------|
| `jw-map/.../service/impl/ExcelImportService.java` | 5 个导入方法全部改为批量模式 |
| `jw-map/.../mapper/JwPeerBankInfoMapper.java` | 新增 `batchInsert()` 接口方法 |

### 各方法优化详情

| 方法 | 优化内容 |
|------|---------|
| `importPoiInfo` | 逐行 `upsertPoiInfo` → 积累 List，每 500 条调用 `poiInfoMapper.batchInsert()` |
| `importPopulationHeat` | 逐行 `upsertPopulationHeat` + 逐网格 `selectByGridCode` 检查 → 去重集合 + 批量 `batchInsert`（热力数据和网格元信息分别批量） |
| `importBranchInfo` | 网点基础信息 `selectByBranchCode`+INSERT/UPDATE → `upsertJwBranchInfo`；指标逐行 INSERT → 积累 List 批量 `branchIndicatorMapper.batchInsert()` |
| `importExistingBranch` | 逐行 `upsertJwBranchInfo` → 积累 List 每 500 条调用 `branchInfoMapper.batchInsert()` |
| `importPeerBank` | 逐行 `upsertJwPeerBankInfo` + 逐行 `selectByPoint` 空间查询 → 预加载网格列表（内存中判断归属）+ 积累 List 批量 `batchInsert` |

### 常量

- `BATCH_SIZE = 500`：每批次积累的数据行数

---

## 9. PostgreSQL 方言 Mapper 文件

### 修改内容

在 `jw-map/src/main/resources/mapper/postMapper/` 目录下创建完整的 PostgreSQL 方言版本 MyBatis XML Mapper，与原有 MySQL 版本保持相同的 Mapper 接口 namespace。

### 涉及文件（16 个）

全部位于 `jw-map/src/main/resources/mapper/postMapper/jwmap/`（含 `ai/` 子目录）：

| MySQL 语法 | PostgreSQL 转换 |
|---|---|
| `sysdate()` | `now()` |
| `ON DUPLICATE KEY UPDATE` + `VALUES(col)` | `ON CONFLICT (columns) DO UPDATE SET col = EXCLUDED.col` |
| `CONCAT('%', #{x}, '%')` | `'%' \|\| #{x} \|\| '%'` |
| `IFNULL(a, b)` | `COALESCE(a, b)` |
| `DELETE d FROM tbl d JOIN ...` | `DELETE FROM tbl USING ...` |

### 冲突列参照（ON CONFLICT）

| 表 | 冲突列 | 约束 |
|---|---|---|
| `jw_branch_info` | `branch_code` | uk_branch_code |
| `jw_branch_indicator` | `(branch_id, data_year, sheet_type, indicator_code)` | uk_branch_year_sheet_indicator |
| `jw_branch_score` | `(branch_id, data_year, city, score_category)` | uk_branch_year_city_cat |
| `jw_branch_summary` | `(city, data_year, indicator_code)` | uk_city_year_indicator |
| `jw_grid_meta` | `grid_code` | PRIMARY KEY |
| `jw_grid_data_raw` | `(grid_code, indicator_code)` | uk_grid_indicator |
| `jw_grid_data_normalized` | `(grid_code, indicator_code)` | uk_grid_indicator |
| `jw_grid_summary` | `(city, indicator_code)` | uk_city_indicator |
| `jw_grid_score` | `(grid_code, score_category)` | COMPOSITE PRIMARY KEY |
| `jw_indicator_config` | `indicator_code` | uk_indicator_code |
| `jw_poi_info` | `(org_code, poi_name, longitude, latitude)` | uk_poi |
| `jw_peer_bank_info` | `org_code` | uk_org_code |
| `jw_population_heat` | `(grid_code, indicator_code)` | uk_grid_indicator |
| `jw_ai_analysis` | `(analysis_type, entity_key)` | UNIQUE KEY |

---

## 10. 高斯数据库兼容改造（去除 ON CONFLICT）

### 背景

内网高斯数据库（PostgreSQL 兼容模式）不支持 `ON CONFLICT` 语法，所有 upsert 操作需要改为先删后插或 SELECT + INSERT/UPDATE。

### 涉及文件

| 文件 | 修改内容 |
|---|---|
| `ExcelImportService.java` | `upsertJwBranchInfo` → `selectByBranchCode` + `insertBranchInfo`/`updateBranchInfo` |
| `ExcelImportService.java` | `importExistingBranch` 批量 batchInsert → 逐行 SELECT+INSERT/UPDATE |
| `ExcelImportService.java` | `importPopulationHeat` 新增 `gridMetaMapper.deleteByCity(city)` 避免 batchInsert 主键冲突 |
| `GridComputeServiceImpl.java` | `upsertGridMeta` → `updateJwGridMeta`（纯 UPDATE） |
| `BranchComputeServiceImpl.java` | `upsertBranchIndicator` → `insertJwBranchIndicator`（先删后插） |
| `BranchComputeServiceImpl.java` | `upsertBranchScore` → 积累 List 后 `batchInsert` |

### 批量插入优化

网点计算中各步骤改为批量插入：

| 方法 | 原方式 | 改为 |
|---|---|---|
| `computeBranchIndicators` | 逐行 `insertJwBranchIndicator` | 积累 List → `batchInsert` |
| `computeBranchSummary` | 逐行 `insertBranchSummary` | 积累 List → `batchInsert` |
| `computeBranchNormalized` | 逐行 `insertJwBranchIndicator` | 积累 List → `batchInsert` |
| `computeBranchScore` | 逐行 `insertJwBranchScore` | 积累 List → `branchScoreMapper.batchInsert` |
| `computeGridScore` | 已批量 | 不变 |
| `computeGridRawData` | 已批量 | 不变 |
| `computeGridNormalized` | 已批量 | 不变 |

### 人口热力导入省市区字段补齐

`importPopulationHeat` 指标列从 `c=3` 改为 `c=6`，读取省市区三列写入 `jw_grid_meta`：

- `cell(3)` → `province`
- `cell(4)` → `cityName`（参考读取）
- `cell(5)` → `district`
```
