-- ============================================================
-- 审批全流程测试数据 - 补充（幂等版本）
-- 在 jw_test_data.sql 之后执行，可重复执行
-- ============================================================
-- 已有数据回顾（来自 jw_test_data.sql）:
--   部门: 省行(200), 市行(201-209), 支行(210-231)
--   用户: test_guiyang(10), test_qingzhen(11), test_zunyi(12),
--         test_renhuai(13), reviewer_guiyang(20), reviewer_zunyi(21)
--   角色: admin(1), common(2), data_reviewer(3)
--   角色-部门: data_reviewer → 贵阳市分行(201), 遵义市分行(202)
--   网点: 贵阳分行下 30+ 网点 (primary_branch='贵阳分行')
-- 本补充文件新增:
--   1. 省行级审核员
--   2. 遵义分行/六盘水分行 网点信息
--   3. 全状态审批记录（待审批/已通过/已拒绝/已撤销）
-- ============================================================

-- ============================================================
-- 1. 省行级数据审核员（幂等：已存在则忽略）
-- ============================================================
INSERT IGNORE INTO sys_user VALUES (30, 200, 'reviewer_province', '审核-贵州省分行',
    '00', '', '13800000103', '1', '',
    '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
    '0', '0', '127.0.0.1', sysdate(), sysdate(),
    'admin', sysdate(), '', null,
    '测试：贵州省分行数据审核员，可审批市行级申请');

INSERT IGNORE INTO sys_user_role VALUES (30, '3');

-- data_reviewer 角色关联省行部门，使省行审核员能审批 target parent=省行的申请
INSERT IGNORE INTO sys_role_dept VALUES (3, 200);


-- ============================================================
-- 2. 遵义分行 + 六盘水分行 网点信息（幂等：branch_code 唯一）
-- ============================================================
INSERT IGNORE INTO jw_branch_info (branch_code, primary_branch, secondary_branch, city, address,
    total_staff, personal_manager, corporate_manager, counter_staff, lobby_staff,
    branch_manager, total_area, cash_counter, non_cash_counter, manager_seat,
    property_right, branch_type, longitude, latitude)
VALUES
-- 遵义分行直辖网点
('2001', '遵义分行', '遵义分行营业部', '遵义市', '红花岗区中华南路1号',
    18, 4, 3, 6, 5, '张明', 580.00, 4, 4, 2, '自有', '分行营业部', 106.932100, 27.698200),
('2002', '遵义分行', '红花岗区支行',   '遵义市', '红花岗区解放路56号',
    12, 3, 2, 4, 3, '李莉', 320.00, 3, 2, 1, '自有', '城区支行', 106.871900, 27.694500),
('2003', '遵义分行', '汇川区支行',     '遵义市', '汇川区大连路189号',
    14, 3, 2, 5, 4, '王强', 380.00, 3, 3, 1, '自有', '城区支行', 106.915800, 27.723400),
('2004', '遵义分行', '播州区支行',     '遵义市', '播州区南白镇万寿街78号',
    10, 2, 2, 3, 3, '陈芳', 280.00, 2, 2, 1, '自有', '县域支行', 106.741900, 27.536200),
('2005', '遵义分行', '新蒲新区支行',   '遵义市', '新蒲新区播州大道23号',
    8, 2, 1, 3, 2, '刘伟', 220.00, 2, 2, 1, '租赁', '城区支行', 107.066500, 27.702400),

-- 仁怀市支行下网点
('2101', '仁怀市支行', '仁怀市支行营业部', '遵义市', '仁怀市国酒大道中段20号',
    15, 3, 3, 5, 4, '赵刚', 450.00, 4, 3, 2, '自有', '支行营业部', 106.389500, 27.789200),
('2102', '仁怀市支行', '茅台镇支行',       '遵义市', '仁怀市茅台镇杨柳湾街1号',
    10, 2, 2, 3, 3, '孙梅', 300.00, 3, 2, 1, '自有', '乡镇支行', 106.372800, 27.754600),
('2103', '仁怀市支行', '中枢街道支行',     '遵义市', '仁怀市中枢街道陵园路12号',
    8, 2, 1, 3, 2, '周强', 240.00, 2, 2, 1, '租赁', '城区支行', 106.397600, 27.795800),

-- 赤水市支行下网点
('2201', '赤水市支行', '赤水市支行营业部', '遵义市', '赤水市人民南路18号',
    11, 2, 2, 4, 3, '吴燕', 310.00, 3, 2, 1, '自有', '支行营业部', 105.694800, 28.578400),
('2202', '赤水市支行', '市中街道支行',     '遵义市', '赤水市市中街道延安路9号',
    7, 1, 1, 3, 2, '郑勇', 200.00, 2, 1, 1, '租赁', '城区支行', 105.701200, 28.587100),

-- 正安县支行下网点
('2301', '正安县支行', '正安县支行营业部', '遵义市', '正安县凤仪镇解放街5号',
    9, 2, 1, 3, 3, '黄丽', 260.00, 2, 2, 1, '自有', '县域支行', 107.415200, 28.531600),
('2302', '正安县支行', '安场镇支行',       '遵义市', '正安县安场镇老街18号',
    6, 1, 1, 2, 2, '杨军', 180.00, 2, 1, 1, '租赁', '乡镇支行', 107.456800, 28.560100),

-- 六盘水分行直辖网点
('3001', '六盘水分行', '六盘水分行营业部', '六盘水市', '钟山区钟山大道中段1号',
    16, 4, 3, 5, 4, '谢华', 520.00, 4, 3, 2, '自有', '分行营业部', 104.832500, 26.594700),
('3002', '六盘水分行', '钟山区支行',       '六盘水市', '钟山区人民中路67号',
    11, 2, 2, 4, 3, '唐敏', 300.00, 3, 2, 1, '自有', '城区支行', 104.818900, 26.601200),
('3003', '六盘水分行', '水城区支行',       '六盘水市', '水城区双水大道8号',
    10, 2, 2, 3, 3, '何丽', 270.00, 2, 2, 1, '自有', '县域支行', 104.954300, 26.544600),

-- 盘州市支行下网点
('3101', '盘州市支行', '盘州市支行营业部', '六盘水市', '盘州市亦资街道胜境大道22号',
    12, 3, 2, 4, 3, '秦勇', 350.00, 3, 2, 1, '自有', '支行营业部', 104.476400, 25.709900),
('3102', '盘州市支行', '红果街道支行',     '六盘水市', '盘州市红果街道干沟桥12号',
    8, 2, 1, 3, 2, '宋洁', 210.00, 2, 2, 1, '租赁', '城区支行', 104.456200, 25.718800);


-- ============================================================
-- 3. 全流程审批测试记录（幂等：用唯一约束防重复）
-- ============================================================
-- 场景 A：同市跨支行申请 → 已通过
--   test_guiyang(10) → 清镇市支行(211), reviewer_guiyang(20)
INSERT IGNORE INTO jw_data_access_request
    (applicant_id, target_dept_id, reason, valid_days, status,
     reviewer_id, review_comment, review_time,
     valid_date_from, valid_date_to,
     del_flag, create_by, create_time, update_by, update_time)
VALUES
    (10, 211, '因业务需要，需查看清镇市支行网点分布及效能数据', 30, '1',
     20, '同意，请在有效期内使用', DATE_ADD(CURDATE(), INTERVAL 1 HOUR),
     CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY),
     '0', 'test_guiyang', DATE_ADD(CURDATE(), INTERVAL -2 HOUR),
     'reviewer_guiyang', DATE_ADD(CURDATE(), INTERVAL 1 HOUR));

-- 场景 B：同市跨支行申请 → 已拒绝
--   test_guiyang(10) → 开阳县支行(212), reviewer_guiyang(20)
INSERT IGNORE INTO jw_data_access_request
    (applicant_id, target_dept_id, reason, valid_days, status,
     reviewer_id, review_comment, review_time,
     del_flag, create_by, create_time, update_by, update_time)
VALUES
    (10, 212, '项目调研需要参考开阳县支行数据', 30, '2',
     20, '当前数据尚在整理中，暂不对外开放',
     DATE_ADD(CURDATE(), INTERVAL 30 MINUTE),
     '0', 'test_guiyang', DATE_ADD(CURDATE(), INTERVAL -1 HOUR),
     'reviewer_guiyang', DATE_ADD(CURDATE(), INTERVAL 30 MINUTE));

-- 场景 C：跨市申请 → 已通过
--   test_guiyang(10) → 遵义分行(220), reviewer_zunyi(21)
INSERT IGNORE INTO jw_data_access_request
    (applicant_id, target_dept_id, reason, valid_days, status,
     reviewer_id, review_comment, review_time,
     valid_date_from, valid_date_to,
     del_flag, create_by, create_time, update_by, update_time)
VALUES
    (10, 220, '跨市业务协同，需了解遵义分行网点布局', 7, '1',
     21, '同意交流，请与遵义分行对口部门对接',
     DATE_ADD(CURDATE(), INTERVAL 2 HOUR),
     CURDATE(), DATE_ADD(CURDATE(), INTERVAL 7 DAY),
     '0', 'test_guiyang', DATE_ADD(CURDATE(), INTERVAL -3 HOUR),
     'reviewer_zunyi', DATE_ADD(CURDATE(), INTERVAL 2 HOUR));

-- 场景 D：跨市申请 → 待审批
--   test_zunyi(12) → 贵阳分行(210), reviewer_guiyang(20)
INSERT IGNORE INTO jw_data_access_request
    (applicant_id, target_dept_id, reason, valid_days, status,
     reviewer_id,
     del_flag, create_by, create_time)
VALUES
    (12, 210, '学习考察贵阳分行先进管理经验，需参考相关数据', 30, '0',
     20,
     '0', 'test_zunyi', DATE_ADD(CURDATE(), INTERVAL -10 MINUTE));

-- 场景 E：跨市申请 → 已撤销
--   test_qingzhen(11) → 仁怀市支行(221), reviewer_zunyi(21)
INSERT IGNORE INTO jw_data_access_request
    (applicant_id, target_dept_id, reason, valid_days, status,
     reviewer_id,
     del_flag, create_by, create_time, update_by, update_time)
VALUES
    (11, 221, '需要对比仁怀市支行酱酒产业客群数据', 30, '3',
     21,
     '0', 'test_qingzhen', DATE_ADD(CURDATE(), INTERVAL -5 HOUR),
     'test_qingzhen', DATE_ADD(CURDATE(), INTERVAL -4 HOUR));

-- 场景 F：市行级申请 → 待审批（省行审核）
--   test_guiyang(10) → 遵义市分行(202), reviewer_province(30)
INSERT IGNORE INTO jw_data_access_request
    (applicant_id, target_dept_id, reason, valid_days, status,
     reviewer_id,
     del_flag, create_by, create_time)
VALUES
    (10, 202, '全辖业务分析需要，需查看遵义市分行整体经营数据', 30, '0',
     30,
     '0', 'test_guiyang', DATE_ADD(CURDATE(), INTERVAL -20 MINUTE));

-- 场景 G：市行级申请 → 已通过（省行审核）
--   test_zunyi(12) → 六盘水市分行(203), reviewer_province(30)
INSERT IGNORE INTO jw_data_access_request
    (applicant_id, target_dept_id, reason, valid_days, status,
     reviewer_id, review_comment, review_time,
     valid_date_from, valid_date_to,
     del_flag, create_by, create_time, update_by, update_time)
VALUES
    (12, 203, '跨区域业务拓展调研，需参看六盘水市分行数据作为参考', 90, '1',
     30, '同意跨区调研，注意数据安全保密',
     DATE_ADD(CURDATE(), INTERVAL 3 HOUR),
     CURDATE(), DATE_ADD(CURDATE(), INTERVAL 90 DAY),
     '0', 'test_zunyi', DATE_ADD(CURDATE(), INTERVAL -4 HOUR),
     'reviewer_province', DATE_ADD(CURDATE(), INTERVAL 3 HOUR));


-- ============================================================
-- 验证插入结果
-- ============================================================
SELECT '[验证] 用户' AS '';
SELECT user_id, user_name, nick_name, dept_id FROM sys_user WHERE user_name LIKE 'reviewer_%' OR user_name LIKE 'test_%' ORDER BY user_id;

SELECT '[验证] 角色-部门' AS '';
SELECT r.role_name, d.dept_name, d.dept_id
FROM sys_role_dept rd
JOIN sys_role r ON rd.role_id=r.role_id
JOIN sys_dept d ON rd.dept_id=d.dept_id
WHERE r.role_key='data_reviewer';

SELECT '[验证] 网点数' AS '';
SELECT primary_branch, COUNT(*) AS cnt FROM jw_branch_info GROUP BY primary_branch ORDER BY primary_branch;

SELECT '[验证] 审批记录' AS '';
SELECT r.request_id, u1.user_name AS applicant, d.dept_name AS target, rs.status, u2.user_name AS reviewer
FROM jw_data_access_request r
LEFT JOIN sys_user u1 ON r.applicant_id=u1.user_id
LEFT JOIN sys_dept d ON r.target_dept_id=d.dept_id
LEFT JOIN (SELECT '0' AS k, '待审批' AS status UNION SELECT '1', '已通过' UNION SELECT '2', '已拒绝' UNION SELECT '3', '已撤销' UNION SELECT '4', '已过期') rs ON r.status=rs.k
LEFT JOIN sys_user u2 ON r.reviewer_id=u2.user_id
ORDER BY r.request_id;
