CREATE SCHEMA IF NOT EXISTS demo;
CREATE SCHEMA IF NOT EXISTS app_a;
CREATE SCHEMA IF NOT EXISTS app_b;

CREATE TABLE demo.`user` (
  `id` BIGINT PRIMARY KEY,
  `name` VARCHAR(64) NOT NULL,
  `sex` INT,
  `tag` VARCHAR(64)
);
CREATE TABLE app_a.`user` (
  `id` BIGINT PRIMARY KEY,
  `name` VARCHAR(64) NOT NULL,
  `sex` INT,
  `tag` VARCHAR(64)
);
CREATE TABLE app_b.`user` (
  `id` BIGINT PRIMARY KEY,
  `name` VARCHAR(64) NOT NULL,
  `sex` INT,
  `tag` VARCHAR(64)
);

INSERT INTO demo.`user` (`id`, `name`, `sex`, `tag`) VALUES
 (10001, 'Blank', 0, NULL),
 (38710, 'Tommy', 0, 'student'),
 (70793, 'Lily', 1, 'teacher'),
 (82001, 'Tom', 0, 'student');
INSERT INTO app_a.`user` (`id`, `name`, `sex`, `tag`) VALUES (1, 'alpha', 0, 'a');
INSERT INTO app_b.`user` (`id`, `name`, `sex`, `tag`) VALUES (1, 'beta', 0, 'b');

CREATE TABLE demo.`moment` (
  `id` BIGINT PRIMARY KEY,
  `userId` BIGINT,
  `content` VARCHAR(255)
);
INSERT INTO demo.`moment` (`id`, `userId`, `content`) VALUES
 (1, 38710, 'hello'),
 (2, 38710, 'world'),
 (3, 82001, 'other');

CREATE TABLE demo.`comment` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `momentId` BIGINT,
  `userId` BIGINT,
  `content` VARCHAR(255)
);
INSERT INTO demo.`comment` (`id`, `momentId`, `userId`, `content`) VALUES
 (10, 1, 82001, 'nice'),
 (11, 1, 70793, 'good'),
 (12, 2, 82001, 'ok'),
 (13, 1, 38710, 'aoisa');

CREATE TABLE demo.`book` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT,
  `book_name` VARCHAR(255),
  `book_desc` VARCHAR(255),
  `type_id` BIGINT
);
INSERT INTO demo.`book` (`id`, `user_id`, `book_name`, `book_desc`, `type_id`) VALUES
 (1, 70793, '一千零一夜', '一千零一夜的描述', 3),
 (2, 38710, '安徒生童话', '安徒生童话的描述', 1);

CREATE TABLE demo.`book_menu` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT,
  `menu_name` VARCHAR(255),
  `book_id` BIGINT
);
INSERT INTO demo.`book_menu` (`id`, `user_id`, `menu_name`, `book_id`) VALUES
 (1, 38710, '章节一', 2),
 (2, 38710, '章节二', 2),
 (3, 70793, '章节一', 1);

CREATE TABLE demo.`type` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT,
  `type_name` VARCHAR(255),
  `comment_id` BIGINT
);
INSERT INTO demo.`type` (`id`, `user_id`, `type_name`, `comment_id`) VALUES
 (1, 38710, '类型名称', 13),
 (2, 38710, '新增', 13),
 (3, 70793, '删除', 11);

CREATE TABLE demo.`book_menu_tip` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `book_menu_id` BIGINT,
  `tip_name` VARCHAR(255)
);
INSERT INTO demo.`book_menu_tip` (`id`, `book_menu_id`, `tip_name`) VALUES
 (1, 1, '章节一提示'),
 (2, 2, '章节二提示');

ALTER TABLE demo.`comment` ALTER COLUMN `id` RESTART WITH 100;
ALTER TABLE demo.`type` ALTER COLUMN `id` RESTART WITH 100;
ALTER TABLE demo.`book` ALTER COLUMN `id` RESTART WITH 100;
ALTER TABLE demo.`book_menu` ALTER COLUMN `id` RESTART WITH 100;
ALTER TABLE demo.`book_menu_tip` ALTER COLUMN `id` RESTART WITH 100;

CREATE TABLE demo.`node` (
  `id` BIGINT PRIMARY KEY,
  `parentId` BIGINT,
  `name` VARCHAR(64)
);
INSERT INTO demo.`node` (`id`, `parentId`, `name`) VALUES
 (1, NULL, 'n1'),
 (2, 1, 'n2'),
 (3, 2, 'n3'),
 (4, 3, 'n4'),
 (5, 4, 'n5'),
 (6, 5, 'n6'),
 (7, 6, 'n7'),
 (8, 7, 'n8'),
 (9, 8, 'n9'),
 (10, 9, 'n10'),
 (11, 10, 'n11'),
 (12, 11, 'n12'),
 (13, 12, 'n13');

CREATE TABLE demo.`todo` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `title` VARCHAR(128) NOT NULL,
  `done` INT
);
