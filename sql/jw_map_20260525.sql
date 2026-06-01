-- ----------------------------
-- jw-map 网点布局优化模块数据库脚本 (优化版)
-- 数据库：高斯数据库（MySQL兼容模式）
-- 共14张业务表 + 1张预留表 + 指标预置数据
-- ----------------------------
--
-- ==============================
-- 表关系总览 (ER)
-- ==============================
--
--   jw_indicator_config ──元数据──→ jw_population_heat (grid_code + indicator_code)
--          │                        jw_grid_data_raw    (grid_code + indicator_code)
--          │                        jw_grid_data_normalized (grid_code + indicator_code)
--          │                        jw_branch_indicator  (branch_id + indicator_code)
--          │                        jw_grid_summary      (city + indicator_code)
--          │                        jw_branch_summary    (city + year + indicator_code)
--          │
--          └──权重──→ jw_external_resource_weight (indicator_code)
--                     jw_branch_efficiency_weight (indicator_code)
--
--   jw_poi_info ──坐标+类型──→ jw_grid_data_raw (按poi_type分组计数，各类独立指标独立权重)
--          │
--   jw_population_heat ──计算──→ jw_grid_data_raw ──归一化──→ jw_grid_data_normalized ──TOPSIS──→ jw_grid_score
--          │
--   jw_grid_meta (1km网格) ──关联──→ jw_branch_info.grid_code
--
--   jw_branch_info ──指标──→ jw_branch_indicator ──汇总──→ jw_branch_summary
--          │                                              │
--          └──TOPSIS──→ jw_branch_score ←──权重──┘
--
--   数据流向:
--   ① Excel导入 → jw_poi_info / jw_population_heat / jw_branch_info / jw_branch_indicator / 权重表
--   ② 网格计算 → jw_grid_meta → jw_grid_data_raw → jw_grid_summary → jw_grid_data_normalized → jw_grid_score
--   ③ 网点计算 → jw_branch_indicator (计算列) → jw_branch_summary → jw_branch_indicator (归一化列) → jw_branch_score
--   ④ Excel导出 ← jw_grid_data_raw / jw_grid_data_normalized / jw_branch_info / jw_branch_indicator
--
-- ==============================


-- ============================================================
-- 1、指标配置表（核心元数据，驱动所有动态列逻辑）
-- ============================================================
DROP TABLE IF EXISTS jw_indicator_config;
CREATE TABLE jw_indicator_config (
  indicator_id      BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT '指标主键ID',
  indicator_code    VARCHAR(64)     NOT NULL                 COMMENT '指标编码（唯一标识，程序通过此字段匹配）',
  indicator_name    VARCHAR(200)    NOT NULL                 COMMENT '指标中文名称（导出Excel列头）',
  category_level1   VARCHAR(64)     DEFAULT ''               COMMENT '一级分类（revenue/indicator/customer/operation/pop/age/income/consume/education/asset/gender/industry/job/life/poi）',
  category_level2   VARCHAR(64)     DEFAULT ''               COMMENT '二级分类（细分维度）',
  data_type         VARCHAR(16)     DEFAULT 'DECIMAL'        COMMENT '数据类型（INT/DECIMAL）',
  sort_order        INT(4)          DEFAULT 0                COMMENT '导出列排序序号',
  source_tables     VARCHAR(200)    DEFAULT ''               COMMENT '数据来源（人口热力/网点指标/网点指标(计算)/网格数据）',
  is_weighted       CHAR(1)         DEFAULT '1'              COMMENT '是否参与TOPSIS加权计算（1是 0否）',
  is_active         CHAR(1)         DEFAULT '1'              COMMENT '是否启用（1启用 0停用）',
  create_by         VARCHAR(64)     DEFAULT ''               COMMENT '创建者',
  create_time       DATETIME                                 COMMENT '创建时间',
  update_by         VARCHAR(64)     DEFAULT ''               COMMENT '更新者',
  update_time       DATETIME                                 COMMENT '更新时间',
  PRIMARY KEY (indicator_id),
  UNIQUE KEY uk_indicator_code (indicator_code)
) ENGINE=INNODB AUTO_INCREMENT=100 COMMENT = '指标配置表（元数据，驱动导入/导出/计算全流程）';

-- ============================================================
-- 1.1 网格指标（人口热力类，共70个，参与网格TOPSIS）
-- ============================================================
INSERT INTO jw_indicator_config (indicator_code, indicator_name, category_level1, category_level2, data_type, sort_order, source_tables, is_weighted) VALUES
-- 常住流动人数 (3)
('pop_work',        '工作人口',        'pop',  'pop',      'INT', 1,  '人口热力,网格数据', '1'),
('pop_live',        '居住人口',        'pop',  'pop',      'INT', 2,  '人口热力,网格数据', '1'),
('pop_flow',        '流动人口',        'pop',  'pop',      'INT', 3,  '人口热力,网格数据', '1'),
-- 年龄分布 (7)
('age_under_18',    '18岁以下',        'age',  'age_group','INT', 4,  '人口热力,网格数据', '1'),
('age_18_24',       '18-24岁',         'age',  'age_group','INT', 5,  '人口热力,网格数据', '1'),
('age_25_34',       '25-34岁',         'age',  'age_group','INT', 6,  '人口热力,网格数据', '1'),
('age_35_44',       '35-44岁',         'age',  'age_group','INT', 7,  '人口热力,网格数据', '1'),
('age_45_54',       '45-54岁',         'age',  'age_group','INT', 8,  '人口热力,网格数据', '1'),
('age_55_64',       '55-64岁',         'age',  'age_group','INT', 9,  '人口热力,网格数据', '1'),
('age_65_above',    '65岁以上',        'age',  'age_group','INT', 10, '人口热力,网格数据', '1'),
-- 收入水平 (5)
('income_under_2499',   '2499及以下',     'income', 'income_level', 'INT', 11, '人口热力,网格数据', '1'),
('income_2500_3999',    '2500~3999',      'income', 'income_level', 'INT', 12, '人口热力,网格数据', '1'),
('income_4000_7999',    '4000~7999',      'income', 'income_level', 'INT', 13, '人口热力,网格数据', '1'),
('income_8000_19999',   '8000~19999',     'income', 'income_level', 'INT', 14, '人口热力,网格数据', '1'),
('income_20000_above',  '20000及以上',    'income', 'income_level', 'INT', 15, '人口热力,网格数据', '1'),
-- 消费水平 (3)
('consume_low',     '消费水平-低',     'consume', 'consume',   'INT', 16, '人口热力,网格数据', '1'),
('consume_mid',     '消费水平-中',     'consume', 'consume',   'INT', 17, '人口热力,网格数据', '1'),
('consume_high',    '消费水平-高',     'consume', 'consume',   'INT', 18, '人口热力,网格数据', '1'),
-- 教育水平 (3)
('edu_high_school_below', '高中及以下',   'education', 'education', 'INT', 19, '人口热力,网格数据', '1'),
('edu_college',         '大专',          'education', 'education', 'INT', 20, '人口热力,网格数据', '1'),
('edu_bachelor_above',  '本科及以上',    'education', 'education', 'INT', 21, '人口热力,网格数据', '1'),
-- 资产状况 (2)
('asset_has_car',   '有车',           'asset',  'asset',    'INT', 22, '人口热力,网格数据', '1'),
('asset_no_car',    '无车',           'asset',  'asset',    'INT', 23, '人口热力,网格数据', '1'),
-- 性别 (2)
('gender_male',     '男',             'gender', 'gender',   'INT', 24, '人口热力,网格数据', '1'),
('gender_female',   '女',             'gender', 'gender',   'INT', 25, '人口热力,网格数据', '1'),
-- 所在行业 (24)
('industry_it',             'IT',              'industry', 'industry', 'INT', 26, '人口热力,网格数据', '1'),
('industry_communication',  '通信电子',        'industry', 'industry', 'INT', 27, '人口热力,网格数据', '1'),
('industry_transport',      '交通运输和仓储邮政', 'industry', 'industry', 'INT', 28, '人口热力,网格数据', '1'),
('industry_hotel',          '住宿旅游',        'industry', 'industry', 'INT', 29, '人口热力,网格数据', '1'),
('industry_agriculture',    '农林牧渔',        'industry', 'industry', 'INT', 30, '人口热力,网格数据', '1'),
('industry_medical',        '医药卫生',        'industry', 'industry', 'INT', 31, '人口热力,网格数据', '1'),
('industry_appliance',      '家电',            'industry', 'industry', 'INT', 32, '人口热力,网格数据', '1'),
('industry_advertising',    '广告营销',        'industry', 'industry', 'INT', 33, '人口热力,网格数据', '1'),
('industry_building',       '建材家居',        'industry', 'industry', 'INT', 34, '人口热力,网格数据', '1'),
('industry_real_estate',    '建筑房地产',      'industry', 'industry', 'INT', 35, '人口热力,网格数据', '1'),
('industry_education',      '教育',            'industry', 'industry', 'INT', 36, '人口热力,网格数据', '1'),
('industry_entertainment',  '文化体育娱乐',    'industry', 'industry', 'INT', 37, '人口热力,网格数据', '1'),
('industry_daily_chem',     '日化百货',        'industry', 'industry', 'INT', 38, '人口热力,网格数据', '1'),
('industry_machinery',      '机械制造',        'industry', 'industry', 'INT', 39, '人口热力,网格数据', '1'),
('industry_auto',           '汽车',            'industry', 'industry', 'INT', 40, '人口热力,网格数据', '1'),
('industry_legal',          '法律服务',        'industry', 'industry', 'INT', 41, '人口热力,网格数据', '1'),
('industry_trade',          '人力外贸',        'industry', 'industry', 'INT', 42, '人口热力,网格数据', '1'),
('industry_life_service',   '生活服务',        'industry', 'industry', 'INT', 43, '人口热力,网格数据', '1'),
('industry_public_mgmt',    '社会公共管理',    'industry', 'industry', 'INT', 44, '人口热力,网格数据', '1'),
('industry_research',       '科学研究',        'industry', 'industry', 'INT', 45, '人口热力,网格数据', '1'),
('industry_textile',        '纺织服装',        'industry', 'industry', 'INT', 46, '人口热力,网格数据', '1'),
('industry_energy',         '能源采矿化工',    'industry', 'industry', 'INT', 47, '人口热力,网格数据', '1'),
('industry_finance',        '金融保险',        'industry', 'industry', 'INT', 48, '人口热力,网格数据', '1'),
('industry_food',           '食品加工',        'industry', 'industry', 'INT', 49, '人口热力,网格数据', '1'),
('industry_catering',       '餐饮',            'industry', 'industry', 'INT', 50, '人口热力,网格数据', '1'),
-- 职业类别 (6)
('job_professional',    '专业技术人员',    'job', 'job', 'INT', 51, '人口热力,网格数据', '1'),
('job_self_employed',   '个体经营者',      'job', 'job', 'INT', 52, '人口热力,网格数据', '1'),
('job_clerk',           '文职人员',        'job', 'job', 'INT', 53, '人口热力,网格数据', '1'),
('job_service',         '服务人员',        'job', 'job', 'INT', 54, '人口热力,网格数据', '1'),
('job_operator',        '生产操作人员',    'job', 'job', 'INT', 55, '人口热力,网格数据', '1'),
('job_manager',         '管理者和企业主',  'job', 'job', 'INT', 56, '人口热力,网格数据', '1'),
-- 人生阶段 (13)
('life_middle_school',      '初中生',        'life', 'life_stage', 'INT', 57, '人口热力,网格数据', '1'),
('life_high_school',        '高中生',        'life', 'life_stage', 'INT', 58, '人口热力,网格数据', '1'),
('life_college_student',    '大学生',        'life', 'life_stage', 'INT', 59, '人口热力,网格数据', '1'),
('life_graduate',           '研究生',        'life', 'life_stage', 'INT', 60, '人口热力,网格数据', '1'),
('life_pregnant',           '孕期',          'life', 'life_stage', 'INT', 61, '人口热力,网格数据', '1'),
('life_child_0_1',          '家有0-1岁小孩', 'life', 'life_stage', 'INT', 62, '人口热力,网格数据', '1'),
('life_child_1_3',          '家有1-3岁小孩', 'life', 'life_stage', 'INT', 63, '人口热力,网格数据', '1'),
('life_child_3_6',          '家有3-6岁小孩', 'life', 'life_stage', 'INT', 64, '人口热力,网格数据', '1'),
('life_primary_school',     '家有小学生',    'life', 'life_stage', 'INT', 65, '人口热力,网格数据', '1'),
('life_junior_high',        '家有初中生',    'life', 'life_stage', 'INT', 66, '人口热力,网格数据', '1'),
('life_senior_high',        '家有高中生',    'life', 'life_stage', 'INT', 67, '人口热力,网格数据', '1'),
('life_family_pregnant',    '家有孕妇',      'life', 'life_stage', 'INT', 68, '人口热力,网格数据', '1'),
('life_parenting',          '育儿阶段',      'life', 'life_stage', 'INT', 69, '人口热力,网格数据', '1');
-- 注: POI类指标不再硬编码，改为计算时根据 jw_poi_info.poi_type 自动生成
--     格式: poi_type_{类型名}, 如 poi_type_银行 / poi_type_商圈 / poi_type_学校
--     每类POI作为独立指标写入 jw_grid_data_raw, 有权重表对应可独立调权

-- ============================================================
-- 1.2 网点基础数据指标（共23个，从Excel基础数据Sheet导入，不参与TOPSIS）
-- ============================================================
INSERT INTO jw_indicator_config (indicator_code, indicator_name, category_level1, category_level2, data_type, sort_order, source_tables, is_weighted) VALUES
-- 经营情况-营业收入 (2)
('interest_income',         '利息净收入(万元)',                          'revenue', 'interest',    'DECIMAL', 71, '网点指标', '0'),
('fee_income',              '手续费净收入(万元)',                        'revenue', 'fee',         'DECIMAL', 72, '网点指标', '0'),
-- 业绩表现-全量个人金融资产 (2)
('total_asset_balance',     '全量个人金融资产 日均余额(万元)',           'indicator','asset_bal',  'DECIMAL', 73, '网点指标', '0'),
('total_asset_growth',      '全量个人金融资产 日均增量(万元)',           'indicator','asset_grow', 'DECIMAL', 74, '网点指标', '0'),
-- 业绩表现-储蓄存款 (2)
('saving_balance',          '储蓄存款 日均余额(万元)',                   'indicator','saving_bal', 'DECIMAL', 75, '网点指标', '0'),
('saving_growth',           '储蓄存款 日均增量(万元)',                   'indicator','saving_grow','DECIMAL', 76, '网点指标', '0'),
-- 业绩表现-公司客户存款 (2)
('corp_dep_balance',        '公司客户存款 日均余额(万元)',               'indicator','corp_bal',   'DECIMAL', 77, '网点指标', '0'),
('corp_dep_growth',         '公司客户存款 日均增量(万元)',               'indicator','corp_grow',  'DECIMAL', 78, '网点指标', '0'),
-- 业绩表现-机构客户存款 (2)
('inst_dep_balance',        '机构客户存款 日均余额(万元)',               'indicator','inst_bal',   'DECIMAL', 79, '网点指标', '0'),
('inst_dep_growth',         '机构客户存款 日均增量(万元)',               'indicator','inst_grow',  'DECIMAL', 80, '网点指标', '0'),
-- 业绩表现-贷款 (2)
('inclusive_loan_amount',   '普惠贷款 营销额(万元)',                     'indicator','incloan',    'DECIMAL', 81, '网点指标', '0'),
('personal_loan_amount',    '个人贷款 发放额(万元)',                     'indicator','perloan',    'DECIMAL', 82, '网点指标', '0'),
-- 客户发展-个人客户 (3)
('pcust_t1',                '日均0元(不含)-20万元(不含)客户数(户)',     'customer', 'personal_l', 'DECIMAL', 83, '网点指标', '0'),
('pcust_t2',                '日均20万元(含)-600万元(不含)客户数(户)',   'customer', 'personal_m', 'DECIMAL', 84, '网点指标', '0'),
('pcust_t3',                '日均大于等于600万(含)客户数(户)',           'customer', 'personal_h', 'DECIMAL', 85, '网点指标', '0'),
-- 客户发展-对公客户 (2)
('ccust_h',                 '头部、中部对公客户数 日均资产50万元(含)以上(户)',  'customer', 'corp_h',   'DECIMAL', 86, '网点指标', '0'),
('ccust_l',                 '底尾部对公客户数 日均资产50万元(不含)以下(户)',    'customer', 'corp_l',   'DECIMAL', 87, '网点指标', '0'),
-- 客户发展-机构客户 (2)
('icust_h',                 '日均资产1万元(不含)以上机构客户数(户)',    'customer', 'inst_h',     'DECIMAL', 88, '网点指标', '0'),
('icust_l',                 '日均资产1万元(含)以下机构客户数(户)',      'customer', 'inst_l',     'DECIMAL', 89, '网点指标', '0'),
-- 客户发展-普惠客户 (1)
('inclusive_cust_total',    '普惠客户 总量(户)',                         'customer', 'inclusive',  'DECIMAL', 90, '网点指标', '0'),
-- 业务运营 (3)
('counter_txn',             '柜台日均交易笔数',                          'operation','counter',    'DECIMAL', 91, '网点指标', '0'),
('terminal_txn',            '自助终端日均交易笔数',                      'operation','terminal',   'DECIMAL', 92, '网点指标', '0'),
('atm_txn',                 '附行式、网点自助ATM日均交易笔数',           'operation','atm',        'DECIMAL', 93, '网点指标', '0');

-- ============================================================
-- 1.3 网点计算指标（共22个，由基础数据+人员数据计算得出，参与网点TOPSIS）
-- ============================================================
INSERT INTO jw_indicator_config (indicator_code, indicator_name, category_level1, category_level2, data_type, sort_order, source_tables, is_weighted) VALUES
-- 营收 (2)
('branch_rev_per_capita',       '人均营业收入(万元)',          'revenue', 'per_capita',  'DECIMAL', 100,'网点指标(计算)', '1'),
('branch_rev_per_area',         '单位面积营业收入(万元)',      'revenue', 'per_area',    'DECIMAL', 101,'网点指标(计算)', '1'),
-- 全量个人金融资产 (2)
('branch_asset_avg_balance',    '全量个人金融资产 户日均余额',  'indicator','asset',      'DECIMAL', 102,'网点指标(计算)', '1'),
('branch_asset_avg_growth',     '全量个人金融资产 日均增幅',    'indicator','asset',      'DECIMAL', 103,'网点指标(计算)', '1'),
-- 储蓄存款 (2)
('branch_saving_avg_balance',   '储蓄存款 户日均余额',         'indicator','saving',     'DECIMAL', 104,'网点指标(计算)', '1'),
('branch_saving_avg_growth',    '储蓄存款 日均增幅',           'indicator','saving',     'DECIMAL', 105,'网点指标(计算)', '1'),
-- 公司客户存款 (2)
('branch_corp_dep_avg_balance', '公司客户存款 户日均余额',     'indicator','corp_dep',   'DECIMAL', 106,'网点指标(计算)', '1'),
('branch_corp_dep_avg_growth',  '公司客户存款 日均增幅',       'indicator','corp_dep',   'DECIMAL', 107,'网点指标(计算)', '1'),
-- 机构客户存款 (2)
('branch_inst_dep_avg_balance', '机构客户存款 户日均余额',     'indicator','inst_dep',   'DECIMAL', 108,'网点指标(计算)', '1'),
('branch_inst_dep_avg_growth',  '机构客户存款 日均增幅',       'indicator','inst_dep',   'DECIMAL', 109,'网点指标(计算)', '1'),
-- 贷款 (2)
('branch_incloan_per_capita',   '普惠贷款 人均营销额',         'indicator','loan',       'DECIMAL', 110,'网点指标(计算)', '1'),
('branch_perloan_per_capita',   '个人贷款 人均发放额',         'indicator','loan',       'DECIMAL', 111,'网点指标(计算)', '1'),
-- 个人客户 (3)
('branch_pcust_t1_per_capita',  '人均服务个人客户(低端)',      'customer', 'personal',   'DECIMAL', 112,'网点指标(计算)', '1'),
('branch_pcust_t2_per_capita',  '人均服务个人客户(中端)',      'customer', 'personal',   'DECIMAL', 113,'网点指标(计算)', '1'),
('branch_pcust_t3_per_capita',  '人均服务个人客户(高端)',      'customer', 'personal',   'DECIMAL', 114,'网点指标(计算)', '1'),
-- 对公客户 (2)
('branch_ccust_h_per_capita',   '人均服务对公客户(头部)',      'customer', 'corp',       'DECIMAL', 115,'网点指标(计算)', '1'),
('branch_ccust_l_per_capita',   '人均服务对公客户(底尾部)',    'customer', 'corp',       'DECIMAL', 116,'网点指标(计算)', '1'),
-- 机构客户 (2)
('branch_icust_h_per_capita',   '人均服务机构客户(高)',        'customer', 'inst',       'DECIMAL', 117,'网点指标(计算)', '1'),
('branch_icust_l_per_capita',   '人均服务机构客户(低)',        'customer', 'inst',       'DECIMAL', 118,'网点指标(计算)', '1'),
-- 运营 (3)
('branch_counter_per_area',     '每单位面积柜台日均工作量',    'operation','counter',    'DECIMAL', 119,'网点指标(计算)', '1'),
('branch_terminal_per_area',    '每单位面积自助终端日均交易',  'operation','terminal',   'DECIMAL', 120,'网点指标(计算)', '1'),
('branch_atm_per_area',         '每单位面积ATM日均交易',       'operation','atm',        'DECIMAL', 121,'网点指标(计算)', '1');


-- ============================================================
-- 2、POI信息表
--   数据来源: POI信息_xxx.xlsx
--   关联: jw_grid_meta (通过经纬度 bounding box 统计每个网格POI数)
-- ============================================================
DROP TABLE IF EXISTS jw_poi_info;
CREATE TABLE jw_poi_info (
  poi_id            BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT 'POI主键ID',
  org_code          VARCHAR(64)     DEFAULT ''               COMMENT '所属机构编码',
  poi_name          VARCHAR(200)    DEFAULT ''               COMMENT 'POI名称',
  longitude         DECIMAL(12,8)   DEFAULT NULL             COMMENT '经度',
  latitude          DECIMAL(12,8)   DEFAULT NULL             COMMENT '纬度',
  province          VARCHAR(32)     DEFAULT ''               COMMENT '省',
  city              VARCHAR(32)     DEFAULT ''               COMMENT '市',
  district          VARCHAR(32)     DEFAULT ''               COMMENT '区县',
  address           VARCHAR(500)    DEFAULT ''               COMMENT '地址',
  poi_type          VARCHAR(64)     DEFAULT ''               COMMENT 'POI类型（银行/商圈/学校/医院等）',
  del_flag          CHAR(1)         DEFAULT '0'              COMMENT '删除标志（0存在 2删除）',
  create_by         VARCHAR(64)     DEFAULT ''               COMMENT '创建者',
  create_time       DATETIME                                 COMMENT '创建时间',
  update_by         VARCHAR(64)     DEFAULT ''               COMMENT '更新者',
  update_time       DATETIME                                 COMMENT '更新时间',
  remark            VARCHAR(500)    DEFAULT NULL             COMMENT '备注',
  PRIMARY KEY (poi_id),
  UNIQUE KEY uk_poi (org_code, poi_name, longitude, latitude),
  KEY idx_city (city),
  KEY idx_coord (longitude, latitude)
) ENGINE=INNODB AUTO_INCREMENT=100 COMMENT = 'POI信息表';


-- ============================================================
-- 3、人口热力表（垂直存储）
--   数据来源: 人口热力_xxx.xlsx
--   关联: jw_grid_meta (grid_code), jw_indicator_config (indicator_code)
--   → 计算后写入 jw_grid_data_raw
-- ============================================================
DROP TABLE IF EXISTS jw_population_heat;
CREATE TABLE jw_population_heat (
  heat_id           BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
  grid_code         VARCHAR(64)     NOT NULL                 COMMENT '网格编号（关联jw_grid_meta）',
  indicator_code    VARCHAR(64)     NOT NULL                 COMMENT '指标编码（关联jw_indicator_config）',
  indicator_value   DECIMAL(16,4)   DEFAULT 0                COMMENT '指标值',
  create_time       DATETIME                                 COMMENT '创建时间',
  PRIMARY KEY (heat_id),
  UNIQUE KEY uk_grid_indicator (grid_code, indicator_code),
  KEY idx_grid (grid_code)
) ENGINE=INNODB AUTO_INCREMENT=100 COMMENT = '人口热力表（垂直存储，网格×指标）';


-- ============================================================
-- 4、外部资源权重表
--   数据来源: 外部资源权重表_xxx.xlsx
--   关联: jw_indicator_config (indicator_code)
--   用途: 网格TOPSIS计算时加权
-- ============================================================
DROP TABLE IF EXISTS jw_external_resource_weight;
CREATE TABLE jw_external_resource_weight (
  weight_id         BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT '权重主键ID',
  level1_name       VARCHAR(100)    DEFAULT ''               COMMENT '一级指标名称（如:人口因素）',
  level1_ratio      DECIMAL(10,6)   DEFAULT 0                COMMENT '一级指标占比',
  level2_name       VARCHAR(100)    DEFAULT ''               COMMENT '二级指标名称（如:人口密度）',
  level2_ratio      DECIMAL(10,6)   DEFAULT 0                COMMENT '二级指标占比',
  level3_name       VARCHAR(100)    DEFAULT ''               COMMENT '三级指标名称（如:工作人口）',
  level3_ratio      DECIMAL(10,6)   DEFAULT 0                COMMENT '三级指标占比',
  total_weight      DECIMAL(10,6)   DEFAULT 0                COMMENT '总权重 = level1_ratio * level2_ratio * level3_ratio',
  indicator_code    VARCHAR(64)     DEFAULT ''               COMMENT '关联指标编码（对应jw_indicator_config.indicator_code）',
  create_by         VARCHAR(64)     DEFAULT ''               COMMENT '创建者',
  create_time       DATETIME                                 COMMENT '创建时间',
  update_by         VARCHAR(64)     DEFAULT ''               COMMENT '更新者',
  update_time       DATETIME                                 COMMENT '更新时间',
  PRIMARY KEY (weight_id),
  KEY idx_indicator (indicator_code)
) ENGINE=INNODB AUTO_INCREMENT=100 COMMENT = '外部资源权重表（网格选址TOPSIS权重）';


-- ============================================================
-- 5、网点效能权重表
--   数据来源: 网点效能权重表_xxx.xlsx
--   关联: jw_indicator_config (indicator_code)
--   用途: 网点TOPSIS计算时加权
-- ============================================================
DROP TABLE IF EXISTS jw_branch_efficiency_weight;
CREATE TABLE jw_branch_efficiency_weight (
  weight_id         BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT '权重主键ID',
  level1_name       VARCHAR(100)    DEFAULT ''               COMMENT '一级指标名称（如:营收能力/业绩指标/客户发展/业务运营）',
  level1_ratio      DECIMAL(10,6)   DEFAULT 0                COMMENT '一级指标占比',
  level2_name       VARCHAR(100)    DEFAULT ''               COMMENT '二级指标名称（如:营业收入/全量个人金融资产/储蓄存款/...）',
  level2_ratio      DECIMAL(10,6)   DEFAULT 0                COMMENT '二级指标占比',
  level3_name       VARCHAR(100)    DEFAULT ''               COMMENT '三级指标名称（如:人均营业净收入/单位面积营业净收入/...）',
  level3_ratio      DECIMAL(10,6)   DEFAULT 0                COMMENT '三级指标占比',
  total_weight      DECIMAL(10,6)   DEFAULT 0                COMMENT '总权重 = level1_ratio * level2_ratio * level3_ratio',
  indicator_code    VARCHAR(64)     DEFAULT ''               COMMENT '关联指标编码（对应jw_indicator_config.indicator_code）',
  create_by         VARCHAR(64)     DEFAULT ''               COMMENT '创建者',
  create_time       DATETIME                                 COMMENT '创建时间',
  update_by         VARCHAR(64)     DEFAULT ''               COMMENT '更新者',
  update_time       DATETIME                                 COMMENT '更新时间',
  PRIMARY KEY (weight_id),
  KEY idx_indicator (indicator_code)
) ENGINE=INNODB AUTO_INCREMENT=100 COMMENT = '网点效能权重表（网点TOPSIS权重）';


-- ============================================================
-- 6、网点基本信息表
--   数据来源: 网点信息表_xxx.xlsx (基础数据Sheet) / 存量网点基本信息表_xxx.xlsx
--   关联: jw_grid_meta (grid_code, 空间关联)
--        jw_branch_indicator (branch_id)
--   → 其余功能: 地图网点标记、按行政区/一级支行筛选
-- ============================================================
DROP TABLE IF EXISTS jw_branch_info;
CREATE TABLE jw_branch_info (
  branch_id         BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT '网点主键ID',
  primary_branch    VARCHAR(100)    DEFAULT ''               COMMENT '一级支行（如:贵阳分行）',
  secondary_branch  VARCHAR(100)    DEFAULT ''               COMMENT '二级支行（如:清镇市支行）',
  branch_code       VARCHAR(32)     DEFAULT ''               COMMENT '网点号（4位数字编码，唯一标识）',
  city              VARCHAR(32)     DEFAULT ''               COMMENT '所属市（如:贵阳市）',
  grid_code         VARCHAR(64)     DEFAULT ''               COMMENT '所属1km网格编号（空间关联jw_grid_meta）',
  district_name     VARCHAR(64)     DEFAULT ''               COMMENT '行政区（如:清镇市/南明区）',
  street            VARCHAR(100)    DEFAULT ''               COMMENT '街道（如:云岭路）',
  address           VARCHAR(500)    DEFAULT ''               COMMENT '具体地址',
  longitude         DECIMAL(12,8)   DEFAULT NULL             COMMENT '经度',
  latitude          DECIMAL(12,8)   DEFAULT NULL             COMMENT '纬度',
  -- 人员信息
  total_staff       INT(11)         DEFAULT 0                COMMENT '总人数',
  personal_manager  INT(11)         DEFAULT 0                COMMENT '个人客户经理人数',
  corporate_manager INT(11)         DEFAULT 0                COMMENT '对公客户经理人数（专职）',
  counter_staff     INT(11)         DEFAULT 0                COMMENT '客服经理（柜面）人数',
  lobby_staff       INT(11)         DEFAULT 0                COMMENT '客服经理（厅堂）人数',
  -- 行长信息
  branch_manager    VARCHAR(64)     DEFAULT ''               COMMENT '网点行长姓名',
  manager_tenure    VARCHAR(100)    DEFAULT ''               COMMENT '在本网点任职时间（如:3年）',
  manager_resume    TEXT                                      COMMENT '完整履历信息（人力资源系统内格式）',
  manager_history   TEXT                                      COMMENT '历任行长记录（如:2023-2025年本网点历任行长）',
  -- 面积及功能分区
  total_area        DECIMAL(10,2)   DEFAULT 0                COMMENT '营业面积-总面积（不含公摊,㎡）',
  other_floor_area  DECIMAL(10,2)   DEFAULT 0                COMMENT '非首层面积（若为多层,除首层以外,㎡）',
  cash_counter      INT(11)         DEFAULT 0                COMMENT '现金柜台个数',
  non_cash_counter  INT(11)         DEFAULT 0                COMMENT '非现金柜台个数',
  manager_seat      INT(11)         DEFAULT 0                COMMENT '个人客户经理工位数',
  -- 其他
  property_right    VARCHAR(32)     DEFAULT ''               COMMENT '产权状态（自有/租赁）',
  lease_expire      VARCHAR(32)     DEFAULT ''               COMMENT '租赁到期时间（如:2029-12-01）',
  last_renovation   VARCHAR(32)     DEFAULT ''               COMMENT '最近一次装修时间（如:2018年）',
  branch_type       VARCHAR(64)     DEFAULT ''               COMMENT '网点业态分类（如:综合型网点/精品型网点）',
  relocation        VARCHAR(100)    DEFAULT ''               COMMENT '迁并情况（如:否/是-迁至xxx）',
  -- 系统字段
  data_source       VARCHAR(32)     DEFAULT '网点信息'       COMMENT '数据来源（网点信息/存量网点）',
  del_flag          CHAR(1)         DEFAULT '0'              COMMENT '删除标志（0存在 2删除）',
  create_by         VARCHAR(64)     DEFAULT ''               COMMENT '创建者',
  create_time       DATETIME                                 COMMENT '创建时间',
  update_by         VARCHAR(64)     DEFAULT ''               COMMENT '更新者',
  update_time       DATETIME                                 COMMENT '更新时间',
  remark            VARCHAR(500)    DEFAULT NULL             COMMENT '备注',
  PRIMARY KEY (branch_id),
  UNIQUE KEY uk_branch_code (branch_code),
  KEY idx_city (city),
  KEY idx_district (district_name),
  KEY idx_primary_branch (primary_branch),
  KEY idx_grid_code (grid_code)
) ENGINE=INNODB AUTO_INCREMENT=100 COMMENT = '网点基本信息表';


-- ============================================================
-- 7、网点业务指标表（垂直存储）
--   数据来源: 网点信息表_xxx.xlsx (3个Sheet)
--            Sheet1"基础数据" → sheet_type='基础数据', 共23个指标 × 3年
--            Sheet2"数据计算表" → sheet_type='数据计算表', 共22个计算指标
--            Sheet3"数据计算表（归一化处理）" → sheet_type='数据计算表归一化', 共22个归一化指标
--   关联: jw_branch_info (branch_id), jw_indicator_config (indicator_code)
--   → 计算后写入 jw_branch_summary → jw_branch_score
-- ============================================================
DROP TABLE IF EXISTS jw_branch_indicator;
CREATE TABLE jw_branch_indicator (
  indicator_id      BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
  branch_id         BIGINT(20)      NOT NULL                 COMMENT '网点ID（关联jw_branch_info）',
  data_year         INT(4)          NOT NULL                 COMMENT '数据年份（如:2024）',
  sheet_type        VARCHAR(32)     DEFAULT '基础数据'       COMMENT 'Sheet类型: 基础数据 / 数据计算表 / 数据计算表归一化',
  indicator_code    VARCHAR(64)     NOT NULL                 COMMENT '指标编码（关联jw_indicator_config）',
  indicator_value   DECIMAL(16,4)   DEFAULT 0                COMMENT '指标值',
  create_time       DATETIME                                 COMMENT '创建时间',
  PRIMARY KEY (indicator_id),
  UNIQUE KEY uk_branch_year_sheet_indicator (branch_id, data_year, sheet_type, indicator_code),
  KEY idx_branch_year (branch_id, data_year),
  KEY idx_indicator (indicator_code)
) ENGINE=INNODB AUTO_INCREMENT=100 COMMENT = '网点业务指标表（垂直存储，网点×年×Sheet×指标）';


-- ============================================================
-- 8、网点指标汇总表（权重/MAX/MIN）
--   数据来源: BranchComputeServiceImpl 计算生成
--   维度: 市 + 数据年份 + 指标编码
--   用途: 导出时填入"实际权重/MAX/MIN"行
-- ============================================================
DROP TABLE IF EXISTS jw_branch_summary;
CREATE TABLE jw_branch_summary (
  summary_id        BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
  city              VARCHAR(32)     NOT NULL                 COMMENT '市',
  data_year         INT(4)          NOT NULL                 COMMENT '数据年份',
  indicator_code    VARCHAR(64)     NOT NULL                 COMMENT '指标编码（关联jw_indicator_config）',
  actual_weight     DECIMAL(16,10)  DEFAULT 0                COMMENT '实际权重（来自jw_branch_efficiency_weight）',
  max_value         DECIMAL(16,4)   DEFAULT 0                COMMENT '该市该年该指标最大值',
  min_value         DECIMAL(16,4)   DEFAULT 0                COMMENT '该市该年该指标最小值',
  max_norm          DECIMAL(16,10)  DEFAULT NULL             COMMENT '归一化后最大值',
  min_norm          DECIMAL(16,10)  DEFAULT NULL             COMMENT '归一化后最小值',
  create_time       DATETIME                                 COMMENT '创建时间',
  PRIMARY KEY (summary_id),
  UNIQUE KEY uk_city_year_indicator (city, data_year, indicator_code)
) ENGINE=INNODB AUTO_INCREMENT=100 COMMENT = '网点指标汇总表（权重/MAX/MIN，驱动导出汇总行）';


-- ============================================================
-- 9、网点得分表（TOPSIS计算结果）
--   数据来源: BranchComputeServiceImpl 计算生成
--   维度: 网点 + 数据年份 + 市 + 评分类别(5类)
--   用途: 导出归一化Sheet右侧得分列、其余功能地图网点标记Popup
-- ============================================================
DROP TABLE IF EXISTS jw_branch_score;
CREATE TABLE jw_branch_score (
  score_id          BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
  branch_id         BIGINT(20)      NOT NULL                 COMMENT '网点ID（关联jw_branch_info）',
  data_year         INT(4)          NOT NULL                 COMMENT '数据年份',
  city              VARCHAR(32)     DEFAULT ''               COMMENT '所属市',
  score_category    VARCHAR(32)     NOT NULL                 COMMENT '评分类别: revenue(营收) / indicator(指标) / customer(客户) / operation(运营) / overall(总分)',
  positive_distance DECIMAL(16,10)  DEFAULT NULL             COMMENT '正理想解欧式距离 D+',
  negative_distance DECIMAL(16,10)  DEFAULT NULL             COMMENT '负理想解欧式距离 D-',
  category_score    DECIMAL(16,10)  DEFAULT NULL             COMMENT '类别得分 = D- / (D+ + D-)',
  rank_num          INT(11)         DEFAULT NULL             COMMENT '排名',
  create_time       DATETIME                                 COMMENT '创建时间',
  PRIMARY KEY (score_id),
  UNIQUE KEY uk_branch_year_city_cat (branch_id, data_year, city, score_category),
  KEY idx_city_year (city, data_year)
) ENGINE=INNODB AUTO_INCREMENT=100 COMMENT = '网点得分表（TOPSIS计算结果，5个评分类别）';


-- ============================================================
-- 10、网格元信息表
--     data来源: 人口热力_xxx.xlsx 导入时自动 upsert
--     POI统计不再在此表(已移至jw_grid_data_raw按类型分指标)
--     网格划分: 1km × 1km, 通过 bounding box 计算
--     其余功能: 地图网格热力展示
-- ============================================================
DROP TABLE IF EXISTS jw_grid_meta;
CREATE TABLE jw_grid_meta (
  grid_code         VARCHAR(64)     NOT NULL                 COMMENT '网格编号（主键，如:GZGY云岩0001）',
  longitude         DECIMAL(12,8)   DEFAULT NULL             COMMENT '经度（网格中心点）',
  latitude          DECIMAL(12,8)   DEFAULT NULL             COMMENT '纬度（网格中心点）',
  west_longitude    DECIMAL(12,8)   DEFAULT NULL             COMMENT '西经 = 经度 - 0.5/(111.32*COS(RADIANS(纬度)))',
  east_longitude    DECIMAL(12,8)   DEFAULT NULL             COMMENT '东经 = 经度 + 0.5/(111.32*COS(RADIANS(纬度)))',
  north_latitude    DECIMAL(12,8)   DEFAULT NULL             COMMENT '北纬 = 纬度 + 0.5/111.32',
  south_latitude    DECIMAL(12,8)   DEFAULT NULL             COMMENT '南纬 = 纬度 - 0.5/111.32',
  province          VARCHAR(32)     DEFAULT ''               COMMENT '省',
  city              VARCHAR(32)     DEFAULT ''               COMMENT '市',
  district          VARCHAR(32)     DEFAULT ''               COMMENT '区县',
  create_by         VARCHAR(64)     DEFAULT ''               COMMENT '创建者',
  create_time       DATETIME                                 COMMENT '创建时间',
  update_by         VARCHAR(64)     DEFAULT ''               COMMENT '更新者',
  update_time       DATETIME                                 COMMENT '更新时间',
  PRIMARY KEY (grid_code),
  KEY idx_city_district (city, district),
  KEY idx_coord (longitude, latitude)
) ENGINE=INNODB COMMENT = '网格元信息表（1km×1km网格，bounding box）';


-- ============================================================
-- 11、网格原始指标数据表（垂直存储）
--     data来源: GridComputeServiceImpl.computeGridRawData 生成
--     人口热力指标: 从 jw_population_heat 按网格聚合
--     POI指标: 从 jw_poi_info 按网格+poi_type分组计数(自动注册到jw_indicator_config)
-- ============================================================
DROP TABLE IF EXISTS jw_grid_data_raw;
CREATE TABLE jw_grid_data_raw (
  data_id           BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
  grid_code         VARCHAR(64)     NOT NULL                 COMMENT '网格编号（关联jw_grid_meta）',
  indicator_code    VARCHAR(64)     NOT NULL                 COMMENT '指标编码（关联jw_indicator_config）',
  indicator_value   DECIMAL(16,4)   DEFAULT 0                COMMENT '指标原始值',
  create_time       DATETIME                                 COMMENT '创建时间',
  PRIMARY KEY (data_id),
  UNIQUE KEY uk_grid_indicator (grid_code, indicator_code),
  KEY idx_grid (grid_code),
  KEY idx_indicator (indicator_code)
) ENGINE=INNODB AUTO_INCREMENT=100 COMMENT = '网格原始指标数据表（垂直存储，导出原始数据Sheet）';


-- ============================================================
-- 12、网格指标汇总表（权重/MAX/MIN）
--     data来源: GridComputeServiceImpl.computeGridSummary 生成
--     维度: 市 + 指标编码
-- ============================================================
DROP TABLE IF EXISTS jw_grid_summary;
CREATE TABLE jw_grid_summary (
  summary_id        BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
  city              VARCHAR(32)     NOT NULL                 COMMENT '市',
  indicator_code    VARCHAR(64)     NOT NULL                 COMMENT '指标编码（关联jw_indicator_config）',
  actual_weight     DECIMAL(16,10)  DEFAULT 0                COMMENT '实际权重（来自jw_external_resource_weight）',
  max_raw           DECIMAL(16,4)   DEFAULT 0                COMMENT '原始值最大值',
  min_raw           DECIMAL(16,4)   DEFAULT 0                COMMENT '原始值最小值',
  max_norm          DECIMAL(16,10)  DEFAULT NULL             COMMENT '归一化后最大值',
  min_norm          DECIMAL(16,10)  DEFAULT NULL             COMMENT '归一化后最小值',
  create_time       DATETIME                                 COMMENT '创建时间',
  PRIMARY KEY (summary_id),
  UNIQUE KEY uk_city_indicator (city, indicator_code)
) ENGINE=INNODB AUTO_INCREMENT=100 COMMENT = '网格指标汇总表（权重/MAX/MIN，驱动导出汇总行）';


-- ============================================================
-- 13、网格归一化指标数据表（垂直存储）
--     data来源: GridComputeServiceImpl.computeGridNormalized 生成
--     归一化公式: value / SQRT(SUMSQ(列))
--     导出为"归一化得分"Sheet
-- ============================================================
DROP TABLE IF EXISTS jw_grid_data_normalized;
CREATE TABLE jw_grid_data_normalized (
  data_id           BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
  grid_code         VARCHAR(64)     NOT NULL                 COMMENT '网格编号（关联jw_grid_meta）',
  indicator_code    VARCHAR(64)     NOT NULL                 COMMENT '指标编码（关联jw_indicator_config）',
  normalized_value  DECIMAL(16,10)  DEFAULT 0                COMMENT '归一化值 = 原始值 / SQRT(SUMSQ(列))',
  create_time       DATETIME                                 COMMENT '创建时间',
  PRIMARY KEY (data_id),
  UNIQUE KEY uk_grid_indicator (grid_code, indicator_code),
  KEY idx_grid (grid_code)
) ENGINE=INNODB AUTO_INCREMENT=100 COMMENT = '网格归一化指标数据表（垂直存储，导出归一化得分Sheet）';


-- ============================================================
-- 14、同业银行信息表
--    数据来源: 同业数据_xxx.xlsx
--    关联: jw_grid_meta (grid_code, 空间关联)
--    导入时自动根据经纬度计算所属网格
-- ============================================================
DROP TABLE IF EXISTS jw_peer_bank_info;
CREATE TABLE jw_peer_bank_info (
  peer_bank_id      BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT '同业银行主键ID',
  org_code          VARCHAR(64)     DEFAULT ''               COMMENT '机构编码',
  org_name          VARCHAR(200)    DEFAULT ''               COMMENT '机构名称',
  org_address       VARCHAR(500)    DEFAULT ''               COMMENT '机构地址',
  longitude         DECIMAL(12,8)   DEFAULT NULL             COMMENT '经度',
  latitude          DECIMAL(12,8)   DEFAULT NULL             COMMENT '纬度',
  bank_name         VARCHAR(64)     DEFAULT ''               COMMENT '银行名称',
  province          VARCHAR(32)     DEFAULT ''               COMMENT '省',
  city              VARCHAR(32)     DEFAULT ''               COMMENT '市',
  district          VARCHAR(64)     DEFAULT ''               COMMENT '区县',
  town              VARCHAR(100)    DEFAULT ''               COMMENT '乡镇/街道',
  grid_code         VARCHAR(64)     DEFAULT ''               COMMENT '所属网格编号（空间关联jw_grid_meta）',
  del_flag          CHAR(1)         DEFAULT '0'              COMMENT '删除标志（0存在 2删除）',
  create_by         VARCHAR(64)     DEFAULT ''               COMMENT '创建者',
  create_time       DATETIME                                 COMMENT '创建时间',
  update_by         VARCHAR(64)     DEFAULT ''               COMMENT '更新者',
  update_time       DATETIME                                 COMMENT '更新时间',
  remark            VARCHAR(500)    DEFAULT NULL             COMMENT '备注',
  PRIMARY KEY (peer_bank_id),
  UNIQUE KEY uk_org_code (org_code),
  KEY idx_city (city),
  KEY idx_bank_name (bank_name),
  KEY idx_grid_code (grid_code),
  KEY idx_district (district)
) ENGINE=INNODB AUTO_INCREMENT=100 COMMENT = '同业银行信息表';

-- ============================================================
-- 15、网格得分表（TOPSIS计算结果）
--     data来源: GridComputeServiceImpl.computeGridScore 生成
--     其余功能: 地图网格热力展示（根据选址得分 0-1 用不同色彩）
-- ============================================================
DROP TABLE IF EXISTS jw_grid_score;
CREATE TABLE jw_grid_score (
  grid_code         VARCHAR(64)     NOT NULL                 COMMENT '网格编号（主键，关联jw_grid_meta）',
  city              VARCHAR(32)     DEFAULT ''               COMMENT '所属市（冗余，便于按市查询热力数据）',
  positive_distance DECIMAL(16,10)  DEFAULT NULL             COMMENT '正理想解欧式距离 D+',
  negative_distance DECIMAL(16,10)  DEFAULT NULL             COMMENT '负理想解欧式距离 D-',
  site_score        DECIMAL(16,10)  DEFAULT NULL             COMMENT '选址得分 = D- / (D+ + D-)',
  create_time       DATETIME                                 COMMENT '创建时间',
  PRIMARY KEY (grid_code),
  KEY idx_city (city)
) ENGINE=INNODB COMMENT = '网格得分表（TOPSIS计算结果，驱动地图热力展示）';




