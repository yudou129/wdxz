/*
 Navicat Premium Data Transfer

 Source Server         : mysql
 Source Server Type    : MySQL
 Source Server Version : 100508
 Source Host           : localhost:3306
 Source Schema         : ry-vue

 Target Server Type    : MySQL
 Target Server Version : 100508
 File Encoding         : 65001

 Date: 03/06/2026 16:17:21
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for jw_indicator_config
-- ----------------------------
DROP TABLE IF EXISTS `jw_indicator_config`;
CREATE TABLE `jw_indicator_config`  (
  `indicator_id` bigint NOT NULL AUTO_INCREMENT COMMENT '指标主键ID',
  `indicator_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '指标编码（唯一标识，程序通过此字段匹配）',
  `indicator_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '指标中文名称（导出Excel列头）',
  `sort_order` int NULL DEFAULT 0 COMMENT '导出列排序序号',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `indicator_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '指标类型: grid/branch_raw/branch',
  `parent_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '上级指标编码',
  `is_derived` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT '0' COMMENT '是否衍生计算指标',
  `computation_pattern` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '计算模式',
  `input_codes` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '参与计算的指标编码',
  `calculation_weight` double NULL DEFAULT NULL COMMENT '本级计算权重',
  PRIMARY KEY (`indicator_id`) USING BTREE,
  UNIQUE INDEX `uk_indicator_code`(`indicator_code` ASC) USING BTREE,
  INDEX `idx_indicator_type`(`indicator_type` ASC) USING BTREE,
  INDEX `idx_parent_code`(`parent_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 795 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '指标配置表（元数据，驱动导入/导出/计算全流程）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of jw_indicator_config
-- ----------------------------
INSERT INTO `jw_indicator_config` VALUES (476, 'pop', '人口聚集', 0, '', '2026-06-03 08:55:30', '', '2026-06-03 08:55:30', 'grid', NULL, '0', NULL, NULL, 0.4);
INSERT INTO `jw_indicator_config` VALUES (477, 'grid_3243', '商业聚集', 1, '', '2026-06-03 08:56:13', '', '2026-06-03 08:56:13', 'grid', NULL, '0', NULL, NULL, 0.3);
INSERT INTO `jw_indicator_config` VALUES (478, 'ent', '企业聚集', 2, '', '2026-06-03 08:56:34', '', '2026-06-03 08:56:34', 'grid', NULL, '0', NULL, NULL, 0.3);
INSERT INTO `jw_indicator_config` VALUES (479, 'pop_2', '常住、流动人口', 0, '', '2026-06-03 08:58:18', '', '2026-06-03 08:58:18', 'grid', 'pop', '0', NULL, NULL, 0.4);
INSERT INTO `jw_indicator_config` VALUES (480, 'grid_871', '工作', 0, '', '2026-06-03 08:58:50', '', '2026-06-03 08:58:50', 'grid', 'pop_2', '0', NULL, NULL, 0.5);
INSERT INTO `jw_indicator_config` VALUES (481, 'grid_2310', '居住', 1, '', '2026-06-03 08:59:16', '', '2026-06-03 08:59:16', 'grid', 'pop_2', '0', NULL, NULL, 0.5);
INSERT INTO `jw_indicator_config` VALUES (482, 'grid_8696', '年龄', 1, '', '2026-06-03 08:59:54', '', '2026-06-03 15:11:59', 'grid', 'pop', '0', NULL, NULL, 0.2);
INSERT INTO `jw_indicator_config` VALUES (483, '2534', '25-34岁', 0, '', '2026-06-03 09:00:47', '', '2026-06-03 09:00:47', 'grid', 'grid_8696', '0', NULL, NULL, 0.05);
INSERT INTO `jw_indicator_config` VALUES (484, '3544', '35-44岁', 1, '', '2026-06-03 09:01:16', '', '2026-06-03 09:01:16', 'grid', 'grid_8696', '0', NULL, NULL, 0.2);
INSERT INTO `jw_indicator_config` VALUES (485, '4554', '45-54岁', 2, '', '2026-06-03 09:01:43', '', '2026-06-03 09:01:43', 'grid', 'grid_8696', '0', NULL, NULL, 0.35);
INSERT INTO `jw_indicator_config` VALUES (486, '55', '55岁以上', 3, '', '2026-06-03 09:02:10', '', '2026-06-03 09:02:10', 'grid', 'grid_8696', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (487, 'grid_9133', '收入水平', 2, '', '2026-06-03 09:02:55', '', '2026-06-03 09:02:55', 'grid', 'pop', '0', NULL, NULL, 0.25);
INSERT INTO `jw_indicator_config` VALUES (488, '40007999', '4000~7999', 0, '', '2026-06-03 09:04:36', '', '2026-06-03 09:04:36', 'grid', 'grid_9133', '0', NULL, NULL, 0.15);
INSERT INTO `jw_indicator_config` VALUES (489, '800019999', '8000~19999', 1, '', '2026-06-03 09:05:19', '', '2026-06-03 09:05:19', 'grid', 'grid_9133', '0', NULL, NULL, 0.4);
INSERT INTO `jw_indicator_config` VALUES (490, '20000', '20000及以上', 2, '', '2026-06-03 09:05:54', '', '2026-06-03 09:05:54', 'grid', 'grid_9133', '0', NULL, NULL, 0.45);
INSERT INTO `jw_indicator_config` VALUES (491, 'grid_9881', '教育水平', 3, '', '2026-06-03 09:07:03', '', '2026-06-03 09:07:03', 'grid', 'pop', '0', NULL, NULL, 0.05);
INSERT INTO `jw_indicator_config` VALUES (492, 'grid_8856', '大专', 0, '', '2026-06-03 09:07:47', '', '2026-06-03 09:07:47', 'grid', 'grid_9881', '0', NULL, NULL, 0.2);
INSERT INTO `jw_indicator_config` VALUES (493, 'grid_2817', '本科及以上', 1, '', '2026-06-03 09:08:09', '', '2026-06-03 09:08:09', 'grid', 'grid_9881', '0', NULL, NULL, 0.8);
INSERT INTO `jw_indicator_config` VALUES (494, 'grid_7744', '所在行业', 4, '', '2026-06-03 09:08:41', '', '2026-06-03 09:08:41', 'grid', 'pop', '0', NULL, NULL, 0.05);
INSERT INTO `jw_indicator_config` VALUES (495, 'it', 'IT通信电子', 0, '', '2026-06-03 09:10:15', '', '2026-06-03 09:10:15', 'grid', 'grid_7744', '0', NULL, NULL, 0.1);
INSERT INTO `jw_indicator_config` VALUES (496, 'grid_45', '交通运输和仓储邮政', 1, '', '2026-06-03 09:10:43', '', '2026-06-03 09:10:43', 'grid', 'grid_7744', '0', NULL, NULL, 0.02);
INSERT INTO `jw_indicator_config` VALUES (497, 'grid_654', '住宿旅游', 2, '', '2026-06-03 09:11:21', '', '2026-06-03 09:11:21', 'grid', 'grid_7744', '0', NULL, NULL, 0.06);
INSERT INTO `jw_indicator_config` VALUES (498, 'grid_363', '农林牧渔', 2, '', '2026-06-03 09:11:40', '', '2026-06-03 09:11:40', 'grid', 'grid_7744', '0', NULL, NULL, 0.02);
INSERT INTO `jw_indicator_config` VALUES (499, 'grid_8114', '医药卫生', 3, '', '2026-06-03 09:11:59', '', '2026-06-03 09:11:59', 'grid', 'grid_7744', '0', NULL, NULL, 0.03);
INSERT INTO `jw_indicator_config` VALUES (500, 'grid_7265', '家电', 4, '', '2026-06-03 09:12:15', '', '2026-06-03 09:12:15', 'grid', 'grid_7744', '0', NULL, NULL, 0.02);
INSERT INTO `jw_indicator_config` VALUES (501, 'grid_6514', '广告营销', 4, '', '2026-06-03 09:12:38', '', '2026-06-03 09:12:38', 'grid', 'grid_7744', '0', NULL, NULL, 0.04);
INSERT INTO `jw_indicator_config` VALUES (502, 'grid_7221', '建材家居', 5, '', '2026-06-03 09:12:54', '', '2026-06-03 09:12:54', 'grid', 'grid_7744', '0', NULL, NULL, 0.04);
INSERT INTO `jw_indicator_config` VALUES (503, 'grid_5393', '建筑房地产', 6, '', '2026-06-03 09:13:17', '', '2026-06-03 09:13:17', 'grid', 'grid_7744', '0', NULL, NULL, 0.04);
INSERT INTO `jw_indicator_config` VALUES (504, 'grid_1012', '教育', 7, '', '2026-06-03 09:13:42', '', '2026-06-03 09:13:42', 'grid', 'grid_7744', '0', NULL, NULL, 0.06);
INSERT INTO `jw_indicator_config` VALUES (505, 'grid_7225', '文化体育娱乐', 7, '', '2026-06-03 09:14:03', '', '2026-06-03 09:14:03', 'grid', 'grid_7744', '0', NULL, NULL, 0.06);
INSERT INTO `jw_indicator_config` VALUES (506, 'grid_2035', '日化百货', 8, '', '2026-06-03 09:14:20', '', '2026-06-03 09:14:20', 'grid', 'grid_7744', '0', NULL, NULL, 0.04);
INSERT INTO `jw_indicator_config` VALUES (507, 'grid_9209', '机械制造', 9, '', '2026-06-03 09:14:37', '', '2026-06-03 09:14:37', 'grid', 'grid_7744', '0', NULL, NULL, 0.04);
INSERT INTO `jw_indicator_config` VALUES (508, 'grid_6026', '汽车', 9, '', '2026-06-03 09:14:53', '', '2026-06-03 09:14:53', 'grid', 'grid_7744', '0', NULL, NULL, 0.06);
INSERT INTO `jw_indicator_config` VALUES (509, 'grid_157', '法律商务人力外贸', 10, '', '2026-06-03 09:15:47', '', '2026-06-03 09:15:47', 'grid', 'grid_7744', '0', NULL, NULL, 0.1);
INSERT INTO `jw_indicator_config` VALUES (510, 'grid_9145', '生活服务', 11, '', '2026-06-03 09:16:06', '', '2026-06-03 09:16:06', 'grid', 'grid_7744', '0', NULL, NULL, 0.02);
INSERT INTO `jw_indicator_config` VALUES (511, 'grid_5511', '社会公共管理', 12, '', '2026-06-03 09:16:33', '', '2026-06-03 09:16:33', 'grid', 'grid_7744', '0', NULL, NULL, 0.02);
INSERT INTO `jw_indicator_config` VALUES (512, 'grid_8268', '纺织服装', 13, '', '2026-06-03 09:16:59', '', '2026-06-03 09:16:59', 'grid', 'grid_7744', '0', NULL, NULL, 0.02);
INSERT INTO `jw_indicator_config` VALUES (513, 'grid_8008', '能源采矿化工', 13, '', '2026-06-03 09:17:18', '', '2026-06-03 09:17:18', 'grid', 'grid_7744', '0', NULL, NULL, 0.04);
INSERT INTO `jw_indicator_config` VALUES (514, 'grid_3747', '金融保险', 14, '', '2026-06-03 09:17:42', '', '2026-06-03 09:17:42', 'grid', 'grid_7744', '0', NULL, NULL, 0.12);
INSERT INTO `jw_indicator_config` VALUES (515, 'grid_4469', '食品加工', 15, '', '2026-06-03 09:18:01', '', '2026-06-03 09:18:01', 'grid', 'grid_7744', '0', NULL, NULL, 0.02);
INSERT INTO `jw_indicator_config` VALUES (516, 'grid_9997', '餐饮', 16, '', '2026-06-03 09:18:32', '', '2026-06-03 09:18:32', 'grid', 'grid_7744', '0', NULL, NULL, 0.02);
INSERT INTO `jw_indicator_config` VALUES (517, 'grid_1637', '职业类别', 6, '', '2026-06-03 09:19:00', '', '2026-06-03 09:19:00', 'grid', 'pop', '0', NULL, NULL, 0.05);
INSERT INTO `jw_indicator_config` VALUES (518, 'grid_8067', '专业技术人员', 0, '', '2026-06-03 09:19:22', '', '2026-06-03 09:19:22', 'grid', 'grid_1637', '0', NULL, NULL, 0.2);
INSERT INTO `jw_indicator_config` VALUES (519, 'grid_3115', '个体经营者', 1, '', '2026-06-03 09:20:01', '', '2026-06-03 09:20:01', 'grid', 'grid_1637', '0', NULL, NULL, 0.2);
INSERT INTO `jw_indicator_config` VALUES (520, 'grid_9134', '文职人员', 2, '', '2026-06-03 09:21:06', '', '2026-06-03 09:21:06', 'grid', 'grid_1637', '0', NULL, NULL, 0.1);
INSERT INTO `jw_indicator_config` VALUES (521, 'grid_2332', '生产操作人员', 3, '', '2026-06-03 09:21:37', '', '2026-06-03 09:21:37', 'grid', 'grid_1637', '0', NULL, NULL, 0.1);
INSERT INTO `jw_indicator_config` VALUES (522, 'ent_2', '管理者和企业主', 4, '', '2026-06-03 09:21:59', '', '2026-06-03 09:21:59', 'grid', 'grid_1637', '0', NULL, NULL, 0.4);
INSERT INTO `jw_indicator_config` VALUES (523, 'grid_48', '人生阶段', 0, '', '2026-06-03 09:24:39', '', '2026-06-03 09:37:21', 'grid', 'grid_3243', '0', NULL, NULL, 0.2);
INSERT INTO `jw_indicator_config` VALUES (524, 'grid_7600', '孕期', 0, '', '2026-06-03 09:25:03', '', '2026-06-03 09:25:03', 'grid', 'grid_48', '0', NULL, NULL, 0.14);
INSERT INTO `jw_indicator_config` VALUES (525, '01', '家有0-1岁小孩', 1, '', '2026-06-03 09:25:38', '', '2026-06-03 09:25:38', 'grid', 'grid_48', '0', NULL, NULL, 0.14);
INSERT INTO `jw_indicator_config` VALUES (526, '13', '家有1-3岁小孩', 2, '', '2026-06-03 09:25:54', '', '2026-06-03 09:25:54', 'grid', 'grid_48', '0', NULL, NULL, 0.14);
INSERT INTO `jw_indicator_config` VALUES (527, '36', '家有3-6岁小孩', 3, '', '2026-06-03 09:26:14', '', '2026-06-03 09:26:14', 'grid', 'grid_48', '0', NULL, NULL, 0.14);
INSERT INTO `jw_indicator_config` VALUES (528, 'grid_8287', '家有小学生', 4, '', '2026-06-03 09:26:33', '', '2026-06-03 09:26:33', 'grid', 'grid_48', '0', NULL, NULL, 0.14);
INSERT INTO `jw_indicator_config` VALUES (529, 'grid_704', '家有初中生', 4, '', '2026-06-03 09:26:55', '', '2026-06-03 09:26:55', 'grid', 'grid_48', '0', NULL, NULL, 0.14);
INSERT INTO `jw_indicator_config` VALUES (530, 'grid_5800', '家有高中生', 6, '', '2026-06-03 09:27:40', '', '2026-06-03 09:27:40', 'grid', 'grid_48', '0', NULL, NULL, 0.14);
INSERT INTO `jw_indicator_config` VALUES (531, 'grid_5543', '资产状况', 1, '', '2026-06-03 09:30:11', '', '2026-06-03 09:30:11', 'grid', 'grid_3243', '0', NULL, NULL, 0.1);
INSERT INTO `jw_indicator_config` VALUES (532, 'grid_9265', '有车', 0, '', '2026-06-03 09:30:33', '', '2026-06-03 09:30:33', 'grid', 'grid_5543', '0', NULL, NULL, 1);
INSERT INTO `jw_indicator_config` VALUES (533, 'grid_6405', '无车', 1, '', '2026-06-03 09:30:48', '', '2026-06-03 09:30:48', 'grid', 'grid_5543', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (534, 'grid_7683', '消费水平', 1, '', '2026-06-03 09:31:14', '', '2026-06-03 09:31:14', 'grid', 'grid_3243', '0', NULL, NULL, 0.2);
INSERT INTO `jw_indicator_config` VALUES (535, 'grid_6205', '低 ', 0, '', '2026-06-03 09:31:38', '', '2026-06-03 09:31:38', 'grid', 'grid_7683', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (536, 'grid_4258', '中', 1, '', '2026-06-03 09:31:49', '', '2026-06-03 09:31:49', 'grid', 'grid_7683', '0', NULL, NULL, 0.4);
INSERT INTO `jw_indicator_config` VALUES (537, 'grid_3843', '高', 2, '', '2026-06-03 09:32:02', '', '2026-06-03 09:32:02', 'grid', 'grid_7683', '0', NULL, NULL, 0.6);
INSERT INTO `jw_indicator_config` VALUES (538, 'grid_poi', 'POI', 3, '', '2026-06-03 09:32:52', '', '2026-06-03 09:32:52', 'grid', 'grid_3243', '0', NULL, NULL, 0.5);
INSERT INTO `jw_indicator_config` VALUES (539, 'grid_9458', '运动健身', 1, '', '2026-06-03 09:35:46', '', '2026-06-03 09:35:46', 'grid', 'grid_poi', '0', NULL, NULL, 0.03);
INSERT INTO `jw_indicator_config` VALUES (540, 'grid_5367', '药店', 1, '', '2026-06-03 09:38:01', '', '2026-06-03 09:38:01', 'grid', 'grid_poi', '0', NULL, NULL, 0.03);
INSERT INTO `jw_indicator_config` VALUES (541, 'grid_9896', '文化传媒', 2, '', '2026-06-03 09:38:17', '', '2026-06-03 09:38:17', 'grid', 'grid_poi', '0', NULL, NULL, 0.05);
INSERT INTO `jw_indicator_config` VALUES (542, 'grid_5067', '市场', 2, '', '2026-06-03 09:38:31', '', '2026-06-03 09:38:31', 'grid', 'grid_poi', '0', NULL, NULL, 0.3);
INSERT INTO `jw_indicator_config` VALUES (543, 'grid_3301', '美食', 4, '', '2026-06-03 09:38:51', '', '2026-06-03 09:38:51', 'grid', 'grid_poi', '0', NULL, NULL, 0.05);
INSERT INTO `jw_indicator_config` VALUES (544, 'grid_8868', '酒店', 6, '', '2026-06-03 09:39:08', '', '2026-06-03 09:39:08', 'grid', 'grid_poi', '0', NULL, NULL, 0.03);
INSERT INTO `jw_indicator_config` VALUES (545, 'grid_1849', '购物中心', 7, '', '2026-06-03 09:39:41', '', '2026-06-03 09:39:41', 'grid', 'grid_poi', '0', NULL, NULL, 0.4);
INSERT INTO `jw_indicator_config` VALUES (546, 'grid_2741', '超市', 8, '', '2026-06-03 09:40:00', '', '2026-06-03 09:40:00', 'grid', 'grid_poi', '0', NULL, NULL, 0.1);
INSERT INTO `jw_indicator_config` VALUES (547, 'grid_6458', '便利店', 8, '', '2026-06-03 09:40:17', '', '2026-06-03 09:40:17', 'grid', 'grid_poi', '0', NULL, NULL, 0.03);
INSERT INTO `jw_indicator_config` VALUES (548, 'grid_8498', '公司', 0, '', '2026-06-03 09:40:52', '', '2026-06-03 09:40:52', 'grid', 'ent', '0', NULL, NULL, 0.7);
INSERT INTO `jw_indicator_config` VALUES (549, 'grid_9293', '写字楼', 1, '', '2026-06-03 09:41:05', '', '2026-06-03 09:41:05', 'grid', 'ent', '0', NULL, NULL, 0.3);
INSERT INTO `jw_indicator_config` VALUES (550, 'branch_raw_5913', '经营情况', 0, '', '2026-06-03 09:51:11', '', '2026-06-03 09:51:11', 'branch_raw', NULL, '0', NULL, NULL, 0.45);
INSERT INTO `jw_indicator_config` VALUES (551, 'interest_income', '利息净收入（万元）', 0, '', '2026-06-03 09:51:58', '', '2026-06-03 09:51:58', 'branch_raw', 'branch_raw_5913', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (552, 'branch_raw_1596', '手佣净收入（万元）', 1, '', '2026-06-03 09:52:58', '', '2026-06-03 09:52:58', 'branch_raw', 'branch_raw_5913', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (553, 'per_capita', '人均营业收入（万元）', 0, '', '2026-06-03 10:01:45', '', '2026-06-03 10:01:45', 'branch', 'branch_raw_5913', '1', 'sum_per_capita', 'interest_income,branch_raw_1596', 0.5);
INSERT INTO `jw_indicator_config` VALUES (554, 'per_area', '每单位面积营业收入（万元）', 1, '', '2026-06-03 10:02:59', '', '2026-06-03 10:02:59', 'branch', 'branch_raw_5913', '1', 'sum_per_area', 'interest_income,branch_raw_1596', 0.5);
INSERT INTO `jw_indicator_config` VALUES (555, 'branch_4568', '业绩表现', 1, '', '2026-06-03 10:40:38', '', '2026-06-03 10:40:38', 'branch_raw', NULL, '0', NULL, NULL, 0.25);
INSERT INTO `jw_indicator_config` VALUES (556, 'total_asset', '全量个人金融资产', 0, '', '2026-06-03 10:41:18', '', '2026-06-03 10:41:18', 'branch_raw', 'branch_4568', '0', NULL, NULL, 0.2);
INSERT INTO `jw_indicator_config` VALUES (557, 'branch_1201', '储蓄存款', 1, '', '2026-06-03 10:41:46', '', '2026-06-03 10:45:38', 'branch_raw', 'branch_4568', '0', NULL, NULL, 0.2);
INSERT INTO `jw_indicator_config` VALUES (558, 'corp_dep', '公司客户存款', 2, '', '2026-06-03 10:42:12', '', '2026-06-03 10:42:12', 'branch_raw', 'branch_4568', '0', NULL, NULL, 0.2);
INSERT INTO `jw_indicator_config` VALUES (559, 'inst_dep', '机构客户存款', 2, '', '2026-06-03 10:42:35', '', '2026-06-03 10:42:35', 'branch_raw', 'branch_4568', '0', NULL, NULL, 0.2);
INSERT INTO `jw_indicator_config` VALUES (560, 'inclusive_loan', '普惠贷款', 5, '', '2026-06-03 10:43:14', '', '2026-06-03 10:43:14', 'branch_raw', 'branch_4568', '0', NULL, NULL, 0.1);
INSERT INTO `jw_indicator_config` VALUES (561, 'personal_loan', '个人贷款', 6, '', '2026-06-03 10:44:14', '', '2026-06-03 10:44:14', 'branch_raw', 'branch_4568', '0', NULL, NULL, 0.1);
INSERT INTO `jw_indicator_config` VALUES (562, 'avg_balance', '日均余额（万元）', 0, '', '2026-06-03 10:45:17', '', '2026-06-03 10:45:17', 'branch_raw', 'total_asset', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (563, 'avg_balance_2', '日均余额（万元）', 0, '', '2026-06-03 10:45:52', '', '2026-06-03 10:45:52', 'branch_raw', 'branch_1201', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (566, 'avg_balance_1', '日均余额（万元）', 0, '', '2026-06-03 10:49:57', '', '2026-06-03 10:49:57', 'branch_raw', 'corp_dep', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (567, 'avg_balance_3', '日均余额（万元）', 0, '', '2026-06-03 10:50:26', '', '2026-06-03 10:50:26', 'branch_raw', 'inst_dep', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (568, 'avg_growth', '日均增量（万元）', 1, '', '2026-06-03 10:51:03', '', '2026-06-03 10:51:03', 'branch_raw', 'total_asset', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (569, 'avg_growth_1', '日均增量（万元）', 1, '', '2026-06-03 10:51:12', '', '2026-06-03 10:51:12', 'branch_raw', 'inst_dep', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (570, 'avg_growth_2', '日均增量（万元）', 1, '', '2026-06-03 10:51:22', '', '2026-06-03 10:51:22', 'branch_raw', 'corp_dep', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (571, 'avg_growth_3', '日均增量（万元）', 1, '', '2026-06-03 10:51:29', '', '2026-06-03 10:51:29', 'branch_raw', 'branch_1201', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (572, 'amount', '发放额（万元）', 0, '', '2026-06-03 10:51:51', '', '2026-06-03 10:52:27', 'branch_raw', 'personal_loan', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (573, 'amount_1', '营销额', 0, '', '2026-06-03 10:52:14', '', '2026-06-03 10:53:53', 'branch_raw', 'inclusive_loan', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (574, 'branch_7584', '客户发展', 3, '', '2026-06-03 11:20:32', '', '2026-06-03 11:20:32', 'branch', NULL, '0', NULL, NULL, 0.25);
INSERT INTO `jw_indicator_config` VALUES (575, 'branch_1512', '个人客户', 0, '', '2026-06-03 11:20:58', '', '2026-06-03 11:21:28', 'branch_raw', 'branch_7584', '0', NULL, NULL, 0.5);
INSERT INTO `jw_indicator_config` VALUES (576, 'branch_9876', '对公客户', 1, '', '2026-06-03 11:21:54', '', '2026-06-03 11:21:54', 'branch_raw', 'branch_7584', '0', NULL, NULL, 0.2);
INSERT INTO `jw_indicator_config` VALUES (577, 'branch_4963', '机构客户', 2, '', '2026-06-03 11:22:25', '', '2026-06-03 11:22:25', 'branch_raw', 'branch_7584', '0', NULL, NULL, 0.2);
INSERT INTO `jw_indicator_config` VALUES (578, 'branch_raw_8804', '普惠客户', 3, '', '2026-06-03 11:22:59', '', '2026-06-03 11:22:59', 'branch_raw', 'branch_7584', '0', NULL, NULL, 0.1);
INSERT INTO `jw_indicator_config` VALUES (579, '020cust', '日均0元（不含）-20万元（不含客户数）(单位：户)', 0, '', '2026-06-03 11:24:06', '', '2026-06-03 11:24:33', 'branch_raw', 'branch_1512', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (580, '20600cust', '日均20万元（含）-600万元（不含客户数）(单位：户)', 1, '', '2026-06-03 11:24:25', '', '2026-06-03 11:24:25', 'branch_raw', 'branch_1512', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (581, '600cust', '日均大于等于600万（含）客户数(单位：户)', 2, '', '2026-06-03 11:24:52', '', '2026-06-03 11:24:52', 'branch', 'branch_1512', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (582, 'cust50', '头部、中部对公客户数 日均资产50万元（含）以上（单位：户）', 0, '', '2026-06-03 11:25:12', '', '2026-06-03 11:25:12', 'branch', 'branch_9876', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (583, 'cust50_1', '底尾部部对公客户数 日均资产50万元（不含）以下（单位：户）', 1, '', '2026-06-03 11:25:28', '', '2026-06-03 11:25:28', 'branch', 'branch_9876', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (584, '1cust', '日均资产1万元（不含）以上机构客户数（单位：户）', 0, '', '2026-06-03 11:25:44', '', '2026-06-03 11:25:44', 'branch_raw', 'branch_4963', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (585, '1cust_1', '日均资产1万元（含）以下机构客户数（单位：户）', 1, '', '2026-06-03 11:26:00', '', '2026-06-03 11:26:00', 'branch_raw', 'branch_4963', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (586, 'branch_1274', '总量（单位：户）', 0, '', '2026-06-03 11:26:14', '', '2026-06-03 11:26:14', 'branch_raw', 'branch_raw_8804', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (587, 'avg_balance_4', '户日均余额 (万元)', 2, '', '2026-06-03 11:36:31', '', '2026-06-03 11:36:31', 'branch', 'total_asset', '1', 'per_customer', NULL, 0.5);
INSERT INTO `jw_indicator_config` VALUES (588, 'avg_growth_4', '日均增幅', 3, '', '2026-06-03 11:40:06', '', '2026-06-03 11:40:06', 'branch', 'total_asset', '1', 'growth_rate', 'avg_balance|avg_growth', 0.5);
INSERT INTO `jw_indicator_config` VALUES (589, 'avg_balance_5', '户日均余额 (万元)', 2, '', '2026-06-03 11:43:20', '', '2026-06-03 11:43:20', 'branch', 'branch_1201', '1', 'per_customer', NULL, 0.5);
INSERT INTO `jw_indicator_config` VALUES (590, 'avg_growth_5', '日均增幅', 3, '', '2026-06-03 11:44:23', '', '2026-06-03 11:44:23', 'branch', 'branch_1201', '1', 'growth_rate', 'avg_balance_2|avg_growth_3', 0.5);
INSERT INTO `jw_indicator_config` VALUES (591, 'avg_growth_6', '日均增幅', 3, '', '2026-06-03 11:45:32', '', '2026-06-03 11:45:32', 'branch', 'corp_dep', '1', 'growth_rate', 'avg_balance_1|avg_growth_2', 0.5);
INSERT INTO `jw_indicator_config` VALUES (592, 'avg_balance_6', '户日均余额 (万元)', 2, '', '2026-06-03 11:46:49', '', '2026-06-03 11:46:49', 'branch', 'corp_dep', '1', 'per_customer', NULL, 0.5);
INSERT INTO `jw_indicator_config` VALUES (593, 'avg_balance_7', '户日均余额 (万元)', 2, '', '2026-06-03 11:47:42', '', '2026-06-03 11:47:42', 'branch', 'inst_dep', '1', 'per_customer', NULL, 0.5);
INSERT INTO `jw_indicator_config` VALUES (594, 'avg_growth_7', '日均增幅', 3, '', '2026-06-03 11:48:32', '', '2026-06-03 11:48:32', 'branch', 'inst_dep', '1', 'growth_rate', 'avg_balance_3|avg_growth_1', 0.5);
INSERT INTO `jw_indicator_config` VALUES (595, 'per_capitaamount', '人均营销额 (万元)', 1, '', '2026-06-03 11:49:56', '', '2026-06-03 11:49:56', 'branch', 'inclusive_loan', '1', 'per_capita', 'amount_1', 1);
INSERT INTO `jw_indicator_config` VALUES (596, 'per_capitaamount_1', '人均发放额 (万元)', 1, '', '2026-06-03 11:50:55', '', '2026-06-03 11:50:55', 'branch', 'personal_loan', '1', 'per_capita', 'amount', 1);
INSERT INTO `jw_indicator_config` VALUES (597, 'per_capita020cust', '人均服务日均0元(不含)-20万元(不含)客户数 (单位：户)', 3, '', '2026-06-03 11:52:29', '', '2026-06-03 11:52:29', 'branch', 'branch_1512', '1', 'per_capita', '020cust', 1);
INSERT INTO `jw_indicator_config` VALUES (598, 'per_capita20600cust', '人均服务日均20万元(含)-600万元(不含)客户数 (单位：户)', 4, '', '2026-06-03 11:53:07', '', '2026-06-03 11:53:07', 'branch', 'branch_1512', '1', 'per_capita', '20600cust', 0.5);
INSERT INTO `jw_indicator_config` VALUES (599, 'per_capita600cust', '人均服务日均大于等于600万元(含)客户数 (单位：户)', 5, '', '2026-06-03 11:53:55', '', '2026-06-03 11:53:55', 'branch', 'branch_1512', '1', 'per_capita', '600cust', 0.4);
INSERT INTO `jw_indicator_config` VALUES (600, 'per_capitacust30', '人均服务头部、中部对公客户数日均资产30万元(含)以上 (单位：户)', 2, '', '2026-06-03 11:55:31', '', '2026-06-03 11:55:31', 'branch', 'branch_9876', '1', 'per_capita', 'cust50', 0.2);
INSERT INTO `jw_indicator_config` VALUES (601, 'per_capitacust30_1', '人均服务底尾部对公客户数日均资产30万元(不含)以下 (单位：户)', 3, '', '2026-06-03 11:56:09', '', '2026-06-03 11:56:09', 'branch', 'branch_9876', '1', 'per_capita', 'cust50_1', 0.8);
INSERT INTO `jw_indicator_config` VALUES (602, 'per_capita1cust', '人均服务日均资产1万元(不含)以上机构客户数 (单位：户)', 2, '', '2026-06-03 11:56:41', '', '2026-06-03 11:56:41', 'branch', 'branch_4963', '1', 'per_capita', '1cust', 0.2);
INSERT INTO `jw_indicator_config` VALUES (603, 'per_capita1cust_1', '人均服务日均资产1万元(含)以下机构客户数 (单位：户)', 3, '', '2026-06-03 11:57:34', '', '2026-06-03 11:57:34', 'branch', 'branch_4963', '1', 'per_capita', '1cust_1', 0.8);
INSERT INTO `jw_indicator_config` VALUES (604, 'per_capitacust', '人均服务客户数（单位：户）', 1, '', '2026-06-03 11:58:25', '', '2026-06-03 11:58:25', 'branch', 'branch_raw_8804', '1', 'per_capita', 'branch_1274', 1);
INSERT INTO `jw_indicator_config` VALUES (605, 'branch_170', '业务运营', 4, '', '2026-06-03 11:58:49', '', '2026-06-03 11:59:43', 'branch', NULL, '0', NULL, NULL, 0.05);
INSERT INTO `jw_indicator_config` VALUES (606, 'countertxn', '柜台日均交易笔数', 0, '', '2026-06-03 11:59:29', '', '2026-06-03 11:59:29', 'branch_raw', 'branch_170', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (607, 'terminaltxn', '自助终端日均交易笔数', 1, '', '2026-06-03 11:59:56', '', '2026-06-03 11:59:56', 'branch_raw', 'branch_170', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (608, 'atmtxn', '附行式、网点自助ATM日均交易笔数', 2, '', '2026-06-03 12:00:12', '', '2026-06-03 12:00:12', 'branch_raw', 'branch_170', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (609, 'per_areacounter', '每单位面积柜台日均工作量(笔)', 3, '', '2026-06-03 12:01:31', '', '2026-06-03 12:01:31', 'branch', 'branch_170', '1', 'per_area', 'countertxn', 0.6);
INSERT INTO `jw_indicator_config` VALUES (610, 'per_areaterminaltxn', '每单位面积自助终端日均交易笔数', 4, '', '2026-06-03 12:02:00', '', '2026-06-03 12:02:00', 'branch', 'branch_170', '1', 'per_area', 'terminaltxn', 0.3);
INSERT INTO `jw_indicator_config` VALUES (611, 'per_areaatmtxn', '每单位面积附行式ATM日均交易笔数', 5, '', '2026-06-03 12:02:27', '', '2026-06-03 12:02:27', 'branch', 'branch_170', '1', 'per_area', 'atmtxn', 0.1);
INSERT INTO `jw_indicator_config` VALUES (764, '18', '18岁以下', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_8696', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (765, '18_24', '18-24岁', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_8696', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (766, '55_64', '55-64岁', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_8696', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (767, '65', '65岁以上', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_8696', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (768, '2499', '2499及以下', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_9133', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (769, '2500_3999', '2500~3999', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_9133', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (770, 'indicator_85178', '高中及以下', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_9881', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (774, 'it_1', 'IT', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_7744', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (775, 'indicator_85224', '通信电子', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_7744', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (776, 'indicator_85259', '法律服务', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_7744', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (777, 'indicator_85261', '人力外贸', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_7744', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (778, 'indicator_85271', '科学研究', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_7744', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (779, 'indicator_85295', '服务人员', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_1637', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (780, 'indicator_85303', '初中生', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_48', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (781, 'indicator_85313', '高中生', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_48', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (782, 'indicator_85317', '大学生', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_48', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (783, 'indicator_85328', '研究生', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_48', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (784, 'indicator_85343', '家有孕妇', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_48', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (785, 'indicator_85353', '育儿阶段', 0, '', '2026-06-03 15:19:45', '', '2026-06-03 15:19:45', 'grid_raw', 'grid_48', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (786, 'auto_import_grid', '自动导入(网格)', -1, '', '2026-06-03 15:27:04', '', '2026-06-03 15:27:04', 'grid_auto', NULL, '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (787, 'indicator_24697', '省', 0, '', '2026-06-03 15:27:04', '', '2026-06-03 15:27:04', 'grid_auto', 'auto_import_grid', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (788, 'indicator_24746', '市', 0, '', '2026-06-03 15:27:04', '', '2026-06-03 15:27:04', 'grid_auto', 'auto_import_grid', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (789, 'indicator_24755', '区县', 0, '', '2026-06-03 15:27:04', '', '2026-06-03 15:27:04', 'grid_auto', 'auto_import_grid', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (790, 'indicator_24765', '常住、流动人数', 0, '', '2026-06-03 15:27:04', '', '2026-06-03 15:27:04', 'grid_auto', 'auto_import_grid', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (791, 'indicator_24781', '流动', 0, '', '2026-06-03 15:27:04', '', '2026-06-03 15:27:04', 'grid_auto', 'indicator_24765', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (792, 'indicator_24837', '性别', 0, '', '2026-06-03 15:27:04', '', '2026-06-03 15:27:04', 'grid_auto', 'auto_import_grid', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (793, 'indicator_24845', '男', 0, '', '2026-06-03 15:27:04', '', '2026-06-03 15:27:04', 'grid_auto', 'indicator_24837', '0', NULL, NULL, 0);
INSERT INTO `jw_indicator_config` VALUES (794, 'indicator_24852', '女', 0, '', '2026-06-03 15:27:04', '', '2026-06-03 15:27:04', 'grid_auto', 'indicator_24837', '0', NULL, NULL, 0);

SET FOREIGN_KEY_CHECKS = 1;
