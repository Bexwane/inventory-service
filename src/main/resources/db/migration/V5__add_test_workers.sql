-- Create the VIEWER_ONLY role mapping
INSERT INTO role_permissions (role_name, permission_name) VALUES
('VIEWER_ONLY', 'CAN_MANAGE_INVENTORY');

-- (We don't need to insert SUSPENDED here, because if a role is missing from role_permissions, 
--  it simply gets 0 permissions, which perfectly enforces a complete lockdown).
