CREATE TABLE IF NOT EXISTS `jw_ai_analysis` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `analysis_type` VARCHAR(20) NOT NULL COMMENT '分析类型：branch-网点分析/grid-网格分析/quadrant-四象限分析',
    `entity_key` VARCHAR(200) NOT NULL COMMENT '实体标识：branch_{branchId}_{year} / grid_{gridCode} / quadrant_{city}_{year}',
    `city` VARCHAR(50) DEFAULT NULL COMMENT '所属城市',
    `content` MEDIUMTEXT NOT NULL COMMENT 'AI分析结果（Markdown格式）',
    `satisfied` TINYINT(1) DEFAULT NULL COMMENT '用户满意度：1-满意 0-不满意 NULL-未评价',
    `expired` TINYINT(1) DEFAULT 0 COMMENT '数据过期标记：0-未过期 1-已过期（数据重新计算后）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_type_entity` (`analysis_type`, `entity_key`),
    INDEX `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析结果持久化表';
