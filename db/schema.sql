-- todo 用之前执行创建
CREATE DATABASE IF NOT EXISTS drone_location DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE drone_location;

DROP TABLE IF EXISTS waypoint;
DROP TABLE IF EXISTS task;

CREATE TABLE `task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `task_name` VARCHAR(255) NOT NULL COMMENT '任务名称',
  `drone_sn` VARCHAR(100) NOT NULL COMMENT '无人机SN码',
  `flight_height` DECIMAL(10,2) DEFAULT 100.00 COMMENT '飞行高度(米)',
  `flight_speed` DECIMAL(10,2) DEFAULT 10.00 COMMENT '飞行速度(m/s)',
  `route_type` VARCHAR(50) DEFAULT 'MANUAL' COMMENT '航线类型: MANUAL-手动, AUTO-自动生成',
  `total_waypoints` INT DEFAULT 0 COMMENT '航点总数',
  `route_data` TEXT COMMENT '航线JSON数据',
  `status` VARCHAR(20) DEFAULT 'SAVED' COMMENT '状态: SAVED-已保存, EXECUTING-执行中, COMPLETED-已完成',
  `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` INT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_task_name` (`task_name`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='无人机航线任务表';

CREATE TABLE `waypoint` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '航点ID',
  `task_id` BIGINT NOT NULL COMMENT '任务ID',
  `sequence_num` INT NOT NULL COMMENT '序号',
  `latitude` DECIMAL(10,8) NOT NULL COMMENT '纬度',
  `longitude` DECIMAL(11,8) NOT NULL COMMENT '经度',
  `altitude` DECIMAL(10,2) COMMENT '高度(米)',
  `speed` DECIMAL(10,2) COMMENT '速度(m/s)',
  `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` INT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_seq` (`task_id`, `sequence_num`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_deleted` (`deleted`),
  CONSTRAINT `fk_waypoint_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='航点表';
