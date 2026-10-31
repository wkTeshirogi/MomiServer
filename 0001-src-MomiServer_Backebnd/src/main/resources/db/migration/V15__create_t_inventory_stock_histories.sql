CREATE TABLE IF NOT EXISTS t_inventory_stock_histories (
    id BIGSERIAL PRIMARY KEY,
    inventory_stock_id BIGINT NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    quantity_delta INTEGER NOT NULL DEFAULT 0,
    quantity_before INTEGER NOT NULL DEFAULT 0,
    quantity_after INTEGER NOT NULL DEFAULT 0,
    inventory_status_before VARCHAR(50),
    inventory_status_after VARCHAR(50),
    reason VARCHAR(255),
    memo TEXT,
    changed_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_t_inventory_stock_histories_stock
        FOREIGN KEY (inventory_stock_id)
            REFERENCES t_inventory_stocks (id),
    CONSTRAINT chk_t_inventory_stock_histories_change_type
        CHECK (change_type IN ('inbound', 'outbound', 'stocktake', 'discard', 'delete')),
    CONSTRAINT chk_t_inventory_stock_histories_quantity_before
        CHECK (quantity_before >= 0),
    CONSTRAINT chk_t_inventory_stock_histories_quantity_after
        CHECK (quantity_after >= 0)
);

CREATE INDEX IF NOT EXISTS idx_t_inventory_stock_histories_stock_id
    ON t_inventory_stock_histories (inventory_stock_id);

CREATE INDEX IF NOT EXISTS idx_t_inventory_stock_histories_change_type
    ON t_inventory_stock_histories (change_type);

CREATE INDEX IF NOT EXISTS idx_t_inventory_stock_histories_changed_dt
    ON t_inventory_stock_histories (changed_dt);
