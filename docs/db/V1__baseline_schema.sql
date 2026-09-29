-- MySQL 8 reference baseline extracted from the existing charge_platform backup.
-- Run only on a NEW, empty database. Then apply V2, V3 and V4 in order.
-- No user accounts, passwords, orders or production data are included.
-- Keep this file free of V2/V3/V4 columns so Docker init can run all four files once.

CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `display_name` varchar(50) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` varchar(20) NOT NULL,
  `username` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK51bvuyvihefoh4kp5syh2jpi4` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `station` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `charger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `station_id` bigint DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgmeu0uodc0c9gt0qfm86dykjl` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `connector` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `charger_id` bigint DEFAULT NULL,
  `code` varchar(255) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `rated_power` int DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr3bihlqby429ui5kwucx4pc3e` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `price_period` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `electricity_price` decimal(10,4) DEFAULT NULL,
  `end_time` time(6) DEFAULT NULL,
  `service_price` decimal(10,4) DEFAULT NULL,
  `start_time` time(6) DEFAULT NULL,
  `station_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `charge_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `connector_id` bigint DEFAULT NULL,
  `electricity_fee` decimal(12,2) DEFAULT NULL,
  `end_time` datetime(6) DEFAULT NULL,
  `energy_kwh` decimal(12,3) DEFAULT NULL,
  `order_no` varchar(255) NOT NULL,
  `plate_number` varchar(255) DEFAULT NULL,
  `service_fee` decimal(12,2) DEFAULT NULL,
  `start_time` datetime(6) DEFAULT NULL,
  `station_id` bigint DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `total_amount` decimal(12,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKrb7vf3p3m6q4aewnxtvw4lxgu` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
