INSERT INTO sys_user (display_name, enabled, password, role, username)
SELECT '演示管理员', b'1', '$2a$10$Y4ypcSEGM5xEJlBW9xWc0eiIamNIjBsNtehxVlGWtP.zOQPN9LQH2', 'ADMIN', 'demo_admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'demo_admin');
INSERT INTO sys_user (display_name, enabled, password, role, username)
SELECT '演示运营员', b'1', '$2a$10$Y4ypcSEGM5xEJlBW9xWc0eiIamNIjBsNtehxVlGWtP.zOQPN9LQH2', 'OPERATOR', 'demo_operator'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'demo_operator');
