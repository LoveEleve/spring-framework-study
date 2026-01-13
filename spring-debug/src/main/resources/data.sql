-- 插入测试用户数据
INSERT INTO users (username, email, phone, age, active) VALUES
('admin', 'admin@debug.com', '13800138000', 30, true),
('john_doe', 'john@debug.com', '13800138001', 25, true),
('jane_smith', 'jane@debug.com', '13800138002', 28, true),
('bob_wilson', 'bob@debug.com', '13800138003', 35, false),
('alice_brown', 'alice@debug.com', '13800138004', 22, true);

-- 插入测试产品数据
INSERT INTO products (name, description, price, category, in_stock) VALUES
('笔记本电脑', '高性能笔记本电脑，适合开发和办公', 5999.00, '电子产品', true),
('无线鼠标', '人体工学设计，无线连接', 199.00, '电子产品', true),
('机械键盘', '青轴机械键盘，手感出色', 399.00, '电子产品', true),
('显示器', '27寸 4K 显示器', 2999.00, '电子产品', true),
('咖啡杯', '陶瓷材质，保温效果好', 89.00, '生活用品', true),
('书籍：Spring实战', 'Spring框架学习必备', 79.00, '图书', true),
('耳机', '降噪耳机，音质出色', 899.00, '电子产品', false);

-- 插入用户产品关联数据
INSERT INTO user_products (user_id, product_id, quantity) VALUES
(1, 1, 1),  -- admin 购买了笔记本电脑
(1, 2, 2),  -- admin 购买了2个无线鼠标
(2, 3, 1),  -- john_doe 购买了机械键盘
(2, 6, 1),  -- john_doe 购买了Spring实战书籍
(3, 4, 1),  -- jane_smith 购买了显示器
(3, 5, 3),  -- jane_smith 购买了3个咖啡杯
(5, 7, 1);  -- alice_brown 购买了耳机

-- 插入审计日志数据
INSERT INTO audit_logs (entity_type, entity_id, operation, old_values, new_values, user_id) VALUES
('User', 1, 'CREATE', NULL, '{"username":"admin","email":"admin@debug.com"}', NULL),
('User', 2, 'CREATE', NULL, '{"username":"john_doe","email":"john@debug.com"}', NULL),
('Product', 1, 'CREATE', NULL, '{"name":"笔记本电脑","price":5999.00}', 1),
('User', 4, 'UPDATE', '{"active":true}', '{"active":false}', 1);