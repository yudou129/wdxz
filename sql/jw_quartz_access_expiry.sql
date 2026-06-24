-- 数据访问审批过期定时任务
-- 每日凌晨2点执行，将已过期的审批申请标记为 status='4'
-- 在系统管理 → 定时任务中也可手动管理此任务

INSERT INTO sys_job (
  job_id, job_name, job_group, invoke_target,
  cron_expression, misfire_policy, concurrent, status,
  create_by, create_time, remark
) VALUES (
  100, '数据访问审批过期处理', 'DEFAULT', 'jwDataAccessExpiryTask.batchExpire',
  '0 0 2 * * ?', '3', '1', '0',
  'admin', sysdate(), '每日凌晨2点，将已过期的数据查看申请自动标记为已过期'
);
