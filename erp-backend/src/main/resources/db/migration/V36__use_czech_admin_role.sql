UPDATE system_users
SET role_name = 'Administrátor'
WHERE username = 'admin';

DELETE FROM role_module_permissions
WHERE role_id = '25000000-0000-0000-0000-000000000004'
  AND NOT EXISTS (
      SELECT 1
      FROM system_users
      WHERE role_name = 'Administrator'
  );

DELETE FROM role_definitions
WHERE id = '25000000-0000-0000-0000-000000000004'
  AND NOT EXISTS (
      SELECT 1
      FROM system_users
      WHERE role_name = 'Administrator'
  );