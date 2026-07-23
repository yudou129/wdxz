-- ============================================================================
-- jw-map 模块 Mapper XML → GaussDB (华为高斯) SQL 转换脚本
--
-- 功能: 将 MySQL MyBatis XML 中所有 SQL 语句提取为 GaussDB 可执行的函数/过程
-- 源文件: src/main/resources/mapper/jwmap/*.xml (MySQL版)
-- 目标:   src/main/postMapper/jwmap/*.xml (GaussDB版)
--
-- 转换规则:
--   [1] now() → CURRENT_TIMESTAMP (MySQL 的 now() GaussDB 兼容, 但明确使用标准写法)
--   [2] ON DUPLICATE KEY UPDATE → ON CONFLICT ... DO UPDATE SET ... EXCLUDED.
--   [3] CASE 批量 UPDATE → UPDATE ... FROM (VALUES ...) AS t WHERE ...
--   [4] useGeneratedKeys + keyProperty → RETURNING id INTO
--   [5] LIMIT 无 OFFSET → LIMIT (兼容)
--   [6] COALESCE (已兼容)
--   [7] SELECT COUNT(1)/COUNT(*) → 均兼容
--   [8] LIKE '%' || #{xxx} || '%' → CONCAT (已使用标准SQL兼容写法)
--   [9] BETWEEN → 兼容
--   [10] 标量子查询 IN: 兼容
-- ============================================================================

-- ============================================================================
-- 1. AiAnalysisMapper — AI分析记录
-- 表: jw_ai_analysis
-- 文件: ai/AiAnalysisMapper.xml
-- 关键转换: ON DUPLICATE KEY UPDATE → ON CONFLICT ... DO UPDATE
-- ============================================================================

-- 查询
SELECT id, analysis_type, entity_key, city, content, satisfied, expired, created_at, updated_at
FROM jw_ai_analysis
WHERE analysis_type = :analysisType AND entity_key = :entityKey;

SELECT id, analysis_type, entity_key, city, content, satisfied, expired, created_at, updated_at
FROM jw_ai_analysis
WHERE city = :city;

-- 插入 (GaussDB不支持ON CONFLICT/MERGE INTO, Java层先查后增改)
INSERT INTO jw_ai_analysis (analysis_type, entity_key, city, content, satisfied, expired, created_at, updated_at)
VALUES (:analysisType, :entityKey, :city, :content, :satisfied, :expired, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 按UK更新
UPDATE jw_ai_analysis
SET content = :content,
    city = :city,
    satisfied = NULL,
    expired = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE analysis_type = :analysisType AND entity_key = :entityKey;

-- 按城市过期
UPDATE jw_ai_analysis
SET expired = 1, updated_at = CURRENT_TIMESTAMP
WHERE city = :city;

-- 更新满意度
UPDATE jw_ai_analysis
SET satisfied = :satisfied, updated_at = CURRENT_TIMESTAMP
WHERE analysis_type = :analysisType AND entity_key = :entityKey;


-- ============================================================================
-- 2. JwBranchIndicatorMapper — 网点指标数据
-- 表: jw_branch_indicator
-- 文件: JwBranchIndicatorMapper.xml
-- ============================================================================

-- 通用查询
SELECT indicator_id, branch_id, data_year, sheet_type, indicator_code, indicator_value, create_time
FROM jw_branch_indicator
WHERE (:branchId IS NULL OR branch_id = :branchId)
  AND (:dataYear IS NULL OR data_year = :dataYear)
  AND (:sheetType IS NULL OR sheet_type = :sheetType)
  AND (:indicatorCode IS NULL OR indicator_code = :indicatorCode)
ORDER BY indicator_id;

SELECT indicator_id, branch_id, data_year, sheet_type, indicator_code, indicator_value, create_time
FROM jw_branch_indicator
WHERE indicator_id = :indicatorId;

-- 按网点+年份查询
SELECT indicator_id, branch_id, data_year, sheet_type, indicator_code, indicator_value, create_time
FROM jw_branch_indicator
WHERE branch_id = :branchId AND data_year = :dataYear
  AND (:sheetType IS NULL OR :sheetType = '' OR sheet_type = :sheetType)
ORDER BY indicator_code;

-- 按网点+年份+表类型+指标查询
SELECT indicator_id, branch_id, data_year, sheet_type, indicator_code, indicator_value, create_time
FROM jw_branch_indicator
WHERE branch_id = :branchId AND data_year = :dataYear
  AND sheet_type = :sheetType AND indicator_code = :indicatorCode;

-- 按城市+年份（JOIN网点信息）
SELECT i.indicator_id, i.branch_id, i.data_year, i.sheet_type, i.indicator_code, i.indicator_value, i.create_time
FROM jw_branch_indicator i
JOIN jw_branch_info b ON i.branch_id = b.branch_id
WHERE b.city = :city AND i.data_year = :dataYear
  AND (:sheetType IS NULL OR :sheetType = '' OR i.sheet_type = :sheetType)
ORDER BY i.branch_id, i.indicator_code;

-- 按城市+年份范围
SELECT i.indicator_id, i.branch_id, i.data_year, i.sheet_type, i.indicator_code, i.indicator_value, i.create_time
FROM jw_branch_indicator i
JOIN jw_branch_info b ON i.branch_id = b.branch_id
WHERE b.city = :city AND i.data_year BETWEEN :startYear AND :endYear
  AND (:sheetType IS NULL OR :sheetType = '' OR i.sheet_type = :sheetType)
ORDER BY i.branch_id, i.indicator_code;

-- 按城市+表类型
SELECT i.indicator_id, i.branch_id, i.data_year, i.sheet_type, i.indicator_code, i.indicator_value, i.create_time
FROM jw_branch_indicator i
JOIN jw_branch_info b ON i.branch_id = b.branch_id
WHERE b.city = :city
  AND (:sheetType IS NULL OR :sheetType = '' OR i.sheet_type = :sheetType)
ORDER BY i.branch_id, i.data_year, i.indicator_code;

-- 按城市+年份+表类型
SELECT i.indicator_id, i.branch_id, i.data_year, i.sheet_type, i.indicator_code, i.indicator_value, i.create_time
FROM jw_branch_indicator i
JOIN jw_branch_info b ON i.branch_id = b.branch_id
WHERE b.city = :city AND i.data_year = :dataYear AND i.sheet_type = :sheetType
ORDER BY i.branch_id, i.indicator_code;

-- 插入 (使用 RETURNING 替代 useGeneratedKeys)
INSERT INTO jw_branch_indicator (branch_id, data_year, sheet_type, indicator_code, indicator_value, create_time)
VALUES (:branchId, :dataYear, :sheetType, :indicatorCode, :indicatorValue, CURRENT_TIMESTAMP)
RETURNING indicator_id;

-- 更新
UPDATE jw_branch_indicator
SET branch_id = COALESCE(:branchId, branch_id),
    data_year = COALESCE(:dataYear, data_year),
    sheet_type = COALESCE(:sheetType, sheet_type),
    indicator_code = COALESCE(:indicatorCode, indicator_code),
    indicator_value = COALESCE(:indicatorValue, indicator_value)
WHERE indicator_id = :indicatorId;

-- 删除
DELETE FROM jw_branch_indicator WHERE indicator_id = :indicatorId;
DELETE FROM jw_branch_indicator WHERE indicator_id = ANY(:ids);
DELETE FROM jw_branch_indicator
WHERE branch_id = :branchId AND data_year = :dataYear
  AND (:sheetType IS NULL OR :sheetType = '' OR sheet_type = :sheetType);
DELETE FROM jw_branch_indicator
WHERE branch_id = :branchId
  AND (:sheetType IS NULL OR :sheetType = '' OR sheet_type = :sheetType);
DELETE FROM jw_branch_indicator
WHERE branch_id IN (SELECT branch_id FROM jw_branch_info WHERE city = :city)
  AND data_year = :dataYear
  AND sheet_type = :sheetType;
DELETE FROM jw_branch_indicator WHERE indicator_code = :indicatorCode;

-- 批量插入 (GaussDB兼容写法)
INSERT INTO jw_branch_indicator (branch_id, data_year, sheet_type, indicator_code, indicator_value, create_time)
SELECT * FROM (
    VALUES
    (:branchId1, :dataYear1, :sheetType1, :indicatorCode1, :indicatorValue1, CURRENT_TIMESTAMP),
    (:branchId2, :dataYear2, :sheetType2, :indicatorCode2, :indicatorValue2, CURRENT_TIMESTAMP)
) AS t(branch_id, data_year, sheet_type, indicator_code, indicator_value, create_time);

-- 更新指标编码
UPDATE jw_branch_indicator SET indicator_code = :newCode WHERE indicator_code = :oldCode;


-- ============================================================================
-- 3. JwBranchInfoMapper — 网点基础信息
-- 表: jw_branch_info
-- 文件: JwBranchInfoMapper.xml
-- 差异: postMapper版删除了 `AND gs.score_category = 'overall'` 条件
-- ============================================================================

-- 通用查询
SELECT branch_id, primary_branch, secondary_branch, branch_code, city, grid_code,
       district_name, street, address, longitude, latitude,
       total_staff, personal_manager, corporate_manager, counter_staff, lobby_staff,
       branch_manager, manager_tenure, manager_resume, manager_history,
       total_area, other_floor_area, cash_counter, non_cash_counter, manager_seat,
       property_right, lease_expire, last_renovation, branch_type, relocation, data_source,
       del_flag, create_time, update_time
FROM jw_branch_info
WHERE (:branchCode IS NULL OR branch_code LIKE '%' || :branchCode || '%')
  AND (:primaryBranch IS NULL OR primary_branch = :primaryBranch)
  AND (:secondaryBranch IS NULL OR secondary_branch = :secondaryBranch)
  AND (:gridCode IS NULL OR grid_code = :gridCode)
  AND (:city IS NULL OR city = :city)
  AND (:branchType IS NULL OR branch_type = :branchType)
  AND (:dataSource IS NULL OR data_source = :dataSource)
  AND (:delFlag IS NULL OR del_flag = :delFlag)
ORDER BY branch_id;

-- selectJwBranchInfoById
SELECT branch_id, primary_branch, secondary_branch, branch_code, city, grid_code,
       district_name, street, address, longitude, latitude,
       total_staff, personal_manager, corporate_manager, counter_staff, lobby_staff,
       branch_manager, manager_tenure, manager_resume, manager_history,
       total_area, other_floor_area, cash_counter, non_cash_counter, manager_seat,
       property_right, lease_expire, last_renovation, branch_type, relocation, data_source,
       del_flag, create_time, update_time
FROM jw_branch_info WHERE branch_id = :branchId;

-- selectByBranchCode
SELECT branch_id, primary_branch, secondary_branch, branch_code, city, grid_code,
       district_name, street, address, longitude, latitude,
       total_staff, personal_manager, corporate_manager, counter_staff, lobby_staff,
       branch_manager, manager_tenure, manager_resume, manager_history,
       total_area, other_floor_area, cash_counter, non_cash_counter, manager_seat,
       property_right, lease_expire, last_renovation, branch_type, relocation, data_source,
       del_flag, create_time, update_time
FROM jw_branch_info WHERE branch_code = :branchCode;

-- 按城市查询
SELECT branch_id, primary_branch, secondary_branch, branch_code, city, grid_code,
       district_name, street, address, longitude, latitude,
       total_staff, personal_manager, corporate_manager, counter_staff, lobby_staff,
       branch_manager, manager_tenure, manager_resume, manager_history,
       total_area, other_floor_area, cash_counter, non_cash_counter, manager_seat,
       property_right, lease_expire, last_renovation, branch_type, relocation, data_source,
       del_flag, create_time, update_time
FROM jw_branch_info
WHERE city = :city AND (del_flag IS NULL OR del_flag = '0')
ORDER BY branch_id;

-- selectByGridCode
SELECT branch_id, primary_branch, secondary_branch, branch_code, city, grid_code,
       district_name, street, address, longitude, latitude,
       total_staff, personal_manager, corporate_manager, counter_staff, lobby_staff,
       branch_manager, manager_tenure, manager_resume, manager_history,
       total_area, other_floor_area, cash_counter, non_cash_counter, manager_seat,
       property_right, lease_expire, last_renovation, branch_type, relocation, data_source,
       del_flag, create_time, update_time
FROM jw_branch_info WHERE grid_code = :gridCode AND (del_flag IS NULL OR del_flag = '0')
ORDER BY branch_id;

-- selectByPrimaryBranch
SELECT branch_id, primary_branch, secondary_branch, branch_code, city, grid_code,
       district_name, street, address, longitude, latitude,
       total_staff, personal_manager, corporate_manager, counter_staff, lobby_staff,
       branch_manager, manager_tenure, manager_resume, manager_history,
       total_area, other_floor_area, cash_counter, non_cash_counter, manager_seat,
       property_right, lease_expire, last_renovation, branch_type, relocation, data_source,
       del_flag, create_time, update_time
FROM jw_branch_info WHERE primary_branch = :primaryBranch AND (del_flag IS NULL OR del_flag = '0')
ORDER BY branch_id;

-- selectByDeptName
SELECT branch_id, primary_branch, secondary_branch, branch_code, city, grid_code,
       district_name, street, address, longitude, latitude,
       total_staff, personal_manager, corporate_manager, counter_staff, lobby_staff,
       branch_manager, manager_tenure, manager_resume, manager_history,
       total_area, other_floor_area, cash_counter, non_cash_counter, manager_seat,
       property_right, lease_expire, last_renovation, branch_type, relocation, data_source,
       del_flag, create_time, update_time
FROM jw_branch_info WHERE (primary_branch = :deptName OR secondary_branch = :deptName) AND (del_flag IS NULL OR del_flag = '0')
ORDER BY branch_id;

SELECT DISTINCT city FROM jw_branch_info WHERE (del_flag IS NULL OR del_flag = '0') ORDER BY city;

-- 插入
INSERT INTO jw_branch_info (
    primary_branch, secondary_branch, branch_code, city, grid_code,
    district_name, street, address, longitude, latitude,
    total_staff, personal_manager, corporate_manager, counter_staff, lobby_staff,
    branch_manager, manager_tenure, manager_resume, manager_history,
    total_area, other_floor_area, cash_counter, non_cash_counter, manager_seat,
    property_right, lease_expire, last_renovation, branch_type, relocation, data_source,
    del_flag, create_time, update_time
) VALUES (
    :primaryBranch, :secondaryBranch, :branchCode, :city, :gridCode,
    :districtName, :street, :address, :longitude, :latitude,
    :totalStaff, :personalManager, :corporateManager, :counterStaff, :lobbyStaff,
    :branchManager, :managerTenure, :managerResume, :managerHistory,
    :totalArea, :otherFloorArea, :cashCounter, :nonCashCounter, :managerSeat,
    :propertyRight, :leaseExpire, :lastRenovation, :branchType, :relocation, :dataSource,
    :delFlag, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) RETURNING branch_id;

-- 更新
UPDATE jw_branch_info
SET primary_branch = COALESCE(:primaryBranch, primary_branch),
    secondary_branch = COALESCE(:secondaryBranch, secondary_branch),
    branch_code = COALESCE(:branchCode, branch_code),
    city = COALESCE(:city, city),
    grid_code = COALESCE(:gridCode, grid_code),
    district_name = COALESCE(:districtName, district_name),
    street = COALESCE(:street, street),
    address = COALESCE(:address, address),
    longitude = COALESCE(:longitude, longitude),
    latitude = COALESCE(:latitude, latitude),
    total_staff = COALESCE(:totalStaff, total_staff),
    personal_manager = COALESCE(:personalManager, personal_manager),
    corporate_manager = COALESCE(:corporateManager, corporate_manager),
    counter_staff = COALESCE(:counterStaff, counter_staff),
    lobby_staff = COALESCE(:lobbyStaff, lobby_staff),
    branch_manager = COALESCE(:branchManager, branch_manager),
    manager_tenure = COALESCE(:managerTenure, manager_tenure),
    manager_resume = COALESCE(:managerResume, manager_resume),
    manager_history = COALESCE(:managerHistory, manager_history),
    total_area = COALESCE(:totalArea, total_area),
    other_floor_area = COALESCE(:otherFloorArea, other_floor_area),
    cash_counter = COALESCE(:cashCounter, cash_counter),
    non_cash_counter = COALESCE(:nonCashCounter, non_cash_counter),
    manager_seat = COALESCE(:managerSeat, manager_seat),
    property_right = COALESCE(:propertyRight, property_right),
    lease_expire = COALESCE(:leaseExpire, lease_expire),
    last_renovation = COALESCE(:lastRenovation, last_renovation),
    branch_type = COALESCE(:branchType, branch_type),
    relocation = COALESCE(:relocation, relocation),
    data_source = COALESCE(:dataSource, data_source),
    del_flag = COALESCE(:delFlag, del_flag),
    update_time = CURRENT_TIMESTAMP
WHERE branch_id = :branchId;

-- 更新网格编码
UPDATE jw_branch_info SET grid_code = :gridCode, update_time = CURRENT_TIMESTAMP WHERE branch_id = :branchId;

-- 批量更新网格编码 (CASE → GaussDB兼容的UPDATE FROM)
UPDATE jw_branch_info t SET
    grid_code = s.grid_code,
    update_time = CURRENT_TIMESTAMP
FROM (
    VALUES
    (:branchId1::BIGINT, :gridCode1::VARCHAR(50)),
    (:branchId2::BIGINT, :gridCode2::VARCHAR(50))
) AS s(branch_id, grid_code)
WHERE t.branch_id = s.branch_id;

-- 删除
DELETE FROM jw_branch_info WHERE branch_id = :branchId;
DELETE FROM jw_branch_info WHERE branch_id = ANY(:ids);

-- 批量插入 (完整示例)
INSERT INTO jw_branch_info (
    primary_branch, secondary_branch, branch_code, city, grid_code,
    district_name, street, address, longitude, latitude,
    total_staff, personal_manager, corporate_manager, counter_staff, lobby_staff,
    branch_manager, manager_tenure, manager_resume, manager_history,
    total_area, other_floor_area, cash_counter, non_cash_counter, manager_seat,
    property_right, lease_expire, last_renovation, branch_type, relocation, data_source,
    del_flag, create_time, update_time
)
SELECT * FROM (
    VALUES
    (:primaryBranch1, :secondaryBranch1, :branchCode1, :city1, :gridCode1,
     :districtName1, :street1, :address1, :longitude1, :latitude1,
     :totalStaff1, :personalManager1, :corporateManager1, :counterStaff1, :lobbyStaff1,
     :branchManager1, :managerTenure1, :managerResume1, :managerHistory1,
     :totalArea1, :otherFloorArea1, :cashCounter1, :nonCashCounter1, :managerSeat1,
     :propertyRight1, :leaseExpire1, :lastRenovation1, :branchType1, :relocation1, :dataSource1,
     :delFlag1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (:primaryBranch2, :secondaryBranch2, :branchCode2, :city2, :gridCode2,
     :districtName2, :street2, :address2, :longitude2, :latitude2,
     :totalStaff2, :personalManager2, :corporateManager2, :counterStaff2, :lobbyStaff2,
     :branchManager2, :managerTenure2, :managerResume2, :managerHistory2,
     :totalArea2, :otherFloorArea2, :cashCounter2, :nonCashCounter2, :managerSeat2,
     :propertyRight2, :leaseExpire2, :lastRenovation2, :branchType2, :relocation2, :dataSource2,
     :delFlag2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS t(primary_branch, secondary_branch, branch_code, city, grid_code,
       district_name, street, address, longitude, latitude,
       total_staff, personal_manager, corporate_manager, counter_staff, lobby_staff,
       branch_manager, manager_tenure, manager_resume, manager_history,
       total_area, other_floor_area, cash_counter, non_cash_counter, manager_seat,
       property_right, lease_expire, last_renovation, branch_type, relocation, data_source,
       del_flag, create_time, update_time);

-- 四象限分析
SELECT
    bi.branch_id AS branchId,
    bi.secondary_branch AS branchName,
    bi.primary_branch AS primaryBranch,
    bi.grid_code AS gridCode,
    bi.longitude, bi.latitude,
    bi.total_staff AS totalStaff,
    bi.total_area AS totalArea,
    gs.site_score AS siteScore,
    bs.category_score AS branchScore
FROM jw_branch_info bi
LEFT JOIN jw_grid_score gs ON bi.grid_code = gs.grid_code
LEFT JOIN jw_branch_score bs ON bi.branch_id = bs.branch_id
    AND bs.score_category = 'overall'
    AND bs.data_year = :year
WHERE bi.city = :city
    AND (bi.del_flag IS NULL OR bi.del_flag = '0')
ORDER BY bi.secondary_branch;

-- ★ GaussDB 适配说明:
-- MySQL版: LEFT JOIN jw_grid_score gs ON bi.grid_code = gs.grid_code AND gs.score_category = 'overall'
-- postMapper版已删除 `AND gs.score_category = 'overall'` 条件


-- ============================================================================
-- 4. JwBranchScoreMapper — 网点评分
-- 表: jw_branch_score
-- 关键转换: CASE批量UPDATE → UPDATE ... FROM (VALUES ...) AS s WHERE
--           batchUpdateQuadrant 被移除 (postMapper版)
-- ============================================================================

-- 通用查询
SELECT score_id, branch_id, data_year, city, score_category,
       positive_distance, negative_distance, category_score, rank_num, create_time
FROM jw_branch_score
WHERE (:branchId IS NULL OR branch_id = :branchId)
  AND (:dataYear IS NULL OR data_year = :dataYear)
  AND (:city IS NULL OR city = :city)
  AND (:scoreCategory IS NULL OR score_category = :scoreCategory)
ORDER BY rank_num;

SELECT score_id, branch_id, data_year, city, score_category,
       positive_distance, negative_distance, category_score, rank_num, create_time
FROM jw_branch_score
WHERE score_id = :scoreId;

SELECT score_id, branch_id, data_year, city, score_category,
       positive_distance, negative_distance, category_score, rank_num, create_time
FROM jw_branch_score
WHERE branch_id = :branchId AND data_year = :dataYear
ORDER BY score_category;

SELECT score_id, branch_id, data_year, city, score_category,
       positive_distance, negative_distance, category_score, rank_num, create_time
FROM jw_branch_score
WHERE branch_id = ANY(:branchIds) AND data_year = :dataYear
ORDER BY score_category;

SELECT score_id, branch_id, data_year, city, score_category,
       positive_distance, negative_distance, category_score, rank_num, create_time
FROM jw_branch_score
WHERE city = :city AND data_year = :dataYear
ORDER BY score_category, rank_num;

SELECT score_id, branch_id, data_year, city, score_category,
       positive_distance, negative_distance, category_score, rank_num, create_time
FROM jw_branch_score
WHERE city = :city AND data_year BETWEEN :startYear AND :endYear
ORDER BY score_category, rank_num;

-- 带网点名称查询
SELECT s.score_id, s.branch_id, s.data_year, s.city, s.score_category,
       s.positive_distance, s.negative_distance, s.category_score, s.rank_num, s.create_time,
       bi.secondary_branch, bi.primary_branch, bi.grid_code
FROM jw_branch_score s
LEFT JOIN jw_branch_info bi ON s.branch_id = bi.branch_id
WHERE s.city = :city AND s.data_year = :dataYear AND s.score_category = :category
ORDER BY s.rank_num;

-- 插入
INSERT INTO jw_branch_score (
    branch_id, data_year, city, score_category,
    positive_distance, negative_distance, category_score, rank_num, create_time
) VALUES (
    :branchId, :dataYear, :city, :scoreCategory,
    :positiveDistance, :negativeDistance, :categoryScore, :rankNum, CURRENT_TIMESTAMP
) RETURNING score_id;

-- 更新
UPDATE jw_branch_score
SET branch_id = COALESCE(:branchId, branch_id),
    data_year = COALESCE(:dataYear, data_year),
    city = COALESCE(:city, city),
    score_category = COALESCE(:scoreCategory, score_category),
    positive_distance = COALESCE(:positiveDistance, positive_distance),
    negative_distance = COALESCE(:negativeDistance, negative_distance),
    category_score = COALESCE(:categoryScore, category_score),
    rank_num = COALESCE(:rankNum, rank_num)
WHERE score_id = :scoreId;

-- 更新排名
UPDATE jw_branch_score SET rank_num = :rankNum WHERE score_id = :scoreId;

-- 删除
DELETE FROM jw_branch_score WHERE score_id = :scoreId;
DELETE FROM jw_branch_score WHERE score_id = ANY(:ids);
DELETE FROM jw_branch_score WHERE city = :city AND data_year = :dataYear;

-- 批量插入 (GaussDB兼容)
INSERT INTO jw_branch_score (
    branch_id, data_year, city, score_category,
    positive_distance, negative_distance, category_score, rank_num, create_time
)
SELECT * FROM (
    VALUES
    (:branchId1, :dataYear1, :city1, :scoreCategory1,
     :positiveDistance1, :negativeDistance1, :categoryScore1, :rankNum1, CURRENT_TIMESTAMP),
    (:branchId2, :dataYear2, :city2, :scoreCategory2,
     :positiveDistance2, :negativeDistance2, :categoryScore2, :rankNum2, CURRENT_TIMESTAMP)
) AS t(branch_id, data_year, city, score_category,
       positive_distance, negative_distance, category_score, rank_num, create_time);

-- 批量更新排名 (MySQL CASE → GaussDB UPDATE FROM)
UPDATE jw_branch_score t SET rank_num = s.rank_num
FROM (
    VALUES
    (:scoreId1::BIGINT, :rankNum1::INTEGER),
    (:scoreId2::BIGINT, :rankNum2::INTEGER)
) AS s(score_id, rank_num)
WHERE t.score_id = s.score_id;

-- ★ MySQL版有 batchUpdateQuadrant (CASE WHEN)，postMapper版已移除


-- ============================================================================
-- 5. JwBranchSummaryMapper — 网点汇总统计
-- 表: jw_branch_summary
-- 文件: JwBranchSummaryMapper.xml
-- ============================================================================

SELECT summary_id, city, data_year, indicator_code,
       actual_weight, max_value, min_value, max_norm, min_norm, create_time
FROM jw_branch_summary
WHERE (:city IS NULL OR city = :city)
  AND (:dataYear IS NULL OR data_year = :dataYear)
  AND (:indicatorCode IS NULL OR indicator_code = :indicatorCode)
ORDER BY indicator_code;

SELECT summary_id, city, data_year, indicator_code,
       actual_weight, max_value, min_value, max_norm, min_norm, create_time
FROM jw_branch_summary
WHERE summary_id = :summaryId;

SELECT summary_id, city, data_year, indicator_code,
       actual_weight, max_value, min_value, max_norm, min_norm, create_time
FROM jw_branch_summary
WHERE city = :city AND data_year = :dataYear
ORDER BY indicator_code;

SELECT summary_id, city, data_year, indicator_code,
       actual_weight, max_value, min_value, max_norm, min_norm, create_time
FROM jw_branch_summary
WHERE city = :city AND data_year BETWEEN :startYear AND :endYear
ORDER BY indicator_code;

-- 插入
INSERT INTO jw_branch_summary (
    city, data_year, indicator_code, actual_weight,
    max_value, min_value, max_norm, min_norm, create_time
) VALUES (
    :city, :dataYear, :indicatorCode, :actualWeight,
    :maxValue, :minValue, :maxNorm, :minNorm, CURRENT_TIMESTAMP
) RETURNING summary_id;

-- 更新
UPDATE jw_branch_summary
SET city = COALESCE(:city, city),
    data_year = COALESCE(:dataYear, data_year),
    indicator_code = COALESCE(:indicatorCode, indicator_code),
    actual_weight = COALESCE(:actualWeight, actual_weight),
    max_value = COALESCE(:maxValue, max_value),
    min_value = COALESCE(:minValue, min_value),
    max_norm = COALESCE(:maxNorm, max_norm),
    min_norm = COALESCE(:minNorm, min_norm)
WHERE summary_id = :summaryId;

-- 删除
DELETE FROM jw_branch_summary WHERE summary_id = :summaryId;
DELETE FROM jw_branch_summary WHERE summary_id = ANY(:ids);
DELETE FROM jw_branch_summary WHERE city = :city AND data_year = :dataYear;
DELETE FROM jw_branch_summary WHERE indicator_code = :indicatorCode;

-- 批量插入
INSERT INTO jw_branch_summary (
    city, data_year, indicator_code, actual_weight,
    max_value, min_value, max_norm, min_norm, create_time
)
SELECT * FROM (
    VALUES
    (:city1, :dataYear1, :indicatorCode1, :actualWeight1,
     :maxValue1, :minValue1, :maxNorm1, :minNorm1, CURRENT_TIMESTAMP),
    (:city2, :dataYear2, :indicatorCode2, :actualWeight2,
     :maxValue2, :minValue2, :maxNorm2, :minNorm2, CURRENT_TIMESTAMP)
) AS t(city, data_year, indicator_code, actual_weight,
       max_value, min_value, max_norm, min_norm, create_time);

-- 更新指标编码
UPDATE jw_branch_summary SET indicator_code = :newCode WHERE indicator_code = :oldCode;


-- ============================================================================
-- 6. JwGridDataRawMapper — 网格原始数据
-- 表: jw_grid_data_raw
-- 文件: JwGridDataRawMapper.xml
-- ============================================================================

SELECT data_id, grid_code, indicator_code, indicator_value, create_time
FROM jw_grid_data_raw
WHERE (:gridCode IS NULL OR grid_code = :gridCode)
  AND (:indicatorCode IS NULL OR indicator_code = :indicatorCode)
ORDER BY data_id;

SELECT data_id, grid_code, indicator_code, indicator_value, create_time
FROM jw_grid_data_raw WHERE data_id = :dataId;

SELECT data_id, grid_code, indicator_code, indicator_value, create_time
FROM jw_grid_data_raw WHERE grid_code = :gridCode ORDER BY indicator_code;

SELECT data_id, grid_code, indicator_code, indicator_value, create_time
FROM jw_grid_data_raw WHERE grid_code = :gridCode AND indicator_code = :indicatorCode;

SELECT d.data_id, d.grid_code, d.indicator_code, d.indicator_value, d.create_time
FROM jw_grid_data_raw d
JOIN jw_grid_meta m ON d.grid_code = m.grid_code
WHERE m.city = :city
ORDER BY d.grid_code, d.indicator_code;

-- 插入
INSERT INTO jw_grid_data_raw (grid_code, indicator_code, indicator_value, create_time)
VALUES (:gridCode, :indicatorCode, :indicatorValue, CURRENT_TIMESTAMP)
RETURNING data_id;

-- 更新
UPDATE jw_grid_data_raw
SET grid_code = COALESCE(:gridCode, grid_code),
    indicator_code = COALESCE(:indicatorCode, indicator_code),
    indicator_value = COALESCE(:indicatorValue, indicator_value)
WHERE data_id = :dataId;

-- 删除
DELETE FROM jw_grid_data_raw WHERE data_id = :dataId;
DELETE FROM jw_grid_data_raw WHERE data_id = ANY(:ids);
DELETE FROM jw_grid_data_raw
WHERE grid_code IN (SELECT grid_code FROM jw_grid_meta WHERE city = :city);
DELETE FROM jw_grid_data_raw WHERE indicator_code = :indicatorCode;

-- 批量插入
INSERT INTO jw_grid_data_raw (grid_code, indicator_code, indicator_value, create_time)
SELECT * FROM (
    VALUES
    (:gridCode1, :indicatorCode1, :indicatorValue1, CURRENT_TIMESTAMP),
    (:gridCode2, :indicatorCode2, :indicatorValue2, CURRENT_TIMESTAMP)
) AS t(grid_code, indicator_code, indicator_value, create_time);

-- 按网格编码集合查询
SELECT * FROM jw_grid_data_raw WHERE grid_code = ANY(:gridCodes);

-- 更新指标编码
UPDATE jw_grid_data_raw SET indicator_code = :newCode WHERE indicator_code = :oldCode;


-- ============================================================================
-- 7. JwGridDataNormalizedMapper — 网格归一化数据
-- 表: jw_grid_data_normalized
-- 文件: JwGridDataNormalizedMapper.xml
-- ============================================================================

SELECT data_id, grid_code, indicator_code, normalized_value, create_time
FROM jw_grid_data_normalized
WHERE (:gridCode IS NULL OR grid_code = :gridCode)
  AND (:indicatorCode IS NULL OR indicator_code = :indicatorCode)
ORDER BY data_id;

SELECT data_id, grid_code, indicator_code, normalized_value, create_time
FROM jw_grid_data_normalized WHERE data_id = :dataId;

SELECT data_id, grid_code, indicator_code, normalized_value, create_time
FROM jw_grid_data_normalized WHERE grid_code = :gridCode ORDER BY indicator_code;

SELECT data_id, grid_code, indicator_code, normalized_value, create_time
FROM jw_grid_data_normalized WHERE grid_code = :gridCode AND indicator_code = :indicatorCode;

SELECT d.data_id, d.grid_code, d.indicator_code, d.normalized_value, d.create_time
FROM jw_grid_data_normalized d
JOIN jw_grid_meta m ON d.grid_code = m.grid_code
WHERE m.city = :city
ORDER BY d.grid_code, d.indicator_code;

-- 插入
INSERT INTO jw_grid_data_normalized (grid_code, indicator_code, normalized_value, create_time)
VALUES (:gridCode, :indicatorCode, :normalizedValue, CURRENT_TIMESTAMP)
RETURNING data_id;

-- 更新
UPDATE jw_grid_data_normalized
SET grid_code = COALESCE(:gridCode, grid_code),
    indicator_code = COALESCE(:indicatorCode, indicator_code),
    normalized_value = COALESCE(:normalizedValue, normalized_value)
WHERE data_id = :dataId;

-- 删除
DELETE FROM jw_grid_data_normalized WHERE data_id = :dataId;
DELETE FROM jw_grid_data_normalized WHERE data_id = ANY(:ids);
DELETE FROM jw_grid_data_normalized
WHERE grid_code IN (SELECT grid_code FROM jw_grid_meta WHERE city = :city);
DELETE FROM jw_grid_data_normalized WHERE indicator_code = :indicatorCode;

-- 批量插入
INSERT INTO jw_grid_data_normalized (grid_code, indicator_code, normalized_value, create_time)
SELECT * FROM (
    VALUES
    (:gridCode1, :indicatorCode1, :normalizedValue1, CURRENT_TIMESTAMP),
    (:gridCode2, :indicatorCode2, :normalizedValue2, CURRENT_TIMESTAMP)
) AS t(grid_code, indicator_code, normalized_value, create_time);

-- 更新指标编码
UPDATE jw_grid_data_normalized SET indicator_code = :newCode WHERE indicator_code = :oldCode;


-- ============================================================================
-- 8. JwGridMetaMapper — 网格元数据
-- 表: jw_grid_meta
-- 关键转换: batchUpdateGridMeta (CASE → UPDATE FROM)
-- ============================================================================

SELECT grid_code, longitude, latitude,
       west_longitude, east_longitude, north_latitude, south_latitude,
       province, city, district, create_time, update_time
FROM jw_grid_meta
WHERE (:gridCode IS NULL OR grid_code LIKE '%' || :gridCode || '%')
  AND (:city IS NULL OR city = :city)
  AND (:province IS NULL OR province = :province)
  AND (:district IS NULL OR district = :district)
ORDER BY grid_code;

-- selectJwGridMetaById / selectByGridCode (同SQL)
SELECT grid_code, longitude, latitude,
       west_longitude, east_longitude, north_latitude, south_latitude,
       province, city, district, create_time, update_time
FROM jw_grid_meta WHERE grid_code = :gridCode;

SELECT grid_code, longitude, latitude,
       west_longitude, east_longitude, north_latitude, south_latitude,
       province, city, district, create_time, update_time
FROM jw_grid_meta WHERE city = :city ORDER BY grid_code;

SELECT DISTINCT city FROM jw_grid_meta ORDER BY city;

-- 插入
INSERT INTO jw_grid_meta (
    grid_code, longitude, latitude,
    west_longitude, east_longitude, north_latitude, south_latitude,
    province, city, district, create_time, update_time
) VALUES (
    :gridCode, :longitude, :latitude,
    :westLongitude, :eastLongitude, :northLatitude, :southLatitude,
    :province, :city, :district, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 更新
UPDATE jw_grid_meta
SET longitude = COALESCE(:longitude, longitude),
    latitude = COALESCE(:latitude, latitude),
    west_longitude = COALESCE(:westLongitude, west_longitude),
    east_longitude = COALESCE(:eastLongitude, east_longitude),
    north_latitude = COALESCE(:northLatitude, north_latitude),
    south_latitude = COALESCE(:southLatitude, south_latitude),
    province = COALESCE(:province, province),
    city = COALESCE(:city, city),
    district = COALESCE(:district, district),
    update_time = CURRENT_TIMESTAMP
WHERE grid_code = :gridCode;

-- 删除
DELETE FROM jw_grid_meta WHERE grid_code = :gridCode;
DELETE FROM jw_grid_meta WHERE grid_code = ANY(:codes);
DELETE FROM jw_grid_meta WHERE city = :city;

-- 批量插入
INSERT INTO jw_grid_meta (
    grid_code, longitude, latitude,
    west_longitude, east_longitude, north_latitude, south_latitude,
    province, city, district, create_time, update_time
)
SELECT * FROM (
    VALUES
    (:gridCode1, :longitude1, :latitude1,
     :westLongitude1, :eastLongitude1, :northLatitude1, :southLatitude1,
     :province1, :city1, :district1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (:gridCode2, :longitude2, :latitude2,
     :westLongitude2, :eastLongitude2, :northLatitude2, :southLatitude2,
     :province2, :city2, :district2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS t(grid_code, longitude, latitude,
       west_longitude, east_longitude, north_latitude, south_latitude,
       province, city, district, create_time, update_time);

-- 按坐标查询网格 (GaussDB兼容)
SELECT grid_code, longitude, latitude,
       west_longitude, east_longitude, north_latitude, south_latitude,
       province, city, district, create_time, update_time
FROM jw_grid_meta
WHERE west_longitude <= :longitude
  AND east_longitude >= :longitude
  AND south_latitude <= :latitude
  AND north_latitude >= :latitude
LIMIT 1;

-- 批量更新网格坐标 (MySQL CASE → GaussDB UPDATE FROM)
UPDATE jw_grid_meta t SET
    longitude = s.longitude,
    latitude = s.latitude,
    west_longitude = s.west_longitude,
    east_longitude = s.east_longitude,
    north_latitude = s.north_latitude,
    south_latitude = s.south_latitude,
    update_time = CURRENT_TIMESTAMP
FROM (
    VALUES
    (:gridCode1::VARCHAR(50), :longitude1::DOUBLE PRECISION, :latitude1::DOUBLE PRECISION,
     :westLongitude1::DOUBLE PRECISION, :eastLongitude1::DOUBLE PRECISION,
     :northLatitude1::DOUBLE PRECISION, :southLatitude1::DOUBLE PRECISION),
    (:gridCode2::VARCHAR(50), :longitude2::DOUBLE PRECISION, :latitude2::DOUBLE PRECISION,
     :westLongitude2::DOUBLE PRECISION, :eastLongitude2::DOUBLE PRECISION,
     :northLatitude2::DOUBLE PRECISION, :southLatitude2::DOUBLE PRECISION)
) AS s(grid_code, longitude, latitude,
       west_longitude, east_longitude, north_latitude, south_latitude)
WHERE t.grid_code = s.grid_code;


-- ============================================================================
-- 9. JwGridScoreMapper — 网格评分
-- 表: jw_grid_score
-- 差异: postMapper版删除了 selectScoresByGridCodesAndCategory, selectByCityAndDistrict, selectBetterBlankCodes
-- ============================================================================

SELECT grid_code, city, score_category, positive_distance, negative_distance, site_score, create_time
FROM jw_grid_score
WHERE (:gridCode IS NULL OR grid_code LIKE '%' || :gridCode || '%')
  AND (:city IS NULL OR city = :city)
  AND (:scoreCategory IS NULL OR score_category = :scoreCategory)
ORDER BY site_score DESC;

SELECT grid_code, city, score_category, positive_distance, negative_distance, site_score, create_time
FROM jw_grid_score WHERE grid_code = :gridCode;

SELECT grid_code, city, score_category, positive_distance, negative_distance, site_score, create_time
FROM jw_grid_score WHERE grid_code = :gridCode AND score_category = 'overall';

SELECT grid_code, city, score_category, positive_distance, negative_distance, site_score, create_time
FROM jw_grid_score WHERE grid_code = :gridCode;

SELECT grid_code, city, score_category, positive_distance, negative_distance, site_score, create_time
FROM jw_grid_score WHERE grid_code = ANY(:gridCodes);

-- ★ postMapper版已移除以下查询:
-- SELECT grid_code, city, score_category, positive_distance, negative_distance, site_score, create_time
-- FROM jw_grid_score WHERE grid_code = ANY(:gridCodes) AND score_category = :category;

SELECT grid_code, city, score_category, positive_distance, negative_distance, site_score, create_time
FROM jw_grid_score WHERE city = :city AND score_category = 'overall'
ORDER BY site_score DESC;

-- ★ postMapper版已移除:
-- SELECT ... FROM jw_grid_score gs JOIN jw_grid_meta gm ... WHERE gm.district = :district;

SELECT COUNT(*) FROM jw_grid_score WHERE city = :city AND score_category = 'overall';

-- 插入
INSERT INTO jw_grid_score (grid_code, city, score_category, positive_distance, negative_distance, site_score, create_time)
VALUES (:gridCode, :city, :scoreCategory, :positiveDistance, :negativeDistance, :siteScore, CURRENT_TIMESTAMP);

-- 更新
UPDATE jw_grid_score
SET positive_distance = COALESCE(:positiveDistance, positive_distance),
    negative_distance = COALESCE(:negativeDistance, negative_distance),
    site_score = COALESCE(:siteScore, site_score)
WHERE grid_code = :gridCode AND score_category = :scoreCategory;

-- 删除
DELETE FROM jw_grid_score WHERE grid_code = :gridCode;
DELETE FROM jw_grid_score WHERE grid_code = ANY(:codes);
DELETE FROM jw_grid_score WHERE city = :city;

-- 批量插入
INSERT INTO jw_grid_score (grid_code, city, score_category, positive_distance, negative_distance, site_score, create_time)
SELECT * FROM (
    VALUES
    (:gridCode1, :city1, :scoreCategory1, :positiveDistance1, :negativeDistance1, :siteScore1, CURRENT_TIMESTAMP),
    (:gridCode2, :city2, :scoreCategory2, :positiveDistance2, :negativeDistance2, :siteScore2, CURRENT_TIMESTAMP)
) AS t(grid_code, city, score_category, positive_distance, negative_distance, site_score, create_time);

-- 获取最优空白网点 (GaussDB兼容 LIMIT)
SELECT gs.grid_code
FROM jw_grid_score gs
JOIN jw_grid_meta gm ON gs.grid_code = gm.grid_code
WHERE gs.city = :city AND gs.score_category = 'overall'
  AND NOT EXISTS (
    SELECT 1 FROM jw_branch_info bi
    WHERE bi.grid_code = gs.grid_code
      AND (bi.del_flag IS NULL OR bi.del_flag = '0')
  )
  AND (:district IS NULL OR :district = '' OR gm.district = :district)
ORDER BY gs.site_score DESC
LIMIT :limit;

-- ★ postMapper版已移除 selectBetterBlankCodes
-- SELECT ... WHERE gs.site_score > :minScore ... ORDER BY gs.site_score DESC;


-- ============================================================================
-- 10. JwGridSummaryMapper — 网格汇总统计
-- 表: jw_grid_summary
-- 文件: JwGridSummaryMapper.xml
-- ============================================================================

SELECT summary_id, city, indicator_code,
       actual_weight, max_raw, min_raw, max_norm, min_norm, create_time
FROM jw_grid_summary
WHERE (:city IS NULL OR city = :city)
  AND (:indicatorCode IS NULL OR indicator_code = :indicatorCode)
ORDER BY indicator_code;

SELECT summary_id, city, indicator_code,
       actual_weight, max_raw, min_raw, max_norm, min_norm, create_time
FROM jw_grid_summary WHERE summary_id = :summaryId;

SELECT summary_id, city, indicator_code,
       actual_weight, max_raw, min_raw, max_norm, min_norm, create_time
FROM jw_grid_summary WHERE city = :city ORDER BY indicator_code;

-- 插入
INSERT INTO jw_grid_summary (
    city, indicator_code, actual_weight,
    max_raw, min_raw, max_norm, min_norm, create_time
) VALUES (
    :city, :indicatorCode, :actualWeight,
    :maxRaw, :minRaw, :maxNorm, :minNorm, CURRENT_TIMESTAMP
) RETURNING summary_id;

-- 更新
UPDATE jw_grid_summary
SET city = COALESCE(:city, city),
    indicator_code = COALESCE(:indicatorCode, indicator_code),
    actual_weight = COALESCE(:actualWeight, actual_weight),
    max_raw = COALESCE(:maxRaw, max_raw),
    min_raw = COALESCE(:minRaw, min_raw),
    max_norm = COALESCE(:maxNorm, max_norm),
    min_norm = COALESCE(:minNorm, min_norm)
WHERE summary_id = :summaryId;

-- 删除
DELETE FROM jw_grid_summary WHERE summary_id = :summaryId;
DELETE FROM jw_grid_summary WHERE summary_id = ANY(:ids);
DELETE FROM jw_grid_summary WHERE city = :city;
DELETE FROM jw_grid_summary WHERE indicator_code = :indicatorCode;

-- 批量插入
INSERT INTO jw_grid_summary (
    city, indicator_code, actual_weight,
    max_raw, min_raw, max_norm, min_norm, create_time
)
SELECT * FROM (
    VALUES
    (:city1, :indicatorCode1, :actualWeight1,
     :maxRaw1, :minRaw1, :maxNorm1, :minNorm1, CURRENT_TIMESTAMP),
    (:city2, :indicatorCode2, :actualWeight2,
     :maxRaw2, :minRaw2, :maxNorm2, :minNorm2, CURRENT_TIMESTAMP)
) AS t(city, indicator_code, actual_weight,
       max_raw, min_raw, max_norm, min_norm, create_time);

-- 更新指标编码
UPDATE jw_grid_summary SET indicator_code = :newCode WHERE indicator_code = :oldCode;


-- ============================================================================
-- 11. JwIndicatorConfigMapper — 指标配置树
-- 表: jw_indicator_config
-- 文件: JwIndicatorConfigMapper.xml
-- ============================================================================

SELECT indicator_id, indicator_code, indicator_name, indicator_type,
       parent_code, is_derived, computation_pattern, input_codes,
       calculation_weight, sort_order, create_time, update_time
FROM jw_indicator_config
WHERE (:indicatorCode IS NULL OR indicator_code LIKE '%' || :indicatorCode || '%')
  AND (:indicatorName IS NULL OR indicator_name LIKE '%' || :indicatorName || '%')
  AND (:indicatorType IS NULL OR indicator_type = :indicatorType)
  AND (:parentCode IS NULL OR parent_code = :parentCode)
ORDER BY indicator_type, sort_order, indicator_id;

SELECT indicator_id, indicator_code, indicator_name, indicator_type,
       parent_code, is_derived, computation_pattern, input_codes,
       calculation_weight, sort_order, create_time, update_time
FROM jw_indicator_config WHERE indicator_id = :indicatorId;

SELECT indicator_id, indicator_code, indicator_name, indicator_type,
       parent_code, is_derived, computation_pattern, input_codes,
       calculation_weight, sort_order, create_time, update_time
FROM jw_indicator_config WHERE indicator_type = :indicatorType
ORDER BY sort_order, indicator_id;

SELECT indicator_id, indicator_code, indicator_name, indicator_type,
       parent_code, is_derived, computation_pattern, input_codes,
       calculation_weight, sort_order, create_time, update_time
FROM jw_indicator_config WHERE indicator_type = ANY(:types)
ORDER BY sort_order, indicator_id;

-- 叶子节点查询 (NOT EXISTS子查询)
SELECT t.* FROM jw_indicator_config t
WHERE t.indicator_type = :indicatorType
  AND NOT EXISTS (
      SELECT 1 FROM jw_indicator_config c
      WHERE c.parent_code = t.indicator_code
  )
ORDER BY t.sort_order, t.indicator_id;

SELECT indicator_id, indicator_code, indicator_name, indicator_type,
       parent_code, is_derived, computation_pattern, input_codes,
       calculation_weight, sort_order, create_time, update_time
FROM jw_indicator_config WHERE parent_code = :parentCode
ORDER BY sort_order, indicator_id;

SELECT indicator_id, indicator_code, indicator_name, indicator_type,
       parent_code, is_derived, computation_pattern, input_codes,
       calculation_weight, sort_order, create_time, update_time
FROM jw_indicator_config WHERE parent_code IS NULL
  AND (:indicatorType IS NULL OR indicator_type = :indicatorType)
ORDER BY sort_order, indicator_id;

SELECT indicator_id, indicator_code, indicator_name, indicator_type,
       parent_code, is_derived, computation_pattern, input_codes,
       calculation_weight, sort_order, create_time, update_time
FROM jw_indicator_config WHERE indicator_code = :indicatorCode;

SELECT indicator_id, indicator_code, indicator_name, indicator_type,
       parent_code, is_derived, computation_pattern, input_codes,
       calculation_weight, sort_order, create_time, update_time
FROM jw_indicator_config WHERE indicator_name = :indicatorName;

SELECT indicator_id, indicator_code, indicator_name, indicator_type,
       parent_code, is_derived, computation_pattern, input_codes,
       calculation_weight, sort_order, create_time, update_time
FROM jw_indicator_config WHERE indicator_code = ANY(:codes)
ORDER BY sort_order;

-- 插入
INSERT INTO jw_indicator_config (
    indicator_code, indicator_name, indicator_type,
    parent_code, is_derived, computation_pattern, input_codes,
    calculation_weight, sort_order, create_time, update_time
) VALUES (
    :indicatorCode, :indicatorName, :indicatorType,
    :parentCode, :isDerived, :computationPattern, :inputCodes,
    :calculationWeight, :sortOrder, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) RETURNING indicator_id;

-- 更新
UPDATE jw_indicator_config
SET indicator_code = COALESCE(:indicatorCode, indicator_code),
    indicator_name = COALESCE(:indicatorName, indicator_name),
    indicator_type = COALESCE(:indicatorType, indicator_type),
    parent_code = COALESCE(:parentCode, parent_code),
    is_derived = COALESCE(:isDerived, is_derived),
    computation_pattern = COALESCE(:computationPattern, computation_pattern),
    input_codes = COALESCE(:inputCodes, input_codes),
    calculation_weight = COALESCE(:calculationWeight, calculation_weight),
    sort_order = COALESCE(:sortOrder, sort_order),
    update_time = CURRENT_TIMESTAMP
WHERE indicator_id = :indicatorId;

-- 删除
DELETE FROM jw_indicator_config WHERE indicator_id = :indicatorId;
DELETE FROM jw_indicator_config WHERE indicator_id = ANY(:ids);
DELETE FROM jw_indicator_config WHERE parent_code = :parentCode;
DELETE FROM jw_indicator_config WHERE indicator_code = :indicatorCode;

-- 更新父编码
UPDATE jw_indicator_config SET parent_code = :newCode WHERE parent_code = :oldCode;

-- 批量插入
INSERT INTO jw_indicator_config (
    indicator_code, indicator_name, indicator_type,
    parent_code, is_derived, computation_pattern, input_codes,
    calculation_weight, sort_order, create_time, update_time
)
SELECT * FROM (
    VALUES
    (:indicatorCode1, :indicatorName1, :indicatorType1,
     :parentCode1, :isDerived1, :computationPattern1, :inputCodes1,
     :calculationWeight1, :sortOrder1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (:indicatorCode2, :indicatorName2, :indicatorType2,
     :parentCode2, :isDerived2, :computationPattern2, :inputCodes2,
     :calculationWeight2, :sortOrder2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS t(indicator_code, indicator_name, indicator_type,
       parent_code, is_derived, computation_pattern, input_codes,
       calculation_weight, sort_order, create_time, update_time);


-- ============================================================================
-- 12. JwPeerBankInfoMapper — 同业银行信息
-- 表: jw_peer_bank_info
-- 文件: JwPeerBankInfoMapper.xml
-- ============================================================================

SELECT peer_bank_id, org_code, org_name, org_address,
       longitude, latitude, bank_name,
       province, city, district, town, grid_code,
       del_flag, create_time, update_time
FROM jw_peer_bank_info
WHERE (:orgCode IS NULL OR org_code LIKE '%' || :orgCode || '%')
  AND (:bankName IS NULL OR bank_name LIKE '%' || :bankName || '%')
  AND (:city IS NULL OR city = :city)
  AND (:district IS NULL OR district = :district)
  AND (:gridCode IS NULL OR grid_code = :gridCode)
  AND (:delFlag IS NULL OR del_flag = :delFlag)
ORDER BY peer_bank_id;

SELECT peer_bank_id, org_code, org_name, org_address,
       longitude, latitude, bank_name,
       province, city, district, town, grid_code,
       del_flag, create_time, update_time
FROM jw_peer_bank_info WHERE peer_bank_id = :peerBankId;

SELECT peer_bank_id, org_code, org_name, org_address,
       longitude, latitude, bank_name,
       province, city, district, town, grid_code,
       del_flag, create_time, update_time
FROM jw_peer_bank_info
WHERE city = :city AND (del_flag IS NULL OR del_flag = '0')
  AND bank_name NOT IN ('其他银行', '工商银行')
ORDER BY peer_bank_id;

-- 插入
INSERT INTO jw_peer_bank_info (
    org_code, org_name, org_address, longitude, latitude,
    bank_name, province, city, district, town, grid_code,
    del_flag, create_time, update_time
) VALUES (
    :orgCode, :orgName, :orgAddress, :longitude, :latitude,
    :bankName, :province, :city, :district, :town, :gridCode,
    :delFlag, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) RETURNING peer_bank_id;

-- 删除
DELETE FROM jw_peer_bank_info WHERE peer_bank_id = :peerBankId;

-- 批量插入
INSERT INTO jw_peer_bank_info (
    org_code, org_name, org_address, longitude, latitude,
    bank_name, province, city, district, town, grid_code,
    del_flag, create_time, update_time
)
SELECT * FROM (
    VALUES
    (:orgCode1, :orgName1, :orgAddress1, :longitude1, :latitude1,
     :bankName1, :province1, :city1, :district1, :town1, :gridCode1,
     :delFlag1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (:orgCode2, :orgName2, :orgAddress2, :longitude2, :latitude2,
     :bankName2, :province2, :city2, :district2, :town2, :gridCode2,
     :delFlag2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS t(org_code, org_name, org_address, longitude, latitude,
       bank_name, province, city, district, town, grid_code,
       del_flag, create_time, update_time);


-- ============================================================================
-- 13. JwPoiInfoMapper — 兴趣点(POI)信息
-- 表: jw_poi_info
-- 文件: JwPoiInfoMapper.xml
-- ============================================================================

SELECT poi_id, org_code, poi_name, longitude, latitude, province, city, district,
       address, poi_type, del_flag, create_time, update_time
FROM jw_poi_info
WHERE (:poiName IS NULL OR poi_name LIKE '%' || :poiName || '%')
  AND (:city IS NULL OR city = :city)
  AND (:province IS NULL OR province = :province)
  AND (:poiType IS NULL OR poi_type = :poiType)
  AND (:delFlag IS NULL OR del_flag = :delFlag)
ORDER BY poi_id;

SELECT poi_id, org_code, poi_name, longitude, latitude, province, city, district,
       address, poi_type, del_flag, create_time, update_time
FROM jw_poi_info WHERE poi_id = :poiId;

SELECT poi_id, org_code, poi_name, longitude, latitude, province, city, district,
       address, poi_type, del_flag, create_time, update_time
FROM jw_poi_info
WHERE city = :city AND (del_flag IS NULL OR del_flag = '0');

SELECT COUNT(*) FROM jw_poi_info
WHERE city = :city AND (del_flag IS NULL OR del_flag = '0');

SELECT DISTINCT city FROM jw_poi_info
WHERE (del_flag IS NULL OR del_flag = '0') ORDER BY city;

-- 插入
INSERT INTO jw_poi_info (
    org_code, poi_name, longitude, latitude, province, city, district,
    address, poi_type, del_flag, create_time, update_time
) VALUES (
    :orgCode, :poiName, :longitude, :latitude, :province, :city, :district,
    :address, :poiType, :delFlag, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) RETURNING poi_id;

-- 更新
UPDATE jw_poi_info
SET org_code = COALESCE(:orgCode, org_code),
    poi_name = COALESCE(:poiName, poi_name),
    longitude = COALESCE(:longitude, longitude),
    latitude = COALESCE(:latitude, latitude),
    province = COALESCE(:province, province),
    city = COALESCE(:city, city),
    district = COALESCE(:district, district),
    address = COALESCE(:address, address),
    poi_type = COALESCE(:poiType, poi_type),
    del_flag = COALESCE(:delFlag, del_flag),
    update_time = CURRENT_TIMESTAMP
WHERE poi_id = :poiId;

-- 删除
DELETE FROM jw_poi_info WHERE poi_id = :poiId;
DELETE FROM jw_poi_info WHERE poi_id = ANY(:ids);
DELETE FROM jw_poi_info WHERE city = :city;

-- 批量插入
INSERT INTO jw_poi_info (
    org_code, poi_name, longitude, latitude, province, city, district,
    address, poi_type, del_flag, create_time, update_time
)
SELECT * FROM (
    VALUES
    (:orgCode1, :poiName1, :longitude1, :latitude1, :province1, :city1, :district1,
     :address1, :poiType1, :delFlag1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (:orgCode2, :poiName2, :longitude2, :latitude2, :province2, :city2, :district2,
     :address2, :poiType2, :delFlag2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS t(org_code, poi_name, longitude, latitude, province, city, district,
       address, poi_type, del_flag, create_time, update_time);

-- 边界查询
SELECT poi_id, org_code, poi_name, longitude, latitude, province, city, district,
       address, poi_type, del_flag, create_time, update_time
FROM jw_poi_info
WHERE city = :city
  AND (del_flag IS NULL OR del_flag = '0')
  AND longitude BETWEEN :westLng AND :eastLng
  AND latitude BETWEEN :southLat AND :northLat
ORDER BY poi_type, poi_name;

-- 去重POI类型
SELECT DISTINCT poi_type FROM jw_poi_info
WHERE city = :city
  AND (del_flag IS NULL OR del_flag = '0')
  AND poi_type IS NOT NULL AND poi_type != ''
ORDER BY poi_type;


-- ============================================================================
-- 14. JwPopulationHeatMapper — 人口热力数据
-- 表: jw_population_heat
-- 文件: JwPopulationHeatMapper.xml
-- ============================================================================

SELECT heat_id, grid_code, indicator_code, indicator_value, create_time
FROM jw_population_heat
WHERE (:gridCode IS NULL OR grid_code = :gridCode)
  AND (:indicatorCode IS NULL OR indicator_code = :indicatorCode)
ORDER BY heat_id;

SELECT heat_id, grid_code, indicator_code, indicator_value, create_time
FROM jw_population_heat WHERE heat_id = :heatId;

SELECT heat_id, grid_code, indicator_code, indicator_value, create_time
FROM jw_population_heat WHERE grid_code = :gridCode;

SELECT heat_id, grid_code, indicator_code, indicator_value, create_time
FROM jw_population_heat WHERE grid_code = ANY(:gridCodes);

SELECT heat_id, grid_code, indicator_code, indicator_value, create_time
FROM jw_population_heat WHERE grid_code = :gridCode AND indicator_code = :indicatorCode;

SELECT DISTINCT grid_code FROM jw_population_heat;

SELECT DISTINCT h.grid_code
FROM jw_population_heat h
JOIN jw_grid_meta m ON h.grid_code = m.grid_code
WHERE m.city = :city;

SELECT DISTINCT m.city
FROM jw_population_heat h
JOIN jw_grid_meta m ON h.grid_code = m.grid_code
ORDER BY m.city;

-- 插入
INSERT INTO jw_population_heat (grid_code, indicator_code, indicator_value, create_time)
VALUES (:gridCode, :indicatorCode, :indicatorValue, CURRENT_TIMESTAMP)
RETURNING heat_id;

-- 更新
UPDATE jw_population_heat
SET grid_code = COALESCE(:gridCode, grid_code),
    indicator_code = COALESCE(:indicatorCode, indicator_code),
    indicator_value = COALESCE(:indicatorValue, indicator_value)
WHERE heat_id = :heatId;

-- 删除
DELETE FROM jw_population_heat WHERE heat_id = :heatId;
DELETE FROM jw_population_heat WHERE heat_id = ANY(:ids);
DELETE FROM jw_population_heat WHERE grid_code = :gridCode;
DELETE FROM jw_population_heat
WHERE grid_code IN (SELECT grid_code FROM jw_grid_meta WHERE city = :city);
DELETE FROM jw_population_heat WHERE indicator_code = :indicatorCode;

-- 批量插入
INSERT INTO jw_population_heat (grid_code, indicator_code, indicator_value, create_time)
SELECT * FROM (
    VALUES
    (:gridCode1, :indicatorCode1, :indicatorValue1, CURRENT_TIMESTAMP),
    (:gridCode2, :indicatorCode2, :indicatorValue2, CURRENT_TIMESTAMP)
) AS t(grid_code, indicator_code, indicator_value, create_time);

-- 更新指标编码
UPDATE jw_population_heat SET indicator_code = :newCode WHERE indicator_code = :oldCode;


-- ============================================================================
-- 15. JwDataAccessRequestMapper — 数据访问申请审批
-- 表: jw_data_access_request
-- 文件: JwDataAccessRequestMapper.xml
-- 注意: 此段引用了 sys_user, sys_dept, sys_role 等 RuoYi 系统表
-- 迁移时需要确保这些系统表也存在 GaussDB 中
SELECT r.request_id, r.applicant_id, r.target_dept_id, r.reason,
       r.valid_days, r.status, r.reviewer_id, r.review_comment,
       r.review_time, r.valid_date_from, r.valid_date_to,
       r.del_flag, r.create_by, r.create_time, r.update_by, r.update_time,
       u1.user_name AS applicant_name,
       d.dept_name AS target_dept_name,
       u2.user_name AS reviewer_name
FROM jw_data_access_request r
LEFT JOIN sys_user u1 ON r.applicant_id = u1.user_id
LEFT JOIN sys_dept d ON r.target_dept_id = d.dept_id
LEFT JOIN sys_user u2 ON r.reviewer_id = u2.user_id
WHERE r.applicant_id = :applicantId
  AND (r.del_flag IS NULL OR r.del_flag = '0')
ORDER BY r.create_time DESC;

-- 待审批列表 (selectPendingListByReviewerId)
SELECT r.request_id, r.applicant_id, r.target_dept_id, r.reason,
       r.valid_days, r.status, r.reviewer_id, r.review_comment,
       r.review_time, r.valid_date_from, r.valid_date_to,
       r.del_flag, r.create_by, r.create_time, r.update_by, r.update_time,
       u1.user_name AS applicant_name,
       d.dept_name AS target_dept_name,
       u2.user_name AS reviewer_name
FROM jw_data_access_request r
LEFT JOIN sys_user u1 ON r.applicant_id = u1.user_id
LEFT JOIN sys_dept d ON r.target_dept_id = d.dept_id
LEFT JOIN sys_user u2 ON r.reviewer_id = u2.user_id
WHERE r.status = '0'
  AND (r.del_flag IS NULL OR r.del_flag = '0')
  AND r.reviewer_id = :reviewerId
ORDER BY r.create_time DESC;

-- 已审批列表 (selectReviewedList)
SELECT r.request_id, r.applicant_id, r.target_dept_id, r.reason,
       r.valid_days, r.status, r.reviewer_id, r.review_comment,
       r.review_time, r.valid_date_from, r.valid_date_to,
       r.del_flag, r.create_by, r.create_time, r.update_by, r.update_time,
       u1.user_name AS applicant_name,
       d.dept_name AS target_dept_name,
       u2.user_name AS reviewer_name
FROM jw_data_access_request r
LEFT JOIN sys_user u1 ON r.applicant_id = u1.user_id
LEFT JOIN sys_dept d ON r.target_dept_id = d.dept_id
LEFT JOIN sys_user u2 ON r.reviewer_id = u2.user_id
WHERE r.reviewer_id = :reviewerId
  AND (r.del_flag IS NULL OR r.del_flag = '0')
  AND r.status IN ('1', '2')
ORDER BY r.review_time DESC;

-- 按ID查询 (selectJwDataAccessRequestById)
SELECT r.request_id, r.applicant_id, r.target_dept_id, r.reason,
       r.valid_days, r.status, r.reviewer_id, r.review_comment,
       r.review_time, r.valid_date_from, r.valid_date_to,
       r.del_flag, r.create_by, r.create_time, r.update_by, r.update_time,
       u1.user_name AS applicant_name,
       d.dept_name AS target_dept_name,
       u2.user_name AS reviewer_name
FROM jw_data_access_request r
LEFT JOIN sys_user u1 ON r.applicant_id = u1.user_id
LEFT JOIN sys_dept d ON r.target_dept_id = d.dept_id
LEFT JOIN sys_user u2 ON r.reviewer_id = u2.user_id
WHERE r.request_id = :requestId;

-- 检查有效记录
SELECT COUNT(1) FROM jw_data_access_request
WHERE applicant_id = :applicantId
  AND target_dept_id = :targetDeptId
  AND status = '1'
  AND valid_date_from IS NOT NULL
  AND valid_date_to IS NOT NULL
  AND valid_date_from <= :now
  AND valid_date_to >= :now
  AND (del_flag IS NULL OR del_flag = '0');

-- 待审批计数
SELECT COUNT(1) FROM jw_data_access_request
WHERE status = '0'
  AND (del_flag IS NULL OR del_flag = '0')
  AND reviewer_id = :reviewerId;

-- 插入
INSERT INTO jw_data_access_request (
    applicant_id, target_dept_id, reason, valid_days, reviewer_id, status,
    del_flag, create_by, create_time
) VALUES (
    :applicantId, :targetDeptId, :reason, :validDays, :reviewerId, '0',
    '0', :createBy, CURRENT_TIMESTAMP
) RETURNING request_id;

-- 更新状态
UPDATE jw_data_access_request
SET status = :status,
    reviewer_id = COALESCE(:reviewerId, reviewer_id),
    review_comment = COALESCE(:reviewComment, review_comment),
    review_time = COALESCE(:reviewTime, review_time),
    valid_date_from = COALESCE(:validDateFrom, valid_date_from),
    valid_date_to = COALESCE(:validDateTo, valid_date_to),
    update_by = :updateBy,
    update_time = CURRENT_TIMESTAMP
WHERE request_id = :requestId;

-- 软删除
UPDATE jw_data_access_request
SET del_flag = '2', update_time = CURRENT_TIMESTAMP
WHERE request_id = :requestId;

-- 获取审核人列表
SELECT DISTINCT u.user_id AS userId, u.user_name AS userName,
       COALESCE(u.nick_name, u.user_name) AS nickName
FROM sys_user u
JOIN sys_user_role ur ON u.user_id = ur.user_id
JOIN sys_role r ON ur.role_id = r.role_id
WHERE r.role_key = :roleKey
  AND (:parentDeptId IS NULL OR u.dept_id = :parentDeptId)
ORDER BY u.user_name;

-- 批量过期定时任务
UPDATE jw_data_access_request
SET status = '4', update_time = CURRENT_TIMESTAMP
WHERE status = '1'
  AND valid_date_to IS NOT NULL
  AND valid_date_to < :today;


-- ============================================================================
-- 16. JwScoreCategoryConfigMapper — 评分类别配置
-- 表: jw_score_category_config
-- 文件: JwScoreCategoryConfigMapper.xml
-- ============================================================================

SELECT * FROM jw_score_category_config
WHERE is_active = '1'
ORDER BY category_code, sort_order;

SELECT * FROM jw_score_category_config
WHERE category_code = :categoryCode AND is_active = '1'
ORDER BY sort_order;

SELECT DISTINCT category_code FROM jw_score_category_config
WHERE is_active = '1'
ORDER BY category_code;

-- 插入
INSERT INTO jw_score_category_config (
    category_code, category_name, indicator_code, sort_order, is_active,
    create_time, update_time
) VALUES (
    :categoryCode, :categoryName, :indicatorCode, :sortOrder, :isActive,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) RETURNING config_id;

-- 更新
UPDATE jw_score_category_config
SET category_name = COALESCE(:categoryName, category_name),
    indicator_code = COALESCE(:indicatorCode, indicator_code),
    sort_order = COALESCE(:sortOrder, sort_order),
    is_active = COALESCE(:isActive, is_active),
    update_time = CURRENT_TIMESTAMP
WHERE config_id = :configId;

-- 删除
DELETE FROM jw_score_category_config WHERE category_code = :categoryCode;
DELETE FROM jw_score_category_config WHERE indicator_code = :indicatorCode;

-- 更新指标编码
UPDATE jw_score_category_config SET indicator_code = :newCode WHERE indicator_code = :oldCode;


-- ============================================================================
-- ★ GaussDB转换总结: 所有迁移要点
-- ============================================================================
--
-- 1. now() → CURRENT_TIMESTAMP
--    GaussDB兼容MySQL的now()，但建议统一使用标准SQL写法CURRENT_TIMESTAMP
--
-- 2. ON DUPLICATE KEY UPDATE → ON CONFLICT ... DO UPDATE SET
--    GaussDB使用PostgreSQL兼容的UPSERT语法
--    MySQL:  INSERT ... VALUES (...) ON DUPLICATE KEY UPDATE col=VALUES(col)
--    GaussDB: INSERT ... VALUES (...) ON CONFLICT (uk_col) DO UPDATE SET col=EXCLUDED.col
--
-- 3. CASE批量UPDATE → UPDATE ... FROM (VALUES ...) AS t
--    MySQL:  UPDATE t SET col=CASE id WHEN 1 THEN x WHEN 2 THEN y END WHERE id IN (1,2)
--    GaussDB: UPDATE t SET col=s.col FROM (VALUES (1::BIGINT, x), (2::BIGINT, y)) AS s(id, col) WHERE t.id=s.id
--    注意::类型转换语法(::BIGINT, ::INTEGER, ::DOUBLE PRECISION, ::VARCHAR(n))
--
-- 4. useGeneratedKeys+keyProperty → RETURNING
--    MySQL: INSERT ... VALUES (...) (自动生成key写入属性)
--    GaussDB: INSERT ... VALUES (...) RETURNING id
--
-- 5. LIMIT语法兼容
--    LIMIT #{limit} 和 LIMIT 1 在GaussDB中完全兼容
--
-- 6. 批量插入的切换
--    MySQL: INSERT INTO t (...) VALUES (...), (...), (...)
--    GaussDB: INSERT INTO t (...) SELECT * FROM (VALUES (...), (...)) AS t(...)
--    注意: GaussDB支持VALUES多条写法, 建议用SELECT * FROM VALUES写法保持兼容性
--
-- 7. COALESCE兼容
--    MySQL的IFNULL(a,b) → GaussDB标准COALESCE(a,b) (已广泛使用)
--
-- 8. 字符串连接: MySQL CONCAT(a,b) → a || b (已使用标准写法)
--
-- 9. IN (子查询): 完全兼容
--
-- 10. 表别名: MySQL可直接写UPDATE t SET... → GaussDB需UPDATE t SET...(兼容)
--
-- ============================================================================
-- 文件差异摘要 (MySQL版 vs postMapper版)
-- ============================================================================
--
-- 完全一致(8个): JwBranchIndicatorMapper, JwBranchSummaryMapper,
--   JwDataAccessRequestMapper, JwGridSummaryMapper, JwIndicatorConfigMapper,
--   JwPeerBankInfoMapper, JwPoiInfoMapper, JwScoreCategoryConfigMapper
--
-- 有差异(8个):
--   AiAnalysisMapper: ON DUPLICATE KEY UPDATE → ON CONFLICT; NOW()→now()
--   JwBranchInfoMapper: 删除了四象限查询中的 AND gs.score_category = 'overall'
--   JwBranchScoreMapper: batchUpdateQuadrant移除; batchUpdateRank用UPDATE FROM重写;
--     selectByCityAndYearAndCategoryAndBranch移除; resultMap精简
--   JwGridMetaMapper: batchUpdateGridMeta CASE→UPDATE FROM; 移除poiCount列插入
--   JwGridScoreMapper: 移除selectScoresByGridCodesAndCategory,
--     selectByCityAndDistrict, selectBetterBlankCodes
--   JwGridDataNormalizedMapper: 子查询格式化 (无语法变化)
--   JwGridDataRawMapper: 子查询格式化 (无语法变化)
--   JwPopulationHeatMapper: 子查询格式化 (无语法变化)
-- ============================================================================
