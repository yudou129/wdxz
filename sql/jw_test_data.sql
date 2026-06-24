-- ============================================================
-- 网点数据查看权限审批 - 测试数据
-- 执行顺序：先跑 jw_data_access_request.sql，再跑本文件
-- ============================================================

-- ============================================================
-- 1. 字典数据：审批状态（jw_access_status）
--    只有在系统管理→字典管理未手动添加时才需要执行
-- ============================================================
INSERT INTO sys_dict_type VALUES (10, '审批状态', 'jw_access_status', '0', 'admin', sysdate(), '', null, '数据查看申请审批状态');
INSERT INTO sys_dict_data VALUES (50, 1, '待审批', '0', 'jw_access_status', 'primary',  'Y', '0', 'admin', sysdate(), '', null, '已提交等待审核');
INSERT INTO sys_dict_data VALUES (51, 2, '已通过', '1', 'jw_access_status', 'success',  'Y', '0', 'admin', sysdate(), '', null, '审核通过，在有效期内');
INSERT INTO sys_dict_data VALUES (52, 3, '已拒绝', '2', 'jw_access_status', 'danger',   'Y', '0', 'admin', sysdate(), '', null, '审核不通过');
INSERT INTO sys_dict_data VALUES (53, 4, '已撤销', '3', 'jw_access_status', 'info',     'Y', '0', 'admin', sysdate(), '', null, '申请人主动取消');
INSERT INTO sys_dict_data VALUES (54, 5, '已过期', '4', 'jw_access_status', 'warning',  'Y', '0', 'admin', sysdate(), '', null, '超过有效期自动标记');


-- ============================================================
-- 2. 银行组织架构（sys_dept）
--    贵州省分行 → 各地市分行 → 各支行
--    省行级部门从 200 开始，不与 demo 数据(100-109)冲突
-- ============================================================

-- 省行
INSERT INTO sys_dept VALUES (200, 0,   '0',              '贵州省分行',   0, '省分行', '08518888888', '', '0', '0', 'admin', sysdate(), '', null);

-- 市行（9个地州）
INSERT INTO sys_dept VALUES (201, 200, '0,200',          '贵阳市分行',   1, '贵阳',   '08518888001', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (202, 200, '0,200',          '遵义市分行',   2, '遵义',   '08528888002', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (203, 200, '0,200',          '六盘水市分行', 3, '六盘水', '08588888003', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (204, 200, '0,200',          '安顺市分行',   4, '安顺',   '08538888004', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (205, 200, '0,200',          '毕节市分行',   5, '毕节',   '08578888005', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (206, 200, '0,200',          '铜仁市分行',   6, '铜仁',   '08568888006', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (207, 200, '0,200',          '黔西南州分行', 7, '黔西南', '08598888007', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (208, 200, '0,200',          '黔东南州分行', 8, '黔东南', '08558888008', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (209, 200, '0,200',          '黔南州分行',   9, '黔南',   '08548888009', '', '0', '0', 'admin', sysdate(), '', null);

-- 支行（贵阳市分行下）
INSERT INTO sys_dept VALUES (210, 201, '0,200,201',     '贵阳分行',     1, '贵阳',   '', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (211, 201, '0,200,201',     '清镇市支行',   2, '清镇',   '', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (212, 201, '0,200,201',     '开阳县支行',   3, '开阳',   '', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (213, 201, '0,200,201',     '修文县支行',   4, '修文',   '', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (214, 201, '0,200,201',     '息烽县支行',   5, '息烽',   '', '', '0', '0', 'admin', sysdate(), '', null);

-- 支行（遵义市分行下）
INSERT INTO sys_dept VALUES (220, 202, '0,200,202',     '遵义分行',     1, '遵义',   '', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (221, 202, '0,200,202',     '仁怀市支行',   2, '仁怀',   '', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (222, 202, '0,200,202',     '赤水市支行',   3, '赤水',   '', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (223, 202, '0,200,202',     '正安县支行',   4, '正安',   '', '', '0', '0', 'admin', sysdate(), '', null);

-- 支行（六盘水市分行下）
INSERT INTO sys_dept VALUES (230, 203, '0,200,203',     '六盘水分行',   1, '六盘水', '', '', '0', '0', 'admin', sysdate(), '', null);
INSERT INTO sys_dept VALUES (231, 203, '0,200,203',     '盘州市支行',   2, '盘州',   '', '', '0', '0', 'admin', sysdate(), '', null);


-- ============================================================
-- 3. 数据审核员角色（sys_role）
--    role_id=3，不与 demo 角色(1,2) 冲突
-- ============================================================
INSERT INTO sys_role VALUES ('3', '数据审核员', 'data_reviewer', 3, 2, 1, 1, '0', '0', 'admin', sysdate(), '', null, '可审批跨机构数据查看申请');


-- ============================================================
-- 4. 测试用户（sys_user）
--    密码统一为：admin123（$2a$10$... 是 admin123 的 BCrypt 哈希）
-- ============================================================

-- 支行员工（所属部门=支行级，只能看到自己支行的数据）
-- 贵阳分行员工
INSERT INTO sys_user VALUES (10, 210, 'test_guiyang',  '测试-贵阳分行', '00', '', '13800000001', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '测试：贵阳分行员工，只能看贵阳分行下网点');
-- 清镇市支行员工
INSERT INTO sys_user VALUES (11, 211, 'test_qingzhen', '测试-清镇市支行', '00', '', '13800000002', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '测试：清镇市支行员工，只能看清镇市支行下网点');
-- 遵义分行员工
INSERT INTO sys_user VALUES (12, 220, 'test_zunyi',    '测试-遵义分行', '00', '', '13800000003', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '测试：遵义分行员工，只能看遵义分行下网点');
-- 仁怀市支行员工
INSERT INTO sys_user VALUES (13, 221, 'test_renhuai',  '测试-仁怀市支行', '00', '', '13800000004', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '测试：仁怀市支行员工，只能看仁怀市支行下网点');

-- 市行审核员（所属部门=市行级，data_reviewer 角色，可审核所辖支行申请）
INSERT INTO sys_user VALUES (20, 201, 'reviewer_guiyang', '审核-贵阳市分行', '00', '', '13800000101', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '测试：贵阳市分行数据审核员，可审批贵阳分行/清镇市支行等申请');
INSERT INTO sys_user VALUES (21, 202, 'reviewer_zunyi',   '审核-遵义市分行', '00', '', '13800000102', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '测试：遵义市分行数据审核员，可审批遵义分行/仁怀市支行等申请');


-- ============================================================
-- 5. 用户-角色关联（sys_user_role）
-- ============================================================
INSERT INTO sys_user_role VALUES (10, '2');   -- test_guiyang → common
INSERT INTO sys_user_role VALUES (11, '2');   -- test_qingzhen → common
INSERT INTO sys_user_role VALUES (12, '2');   -- test_zunyi → common
INSERT INTO sys_user_role VALUES (13, '2');   -- test_renhuai → common
INSERT INTO sys_user_role VALUES (20, '3');   -- reviewer_guiyang → data_reviewer
INSERT INTO sys_user_role VALUES (21, '3');   -- reviewer_zunyi → data_reviewer


-- ============================================================
-- 6. 角色-部门关联（sys_role_dept）
--    数据审核员只能审批所辖市行下支行的申请
--    reviewer_guiyang → 贵阳市分行(201) → 管辖 贵阳分行等支行
--    reviewer_zunyi   → 遵义市分行(202) → 管辖 遵义分行等支行
-- ============================================================
INSERT INTO sys_role_dept VALUES (3, 201);    -- data_reviewer → 贵阳市分行
INSERT INTO sys_role_dept VALUES (3, 202);    -- data_reviewer → 遵义市分行


-- ============================================================
-- 7. 角色-菜单关联（sys_role_menu）
--    至少需要给 data_reviewer 授予访问 jwmap 相关菜单的权限
--    以下权限按 RuoYi 标准菜单 ID 配置，实际需根据系统已有菜单调整
--    如果系统菜单 ID 不同，请在系统管理→角色管理→菜单权限中手动勾选
-- ============================================================

-- 注意：RuoYi 菜单 ID 因部署而异。以下给 data_reviewer 分配 jwmap 核心菜单权限。
-- 如果找不到对应菜单，通过 系统管理→菜单管理 查看实际 ID 后调整

-- 选配：给 common 角色也添加 jwmap 基础访问权限（否则普通用户看不到地图）
-- 如果 demo 数据中 common(ID=2) 已有所有菜单权限则无需重复执行


-- ============================================================
-- 8. 设置 sys_job 初始自增值（避免主键冲突）
-- ============================================================
-- 如果 sys_job 已存在 ID=100 的数据，先 truncate 再跑 jw_data_access_request.sql

-- ============================================================
-- 测试验证指南
-- ============================================================
-- ── 同支行可见（无需申请）──
-- 用 test_guiyang/admin123 登录 → 点击地图上 primary_branch='贵阳分行' 的网点
--    → 侧边栏应正常显示效能得分/排名/详细指标
-- 用 test_zunyi/admin123 登录 → 点击 primary_branch='遵义分行' 的网点 → 同理
--
-- ── 跨支行申请（贵阳→遵义）──
-- 1. 用 test_guiyang/admin123 登录
--    → 点击地图上 primary_branch='遵义分行' 的网点
--    → 侧边栏提示"暂无权限" + "申请查看"按钮
--    → 点击跳转到申请页 → 目标选"遵义分行" → 有效期30天 → 提交
--
-- 2. 用 reviewer_zunyi/admin123 登录
--    → 访问 /jwmap/access-approval → 待审批中出现申请
--    → 点审核 → 通过
--
-- 3. 切回 test_guiyang → 再次点击遵义分行网点 → 数据可见
--
-- ── 同市跨支行申请（贵阳→清镇）──
-- 用 test_guiyang 申请查看 清镇市支行 的数据
-- reviewer_guiyang 可审批（同属贵阳市分行管辖）
