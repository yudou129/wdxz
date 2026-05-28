-- 测试用网点效能权重（22个衍生指标）
-- 使用前先清空旧数据：DELETE FROM jw_branch_efficiency_weight;
-- 然后执行本SQL

-- ===== 经营情况 (revenue, 权重0.20) =====
INSERT INTO jw_branch_efficiency_weight (level1_name, level1_ratio, level2_name, level2_ratio, level3_name, level3_ratio, total_weight, indicator_code)
VALUES ('经营情况', 0.20, '经营情况', 0.20, '人均营业收入(万元)', 0.50, 0.10, 'branch_rev_per_capita'),
       ('经营情况', 0.20, '经营情况', 0.20, '单位面积营业收入(万元)', 0.50, 0.10, 'branch_rev_per_area');

-- ===== 业绩表现 (indicator, 权重0.35) =====
-- 全量个人金融资产 (subWeight = 0.08 * 2 = 0.16 within indicator category)
INSERT INTO jw_branch_efficiency_weight (level1_name, level1_ratio, level2_name, level2_ratio, level3_name, level3_ratio, total_weight, indicator_code)
VALUES ('业绩表现', 0.35, '全量个人金融资产', 0.08, '户日均余额(万元)', 0.50, 0.04, 'branch_asset_avg_balance'),
       ('业绩表现', 0.35, '全量个人金融资产', 0.08, '日均增幅', 0.50, 0.04, 'branch_asset_avg_growth'),
       -- 储蓄存款 (subWeight = 0.08 * 2 = 0.16)
       ('业绩表现', 0.35, '储蓄存款', 0.08, '户日均余额(万元)', 0.50, 0.04, 'branch_saving_avg_balance'),
       ('业绩表现', 0.35, '储蓄存款', 0.08, '日均增幅', 0.50, 0.04, 'branch_saving_avg_growth'),
       -- 公司客户存款 (subWeight = 0.06 * 2 = 0.12)
       ('业绩表现', 0.35, '公司客户存款', 0.06, '户日均余额(万元)', 0.50, 0.03, 'branch_corp_dep_avg_balance'),
       ('业绩表现', 0.35, '公司客户存款', 0.06, '日均增幅', 0.50, 0.03, 'branch_corp_dep_avg_growth'),
       -- 机构客户存款 (subWeight = 0.04 * 2 = 0.08)
       ('业绩表现', 0.35, '机构客户存款', 0.04, '户日均余额(万元)', 0.50, 0.02, 'branch_inst_dep_avg_balance'),
       ('业绩表现', 0.35, '机构客户存款', 0.04, '日均增幅', 0.50, 0.02, 'branch_inst_dep_avg_growth'),
       -- 普惠贷款 + 个人贷款 (subWeight = 0.02 each)
       ('业绩表现', 0.35, '普惠贷款', 0.02, '人均营销额(万元)', 1.00, 0.02, 'branch_incloan_per_capita'),
       ('业绩表现', 0.35, '个人贷款', 0.02, '人均发放额(万元)', 1.00, 0.02, 'branch_perloan_per_capita');

-- ===== 客户发展 (customer, 权重0.25) =====
-- 个人客户 (subWeight = 0.12, 3 indicators)
INSERT INTO jw_branch_efficiency_weight (level1_name, level1_ratio, level2_name, level2_ratio, level3_name, level3_ratio, total_weight, indicator_code)
VALUES ('客户发展', 0.25, '个人客户', 0.12, '人均服务日均0元(不含)-20万元(不含)客户数(单位：户)', 0.33, 0.04, 'branch_pcust_t1_per_capita'),
       ('客户发展', 0.25, '个人客户', 0.12, '人均服务日均20万元(含)-600万元(不含)客户数(单位：户)', 0.33, 0.04, 'branch_pcust_t2_per_capita'),
       ('客户发展', 0.25, '个人客户', 0.12, '人均服务日均大于等于600万元(含)客户数(单位：户)', 0.34, 0.04, 'branch_pcust_t3_per_capita'),
       -- 对公客户 (subWeight = 0.08, 2 indicators)
       ('客户发展', 0.25, '对公客户', 0.08, '人均服务头部、中部对公客户数日均资产30万元(含)以上(单位：户)', 0.50, 0.04, 'branch_ccust_h_per_capita'),
       ('客户发展', 0.25, '对公客户', 0.08, '人均服务底尾部对公客户数日均资产30万元(不含)以下(单位：户)', 0.50, 0.04, 'branch_ccust_l_per_capita'),
       -- 机构客户 (subWeight = 0.05, 2 indicators)
       ('客户发展', 0.25, '机构客户', 0.05, '人均服务日均资产1万元(不含)以上机构客户数(单位：户)', 0.50, 0.025, 'branch_icust_h_per_capita'),
       ('客户发展', 0.25, '机构客户', 0.05, '人均服务日均资产1万元(含)以下机构客户数(单位：户)', 0.50, 0.025, 'branch_icust_l_per_capita');

-- ===== 业务运营 (operation, 权重0.20) =====
INSERT INTO jw_branch_efficiency_weight (level1_name, level1_ratio, level2_name, level2_ratio, level3_name, level3_ratio, total_weight, indicator_code)
VALUES ('业务运营', 0.20, '业务运营', 0.20, '每单位面积柜台日均工作量(笔)', 0.30, 0.06, 'branch_counter_per_area'),
       ('业务运营', 0.20, '业务运营', 0.20, '每单位面积自助终端日均交易笔数', 0.35, 0.07, 'branch_terminal_per_area'),
       ('业务运营', 0.20, '业务运营', 0.20, '每单位面积附行式ATM日均交易笔数', 0.35, 0.07, 'branch_atm_per_area');
