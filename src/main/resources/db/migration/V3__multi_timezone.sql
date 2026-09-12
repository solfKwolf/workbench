-- 多时区改造：时间列改存绝对时刻（UTC），用户偏好时区单独存 IANA 时区 ID
-- 1) TIMESTAMP -> TIMESTAMPTZ：
--    历史数据是本机（Asia/Shanghai, UTC+8）写入的墙钟时间，用 AT TIME ZONE 显式指定解读时区，
--    避免依赖迁移会话的 timezone 设置（JVM 设为 UTC 后会话时区是 UTC，隐式转换会差 8 小时）
ALTER TABLE sys_user
    ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'Asia/Shanghai',
    ALTER COLUMN updated_at TYPE timestamptz USING updated_at AT TIME ZONE 'Asia/Shanghai';

-- 2) 用户时区：存 IANA 时区 ID（如 America/New_York），它是"规则"而非偏移快照，
--    能正确处理夏令时；默认 UTC，注册时可由客户端覆盖
ALTER TABLE sys_user
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';

COMMENT ON COLUMN sys_user.timezone IS '用户偏好时区（IANA ID，如 Asia/Shanghai），仅用于展示层换算';

-- 种子用户是本机开发者，标记为上海时区
UPDATE sys_user SET timezone = 'Asia/Shanghai' WHERE username = 'admin';
