# jw-map 网点布局优化系统 — 迁移部署指南

> 基于 **RuoYi-Vue 3.9.2**（Spring Boot 2.5.15 + Vue 2.6.12）
> 本指南帮助将 jw-map 模块迁移到内网已有若依框架中
>
> 最后更新: 2026-07-20

---

## 目录

1. [迁移总览](#一迁移总览)
2. [前置准备](#二前置准备)
3. [数据库迁移](#三数据库迁移)
4. [后端迁移](#四后端迁移)
5. [前端迁移](#五前端迁移)
6. [百度地图迁移（关键）](#六百度地图迁移关键)
7. [AI 模块迁移](#七ai-模块迁移)
8. [配置项完整清单](#八配置项完整清单)
9. [验证与测试](#九验证与测试)
10. [常见问题](#十常见问题)
11. [附录：项目结构与API参考](#十一附录项目结构与api参考)

---

## 一、迁移总览

### 1.1 需要迁移的内容

| 内容 | 源位置 | 说明 |
|------|--------|------|
| jw-map 模块代码 | `jw-map/` | 整个模块目录（Java + XML 映射文件） |
| 前端 jwmap 页面 | `ruoyi-ui/src/views/jwmap/` | Vue 页面、组件、工具 |
| 前端 API 封装 | `ruoyi-ui/src/api/jwmap/` | 后端 API 调用封装 |
| GeoJSON 边界数据 | `ruoyi-ui/public/data/map_data/` | 贵州省各地行政区边界 |
| 数据库 DDL | `sql/` 目录 | 18+ 张业务表 |
| 菜单/权限 SQL | `sql/jw_baidu_map_menu.sql` | 菜单注册 |

### 1.2 迁移后需修改的核心配置

| 优先级 | 配置项 | 文件位置 | 修改内容 |
|--------|--------|----------|----------|
| 🔴 必改 | 数据库连接 | `application-druid.yml` | url/username/password |
| 🔴 必改 | Redis 连接 | `application.yml` | host/password |
| 🔴 必改 | 父 POM 模块声明 | 根 `pom.xml` | 添加 `<module>jw-map</module>` |
| 🔴 必改 | admin POM 依赖 | `ruoyi-admin/pom.xml` | 添加 jw-map 依赖 |
| 🔴 必改 | 百度地图 AK | `sdkLoader.js:5` | 改为内网可用密钥 |
| 🔴 必改 | AI API Key | `application.yml` → `ai.api-key` | 改为内网 LLM 密钥 |
| 🟡 建议改 | 安全配置 | `JwMapSecurityConfig.java` | 确认 `/jwmap/**` 放行 |
| 🟡 建议改 | CORS 跨域 | `application.yml` | 允许内网前端域名 |
| 🟡 建议改 | 上传/报告路径 | `application.yml` → `ruoyi.profile` | 改为内网绝对路径 |
| 🟡 建议改 | 日志路径 | `logback.xml` | 改为内网路径 |

### 1.3 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 2.5.15 |
| JDK | Java | 1.8 |
| 数据库 | MySQL | 5.7+ / 8.0 |
| ORM | MyBatis | Spring Boot 内置 |
| 连接池 | Druid | 1.2.28 |
| Excel | Apache POI (SXSSF) | 4.1.2 |
| HTTP 客户端 | OkHttp | 4.12.0（jw-map 模块特有） |
| JSON | FastJSON | 1.2.83（AI模块）/ 2.x（框架） |
| 前端框架 | Vue 2 + Element UI | 2.6.12 / 2.15.14 |
| 地图库 | BMapGL (百度地图 WebGL) | 在线 SDK |
| 图表 | ECharts | 5.4.0 |
| AI 模型 | DeepSeek V4 Flash | OpenAI 兼容协议 |

---

## 二、前置准备

### 2.1 确认内网环境

- [ ] MySQL 5.7+ / 8.0 可用，创建数据库 `ry-vue`
- [ ] Redis 可用
- [ ] JDK 1.8 已安装
- [ ] Node.js（前端构建用）
- [ ] Maven 3.6+
- [ ] 内网 LLM API 网关（AI 功能需要）
- [ ] 百度地图 AK 或内网地图方案（地图需要）

### 2.2 确认内网若依版本

迁移目标必须是 **RuoYi-Vue 3.9.2 或兼容版本**。检查方法：

```bash
# 查看内网项目根 pom.xml 中的版本
grep '<version>' pom.xml | head -3
# 应为 3.9.2
```

如果版本不一致，需要调整：
- **Spring Boot 2.x 系列**：大概率兼容，只需检查依赖版本差异
- **Spring Boot 3.x 系列**：不兼容（javax → jakarta 迁移，需大量修改）

---

## 三、数据库迁移

### 3.1 建表语句（从当前运行数据库导出）

以下 **17 张 jw_ 表** 的 DDL 直接从当前运行数据库中导出，完全匹配代码中的 Mapper XML：

#### `jw_grid_meta` — 网格元数据（1km×1km）
```sql
CREATE TABLE `jw_grid_meta` (
  `grid_code` varchar(64) NOT NULL COMMENT '网格编号（主键，如:GZGY云岩0001）',
  `longitude` decimal(12,8) DEFAULT NULL COMMENT '经度（网格中心点）',
  `latitude` decimal(12,8) DEFAULT NULL COMMENT '纬度（网格中心点）',
  `west_longitude` decimal(12,8) DEFAULT NULL COMMENT '西经',
  `east_longitude` decimal(12,8) DEFAULT NULL COMMENT '东经',
  `north_latitude` decimal(12,8) DEFAULT NULL COMMENT '北纬',
  `south_latitude` decimal(12,8) DEFAULT NULL COMMENT '南纬',
  `province` varchar(32) DEFAULT '' COMMENT '省',
  `city` varchar(32) DEFAULT '' COMMENT '市',
  `district` varchar(32) DEFAULT '' COMMENT '区县',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`grid_code`),
  KEY `idx_city_district` (`city`,`district`),
  KEY `idx_coord` (`longitude`,`latitude`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='网格元信息表（1km×1km网格，bounding box）';
```

#### `jw_population_heat` — 人口热力（垂直存储）
```sql
CREATE TABLE `jw_population_heat` (
  `heat_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `grid_code` varchar(64) NOT NULL COMMENT '网格编号（关联jw_grid_meta）',
  `indicator_code` varchar(64) NOT NULL COMMENT '指标编码（关联jw_indicator_config）',
  `indicator_value` decimal(16,4) DEFAULT '0.0000' COMMENT '指标值',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`heat_id`),
  UNIQUE KEY `uk_grid_indicator` (`grid_code`,`indicator_code`),
  KEY `idx_grid` (`grid_code`)
) ENGINE=InnoDB AUTO_INCREMENT=108386 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='人口热力表（垂直存储，网格×指标）';
```

#### `jw_grid_data_raw` — 网格原始指标数据（垂直存储）
```sql
CREATE TABLE `jw_grid_data_raw` (
  `data_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `grid_code` varchar(64) NOT NULL COMMENT '网格编号（关联jw_grid_meta）',
  `indicator_code` varchar(64) NOT NULL COMMENT '指标编码（关联jw_indicator_config）',
  `indicator_value` decimal(16,4) DEFAULT '0.0000' COMMENT '指标原始值',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`data_id`),
  UNIQUE KEY `uk_grid_indicator` (`grid_code`,`indicator_code`),
  KEY `idx_grid` (`grid_code`),
  KEY `idx_indicator` (`indicator_code`)
) ENGINE=InnoDB AUTO_INCREMENT=182085 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='网格原始指标数据表（垂直存储，导出原始数据Sheet）';
```

#### `jw_grid_data_normalized` — 网格归一化数据（垂直存储）
```sql
CREATE TABLE `jw_grid_data_normalized` (
  `data_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `grid_code` varchar(64) NOT NULL COMMENT '网格编号（关联jw_grid_meta）',
  `indicator_code` varchar(64) NOT NULL COMMENT '指标编码（关联jw_indicator_config）',
  `normalized_value` decimal(16,10) DEFAULT '0.0000000000' COMMENT '归一化值',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`data_id`),
  UNIQUE KEY `uk_grid_indicator` (`grid_code`,`indicator_code`),
  KEY `idx_grid` (`grid_code`)
) ENGINE=InnoDB AUTO_INCREMENT=142900 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='网格归一化指标数据表（垂直存储）';
```

#### `jw_grid_summary` — 网格汇总（权重+MAX/MIN）
```sql
CREATE TABLE `jw_grid_summary` (
  `summary_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `city` varchar(32) NOT NULL COMMENT '市',
  `indicator_code` varchar(64) NOT NULL COMMENT '指标编码（关联jw_indicator_config）',
  `actual_weight` decimal(16,10) DEFAULT '0.0000000000' COMMENT '实际权重（来自jw_external_resource_weight）',
  `max_raw` decimal(16,4) DEFAULT '0.0000' COMMENT '原始值最大值',
  `min_raw` decimal(16,4) DEFAULT '0.0000' COMMENT '原始值最小值',
  `max_norm` decimal(16,10) DEFAULT NULL COMMENT '归一化后最大值',
  `min_norm` decimal(16,10) DEFAULT NULL COMMENT '归一化后最小值',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`summary_id`),
  UNIQUE KEY `uk_city_indicator` (`city`,`indicator_code`)
) ENGINE=InnoDB AUTO_INCREMENT=562 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='网格指标汇总表（权重/MAX/MIN，驱动导出汇总行）';
```

#### `jw_grid_score` — 网格 TOPSIS 得分（驱动热力图）
```sql
CREATE TABLE `jw_grid_score` (
  `grid_code` varchar(64) NOT NULL COMMENT '网格编号（主键，关联jw_grid_meta）',
  `city` varchar(32) DEFAULT '' COMMENT '所属市',
  `positive_distance` decimal(16,10) DEFAULT NULL COMMENT '正理想解欧式距离 D+',
  `negative_distance` decimal(16,10) DEFAULT NULL COMMENT '负理想解欧式距离 D-',
  `site_score` decimal(16,10) DEFAULT NULL COMMENT '选址得分 = D- / (D+ + D-)',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `score_category` varchar(32) NOT NULL DEFAULT 'overall' COMMENT '得分类别: population/enterprise/business/overall',
  PRIMARY KEY (`grid_code`,`score_category`),
  KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='网格得分表（TOPSIS计算结果，驱动地图热力展示）';
```

---

#### `jw_branch_info` — 网点基础信息
```sql
CREATE TABLE `jw_branch_info` (
  `branch_id` bigint NOT NULL AUTO_INCREMENT COMMENT '网点主键ID',
  `primary_branch` varchar(100) DEFAULT '' COMMENT '一级支行',
  `secondary_branch` varchar(100) DEFAULT '' COMMENT '二级支行',
  `branch_code` varchar(32) DEFAULT '' COMMENT '网点号（4位数字编码）',
  `city` varchar(32) DEFAULT '' COMMENT '所属市',
  `grid_code` varchar(64) DEFAULT '' COMMENT '所属1km网格编号（空间关联jw_grid_meta）',
  `district_name` varchar(64) DEFAULT '' COMMENT '行政区',
  `street` varchar(100) DEFAULT '' COMMENT '街道',
  `address` varchar(500) DEFAULT '' COMMENT '具体地址',
  `longitude` decimal(12,8) DEFAULT NULL COMMENT '经度',
  `latitude` decimal(12,8) DEFAULT NULL COMMENT '纬度',
  `total_staff` int DEFAULT '0' COMMENT '总人数',
  `personal_manager` int DEFAULT '0' COMMENT '个人客户经理人数',
  `corporate_manager` int DEFAULT '0' COMMENT '对公客户经理人数',
  `counter_staff` int DEFAULT '0' COMMENT '客服经理（柜面）人数',
  `lobby_staff` int DEFAULT '0' COMMENT '客服经理（厅堂）人数',
  `branch_manager` varchar(64) DEFAULT '' COMMENT '网点行长姓名',
  `manager_tenure` varchar(100) DEFAULT '' COMMENT '任职时间',
  `manager_resume` text COMMENT '完整履历',
  `manager_history` text COMMENT '历任行长记录',
  `total_area` decimal(10,2) DEFAULT '0.00' COMMENT '营业面积-总面积',
  `other_floor_area` decimal(10,2) DEFAULT '0.00' COMMENT '非首层面积',
  `cash_counter` int DEFAULT '0' COMMENT '现金柜台个数',
  `non_cash_counter` int DEFAULT '0' COMMENT '非现金柜台个数',
  `manager_seat` int DEFAULT '0' COMMENT '个人客户经理工位数',
  `property_right` varchar(32) DEFAULT '' COMMENT '产权状态（自有/租赁）',
  `lease_expire` varchar(32) DEFAULT '' COMMENT '租赁到期时间',
  `last_renovation` varchar(32) DEFAULT '' COMMENT '最近一次装修时间',
  `branch_type` varchar(64) DEFAULT '' COMMENT '网点业态分类',
  `relocation` varchar(100) DEFAULT '' COMMENT '迁并情况',
  `data_source` varchar(32) DEFAULT '网点信息' COMMENT '数据来源',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标记（0存在 2删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`branch_id`),
  UNIQUE KEY `uk_branch_code` (`branch_code`),
  KEY `idx_city` (`city`),
  KEY `idx_district` (`district_name`),
  KEY `idx_primary_branch` (`primary_branch`),
  KEY `idx_grid_code` (`grid_code`)
) ENGINE=InnoDB AUTO_INCREMENT=232 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='网点基本信息表';
```

#### `jw_branch_indicator` — 网点经营指标（垂直存储）
```sql
CREATE TABLE `jw_branch_indicator` (
  `indicator_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `branch_id` bigint NOT NULL COMMENT '网点ID（关联jw_branch_info）',
  `data_year` int NOT NULL COMMENT '数据年份（如:2024）',
  `sheet_type` varchar(32) DEFAULT '基础数据' COMMENT 'Sheet类型: 基础数据 / 数据计算表 / 数据计算表归一化',
  `indicator_code` varchar(64) NOT NULL COMMENT '指标编码（关联jw_indicator_config）',
  `indicator_value` decimal(16,4) DEFAULT '0.0000' COMMENT '指标值',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`indicator_id`),
  UNIQUE KEY `uk_branch_year_sheet_indicator` (`branch_id`,`data_year`,`sheet_type`,`indicator_code`),
  KEY `idx_branch_year` (`branch_id`,`data_year`),
  KEY `idx_indicator` (`indicator_code`)
) ENGINE=InnoDB AUTO_INCREMENT=37156 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='网点业务指标表（垂直存储，网点×年×Sheet×指标）';
```

#### `jw_branch_summary` — 网点指标汇总
```sql
CREATE TABLE `jw_branch_summary` (
  `summary_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `city` varchar(32) NOT NULL COMMENT '市',
  `data_year` int NOT NULL COMMENT '数据年份',
  `indicator_code` varchar(64) NOT NULL COMMENT '指标编码（关联jw_indicator_config）',
  `actual_weight` decimal(16,10) DEFAULT '0.0000000000' COMMENT '实际权重（来自jw_branch_efficiency_weight）',
  `max_value` decimal(16,4) DEFAULT '0.0000' COMMENT '该市该年该指标最大值',
  `min_value` decimal(16,4) DEFAULT '0.0000' COMMENT '该市该年该指标最小值',
  `max_norm` decimal(16,10) DEFAULT NULL COMMENT '归一化后最大值',
  `min_norm` decimal(16,10) DEFAULT NULL COMMENT '归一化后最小值',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`summary_id`),
  UNIQUE KEY `uk_city_year_indicator` (`city`,`data_year`,`indicator_code`)
) ENGINE=InnoDB AUTO_INCREMENT=642 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='网点指标汇总表（权重/MAX/MIN，驱动导出汇总行）';
```

#### `jw_branch_score` — 网点 TOPSIS 得分（5个评分类别）
```sql
CREATE TABLE `jw_branch_score` (
  `score_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `branch_id` bigint NOT NULL COMMENT '网点ID（关联jw_branch_info）',
  `data_year` int NOT NULL COMMENT '数据年份',
  `city` varchar(32) DEFAULT '' COMMENT '所属市',
  `score_category` varchar(32) NOT NULL COMMENT '评分类别: revenue(营收)/indicator(指标)/customer(客户)/operation(运营)/overall(总分)',
  `positive_distance` decimal(16,10) DEFAULT NULL COMMENT '正理想解欧式距离 D+',
  `negative_distance` decimal(16,10) DEFAULT NULL COMMENT '负理想解欧式距离 D-',
  `category_score` decimal(16,10) DEFAULT NULL COMMENT '类别得分 = D- / (D+ + D-)',
  `rank_num` int DEFAULT NULL COMMENT '排名',
  `quadrant` char(2) DEFAULT NULL COMMENT 'Q1/Q2/Q3/Q4（四象限）',
  `median_site_rank` int DEFAULT NULL,
  `median_branch_rank` int DEFAULT NULL,
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`score_id`),
  UNIQUE KEY `uk_branch_year_category` (`branch_id`,`data_year`,`score_category`),
  UNIQUE KEY `uk_branch_year_city_cat` (`branch_id`,`data_year`,`city`,`score_category`),
  KEY `idx_city_year` (`city`,`data_year`)
) ENGINE=InnoDB AUTO_INCREMENT=1650 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='网点得分表（TOPSIS计算结果，5个评分类别）';
```

---

#### `jw_indicator_config` — 指标配置（元数据，驱动导入/导出/计算全流程）
```sql
CREATE TABLE `jw_indicator_config` (
  `indicator_id` bigint NOT NULL AUTO_INCREMENT COMMENT '指标主键ID',
  `indicator_code` varchar(64) NOT NULL COMMENT '指标编码（唯一标识，程序通过此字段匹配）',
  `indicator_name` varchar(200) NOT NULL COMMENT '指标中文名称（导出Excel列头）',
  `sort_order` int DEFAULT '0' COMMENT '导出列排序序号',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `indicator_type` varchar(16) DEFAULT NULL COMMENT '指标类型: grid/branch_raw/branch',
  `parent_code` varchar(64) DEFAULT NULL COMMENT '上级指标编码',
  `is_derived` char(1) DEFAULT '0' COMMENT '是否衍生计算指标',
  `computation_pattern` varchar(32) DEFAULT NULL COMMENT '计算模式',
  `input_codes` varchar(512) DEFAULT NULL COMMENT '参与计算的指标编码',
  `calculation_weight` double DEFAULT NULL COMMENT '本级计算权重',
  PRIMARY KEY (`indicator_id`) USING BTREE,
  UNIQUE KEY `uk_indicator_code` (`indicator_code`) USING BTREE,
  KEY `idx_indicator_type` (`indicator_type`) USING BTREE,
  KEY `idx_parent_code` (`parent_code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=795 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='指标配置表（元数据，驱动导入/导出/计算全流程）';
```

#### `jw_external_resource_weight` — 网格选址权重（TOPSIS权重）
```sql
CREATE TABLE `jw_external_resource_weight` (
  `weight_id` bigint NOT NULL AUTO_INCREMENT COMMENT '权重主键ID',
  `level1_name` varchar(100) DEFAULT '' COMMENT '一级指标名称（如:人口因素）',
  `level1_ratio` decimal(10,6) DEFAULT '0.000000' COMMENT '一级指标占比',
  `level2_name` varchar(100) DEFAULT '' COMMENT '二级指标名称（如:人口密度）',
  `level2_ratio` decimal(10,6) DEFAULT '0.000000' COMMENT '二级指标占比',
  `level3_name` varchar(100) DEFAULT '' COMMENT '三级指标名称（如:工作人口）',
  `level3_ratio` decimal(10,6) DEFAULT '0.000000' COMMENT '三级指标占比',
  `total_weight` decimal(10,6) DEFAULT '0.000000' COMMENT '总权重 = level1_ratio * level2_ratio * level3_ratio',
  `indicator_code` varchar(64) DEFAULT '' COMMENT '关联指标编码',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`weight_id`),
  KEY `idx_indicator` (`indicator_code`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='外部资源权重表（网格选址TOPSIS权重）';
```

#### `jw_branch_efficiency_weight` — 网点效能权重（TOPSIS权重）
```sql
CREATE TABLE `jw_branch_efficiency_weight` (
  `weight_id` bigint NOT NULL AUTO_INCREMENT COMMENT '权重主键ID',
  `level1_name` varchar(100) DEFAULT '' COMMENT '一级指标名称（如:营收能力/业绩指标/客户发展/业务运营）',
  `level1_ratio` decimal(10,6) DEFAULT '0.000000' COMMENT '一级指标占比',
  `level2_name` varchar(100) DEFAULT '' COMMENT '二级指标名称',
  `level2_ratio` decimal(10,6) DEFAULT '0.000000' COMMENT '二级指标占比',
  `level3_name` varchar(100) DEFAULT '' COMMENT '三级指标名称',
  `level3_ratio` decimal(10,6) DEFAULT '0.000000' COMMENT '三级指标占比',
  `total_weight` decimal(10,6) DEFAULT '0.000000' COMMENT '总权重',
  `indicator_code` varchar(64) DEFAULT '' COMMENT '关联指标编码',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`weight_id`),
  KEY `idx_indicator` (`indicator_code`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='网点效能权重表（网点TOPSIS权重）';
```

---

#### `jw_poi_info` — POI 信息
```sql
CREATE TABLE `jw_poi_info` (
  `poi_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'POI主键ID',
  `org_code` varchar(64) DEFAULT '' COMMENT '所属机构编码',
  `poi_name` varchar(200) DEFAULT '' COMMENT 'POI名称',
  `longitude` decimal(12,8) DEFAULT NULL COMMENT '经度',
  `latitude` decimal(12,8) DEFAULT NULL COMMENT '纬度',
  `province` varchar(32) DEFAULT '' COMMENT '省',
  `city` varchar(32) DEFAULT '' COMMENT '市',
  `district` varchar(32) DEFAULT '' COMMENT '区县',
  `address` varchar(500) DEFAULT '' COMMENT '地址',
  `poi_type` varchar(64) DEFAULT '' COMMENT 'POI类型（银行/商圈/学校/医院等）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标记（0存在 2删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`poi_id`),
  UNIQUE KEY `uk_poi` (`org_code`,`poi_name`,`longitude`,`latitude`),
  KEY `idx_city` (`city`),
  KEY `idx_coord` (`longitude`,`latitude`)
) ENGINE=InnoDB AUTO_INCREMENT=2577 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='POI信息表';
```

#### `jw_peer_bank_info` — 同业银行信息
```sql
CREATE TABLE `jw_peer_bank_info` (
  `peer_bank_id` bigint NOT NULL AUTO_INCREMENT COMMENT '同业主键ID',
  `org_code` varchar(64) DEFAULT '' COMMENT '机构编码',
  `org_name` varchar(200) DEFAULT '' COMMENT '机构名称',
  `org_address` varchar(500) DEFAULT '' COMMENT '机构地址',
  `longitude` decimal(12,8) DEFAULT NULL COMMENT '经度',
  `latitude` decimal(12,8) DEFAULT NULL COMMENT '纬度',
  `bank_name` varchar(64) DEFAULT '' COMMENT '银行名称',
  `province` varchar(32) DEFAULT '' COMMENT '省',
  `city` varchar(32) DEFAULT '' COMMENT '市',
  `district` varchar(64) DEFAULT '' COMMENT '区县',
  `town` varchar(100) DEFAULT '' COMMENT '乡镇/街道',
  `grid_code` varchar(64) DEFAULT '' COMMENT '所属网格编号',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标记（0存在 2删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`peer_bank_id`),
  UNIQUE KEY `uk_org_code` (`org_code`),
  KEY `idx_city` (`city`),
  KEY `idx_bank_name` (`bank_name`),
  KEY `idx_grid_code` (`grid_code`),
  KEY `idx_district` (`district`)
) ENGINE=InnoDB AUTO_INCREMENT=458 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='同业银行信息表';
```

#### `jw_data_access_request` — 数据查看申请审批
（需从 `JwDataAccessRequestMapper.xml` 提取，或从 `sql/jw_data_access_request.sql` 执行）

```sql
CREATE TABLE `jw_data_access_request` (
  `request_id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请主键ID',
  `applicant_user_id` bigint NOT NULL COMMENT '申请人用户ID',
  `dept_id` bigint NOT NULL COMMENT '申请部门ID',
  `reviewer_user_id` bigint DEFAULT NULL COMMENT '审批人用户ID',
  `status` char(1) DEFAULT '0' COMMENT '状态:0待审批 1已通过 2已拒绝 3已撤销 4已过期',
  `request_reason` varchar(1024) DEFAULT NULL COMMENT '申请理由',
  `review_comment` varchar(1024) DEFAULT NULL COMMENT '审批意见',
  `valid_days` int DEFAULT '30' COMMENT '有效期天数',
  `valid_until` datetime DEFAULT NULL COMMENT '有效截止日期',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`request_id`),
  KEY `idx_user` (`applicant_user_id`),
  KEY `idx_reviewer` (`reviewer_user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据查看申请表';
```

---

#### `jw_ai_analysis` — AI 分析结果
```sql
CREATE TABLE `jw_ai_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `analysis_type` varchar(20) NOT NULL COMMENT '分析类型：branch-网点分析/grid-网格分析/quadrant-四象限分析',
  `entity_key` varchar(200) NOT NULL COMMENT '实体标识：branch_{branchId}_{year} / grid_{gridCode} / quadrant_{city}_{year}',
  `city` varchar(50) DEFAULT NULL COMMENT '所属城市',
  `content` mediumtext NOT NULL COMMENT 'AI分析结果（Markdown格式）',
  `satisfied` tinyint(1) DEFAULT NULL COMMENT '用户满意度：1-满意 0-不满意 NULL-未评价',
  `expired` tinyint(1) DEFAULT '0' COMMENT '数据过期标记：0-未过期 1-已过期',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_entity` (`analysis_type`,`entity_key`),
  KEY `idx_city` (`city`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI分析结果持久化表';
```

---

### 3.2 执行顺序

```bash
# 1. 创建数据库
mysql -h 内网IP -u root -p -e "CREATE DATABASE IF NOT EXISTS ry-vue DEFAULT CHARSET utf8mb4;"

# 2. 按顺序执行建表
#    方式一：使用 sql/ 目录下的汇总脚本
mysql -h 内网IP -u root -p ry-vue < sql/jw_map_20250525.sql      # ★ 14张业务表（含本表+权重+POI+同业等）
mysql -h 内网IP -u root -p ry-vue < sql/jw_ai_analysis.sql        # ★ AI分析结果表
mysql -h 内网IP -u root -p ry-vue < sql/jw_data_access_request.sql # 数据审批表
#    方式二：直接执行上方 DDL 语句

# 3. 导入业务数据
mysql -h 内网IP -u root -p ry-vue < sql/jw_test_data_full.sql     # 测试数据（约9.3MB）
#    或使用基础初始化数据
mysql -h 内网IP -u root -p ry-vue < sql/jw_indicator_config.sql   # ★ 指标配置数据（必需）
mysql -h 内网IP -u root -p ry-vue < sql/jw_test_data.sql           # 最小测试数据集
mysql -h 内网IP -u root -p ry-vue < sql/jw_test_data_supplement.sql# 补充测试数据
mysql -h 内网IP -u root -p ry-vue < sql/jw_peer_bank_info.sql      # 同业银行
mysql -h 内网IP -u root -p ry-vue < sql/jw_score_category_config.sql # 评分分类映射

# 4. 菜单权限
mysql -h 内网IP -u root -p ry-vue < sql/jw_baidu_map_menu.sql     # ★ 菜单+角色关联

# 5. 定时任务（数据访问过期）
mysql -h 内网IP -u root -p ry-vue < sql/jw_quartz_access_expiry.sql
```

### 3.3 验证表创建

```sql
SHOW TABLES LIKE 'jw_%';
-- 应返回以下 17 张表：
-- jw_ai_analysis, jw_branch_efficiency_weight, jw_branch_indicator, jw_branch_info,
-- jw_branch_score, jw_branch_summary, jw_data_access_request, jw_external_resource_weight,
-- jw_grid_data_normalized, jw_grid_data_raw, jw_grid_meta, jw_grid_score,
-- jw_grid_summary, jw_indicator_config, jw_peer_bank_info, jw_poi_info, jw_population_heat
```

---

## 四、后端迁移

### 4.1 复制代码

```bash
# 以下操作在内网项目目录执行

# 1. 复制 jw-map 整个模块
cp -r 源项目/jw-map ./jw-map

# 2. 复制 MyBatis XML 映射文件到内网对应位置
cp -r 源项目/jw-map/src/main/resources/mapper/jwmap/ 内网项目/jw-map/src/main/resources/mapper/jwmap/

# 3. 复制配置文件（application.yml 相关配置段需要手动合并，见 4.2）
```

### 4.2 修改 pom.xml

**根 pom.xml**（父 POM）：
```xml
<!-- 在 <modules> 中添加 -->
<modules>
    <module>ruoyi-admin</module>
    <module>ruoyi-framework</module>
    <module>ruoyi-system</module>
    <module>ruoyi-quartz</module>
    <module>ruoyi-generator</module>
    <module>ruoyi-common</module>
    <module>jw-map</module>          <!-- ★ 新增 -->
</modules>
```

**ruoyi-admin/pom.xml**：
```xml
<!-- 在 <dependencies> 中添加 -->
<dependency>
    <groupId>com.ruoyi</groupId>
    <artifactId>jw-map</artifactId>
</dependency>
```

**jw-map/pom.xml 特有依赖**（确认内网项目已有这些或添加）：
```xml
<!-- jw-map 特有依赖，内网若依标准项目可能没有 -->
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
    <!-- SseEmitter 所在，Spring Boot 已包含此依赖 -->
</dependency>
```

### 4.3 安全配置

在 `JwMapSecurityConfig.java` 中（或在内网若依的 `SecurityConfig.java` 中确认）：

```java
// ★ 关键：放开 /jwmap/** 和 /jwmap/ai/** 路由，不需要 JWT 认证
.antMatchers("/jwmap/**").permitAll()
```

> **说明**：jw-map 的 `/jwmap/**` 全部 `permitAll()`，地图和 AI 功能不要求登录。数据查看权限由业务层 `JwDataAccessServiceImpl` 控制。

### 4.4 编译验证

```bash
mvn clean install -DskipTests
# 成功后的关键标志：BUILD SUCCESS
# 常见错误：
# - jw-map 模块找不到 → 检查 4.2 的 pom.xml 配置
# - OkHttp/SseEmitter 类找不到 → 检查 jw-map/pom.xml 依赖
# - 包扫描不到 → 确认 ruoyi-admin 启动类有 @ComponentScan 或 @MapperScan 覆盖 com.ruoyi.jwmap
```

---

## 五、前端迁移

### 5.1 复制代码

```bash
# 1. 复制 jwmap 页面文件
cp -r 源项目/ruoyi-ui/src/views/jwmap 内网项目/ruoyi-ui/src/views/jwmap

# 2. 复制 API 封装
cp -r 源项目/ruoyi-ui/src/api/jwmap 内网项目/ruoyi-ui/src/api/jwmap

# 3. 复制 GeoJSON 边界数据（百度地图用）
cp -r 源项目/ruoyi-ui/public/data/map_data 内网项目/ruoyi-ui/public/data/map_data
```

### 5.2 添加前端路由

在 `router/index.js` 的 `dynamicRoutes` 中添加：

```javascript
{
  path: '/jwmap',
  hidden: true,
  component: Layout,
  children: [
    {
      path: 'baidu-map',
      component: () => import('@/views/jwmap/baidu-map/index'),
      name: 'JwMapBaidu',
      meta: { title: '百度地图可视化' }
    },
    {
      path: 'config/indicator',
      component: () => import('@/views/jwmap/config/indicator'),
      name: 'IndicatorConfig',
      meta: { title: '指标管理' }
    },
    {
      path: 'access-request',
      component: () => import('@/views/jwmap/access/index'),
      name: 'AccessRequest',
      meta: { title: '数据查看申请' }
    },
    {
      path: 'access-approval',
      component: () => import('@/views/jwmap/access/approval'),
      name: 'AccessApproval',
      meta: { title: '数据审批管理' }
    }
  ]
}
```

### 5.3 安装前端依赖

```bash
cd ruoyi-ui
npm install echarts@5.4.0    # 四象限图、雷达图、维度统计
npm install clipboard@2.0.8  # 复制功能
```

### 5.4 配置代理

**vue.config.js**（开发环境）：
```javascript
devServer: {
  host: '0.0.0.0',
  port: 8099,
  proxy: {
    '/dev-api': {
      target: 'http://内网后端IP:8080',  // ★ 改为内网地址
      pathRewrite: { '^/dev-api': '' }
    }
  }
}
```

**.env.production**（生产环境）：
```
VUE_APP_BASE_API = '/prod-api'       // 确认与 Nginx 代理路径一致
```

---

## 六、百度地图迁移（关键）

### 6.1 依赖关系

整个百度地图页面**强依赖百度在线服务**。内网迁移是整个项目中最关键的一步。

| 依赖 | 是否必需 | 内网可用性 |
|------|----------|-----------|
| BMapGL SDK（在线加载） | 必需——地图初始化 | 需内网 CDN 或离线方案 |
| 地址搜索 `BMapGL.LocalSearch` | 非必需——可降级 | 纯在线，内网不可用 |
| 逆地理编码 `BMapGL.Geocoder` | 非必需——可移除 | 纯在线，内网不可用 |
| 行政区边界 GeoJSON | 数据文件，无需在线 | 可直接使用 |
| BD09 坐标数据 | 数据库已有 | 无需转换 |

### 6.2 需要修改的文件

| 文件 | 修改内容 |
|------|----------|
| `ruoyi-ui/src/views/jwmap/baidu-map/utils/sdkLoader.js:5` | **AK 密钥** — 改为内网可用密钥或新申请的 AK |
| `sdkLoader.js:6` | **SDK 加载 URL** — 若使用内网 CDN，改 URL 指向内网地址 |
| `bMapSearchTool.js`（全部） | **地址搜索** — 若内网不可用，可移除或改为后端 POI 搜索 |
| `useMapLifecycle.js:reverseGeocode()` | **逆地理编码** — 可移除该功能 |

### 6.3 SDK 加载失败的处理

```
sdkLoader.js 有 15 秒超时机制 → 超时报错但页面其他 UI 组件可正常使用

影响范围：
  - 地图白屏（底图无法显示）→ 所有地图功能不可用
  - 侧边栏/排名列表/AI 分析/指标管理等纯 UI 组件 → 正常可用
```

### 6.4 迁移方案选择

| 方案 | 复杂度 | 操作 |
|------|--------|------|
| **A. 在线加载（最简单）** | 低 | 申请新 AK，确认 API 域名可访问即可 |
| **B. 内网 CDN 代理** | 中 | 将 `api.map.baidu.com` 代理到内网或使用百度私有化部署 |
| **C. 保留 AK + 降级搜索** | 中 | 保留地图底图，移除地址搜索/逆地理编码（改为后端 POI 查询） |
| **D. 替换为 Leaflet/OpenLayers（最复杂）** | 高 | 需重写所有 `window.BMapGL.*` 调用（数百处）+ BD09→WGS84 坐标转换 |

**推荐**：优先尝试方案 A。不通则方案 C——保留 BMapGL 底图（部分环境 AK 仍可用），搜索功能改为调用后端 `/jwmap/data/poi/withinRange`。

### 6.5 坐标说明

```
所有坐标数据使用 BD09（百度坐标系），包括：
- 数据库：jw_branch_info.longitude/latitude
- 数据库：jw_peer_bank_info.longitude/latitude
- 数据库：jw_grid_meta.longitude/latitude
- GeoJSON 边界文件：public/data/map_data/*.json

⚠ 若要替换为其他地图方案（如 Leaflet），需将 BD09 转为 WGS84
```

---

## 七、AI 模块迁移

### 7.1 架构说明

AI 模块包含 **7 个功能**，当前通过 DeepSeek V4 Flash 模型（OpenAI 兼容协议）提供智能分析。还有一个独立的**智能体新后端**（`agent` 包）实现了不同的 SSE 协议，随时可替换。

```
7 个 AI 功能：
① 选址建议      ② 网点诊断      ③ 多网点对比      ④ 网格分析
⑤ 四象限分析    ⑥ 迁址建议      ⑦ 选址报告

数据流：
前端 AiDrawer → SSE 流式请求 → AiController → AiServiceImpl（DeepSeek API）
                                                或 AgentAiServiceImpl（智能体新后端，待接入）
```

### 7.2 配置 AI

**使用当前 DeepSeek API（默认）**：
```yaml
# application.yml
ai:
  api-key: ${AI_API_KEY:sk-xxxx}         # ★ 改为内网 LLM API Key
  base-url: ${AI_BASE_URL:https://api.deepseek.com/v1}  # ★ 改为内网 LLM 网关地址
  model: ${AI_MODEL:deepseek-v4-flash}   # ★ 按需修改模型名称
  temperature: 0.3
  max-tokens: 4096
  timeout-seconds: 120
```

> 内网 LLM 网关须兼容 OpenAI 协议（`/v1/chat/completions` 端点）。

**使用智能体新后端（备用方案）**：
```yaml
# application.yml
agent:
  ai:
    domain-prefix: mlp.xxx               # ★ 内网智能体服务域名
    token: ${AGENT_AI_TOKEN:}            # ★ 认证 token
    agent-name: 贵州分行营业网点布局优化智能体
    default-user-id: ${AGENT_AI_USER_ID:}
    project-id: mock_project_id
    engine: workflow
    timeout-seconds: 120
```

> 该后端使用不同的 SSE 协议（`event: message/end/error`），需确认智能体服务可用后，再修改 Controller 调用的 Service 实现。

### 7.3 AI 后端文件清单

| 包 | 文件 | 说明 |
|----|------|------|
| `com.ruoyi.jwmap.service.impl` | `AiServiceImpl.java` | **当前在用** — DeepSeek API 流式/非流式 |
| | `AiCacheService.java` | AI 结果持久化（jw_ai_analysis 表） |
| | `AiDataAggregator.java` | 查 12+ Mapper 组装分析上下文 |
| | `AiPromptBuilder.java` | 7 套中文 Prompt 模板 |
| | `WordReportGenerator.java` | 选址报告（待实现） |
| `com.ruoyi.jwmap.agent.*` | **8 个文件** | **独立的智能体新后端**（待接入 Controller） |

### 7.4 AI 功能调用的外部服务

| 功能 | 依赖 | 内网迁移处理 |
|------|------|-------------|
| 所有 AI 功能 | LLM API（DeepSeek / 其他） | 改为内网 LLM 网关 |
| 选址建议/网格分析 | `AiDataAggregator` → 查数据库 Mapper | 数据库正常即可 |
| 数据聚合 | 12 个 Mapper 接口 | 数据库迁移正常即可 |
| AiAnalysisCard 流式渲染 | SSE (SseEmitter) | 内网 Nginx 需配置 `proxy_buffering off` |

### 7.5 Nginx SSE 配置

如果内网使用 Nginx 反向代理，必须配置以下参数，否则 SSE 流式连接会中断：

```nginx
location /jwmap/ai/ {
    proxy_pass http://后端地址:8080;
    proxy_buffering off;          # ★ 禁止缓冲
    proxy_cache off;              # ★ 禁止缓存
    proxy_read_timeout 180s;      # SSE 流式超时
    proxy_send_timeout 180s;
    proxy_set_header Connection '';
    chunked_transfer_encoding on;
}
```

---

## 八、配置项完整清单

### 8.1 application.yml — 所有需要修改的位置

```yaml
# ==================== 必须修改 ====================

server:
  port: 8080                    # ★ 改为内网端口

spring:
  redis:
    host: localhost              # ★ 改为内网 Redis 地址
    port: 6379
    password:                    # ★ 内网 Redis 密码

# ==================== 建议修改 ====================

ruoyi:
  profile: ./uploadPath          # ★ 改为内网绝对路径（如 /data/ruoyi/upload）

# 日志路径（在 logback.xml 中修改）
# ★ <property name="log.path" value="./logs" /> 改为内网路径

# ==================== AI 配置（可选的——若不需要 AI 功能可跳过） ====================

ai:
  api-key: ${AI_API_KEY:sk-xxxx}
  base-url: ${AI_BASE_URL:https://api.deepseek.com/v1}
  model: ${AI_MODEL:deepseek-v4-flash}

agent:
  ai:
    domain-prefix: mlp.xxx
    token: ${AGENT_AI_TOKEN:}
```

### 8.2 application-druid.yml

```yaml
spring:
  datasource:
    druid:
      master:
        url: jdbc:mysql://内网IP:3306/ry-vue?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai
        username: root           # ★ 用户名
        password: your_password  # ★ 密码
```

### 8.3 启动类注解

确认内网项目的 `ruoyi-admin` 启动类有 MyBatis Mapper 扫描：

```java
@SpringBootApplication
@MapperScan("com.ruoyi.**.mapper")  // ★ 必须扫描到 com.ruoyi.jwmap.mapper
public class RuoYiApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuoYiApplication.class, args);
    }
}
```

---

## 九、验证与测试

### 9.1 后端验证

```bash
# 1. 编译
mvn clean install -DskipTests
# 成功标志：BUILD SUCCESS

# 2. 启动（若无前端，仅看后端是否正常）
cd ruoyi-admin
mvn spring-boot:run
# 成功标志：Started RuoYiApplication in XX seconds

# 3. 测试 API
curl -s http://localhost:8080/jwmap/data/grid/cities | head -50
# 期望：返回城市列表 JSON（若有测试数据）

# 4. 确认安全放行
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/jwmap/data/grid/cities
# 期望：200（非 401/403）
```

### 9.2 前端验证

```bash
# 1. 构建
cd ruoyi-ui
npm install
npm run dev
# 访问 http://localhost:8099

# 2. 验证页面
#   - 访问 /jwmap/baidu-map → 地图是否加载
#   - 控制台 Network 标签 → API 调用是否 200
#   - 侧边栏/排名列表等 UI 是否正常渲染

# 3. 验证 AI 功能（如配置了 DeepSeek API）
#   - 点击网格/网点 → 点击 AI 按钮
#   - 控制台 Network 标签 → SSE 流式请求状态
#   - 后端日志 → ai-sse-* 线程日志
```

### 9.3 验证检查清单

- [ ] 后端启动无异常日志
- [ ] `/jwmap/data/grid/cities` 返回 200
- [ ] 百度地图页面正常加载（或至少报错但不阻塞其他 UI）
- [ ] 网格/网点排名列表可加载数据
- [ ] 指标管理页面 CRUD 正常
- [ ] AI 分析功能可正常调通（如配置了 LLM）
- [ ] 数据查看申请/审批功能可用

---

## 十、常见问题

### 10.1 启动报错：jw-map 模块找不到
**原因**：父 POM 未添加 module 或 admin POM 未添加依赖。
**解决**：检查 4.2 节的 pom.xml 配置。

### 10.2 启动报错：jwmap Mapper XML 找不到
**原因**：MyBatis 未扫描到 mapper 位置。
**解决**：确认 `application.yml` 中的 mybatis.mapperLocations：
```yaml
mybatis:
  mapperLocations: classpath*:mapper/**/*Mapper.xml
```

### 10.3 地图加载白屏：BMapGL is not defined
**原因**：百度地图 SDK 在线加载失败。
**解决**：
1. 检查 AK 是否有效：`sdkLoader.js:5`
2. 检查内网能否访问 `https://api.map.baidu.com/`
3. 查看第六章百度地图迁移方案

### 10.4 AI 功能无响应 / 一直 loading
**排查步骤**：
1. 控制台 Network → SSE 请求是否返回 200
2. 后端日志搜索 `ai-sse-` 线程 → 是否调用了 LLM API
3. 检查 `ai.api-key` 和 `ai.base-url` 是否正确
4. 若 Nginx 反代，确认配置了 `proxy_buffering off`（见 7.5 节）

### 10.5 跨域问题
若前端和后端不同源：
```yaml
# application.yml
referer:
  enabled: false          # 内网开发可先关闭防盗链
  allowed-domains: localhost,127.0.0.1,内网IP
```

### 10.6 定时任务不执行
数据访问申请过期任务依赖 Quartz：
```sql
-- 确认 Quartz 表存在
SHOW TABLES LIKE 'QRTZ_%';
-- 若没有，执行 sql/quartz.sql
```

---

## 十一、附录：项目结构与API参考

### 11.1 项目结构

```
├── pom.xml                          # 父 POM（Spring Boot 2.5.15）
├── ruoyi-admin/                     # Spring Boot 入口
├── ruoyi-common/                    # 通用工具类
├── ruoyi-framework/                 # 安全框架（JWT）
├── ruoyi-system/                    # 系统管理
├── ruoyi-quartz/                    # 定时任务
├── ruoyi-generator/                 # 代码生成器
├── jw-map/                          # ★ 网点布局优化模块（核心）
│   └── src/main/java/com/ruoyi/jwmap/
│       ├── agent/                   # 智能体新后端（8个文件）
│       ├── config/                  # JwMapConfig, AiProperties, AiConfig, AiAsyncConfig
│       ├── constant/                # AccessStatus, AiConstants
│       ├── controller/              # 12个Controller（含AiController）
│       ├── domain/                  # 18个实体类
│       ├── mapper/                  # 16个Mapper接口
│       ├── service/                 # 5接口 + 10实现类
│       │   └── impl/                # AiServiceImpl, AiCacheService, AiDataAggregator, AiPromptBuilder...
│       └── util/                    # TopsisCalculator, JwGeoUtils, JwIndicatorUtils
├── ruoyi-ui/                        # Vue 2.6 前端
│   └── src/views/jwmap/
│       ├── index.vue                # 数据管理（导入/计算/导出/查看）
│       ├── baidu-map/               # 百度地图可视化（主页面）
│       ├── map/components/          # 26个共享UI组件
│       ├── shared/                  # 共享mixin + 166个银行SVG
│       ├── config/indicator.vue     # 指标管理
│       └── access/                  # 数据查看申请/审批
└── sql/                             # 数据库脚本
```

### 11.2 数据库表一览（17张）

| 分组 | 表名 | 说明 |
|------|------|------|
| **网格选址**（6张） | `jw_grid_meta` | 网格元数据（1km×1km，含 bounding box） |
| | `jw_population_heat` | 人口热力（垂直存储） |
| | `jw_grid_data_raw` | 网格原始指标值 |
| | `jw_grid_data_normalized` | 网格归一化值 |
| | `jw_grid_summary` | 网格汇总（max/min/权重） |
| | `jw_grid_score` | 网格 TOPSIS 得分（驱动热力图，含 population/enterprise/business/overall 类别） |
| **网点效能**（4张） | `jw_branch_info` | 网点基础信息（含人员/面积/柜台/行长等） |
| | `jw_branch_indicator` | 网点业务指标（垂直存储，含 sheet_type 区分基础/计算/归一化） |
| | `jw_branch_summary` | 网点指标汇总 |
| | `jw_branch_score` | 网点 TOPSIS 得分（5类别 + 四象限 Q1~Q4） |
| **配置**（4张） | `jw_indicator_config` | ★ 核心指标配置树（元数据，驱动全流程） |
| | `jw_external_resource_weight` | 网格选址权重（三级分类树） |
| | `jw_branch_efficiency_weight` | 网点效能权重（三级分类树） |
| **业务**（3张） | `jw_poi_info` | POI 点位数据 |
| | `jw_peer_bank_info` | 同业银行信息 |
| | `jw_data_access_request` | 数据查看申请审批 |
| **AI**（1张） | `jw_ai_analysis` | AI 分析结果（UNIQUE KEY on type+entity） |

### 11.3 后端 API 分组

| 分组 | 前缀 | Controller | 端点数量 |
|------|------|-----------|----------|
| **数据计算** | `/jwmap/compute/` | JwComputeController | 5 |
| **网格数据** | `/jwmap/data/` | JwGridDataController | 13 |
| **网点数据** | `/jwmap/data/` | JwBranchDataController | 10 |
| **分析数据** | `/jwmap/data/` | JwAnalysisController | 4 |
| **同业/POI/人口** | `/jwmap/data/` | JwPeerBank/Poi/PopulationController | 5 |
| **指标配置** | `/jwmap/config` | JwConfigController | 9 |
| **数据审批** | `/jwmap/access/` | JwDataAccessController | 16 |
| **导入** | `/jwmap/import/` | JwImportController | 5 |
| **导出** | `/jwmap/export/` | JwExportController | 7 |
| **AI 智能分析** | `/jwmap/ai/` | AiController | 16（7 流式 + 9 非流式/存量） |

### 11.4 AI 功能端点

**7 个流式端点**（SseEmitter，180s超时）：
| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/jwmap/ai/site-suggestion/stream/{gridCode}` | 选址建议 |
| GET | `/jwmap/ai/branch-analysis/stream/{branchId}/{year}?forceRefresh=` | 网点诊断 |
| GET | `/jwmap/ai/branch-comparison/stream?branchIds=&city=&year=` | 多网点对比 |
| GET | `/jwmap/ai/grid-analysis/stream/{gridCode}?forceRefresh=` | 网格分析 |
| GET | `/jwmap/ai/quadrant-analysis/stream/{city}/{year}?forceRefresh=` | 四象限分析 |
| GET | `/jwmap/ai/quadrant-analysis/stream/per-branch?branchId=&year=` | 单网点四象限 |
| GET | `/jwmap/ai/relocation-suggestion/stream/{branchId}/{year}?city=&forceRefresh=` | 迁址建议 |

**9 个非流式端点**：
| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/jwmap/ai/site-report/{gridCode}` | 生成选址报告 |
| GET | `/jwmap/ai/report/download/{reportId}` | 下载报告 |
| POST | `/jwmap/ai/feedback` | 满意度反馈 |
| POST | `/jwmap/ai/save` | 保存分析内容 |
| GET | `/jwmap/ai/branch-analysis/{branchId}/{year}` | 取网点缓存 |
| GET | `/jwmap/ai/grid-analysis/{gridCode}` | 取网格缓存 |
| GET | `/jwmap/ai/quadrant-analysis/{city}/{year}` | 取四象限缓存 |
| GET | `/jwmap/ai/quadrant-analysis/per-branch/{branchId}/{year}` | 取单网点缓存 |
| GET | `/jwmap/ai/relocation-suggestion/cached?branchId=&year=` | 取迁址缓存 |
