-- 种子用户：admin / 123456（密码为 BCrypt 哈希）
-- 哈希由 BCryptHashGeneratorTest 生成（BCrypt 随机盐，任何一次合法哈希均可通过 matches 校验）
INSERT INTO sys_user (username, password, email, enabled)
VALUES ('admin', '$2a$10$l9Ajnj5MQVRQbcJiyT5CN.cgWg0hjzvFemLbaarrcNQamJ13NAKxu', 'admin@workbench.com', TRUE);
