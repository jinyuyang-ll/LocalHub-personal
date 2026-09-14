CREATE TABLE IF NOT EXISTS `tb_order_release` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) NOT NULL,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `voucher_id` bigint(20) UNSIGNED NOT NULL,
  `reason` varchar(128) NOT NULL,
  `redis_released` tinyint(1) NOT NULL DEFAULT 0,
  `released_time` timestamp NULL DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_release_order` (`order_id`),
  KEY `idx_order_release_pending` (`redis_released`, `create_time`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci;
