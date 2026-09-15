-- Run once when upgrading an existing LocalHub database.
CREATE TABLE IF NOT EXISTS `tb_consumer_message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `consumer_name` varchar(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `event_id` varchar(36) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `event_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `consumed_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consumer_event` (`consumer_name`, `event_id`),
  KEY `idx_consumer_message_time` (`consumed_time`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci;
