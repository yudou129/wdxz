# jw_indicator_config indicator_code 重命名方案

> **状态：已完成**（数据库 + Java 代码 `AiDataAggregator.java` 均已完成修改）

本文档列出所有 `indicator_code` 的建议新命名。
重命名后，需要同步修改 Java 代码中的所有硬编码引用。

## 修改规则

- 保留已有语意的英文名（如 `pop`, `ent`, `per_capita`, `avg_balance` 不动）
- 将纯数字 ID（如 `grid_3243`）改为有意义的英文名
- 所有 grid 指标统一加 `grid_` 前缀区分命名空间
- parent_code 随着新编码同步更新

## 重命名映射

### Grid 类型（一级、二级节点）

| 原编码 | 新编码 | 名称 | 新 parent_code |
|--------|-------|------|---------------|
| `pop` | `pop` | 人口聚集 | (不变) |
| `ent` | `ent` | 企业聚集 | (不变) |
| `grid_3243` | `grid_biz` | 商业聚集 | (不变) |
| `pop_2` | `grid_pop_resident` | 常住、流动人口 | pop |
| `grid_8696` | `grid_pop_age` | 年龄 | pop |
| `grid_9133` | `grid_pop_income` | 收入水平 | pop |
| `grid_9881` | `grid_pop_education` | 教育水平 | pop |
| `grid_7744` | `grid_pop_industry` | 所在行业 | pop |
| `grid_1637` | `grid_pop_job` | 职业类别 | pop |
| `grid_48` | `grid_biz_lifecycle` | 人生阶段 | grid_biz |
| `grid_5543` | `grid_biz_asset` | 资产状况 | grid_biz |
| `grid_7683` | `grid_biz_consume` | 消费水平 | grid_biz |
| `grid_poi` | `grid_biz_poi` | POI | grid_biz |
| `grid_8498` | `grid_ent_company` | 公司 | ent |
| `grid_9293` | `grid_ent_office` | 写字楼 | ent |
| `grid_2310` | `grid_pop_resident_home` | 居住 | grid_pop_resident |
| `grid_871` | `grid_pop_resident_work` | 工作 | grid_pop_resident |
| `grid_2817` | `grid_pop_edu_college` | 本科及以上 | grid_pop_education |
| `grid_8856` | `grid_pop_edu_junior` | 大专 | grid_pop_education |
| `grid_3843` | `grid_biz_consume_high` | 高 | grid_biz_consume |
| `grid_4258` | `grid_biz_consume_mid` | 中 | grid_biz_consume |
| `grid_6205` | `grid_biz_consume_low` | 低 | grid_biz_consume |
| `grid_9265` | `grid_biz_asset_car` | 有车 | grid_biz_asset |
| `grid_6405` | `grid_biz_asset_nocar` | 无车 | grid_biz_asset |
| `grid_1849` | `grid_biz_poi_mall` | 购物中心 | grid_biz_poi |
| `grid_2741` | `grid_biz_poi_supermarket` | 超市 | grid_biz_poi |
| `grid_3301` | `grid_biz_poi_food` | 美食 | grid_biz_poi |
| `grid_5067` | `grid_biz_poi_market` | 市场 | grid_biz_poi |
| `grid_5367` | `grid_biz_poi_pharmacy` | 药店 | grid_biz_poi |
| `grid_8868` | `grid_biz_poi_hotel` | 酒店 | grid_biz_poi |
| `grid_9458` | `grid_biz_poi_gym` | 运动健身 | grid_biz_poi |
| `grid_9896` | `grid_biz_poi_media` | 文化传媒 | grid_biz_poi |
| `grid_6458` | `grid_biz_poi_convenience` | 便利店 | grid_biz_poi |
| `grid_9997` | `grid_biz_poi_restaurant` | 餐饮 | grid_biz_poi |
| `grid_8868` | `grid_biz_poi_hotel` | 酒店 | grid_biz_poi |
| `ent_2` | `grid_ent_job_manager` | 管理者和企业主 | grid_pop_job |
| `grid_2332` | `grid_ent_job_worker` | 生产操作人员 | grid_pop_job |
| `grid_3115` | `grid_ent_job_selfemployed` | 个体经营者 | grid_pop_job |
| `grid_8067` | `grid_ent_job_professional` | 专业技术人员 | grid_pop_job |
| `grid_9134` | `grid_ent_job_clerk` | 文职人员 | grid_pop_job |

### Grid 类型（三级行业节点）— 代码无硬编码，此处只列部分

| 原编码 | 新编码 | 名称 | 新 parent_code |
|--------|-------|------|---------------|
| `grid_363` | `grid_ind_agriculture` | 农林牧渔 | grid_pop_industry |
| `grid_45` | `grid_ind_transport` | 交通运输和仓储邮政 | grid_pop_industry |
| `grid_157` | `grid_ind_legal` | 法律商务人力外贸 | grid_pop_industry |
| `grid_4469` | `grid_ind_food` | 食品加工 | grid_pop_industry |
| `grid_5393` | `grid_ind_construction` | 建筑房地产 | grid_pop_industry |
| `grid_5511` | `grid_ind_public` | 社会公共管理 | grid_pop_industry |
| `grid_6026` | `grid_ind_automotive` | 汽车 | grid_pop_industry |
| `grid_6514` | `grid_ind_advertising` | 广告营销 | grid_pop_industry |
| `grid_654` | `grid_ind_tourism` | 住宿旅游 | grid_pop_industry |
| `grid_7221` | `grid_ind_home` | 建材家居 | grid_pop_industry |
| `grid_7225` | `grid_ind_sports` | 文化体育娱乐 | grid_pop_industry |
| `grid_7265` | `grid_ind_appliance` | 家电 | grid_pop_industry |
| `grid_8008` | `grid_ind_energy` | 能源采矿化工 | grid_pop_industry |
| `grid_8114` | `grid_ind_medical` | 医药卫生 | grid_pop_industry |
| `grid_8268` | `grid_ind_textile` | 纺织服装 | grid_pop_industry |
| `grid_9145` | `grid_ind_service` | 生活服务 | grid_pop_industry |
| `grid_9209` | `grid_ind_machinery` | 机械制造 | grid_pop_industry |
| `grid_1012` | `grid_ind_education` | 教育 | grid_pop_industry |
| `grid_2035` | `grid_ind_chemical` | 日化百货 | grid_pop_industry |
| `grid_3747` | `grid_ind_finance` | 金融保险 | grid_pop_industry |
| `it` | `grid_ind_it` | IT通信电子 | grid_pop_industry |

### Grid 人生阶段子节点

| 原编码 | 新编码 | 名称 | 新 parent_code |
|--------|-------|------|---------------|
| `01` | `grid_biz_life_baby` | 家有0-1岁小孩 | grid_biz_lifecycle |
| `13` | `grid_biz_life_toddler` | 家有1-3岁小孩 | grid_biz_lifecycle |
| `36` | `grid_biz_life_child36` | 家有3-6岁小孩 | grid_biz_lifecycle |
| `grid_7600` | `grid_biz_life_pregnant` | 孕期 | grid_biz_lifecycle |
| `grid_5800` | `grid_biz_life_highschool` | 家有高中生 | grid_biz_lifecycle |
| `grid_704` | `grid_biz_life_middleschool` | 家有初中生 | grid_biz_lifecycle |
| `grid_8287` | `grid_biz_life_primaryschool` | 家有小学生 | grid_biz_lifecycle |

### Grid 年龄子节点

| 原编码 | 新编码 | 名称 | 新 parent_code |
|--------|-------|------|---------------|
| `2534` | `grid_pop_age_25_34` | 25-34岁 | grid_pop_age |
| `3544` | `grid_pop_age_35_44` | 35-44岁 | grid_pop_age |
| `4554` | `grid_pop_age_45_54` | 45-54岁 | grid_pop_age |
| `55` | `grid_pop_age_55plus` | 55岁以上 | grid_pop_age |

### Grid 收入子节点

| 原编码 | 新编码 | 名称 | 新 parent_code |
|--------|-------|------|---------------|
| `20000` | `grid_pop_income_high` | 20000及以上 | grid_pop_income |
| `800019999` | `grid_pop_income_mid` | 8000~19999 | grid_pop_income |
| `40007999` | `grid_pop_income_low` | 4000~7999 | grid_pop_income |

### Branch Raw 类型 — 代码无硬编码（仅 as parent_code 引用）

| 原编码 | 新编码 | 名称 | 新 parent_code |
|--------|-------|------|---------------|
| `branch_raw_5913` | `branch_raw_revenue` | 经营情况 | (不变) |
| `branch_4568` | `branch_raw_performance` | 业绩表现 | (不变) |
| `branch_170` | `branch_raw_operation` | 业务运营 | (不变) |
| `branch_7584` | `branch_raw_customer` | 客户发展 | (不变) |
| `branch_1201` | `branch_raw_savings` | 储蓄存款 | branch_raw_performance |
| `corp_dep` | `branch_raw_corp_dep` | 公司客户存款 | branch_raw_performance |
| `inst_dep` | `branch_raw_inst_dep` | 机构客户存款 | branch_raw_performance |
| `total_asset` | `branch_raw_total_asset` | 全量个人金融资产 | branch_raw_performance |
| `inclusive_loan` | `branch_raw_incl_loan` | 普惠贷款 | branch_raw_performance |
| `personal_loan` | `branch_raw_personal_loan` | 个人贷款 | branch_raw_performance |
| `branch_1512` | `branch_raw_personal_cust` | 个人客户 | branch_raw_customer |
| `branch_4963` | `branch_raw_inst_cust` | 机构客户 | branch_raw_customer |
| `branch_9876` | `branch_raw_corp_cust` | 对公客户 | branch_raw_customer |
| `branch_raw_8804` | `branch_raw_incl_cust` | 普惠客户 | branch_raw_customer |
| `interest_income` | `branch_raw_interest_income` | 利息净收入 | branch_raw_revenue |
| `branch_raw_1596` | `branch_raw_fee_income` | 手佣净收入 | branch_raw_revenue |
| `branch_1274` | `branch_raw_cust_total` | 总量（单位：户） | branch_raw_incl_cust |

### Branch（派生）类型

| 原编码 | 新编码 | 名称 | 新 parent_code |
|--------|-------|------|---------------|
| `branch_raw_5913` | `branch_revenue` | 经营情况 | (不变) |
| `branch_4568` | `branch_performance` | 业绩表现 | (不变) |
| `branch_170` | `branch_operation` | 业务运营 | (不变) |
| `branch_7584` | `branch_customer` | 客户发展 | (不变) |

> 注：branch 派生节点的 `parent_code` 指向对应的 branch_raw 节点，parent_code 需随 raw 节点一起更新。

### 代码硬编码引用映射

以下为新旧编码在 Java 代码中的引用替换表。修改 `AiDataAggregator.java` 时按此替换：

| 旧编码 | 新编码 | 出现位置 |
|--------|-------|---------|
| `grid_3243` | `grid_biz` | AiDataAggregator.java (pillars, weightTypes) |
| `grid_8696` | `grid_pop_age` | AiDataAggregator.java (popRootChildren) |
| `grid_9133` | `grid_pop_income` | AiDataAggregator.java (popRootChildren) |
| `grid_9881` | `grid_pop_education` | AiDataAggregator.java (popRootChildren) |
| `grid_7744` | `grid_pop_industry` | AiDataAggregator.java (popRootChildren) |
| `grid_1637` | `grid_pop_job` | AiDataAggregator.java (popRootChildren) |
| `grid_48` | `grid_biz_lifecycle` | AiDataAggregator.java (bizRootChildren) |
| `grid_5543` | `grid_biz_asset` | AiDataAggregator.java (bizRootChildren) |
| `grid_7683` | `grid_biz_consume` | AiDataAggregator.java (bizRootChildren) |
| `grid_poi` | `grid_biz_poi` | AiDataAggregator.java (bizRootChildren) |
| `grid_8498` | `grid_ent_company` | AiDataAggregator.java (enterprise pillar) |
| `grid_9293` | `grid_ent_office` | AiDataAggregator.java (enterprise pillar) |
| `branch_raw_5913` | `branch_raw_revenue` | AiDataAggregator.java (weightTypes) |
| `branch_4568` | `branch_raw_performance` | AiDataAggregator.java (weightTypes) |
| `branch_170` | `branch_raw_operation` | AiDataAggregator.java (weightTypes) |
| `branch_7584` | `branch_raw_customer` | AiDataAggregator.java (weightTypes) |
