CREATE TABLE IF NOT EXISTS m_inventory_items (
    id BIGSERIAL PRIMARY KEY,
    item_name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    min_quantity INTEGER NOT NULL DEFAULT 0,
    manufacturer VARCHAR(255),
    model_number VARCHAR(255),
    jan_code VARCHAR(20),
    purchase_url TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    memo TEXT,
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_m_inventory_items_status
        CHECK (status IN ('active', 'disabled', 'deleted')),
    CONSTRAINT chk_m_inventory_items_min_quantity
        CHECK (min_quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_m_inventory_items_category
    ON m_inventory_items (category);

CREATE INDEX IF NOT EXISTS idx_m_inventory_items_item_name
    ON m_inventory_items (item_name);

CREATE INDEX IF NOT EXISTS idx_m_inventory_items_jan_code
    ON m_inventory_items (jan_code);

CREATE INDEX IF NOT EXISTS idx_m_inventory_items_status
    ON m_inventory_items (status);

CREATE INDEX IF NOT EXISTS idx_m_inventory_items_updated_dt
    ON m_inventory_items (updated_dt);

CREATE OR REPLACE TRIGGER update_m_inventory_items_updated_at
    BEFORE UPDATE ON "m_inventory_items"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
