CREATE TABLE IF NOT EXISTS t_inventory_stocks (
    id BIGSERIAL PRIMARY KEY,
    inventory_item_id BIGINT NOT NULL,
    storage_location VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    replenish_quantity INTEGER,
    inventory_status VARCHAR(50) NOT NULL DEFAULT 'available',
    expiration_date DATE,
    memo TEXT,
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_t_inventory_stocks_inventory_item
        FOREIGN KEY (inventory_item_id)
            REFERENCES m_inventory_items (id),
    CONSTRAINT chk_t_inventory_stocks_quantity
        CHECK (quantity >= 0),
    CONSTRAINT chk_t_inventory_stocks_replenish_quantity
        CHECK (replenish_quantity IS NULL OR replenish_quantity >= 0),
    CONSTRAINT chk_t_inventory_stocks_status
        CHECK (inventory_status IN ('available', 'low', 'out_of_stock', 'deleted'))
);

CREATE INDEX IF NOT EXISTS idx_t_inventory_stocks_item_id
    ON t_inventory_stocks (inventory_item_id);

CREATE INDEX IF NOT EXISTS idx_t_inventory_stocks_status
    ON t_inventory_stocks (inventory_status);

CREATE INDEX IF NOT EXISTS idx_t_inventory_stocks_storage_location
    ON t_inventory_stocks (storage_location);

CREATE INDEX IF NOT EXISTS idx_t_inventory_stocks_updated_dt
    ON t_inventory_stocks (updated_dt);

CREATE OR REPLACE TRIGGER update_t_inventory_stocks_updated_at
    BEFORE UPDATE ON "t_inventory_stocks"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
