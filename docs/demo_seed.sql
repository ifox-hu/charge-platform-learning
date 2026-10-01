-- OPTIONAL local-development data. Do not execute on an existing/production database.
-- Run after V1 -> V4. This file is outside docs/db, so Docker never auto-imports it.
-- Demo credentials: demo_admin / 123456 and demo_operator / 123456.
-- Password hashes are freshly generated BCrypt examples, not copied from the backup.
-- Change/delete these accounts before exposing the environment to others.

START TRANSACTION;

INSERT INTO sys_user (display_name, enabled, password, role, username)
SELECT '演示管理员', b'1', '$2a$10$Y4ypcSEGM5xEJlBW9xWc0eiIamNIjBsNtehxVlGWtP.zOQPN9LQH2', 'ADMIN', 'demo_admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'demo_admin');

INSERT INTO sys_user (display_name, enabled, password, role, username)
SELECT '演示运营员', b'1', '$2a$10$Y4ypcSEGM5xEJlBW9xWc0eiIamNIjBsNtehxVlGWtP.zOQPN9LQH2', 'OPERATOR', 'demo_operator'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'demo_operator');

-- Keep existing demo rows correct when this idempotent seed is re-run.
UPDATE sys_user SET display_name = '演示管理员' WHERE username = 'demo_admin';
UPDATE sys_user SET display_name = '演示运营员' WHERE username = 'demo_operator';

INSERT INTO station (name, address, description, status, latitude, longitude, coordinate_type)
SELECT '开发演示站', '示例地址（请修改）', '仅供本地功能演示', 'OPERATING', NULL, NULL, 'GCJ02'
WHERE NOT EXISTS (SELECT 1 FROM station WHERE name = '开发演示站');

SET @demo_station_id = (SELECT id FROM station WHERE name = '开发演示站' ORDER BY id LIMIT 1);

INSERT INTO charger (code, name, station_id, status)
SELECT 'PILE-001', '1 号模拟充电桩', @demo_station_id, 'ONLINE'
WHERE NOT EXISTS (SELECT 1 FROM charger WHERE code = 'PILE-001');
INSERT INTO charger (code, name, station_id, status)
SELECT 'PILE-002', '2 号模拟充电桩', @demo_station_id, 'ONLINE'
WHERE NOT EXISTS (SELECT 1 FROM charger WHERE code = 'PILE-002');
INSERT INTO charger (code, name, station_id, status)
SELECT 'PILE-003', '3 号模拟充电桩', @demo_station_id, 'ONLINE'
WHERE NOT EXISTS (SELECT 1 FROM charger WHERE code = 'PILE-003');

INSERT INTO connector (charger_id, code, name, rated_power, status)
SELECT c.id, x.code, x.name, 120, 'IDLE'
FROM (
  SELECT 'PILE-001' AS charger_code, 'GUN-001-A' AS code, 'A 枪' AS name UNION ALL
  SELECT 'PILE-001', 'GUN-001-B', 'B 枪' UNION ALL
  SELECT 'PILE-002', 'GUN-002-A', 'A 枪' UNION ALL
  SELECT 'PILE-002', 'GUN-002-B', 'B 枪' UNION ALL
  SELECT 'PILE-003', 'GUN-003-A', 'A 枪' UNION ALL
  SELECT 'PILE-003', 'GUN-003-B', 'B 枪' UNION ALL
  SELECT 'PILE-003', 'GUN-003-C', 'C 枪' UNION ALL
  SELECT 'PILE-003', 'GUN-003-D', 'D 枪'
) AS x
JOIN charger AS c ON c.code = x.charger_code
WHERE c.station_id = @demo_station_id
  AND NOT EXISTS (SELECT 1 FROM connector AS existing WHERE existing.code = x.code);

INSERT INTO price_period (station_id, start_time, end_time, electricity_price, service_price)
SELECT @demo_station_id, '00:00:00', '23:59:59', 0.8000, 0.4000
WHERE NOT EXISTS (SELECT 1 FROM price_period WHERE station_id = @demo_station_id);

COMMIT;
