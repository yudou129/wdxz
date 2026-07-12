-- 百度地图 GL 版本菜单
-- parent_id = 1061 (网点布局)
-- order_num = 2 (原菜单1063已移除，接替其顺序)
INSERT INTO `sys_menu` VALUES (1065,'百度地图',1061,2,'baidu-map','jwmap/baidu-map/index','','',1,0,'C','0','0','jwmap:map:view','map','admin','2026-07-08 00:00:00','',NULL,'百度地图可视化页面');

-- 角色权限（admin角色100拥有此菜单）
INSERT IGNORE INTO `sys_role_menu` VALUES (100, 1065);

-- 清理：移除旧的menu 1063 (地图可视化) 和 1064 (天地图) 及对应角色权限
DELETE FROM `sys_role_menu` WHERE menu_id IN (1063, 1064);
DELETE FROM `sys_menu` WHERE menu_id IN (1063, 1064);
