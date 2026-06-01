-- ----------------------------
-- 同业银行信息表
-- 数据来源: 同业数据_xxx.xlsx
-- 关联: jw_grid_meta (grid_code, 空间关联)
-- 导入时自动根据经纬度计算所属网格
-- ----------------------------
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
