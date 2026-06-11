-- The master list of all possible actions
CREATE TABLE permissions (
    name VARCHAR(50) PRIMARY KEY,
    description VARCHAR(255)
);

-- Maps a Role to a Permission
CREATE TABLE role_permissions (
    role_name VARCHAR(50) NOT NULL,
    permission_name VARCHAR(50) NOT NULL REFERENCES permissions(name),
    PRIMARY KEY (role_name, permission_name)
);

-- 1. Seed Permissions
INSERT INTO permissions (name, description) VALUES
('CAN_PICK', 'Allows worker to reserve, confirm, and release picks'),
('CAN_PUTAWAY', 'Allows worker to receive and putaway stock'),
('CAN_MANAGE_INVENTORY', 'Allows user to view global inventory and activity logs');

-- 2. Map existing roles to permissions
-- MANAGER gets everything
INSERT INTO role_permissions (role_name, permission_name) VALUES
('MANAGER', 'CAN_PICK'),
('MANAGER', 'CAN_PUTAWAY'),
('MANAGER', 'CAN_MANAGE_INVENTORY');

-- SUPERVISOR gets everything
INSERT INTO role_permissions (role_name, permission_name) VALUES
('SUPERVISOR', 'CAN_PICK'),
('SUPERVISOR', 'CAN_PUTAWAY'),
('SUPERVISOR', 'CAN_MANAGE_INVENTORY');

-- WORKER gets everything (for existing workers like worker2)
INSERT INTO role_permissions (role_name, permission_name) VALUES
('WORKER', 'CAN_PICK'),
('WORKER', 'CAN_PUTAWAY'),
('WORKER', 'CAN_MANAGE_INVENTORY');

-- 3. Create the restrictive PICKER_ONLY role
INSERT INTO role_permissions (role_name, permission_name) VALUES
('PICKER_ONLY', 'CAN_PICK'),
('PICKER_ONLY', 'CAN_MANAGE_INVENTORY');

-- 4. Update worker1 to be a PICKER_ONLY
UPDATE users SET role = 'PICKER_ONLY' WHERE username = 'worker1';
