CREATE TABLE user_permissions (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permissions VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, permissions)
);

-- Seed existing users with permissions based on their current role
INSERT INTO user_permissions (user_id, permissions)
SELECT u.id, rp.permission_name
FROM users u
JOIN role_permissions rp ON u.role = rp.role_name;

-- Grant CAN_MANAGE_USERS to any existing MANAGER
INSERT INTO user_permissions (user_id, permissions)
SELECT id, 'CAN_MANAGE_USERS'
FROM users
WHERE role = 'MANAGER';

-- We can now drop the generic RBAC mapping tables
DROP TABLE role_permissions;
DROP TABLE permissions;
