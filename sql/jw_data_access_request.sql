-- ----------------------------
-- 网点数据查看权限申请表
-- ----------------------------
drop table if exists jw_data_access_request;
create table jw_data_access_request (
  request_id      bigint(20)   not null auto_increment comment '申请ID',
  applicant_id    bigint(20)   not null                comment '申请人用户ID',
  target_dept_id  bigint(20)   not null                comment '目标支行部门ID(sync_dept)',
  reason          varchar(500) default ''              comment '申请事由',
  valid_days      int(11)      default 30              comment '申请有效期天数',
  status          char(1)      default '0'             comment '状态(0待审批 1已通过 2已拒绝 3已撤销 4已过期)',
  reviewer_id     bigint(20)   default null            comment '审核人用户ID',
  review_comment  varchar(500) default ''             comment '审核意见',
  review_time     datetime     default null            comment '审核时间',
  valid_date_from datetime       default null            comment '生效日期',
  valid_date_to   datetime       default null            comment '失效日期',
  del_flag        char(1)      default '0'             comment '删除标志(0存在 2删除)',
  create_by       varchar(64)  default ''              comment '创建者',
  create_time     datetime     default null            comment '创建时间',
  update_by       varchar(64)  default ''              comment '更新者',
  update_time     datetime     default null            comment '更新时间',
  primary key (request_id),
  key idx_applicant (applicant_id),
  key idx_target_dept (target_dept_id),
  key idx_status (status),
  key idx_reviewer (reviewer_id)
) engine=innodb default charset=utf8mb4 comment='数据查看申请表';

-- ----------------------------
-- 字典数据：审批状态
-- ----------------------------
-- 注意：dict_type 需要使用 select 查出 type_id 并插入，或在管理系统手动添加
-- INSERT INTO sys_dict_type VALUES (..., '审批状态', 'jw_access_status', ...);
-- INSERT INTO sys_dict_data VALUES (..., '0', '待审批', 'jw_access_status', ...);
-- INSERT INTO sys_dict_data VALUES (..., '1', '已通过', 'jw_access_status', ...);
-- INSERT INTO sys_dict_data VALUES (..., '2', '已拒绝', 'jw_access_status', ...);
-- INSERT INTO sys_dict_data VALUES (..., '3', '已撤销', 'jw_access_status', ...);
-- INSERT INTO sys_dict_data VALUES (..., '4', '已过期', 'jw_access_status', ...);

-- ----------------------------
-- Quartz 定时任务：每日凌晨2点过期检查
-- ---------------------------
INSERT INTO sys_job (
  job_id, job_name, job_group, invoke_target,
  cron_expression, misfire_policy, concurrent, status,
  create_by, create_time, remark
) VALUES (
  100, '数据访问审批过期处理', 'DEFAULT', 'jwDataAccessExpiryTask.batchExpire',
  '0 0 2 * * ?', '3', '1', '0',
  'admin', sysdate(), '每日凌晨2点，将已过期的数据查看申请自动标记为已过期'
);
