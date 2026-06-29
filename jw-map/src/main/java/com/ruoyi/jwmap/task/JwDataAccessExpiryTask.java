package com.ruoyi.jwmap.task;

import com.ruoyi.jwmap.service.IJwDataAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 数据访问审批 - 定时过期任务
 * 每日凌晨执行，将已过期的审批申请标记为 status='4'
 * 在石英调度中配置：调用 jwDataAccessExpiryTask.batchExpire()
 */
@Component("jwDataAccessExpiryTask")
public class JwDataAccessExpiryTask {

    private static final Logger log = LoggerFactory.getLogger(JwDataAccessExpiryTask.class);

    @Autowired
    private IJwDataAccessService accessService;

    public void batchExpire() {
        int count = accessService.batchExpire();
        if (count > 0) {
            log.info("已过期 {} 条申请", count);
        }
    }
}
