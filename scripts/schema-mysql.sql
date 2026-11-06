-- 与用户最新 demo 库一致：表名全小写。列名保持导出时的大小写（userId / user_id）。
-- mysql -uroot -p < scripts/schema-mysql.sql
-- 然后：mvn -pl queryengine-boot -am spring-boot:run -Dspring-boot.run.profiles=mysql

CREATE DATABASE IF NOT EXISTS demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE demo;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `access`;
DROP TABLE IF EXISTS `book_menu_tip`;
DROP TABLE IF EXISTS `book_menu`;
DROP TABLE IF EXISTS `book`;
DROP TABLE IF EXISTS `type`;
DROP TABLE IF EXISTS `comment`;
DROP TABLE IF EXISTS `moment`;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` bigint NOT NULL,
  `name` varchar(64) NOT NULL,
  `sex` int NULL DEFAULT NULL,
  `tag` varchar(64) NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `moment` (
  `id` bigint NOT NULL,
  `userId` bigint NULL DEFAULT NULL,
  `content` varchar(255) NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `comment` (
  `id` bigint NOT NULL,
  `momentId` bigint NULL DEFAULT NULL,
  `userId` bigint NULL DEFAULT NULL,
  `content` varchar(255) NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `book` (
  `id` bigint NOT NULL,
  `user_id` bigint NULL DEFAULT NULL,
  `book_name` varchar(255) NULL DEFAULT NULL,
  `book_desc` varchar(255) NULL DEFAULT NULL,
  `type_id` bigint NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `book_menu` (
  `id` bigint NOT NULL,
  `user_id` bigint NULL DEFAULT NULL,
  `menu_name` varchar(255) NULL DEFAULT NULL,
  `book_id` bigint NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `book_menu_tip` (
  `id` bigint NOT NULL,
  `book_menu_id` bigint DEFAULT NULL,
  `tip_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `type` (
  `id` bigint NOT NULL,
  `user_id` bigint NULL DEFAULT NULL,
  `type_name` varchar(255) NULL DEFAULT NULL,
  `comment_id` bigint NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `user` VALUES (10001, 'Blank', 0, NULL);
INSERT INTO `user` VALUES (38710, 'Tommy', 0, 'student');
INSERT INTO `user` VALUES (70793, 'Lily', 1, 'teacher');
INSERT INTO `user` VALUES (82001, 'Tom', 0, 'student');

INSERT INTO `moment` VALUES (1, 38710, 'hello');
INSERT INTO `moment` VALUES (2, 38710, 'world');
INSERT INTO `moment` VALUES (3, 82001, 'other');

INSERT INTO `comment` VALUES (10, 1, 82001, 'nice');
INSERT INTO `comment` VALUES (11, 1, 70793, 'good');
INSERT INTO `comment` VALUES (12, 2, 82001, 'ok');
INSERT INTO `comment` VALUES (13, 1, 38710, 'aoisa');

INSERT INTO `book` VALUES (1, 70793, '一千零一夜', '一千零一夜的描述', 3);
INSERT INTO `book` VALUES (2, 38710, '安徒生童话', '安徒生童话的描述', 1);

INSERT INTO `book_menu` VALUES (1, 38710, '章节一', 2);
INSERT INTO `book_menu` VALUES (2, 38710, '章节二', 2);
INSERT INTO `book_menu` VALUES (3, 70793, '章节一', 1);

INSERT INTO `book_menu_tip` VALUES (1, 1, '章节一提示');
INSERT INTO `book_menu_tip` VALUES (2, 2, '章节二提示');

INSERT INTO `type` VALUES (1, 38710, '类型名称', 13);
INSERT INTO `type` VALUES (2, 38710, '新增', 13);
INSERT INTO `type` VALUES (3, 70793, '删除', 11);

CREATE TABLE `access` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `alias` varchar(64) DEFAULT NULL,
  `schema_name` varchar(64) DEFAULT NULL,
  `enabled` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `access` (`name`, `alias`, `schema_name`, `enabled`) VALUES
 ('User', 'user', NULL, 1),
 ('Moment', 'moment', NULL, 1),
 ('Comment', 'comment', NULL, 1),
 ('Book', 'book', NULL, 1),
 ('Book_menu', 'book_menu', NULL, 1),
 ('Book_menu_tip', 'book_menu_tip', NULL, 1),
 ('Type', 'type', NULL, 1);

SET FOREIGN_KEY_CHECKS = 1;
