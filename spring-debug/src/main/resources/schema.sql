-- H2 数据库初始化脚本

DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入测试数据
INSERT INTO users (username, email) VALUES ('张三', 'zhangsan@example.com');
INSERT INTO users (username, email) VALUES ('李四', 'lisi@example.com');
