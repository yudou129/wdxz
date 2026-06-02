-- jw_score_category_config: 评分分类指标映射配置表
-- 替代 BranchComputeServiceImpl 中硬编码的 CATEGORY_MAP
-- 支持后续指标扩展，无需修改代码

DROP TABLE IF EXISTS jw_score_category_config;

CREATE TABLE jw_score_category_config (
    config_id     BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_code VARCHAR(32)  NOT NULL COMMENT '类别编码: revenue/indicator/customer/operation',
    category_name VARCHAR(64)  NOT NULL COMMENT '类别名称',
    indicator_code VARCHAR(64) NOT NULL COMMENT '关联指标编码',
    sort_order    INT(4)       DEFAULT 0 COMMENT '排序',
    is_active     CHAR(1)      DEFAULT '1' COMMENT '是否启用',
    create_time   DATETIME     DEFAULT NULL,
    update_time   DATETIME     DEFAULT NULL,
    PRIMARY KEY (config_id),
    KEY idx_category (category_code),
    KEY idx_indicator (indicator_code),
    UNIQUE KEY uk_category_indicator (category_code, indicator_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分分类指标映射配置';

-- ===== 种子数据：22个衍生指标 → 4个分类 =====

-- 营收类 (2)
INSERT INTO jw_score_category_config (category_code, category_name, indicator_code, sort_order, is_active, create_time, update_time) VALUES
('revenue', '营收', 'branch_rev_per_capita', 1, '1', NOW(), NOW()),
('revenue', '营收', 'branch_rev_per_area', 2, '1', NOW(), NOW());

-- 业绩类 (10)
INSERT INTO jw_score_category_config (category_code, category_name, indicator_code, sort_order, is_active, create_time, update_time) VALUES
('indicator', '业绩', 'branch_asset_avg_balance', 1, '1', NOW(), NOW()),
('indicator', '业绩', 'branch_asset_avg_growth', 2, '1', NOW(), NOW()),
('indicator', '业绩', 'branch_saving_avg_balance', 3, '1', NOW(), NOW()),
('indicator', '业绩', 'branch_saving_avg_growth', 4, '1', NOW(), NOW()),
('indicator', '业绩', 'branch_corp_dep_avg_balance', 5, '1', NOW(), NOW()),
('indicator', '业绩', 'branch_corp_dep_avg_growth', 6, '1', NOW(), NOW()),
('indicator', '业绩', 'branch_inst_dep_avg_balance', 7, '1', NOW(), NOW()),
('indicator', '业绩', 'branch_inst_dep_avg_growth', 8, '1', NOW(), NOW()),
('indicator', '业绩', 'branch_incloan_per_capita', 9, '1', NOW(), NOW()),
('indicator', '业绩', 'branch_perloan_per_capita', 10, '1', NOW(), NOW());

-- 客户类 (7)
INSERT INTO jw_score_category_config (category_code, category_name, indicator_code, sort_order, is_active, create_time, update_time) VALUES
('customer', '客户', 'branch_pcust_t1_per_capita', 1, '1', NOW(), NOW()),
('customer', '客户', 'branch_pcust_t2_per_capita', 2, '1', NOW(), NOW()),
('customer', '客户', 'branch_pcust_t3_per_capita', 3, '1', NOW(), NOW()),
('customer', '客户', 'branch_ccust_h_per_capita', 4, '1', NOW(), NOW()),
('customer', '客户', 'branch_ccust_l_per_capita', 5, '1', NOW(), NOW()),
('customer', '客户', 'branch_icust_h_per_capita', 6, '1', NOW(), NOW()),
('customer', '客户', 'branch_icust_l_per_capita', 7, '1', NOW(), NOW());

-- 运营类 (3)
INSERT INTO jw_score_category_config (category_code, category_name, indicator_code, sort_order, is_active, create_time, update_time) VALUES
('operation', '运营', 'branch_counter_per_area', 1, '1', NOW(), NOW()),
('operation', '运营', 'branch_terminal_per_area', 2, '1', NOW(), NOW()),
('operation', '运营', 'branch_atm_per_area', 3, '1', NOW(), NOW());
