CREATE SCHEMA IF NOT EXISTS demo;
CREATE SCHEMA IF NOT EXISTS demo1;

DROP TABLE IF EXISTS demo.`access`;
DROP TABLE IF EXISTS demo.`book_menu_tip`;
DROP TABLE IF EXISTS demo.`book_menu`;
DROP TABLE IF EXISTS demo.`book`;
DROP TABLE IF EXISTS demo.`type`;
DROP TABLE IF EXISTS demo.`comment`;
DROP TABLE IF EXISTS demo.`moment`;
DROP TABLE IF EXISTS demo.`user`;

CREATE TABLE demo.`user` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `name` VARCHAR(64) NOT NULL,
  `sex` INT,
  `tag` VARCHAR(64)
);

CREATE TABLE demo.`moment` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `userId` BIGINT,
  `content` VARCHAR(255)
);

CREATE TABLE demo.`comment` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `momentId` BIGINT,
  `userId` BIGINT,
  `content` VARCHAR(255)
);

CREATE TABLE demo.`book` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `user_id` BIGINT,
  `book_name` VARCHAR(255),
  `book_desc` VARCHAR(255),
  `type_id` BIGINT
);

CREATE TABLE demo.`book_menu` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `user_id` BIGINT,
  `menu_name` VARCHAR(255),
  `book_id` BIGINT
);

CREATE TABLE demo.`book_menu_tip` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `book_menu_id` BIGINT,
  `tip_name` VARCHAR(255)
);

CREATE TABLE demo.`type` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `user_id` BIGINT,
  `type_name` VARCHAR(255),
  `comment_id` BIGINT
);

INSERT INTO demo.`user` (`id`, `name`, `sex`, `tag`) VALUES
 (10001, 'Blank', 0, NULL),
 (38710, 'Tommy', 0, 'student'),
 (70793, 'Lily', 1, 'teacher'),
 (82001, 'Tom', 0, 'student');

INSERT INTO demo.`moment` (`id`, `userId`, `content`) VALUES
 (1, 38710, 'hello'),
 (2, 38710, 'world'),
 (3, 82001, 'other');

INSERT INTO demo.`comment` (`id`, `momentId`, `userId`, `content`) VALUES
 (10, 1, 82001, 'nice'),
 (11, 1, 70793, 'good'),
 (12, 2, 82001, 'ok'),
 (13, 1, 38710, 'aoisa');

INSERT INTO demo.`book` (`id`, `user_id`, `book_name`, `book_desc`, `type_id`) VALUES
 (1, 70793, '一千零一夜', '一千零一夜的描述', 3),
 (2, 38710, '安徒生童话', '安徒生童话的描述', 1);

INSERT INTO demo.`book_menu` (`id`, `user_id`, `menu_name`, `book_id`) VALUES
 (1, 38710, '章节一', 2),
 (2, 38710, '章节二', 2),
 (3, 70793, '章节一', 1);

INSERT INTO demo.`book_menu_tip` (`id`, `book_menu_id`, `tip_name`) VALUES
 (1, 1, '章节一提示'),
 (2, 2, '章节二提示');

INSERT INTO demo.`type` (`id`, `user_id`, `type_name`, `comment_id`) VALUES
 (1, 38710, '类型名称', 13),
 (2, 38710, '新增', 13),
 (3, 70793, '删除', 11);

CREATE TABLE demo.`access` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(64) NOT NULL,
  `alias` VARCHAR(64),
  `schema_name` VARCHAR(64),
  `enabled` INT NOT NULL
);

INSERT INTO demo.`access` (`name`, `alias`, `schema_name`, `enabled`) VALUES
 ('User', 'user', NULL, 1),
 ('Moment', 'moment', NULL, 1),
 ('Comment', 'comment', NULL, 1),
 ('Book', 'book', NULL, 1),
 ('Book_menu', 'book_menu', NULL, 1),
 ('Book_menu_tip', 'book_menu_tip', NULL, 1),
 ('Type', 'type', NULL, 1);

DROP TABLE IF EXISTS demo1.`access`;
DROP TABLE IF EXISTS demo1.`user`;

CREATE TABLE demo1.`user` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `name` VARCHAR(64) NOT NULL,
  `sex` INT,
  `tag` VARCHAR(64)
);

INSERT INTO demo1.`user` (`id`, `name`, `sex`, `tag`) VALUES
 (38710, 'Tommy', 0, 'student');

CREATE TABLE demo1.`access` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(64) NOT NULL,
  `alias` VARCHAR(64),
  `schema_name` VARCHAR(64),
  `enabled` INT NOT NULL
);

INSERT INTO demo1.`access` (`name`, `alias`, `schema_name`, `enabled`) VALUES
 ('User', 'user', NULL, 1),
 ('Moment', 'moment', NULL, 1),
 ('Comment', 'comment', NULL, 1),
 ('Book', 'book', NULL, 1),
 ('Book_menu', 'book_menu', NULL, 1),
 ('Type', 'type', NULL, 1);
