-- ============================================================
-- 指标系统重构：迁移脚本
--
-- 作用：将 jw_indicator_config、jw_external_resource_weight、
--       jw_branch_efficiency_weight 三表合并为一张多级树表
--
-- 运行前提：备份数据库
-- 执行方式：逐段执行，每段完成后检查结果
-- ============================================================

-- ============================================================
-- Phase 1: ALTER TABLE jw_indicator_config 新增字段
-- ============================================================
ALTER TABLE jw_indicator_config
  ADD COLUMN indicator_type        VARCHAR(16)  COMMENT '指标类型: grid/branch_raw/branch',
  ADD COLUMN parent_code           VARCHAR(64)  COMMENT '上级指标编码',
  ADD COLUMN is_derived            CHAR(1)      DEFAULT '0' COMMENT '是否衍生计算指标',
  ADD COLUMN computation_pattern   VARCHAR(32)  COMMENT '计算模式',
  ADD COLUMN input_codes           VARCHAR(512) COMMENT '参与计算的指标编码',
  ADD COLUMN calculation_weight    DOUBLE       COMMENT '本级计算权重',
  ADD INDEX idx_indicator_type (indicator_type),
  ADD INDEX idx_parent_code (parent_code);

-- ============================================================
-- Phase 2: 标记现有指标的 indicator_type
-- ============================================================

-- 2a. 网格指标（人口热力 + 网格数据相关）
UPDATE jw_indicator_config
SET indicator_type = 'grid', is_derived = '0'
WHERE source_tables LIKE '%人口热力%'
   OR source_tables LIKE '%网格数据%';

-- 2b. 网点原始数据指标 -> branch_raw
UPDATE jw_indicator_config
SET indicator_type = 'branch_raw', is_derived = '0'
WHERE indicator_code IN (
    'interest_income', 'fee_income',
    'total_asset_balance', 'total_asset_growth',
    'saving_balance', 'saving_growth',
    'corp_dep_balance', 'corp_dep_growth',
    'inst_dep_balance', 'inst_dep_growth',
    'inclusive_loan_amount', 'personal_loan_amount',
    'pcust_t1', 'pcust_t2', 'pcust_t3',
    'ccust_h', 'ccust_l', 'icust_h', 'icust_l',
    'inclusive_cust_total',
    'counter_txn', 'terminal_txn', 'atm_txn'
);

-- 2c. 网点衍生指标 -> branch
UPDATE jw_indicator_config
SET indicator_type = 'branch', is_derived = '1'
WHERE indicator_code LIKE 'branch_%'
   OR source_tables LIKE '%网点指标(计算)%';

-- ============================================================
-- Phase 3: 从 jw_external_resource_weight 迁移 grid 多级树
-- ============================================================

-- 3a. 创建 grid L1 根节点（去重 level1_name）
INSERT INTO jw_indicator_config (
    indicator_code, indicator_name, indicator_type,
    parent_code, is_derived, calculation_weight,
    sort_order, create_time, update_time
)
SELECT DISTINCT
    CONCAT('grid_l1_', REPLACE(REPLACE(level1_name, ' ', '_'), '（', '_')),
    level1_name,
    'grid',
    NULL,           -- 根节点 parent_code = NULL
    '0',
    MAX(level1_ratio),
    0,
    NOW(), NOW()
FROM jw_external_resource_weight
WHERE level1_name IS NOT NULL AND level1_name != ''
GROUP BY level1_name
ON DUPLICATE KEY UPDATE
    indicator_type = 'grid',
    parent_code = NULL,
    calculation_weight = VALUES(calculation_weight);

-- 3b. 创建 grid L2 中间节点
INSERT INTO jw_indicator_config (
    indicator_code, indicator_name, indicator_type,
    parent_code, is_derived, calculation_weight,
    sort_order, create_time, update_time
)
SELECT DISTINCT
    CONCAT('grid_l1_', REPLACE(REPLACE(w.level1_name, ' ', '_'), '（', '_'), '_l2_', REPLACE(REPLACE(w.level2_name, ' ', '_'), '（', '_')),
    w.level2_name,
    'grid',
    CONCAT('grid_l1_', REPLACE(REPLACE(w.level1_name, ' ', '_'), '（', '_')),   -- parent_code = L1 的 indicator_code
    '0',
    MAX(w.level2_ratio),
    0,
    NOW(), NOW()
FROM jw_external_resource_weight w
WHERE w.level2_name IS NOT NULL AND w.level2_name != ''
GROUP BY w.level1_name, w.level2_name
ON DUPLICATE KEY UPDATE
    indicator_type = 'grid',
    parent_code = VALUES(parent_code),
    calculation_weight = VALUES(calculation_weight);

-- 3c. 更新 grid 叶子指标：设置 parent_code 和 calculation_weight
UPDATE jw_indicator_config leaf
JOIN jw_external_resource_weight w ON leaf.indicator_code = w.indicator_code
SET
    leaf.parent_code = CONCAT('grid_l1_', REPLACE(REPLACE(w.level1_name, ' ', '_'), '（', '_'), '_l2_', REPLACE(REPLACE(w.level2_name, ' ', '_'), '（', '_')),
    leaf.calculation_weight = w.level3_ratio,
    leaf.indicator_type = 'grid',
    leaf.is_derived = '0'
WHERE leaf.indicator_type = 'grid' OR leaf.indicator_type IS NULL;

-- ============================================================
-- Phase 3d: 创建网格三聚焦根节点（人口、企业、商圈）
-- 即使权重表中没有数据也要创建，确保 POI分类有上级节点
-- ============================================================
INSERT INTO jw_indicator_config (
    indicator_code, indicator_name, indicator_type,
    parent_code, is_derived, calculation_weight,
    sort_order, create_time, update_time
) VALUES
('grid_l1_population', '人口',   'grid', NULL, '0', 0.40, 1, NOW(), NOW()),
('grid_l1_enterprise', '企业',   'grid', NULL, '0', 0.35, 2, NOW(), NOW()),
('grid_l1_business',   '商圈',   'grid', NULL, '0', 0.25, 3, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    indicator_type = 'grid',
    parent_code = NULL,
    calculation_weight = VALUES(calculation_weight);

-- ============================================================
-- Phase 3e: POI类型指标由用户通过"指标管理"页面自行添加
-- 在管理页面中选择 grid 类型、设置 parent_code = grid_l1_enterprise
-- 或 grid_l1_business，即可将POI类型归入对应分类参与计算
-- ============================================================

-- ============================================================
-- Phase 4: 从 jw_branch_efficiency_weight 迁移 branch 多级树
-- ============================================================

-- 4a. 创建 branch L1 根节点
INSERT INTO jw_indicator_config (
    indicator_code, indicator_name, indicator_type,
    parent_code, is_derived, calculation_weight,
    sort_order, create_time, update_time
)
SELECT DISTINCT
    CASE level1_name
        WHEN '营收能力' THEN 'branch_l1_revenue'
        WHEN '业绩表现' THEN 'branch_l1_indicator'
        WHEN '客户发展' THEN 'branch_l1_customer'
        WHEN '业务运营' THEN 'branch_l1_operation'
        ELSE CONCAT('branch_l1_', REPLACE(level1_name, ' ', '_'))
    END,
    level1_name,
    'branch',
    NULL,
    '0',
    MAX(level1_ratio),
    0,
    NOW(), NOW()
FROM jw_branch_efficiency_weight
WHERE level1_name IS NOT NULL AND level1_name != ''
GROUP BY level1_name
ON DUPLICATE KEY UPDATE
    indicator_type = 'branch',
    parent_code = NULL,
    calculation_weight = VALUES(calculation_weight);

-- 4b. 创建 branch L2 中间节点
INSERT INTO jw_indicator_config (
    indicator_code, indicator_name, indicator_type,
    parent_code, is_derived, calculation_weight,
    sort_order, create_time, update_time
)
SELECT DISTINCT
    CONCAT(
        CASE w.level1_name
            WHEN '营收能力' THEN 'branch_l1_revenue'
            WHEN '业绩表现' THEN 'branch_l1_indicator'
            WHEN '客户发展' THEN 'branch_l1_customer'
            WHEN '业务运营' THEN 'branch_l1_operation'
            ELSE CONCAT('branch_l1_', REPLACE(w.level1_name, ' ', '_'))
        END,
        '_l2_', REPLACE(REPLACE(w.level2_name, ' ', '_'), '（', '_')
    ),
    w.level2_name,
    'branch',
    CASE w.level1_name
        WHEN '营收能力' THEN 'branch_l1_revenue'
        WHEN '业绩表现' THEN 'branch_l1_indicator'
        WHEN '客户发展' THEN 'branch_l1_customer'
        WHEN '业务运营' THEN 'branch_l1_operation'
        ELSE CONCAT('branch_l1_', REPLACE(w.level1_name, ' ', '_'))
    END,
    '0',
    MAX(w.level2_ratio),
    0,
    NOW(), NOW()
FROM jw_branch_efficiency_weight w
WHERE w.level2_name IS NOT NULL AND w.level2_name != ''
GROUP BY w.level1_name, w.level2_name
ON DUPLICATE KEY UPDATE
    indicator_type = 'branch',
    parent_code = VALUES(parent_code),
    calculation_weight = VALUES(calculation_weight);

-- 4c. 更新 branch 叶子指标
UPDATE jw_indicator_config leaf
JOIN jw_branch_efficiency_weight w ON leaf.indicator_code = w.indicator_code
SET
    leaf.parent_code = CONCAT(
        CASE w.level1_name
            WHEN '营收能力' THEN 'branch_l1_revenue'
            WHEN '业绩表现' THEN 'branch_l1_indicator'
            WHEN '客户发展' THEN 'branch_l1_customer'
            WHEN '业务运营' THEN 'branch_l1_operation'
            ELSE CONCAT('branch_l1_', REPLACE(w.level1_name, ' ', '_'))
        END,
        '_l2_', REPLACE(REPLACE(w.level2_name, ' ', '_'), '（', '_')
    ),
    leaf.calculation_weight = w.level3_ratio,
    leaf.indicator_type = 'branch',
    leaf.is_derived = '1'
WHERE leaf.indicator_type = 'branch' OR leaf.indicator_code LIKE 'branch_%';

-- ============================================================
-- Phase 5: 补充 branch_raw 指标（如果缺失）
-- ============================================================
INSERT INTO jw_indicator_config (
    indicator_code, indicator_name, indicator_type,
    parent_code, is_derived, calculation_weight,
    sort_order, create_time, update_time
) VALUES
('interest_income',         '利息净收入(万元)',                               'branch_raw', NULL, '0', NULL, 1,  NOW(), NOW()),
('fee_income',              '手续费净收入(万元)',                             'branch_raw', NULL, '0', NULL, 2,  NOW(), NOW()),
('total_asset_balance',     '全量个人金融资产 日均余额(万元)',                 'branch_raw', NULL, '0', NULL, 3,  NOW(), NOW()),
('total_asset_growth',      '全量个人金融资产 日均增量(万元)',                 'branch_raw', NULL, '0', NULL, 4,  NOW(), NOW()),
('saving_balance',          '储蓄存款 日均余额(万元)',                        'branch_raw', NULL, '0', NULL, 5,  NOW(), NOW()),
('saving_growth',           '储蓄存款 日均增量(万元)',                        'branch_raw', NULL, '0', NULL, 6,  NOW(), NOW()),
('corp_dep_balance',        '公司客户存款 日均余额(万元)',                     'branch_raw', NULL, '0', NULL, 7,  NOW(), NOW()),
('corp_dep_growth',         '公司客户存款 日均增量(万元)',                     'branch_raw', NULL, '0', NULL, 8,  NOW(), NOW()),
('inst_dep_balance',        '机构客户存款 日均余额(万元)',                     'branch_raw', NULL, '0', NULL, 9,  NOW(), NOW()),
('inst_dep_growth',         '机构客户存款 日均增量(万元)',                     'branch_raw', NULL, '0', NULL, 10, NOW(), NOW()),
('inclusive_loan_amount',   '普惠贷款 营销额(万元)',                          'branch_raw', NULL, '0', NULL, 11, NOW(), NOW()),
('personal_loan_amount',    '个人贷款 发放额(万元)',                          'branch_raw', NULL, '0', NULL, 12, NOW(), NOW()),
('pcust_t1',                '日均0元(不含)-20万元(不含)客户数',               'branch_raw', NULL, '0', NULL, 13, NOW(), NOW()),
('pcust_t2',                '日均20万元(含)-600万元(不含)客户数',             'branch_raw', NULL, '0', NULL, 14, NOW(), NOW()),
('pcust_t3',                '日均大于等于600万(含)客户数',                    'branch_raw', NULL, '0', NULL, 15, NOW(), NOW()),
('ccust_h',                 '头部中部对公客户数 日均资产50万元(含)以上',       'branch_raw', NULL, '0', NULL, 16, NOW(), NOW()),
('ccust_l',                 '底尾部对公客户数 日均资产50万元(不含)以下',       'branch_raw', NULL, '0', NULL, 17, NOW(), NOW()),
('icust_h',                 '日均资产1万元(不含)以上机构客户数',              'branch_raw', NULL, '0', NULL, 18, NOW(), NOW()),
('icust_l',                 '日均资产1万元(含)以下机构客户数',                'branch_raw', NULL, '0', NULL, 19, NOW(), NOW()),
('inclusive_cust_total',    '总量(单位:户)',                                 'branch_raw', NULL, '0', NULL, 20, NOW(), NOW()),
('counter_txn',             '柜台日均交易笔数',                              'branch_raw', NULL, '0', NULL, 21, NOW(), NOW()),
('terminal_txn',            '自助终端日均交易笔数',                          'branch_raw', NULL, '0', NULL, 22, NOW(), NOW()),
('atm_txn',                 '附行式网点自助ATM日均交易笔数',                  'branch_raw', NULL, '0', NULL, 23, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    indicator_type = 'branch_raw',
    indicator_name = VALUES(indicator_name),
    update_time = NOW();

-- ============================================================
-- Phase 6: 更新 branch 叶子指标的 computation_pattern 和 input_codes
-- ============================================================
-- 营收类
UPDATE jw_indicator_config SET computation_pattern = 'sum_per_capita', input_codes = 'interest_income,fee_income' WHERE indicator_code = 'branch_rev_per_capita';
UPDATE jw_indicator_config SET computation_pattern = 'sum_per_area',   input_codes = 'interest_income,fee_income' WHERE indicator_code = 'branch_rev_per_area';

-- 资产类
UPDATE jw_indicator_config SET computation_pattern = 'per_customer', input_codes = 'total_asset_balance|pcust' WHERE indicator_code = 'branch_asset_avg_balance';
UPDATE jw_indicator_config SET computation_pattern = 'growth_rate',  input_codes = 'total_asset_balance|total_asset_growth' WHERE indicator_code = 'branch_asset_avg_growth';

-- 储蓄存款
UPDATE jw_indicator_config SET computation_pattern = 'per_customer', input_codes = 'saving_balance|pcust' WHERE indicator_code = 'branch_saving_avg_balance';
UPDATE jw_indicator_config SET computation_pattern = 'growth_rate',  input_codes = 'saving_balance|saving_growth' WHERE indicator_code = 'branch_saving_avg_growth';

-- 公司存款
UPDATE jw_indicator_config SET computation_pattern = 'per_customer', input_codes = 'corp_dep_balance|ccust' WHERE indicator_code = 'branch_corp_dep_avg_balance';
UPDATE jw_indicator_config SET computation_pattern = 'growth_rate',  input_codes = 'corp_dep_balance|corp_dep_growth' WHERE indicator_code = 'branch_corp_dep_avg_growth';

-- 机构存款
UPDATE jw_indicator_config SET computation_pattern = 'per_customer', input_codes = 'inst_dep_balance|icust' WHERE indicator_code = 'branch_inst_dep_avg_balance';
UPDATE jw_indicator_config SET computation_pattern = 'growth_rate',  input_codes = 'inst_dep_balance|inst_dep_growth' WHERE indicator_code = 'branch_inst_dep_avg_growth';

-- 贷款类
UPDATE jw_indicator_config SET computation_pattern = 'per_capita', input_codes = 'inclusive_loan_amount' WHERE indicator_code = 'branch_incloan_per_capita';
UPDATE jw_indicator_config SET computation_pattern = 'per_capita', input_codes = 'personal_loan_amount' WHERE indicator_code = 'branch_perloan_per_capita';

-- 客户类
UPDATE jw_indicator_config SET computation_pattern = 'per_capita', input_codes = 'pcust_t1' WHERE indicator_code = 'branch_pcust_t1_per_capita';
UPDATE jw_indicator_config SET computation_pattern = 'per_capita', input_codes = 'pcust_t2' WHERE indicator_code = 'branch_pcust_t2_per_capita';
UPDATE jw_indicator_config SET computation_pattern = 'per_capita', input_codes = 'pcust_t3' WHERE indicator_code = 'branch_pcust_t3_per_capita';
UPDATE jw_indicator_config SET computation_pattern = 'per_capita', input_codes = 'ccust_h' WHERE indicator_code = 'branch_ccust_h_per_capita';
UPDATE jw_indicator_config SET computation_pattern = 'per_capita', input_codes = 'ccust_l' WHERE indicator_code = 'branch_ccust_l_per_capita';
UPDATE jw_indicator_config SET computation_pattern = 'per_capita', input_codes = 'icust_h' WHERE indicator_code = 'branch_icust_h_per_capita';
UPDATE jw_indicator_config SET computation_pattern = 'per_capita', input_codes = 'icust_l' WHERE indicator_code = 'branch_icust_l_per_capita';

-- 运营类
UPDATE jw_indicator_config SET computation_pattern = 'per_area', input_codes = 'counter_txn' WHERE indicator_code = 'branch_counter_per_area';
UPDATE jw_indicator_config SET computation_pattern = 'per_area', input_codes = 'terminal_txn' WHERE indicator_code = 'branch_terminal_per_area';
UPDATE jw_indicator_config SET computation_pattern = 'per_area', input_codes = 'atm_txn' WHERE indicator_code = 'branch_atm_per_area';

-- ============================================================
-- Phase 7: ALTER TABLE jw_grid_score 新增 score_category
-- ============================================================
ALTER TABLE jw_grid_score
  ADD COLUMN score_category VARCHAR(32) DEFAULT 'overall' COMMENT '得分类别: population/enterprise/business/overall';

-- 如果有复合主键需要调整（原主键只有 grid_code）
-- 改为 (grid_code, score_category) 联合主键
ALTER TABLE jw_grid_score DROP PRIMARY KEY;
ALTER TABLE jw_grid_score ADD PRIMARY KEY (grid_code, score_category);

-- ============================================================
-- Phase 7b: ALTER TABLE jw_branch_score 添加联合唯一键
-- 使 upsertBranchScore 的 ON DUPLICATE KEY UPDATE 生效
-- ============================================================
ALTER TABLE jw_branch_score
  ADD UNIQUE KEY uk_branch_year_category (branch_id, data_year, score_category);

-- ============================================================
-- Phase 8: DROP 旧字段（检查 Phase 5 数据确实迁移完后再执行）
-- ============================================================
-- ALTER TABLE jw_indicator_config
--   DROP COLUMN category_level1,
--   DROP COLUMN category_level2,
--   DROP COLUMN data_type,
--   DROP COLUMN source_tables,
--   DROP COLUMN is_weighted,
--   DROP COLUMN is_active;

-- ============================================================
-- Phase 9: DROP 权重表（确认计算正常后再执行）
-- ============================================================
-- DROP TABLE IF EXISTS jw_external_resource_weight;
-- DROP TABLE IF EXISTS jw_branch_efficiency_weight;
