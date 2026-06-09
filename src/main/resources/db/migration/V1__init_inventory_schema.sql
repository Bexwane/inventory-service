-- Users table for auth
CREATE TABLE users (
    id UUID PRIMARY KEY,
    employee_id VARCHAR(50) UNIQUE,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Refactored Inventory table (Rich Entity)
CREATE TABLE inventory_items (
    id UUID PRIMARY KEY,
    sku VARCHAR(100) NOT NULL,
    location_id UUID NOT NULL,
    container_id UUID,
    qty_on_hand INTEGER NOT NULL CHECK (qty_on_hand >= 0),
    qty_reserved INTEGER NOT NULL DEFAULT 0 CHECK (qty_reserved >= 0),
    lot_number VARCHAR(50),
    expiry_date TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inventory_sku ON inventory_items(sku);
CREATE INDEX idx_inventory_location ON inventory_items(location_id, container_id);

-- Append-only Stock Movements table
CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    movement_type VARCHAR(20) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    from_location_id UUID,
    to_location_id UUID,
    container_id UUID,
    qty INTEGER NOT NULL,
    reference_id VARCHAR(100),
    reference_type VARCHAR(50),
    performed_by UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    synced_to_sap BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_movement_sku ON stock_movements(sku);
CREATE INDEX idx_movement_ref ON stock_movements(reference_id, reference_type);
CREATE INDEX idx_movement_sap_sync ON stock_movements(synced_to_sap, occurred_at);