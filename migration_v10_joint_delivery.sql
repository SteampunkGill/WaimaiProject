-- ============================================================
-- Migration v10: Multi-Rider Joint Delivery (Refactor)
-- Drops old delivery_batch/subtask, creates new joint system
-- ============================================================
USE waimai;

DROP TABLE IF EXISTS `delivery_sub_task`;
DROP TABLE IF EXISTS `delivery_batch`;

CREATE TABLE IF NOT EXISTS `joint_delivery_group` (
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT,
    `order_id`              BIGINT        NOT NULL COMMENT '订单ID',
    `group_no`              VARCHAR(32)   NOT NULL COMMENT '联合配送编号',
    `required_rider_count`  INT           NOT NULL DEFAULT 2 COMMENT '所需骑手数',
    `joined_rider_count`    INT           NOT NULL DEFAULT 0 COMMENT '已加入骑手数',
    `completed_rider_count` INT           NOT NULL DEFAULT 0 COMMENT '已完成骑手数',
    `status`                VARCHAR(24)   NOT NULL DEFAULT 'RECRUITING' COMMENT 'RECRUITING/READY/DELIVERING/COMPLETED/CANCELLED',
    `delivery_fee_total`    DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '总配送费',
    `create_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_no` (`group_no`),
    UNIQUE KEY `uk_order` (`order_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='联合配送组表';

CREATE TABLE IF NOT EXISTS `joint_delivery_member` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `group_id`        BIGINT        NOT NULL COMMENT '联合配送组ID',
    `rider_id`        BIGINT        NOT NULL COMMENT '骑手ID',
    `order_id`        BIGINT        NOT NULL COMMENT '订单ID(冗余便于查询)',
    `status`          VARCHAR(24)   NOT NULL DEFAULT 'INVITED' COMMENT 'INVITED/JOINED/PICKED_UP/COMPLETED/CANCELLED',
    `earnings`        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '该骑手收益',
    `join_time`       DATETIME      DEFAULT NULL COMMENT '加入时间',
    `pickup_time`     DATETIME      DEFAULT NULL COMMENT '取餐时间',
    `complete_time`   DATETIME      DEFAULT NULL COMMENT '完成时间',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_rider` (`group_id`, `rider_id`),
    KEY `idx_rider_status` (`rider_id`, `status`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='联合配送成员表';

SET @s = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='waimai' AND TABLE_NAME='`order`' AND COLUMN_NAME='is_joint_delivery') = 0,
    'ALTER TABLE `order` ADD COLUMN is_joint_delivery TINYINT DEFAULT 0 COMMENT ''是否联合配送: 0否 1是'' AFTER is_overtime',
    'SELECT 1'
);
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Fix rider_income unique key: allow multiple riders to earn from same joint order
SET @drop_uk = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA='waimai' AND TABLE_NAME='rider_income' AND INDEX_NAME='uk_order') > 0,
    'ALTER TABLE rider_income DROP INDEX uk_order',
    'SELECT 1'
);
PREPARE stmt FROM @drop_uk; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_uk = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA='waimai' AND TABLE_NAME='rider_income' AND INDEX_NAME='uk_rider_order') = 0,
    'ALTER TABLE rider_income ADD UNIQUE KEY uk_rider_order (rider_id, order_id)',
    'SELECT 1'
);
PREPARE stmt FROM @add_uk; EXECUTE stmt; DEALLOCATE PREPARE stmt;
