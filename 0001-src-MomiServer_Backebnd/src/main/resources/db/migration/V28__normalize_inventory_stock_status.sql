ALTER TABLE t_inventory_stocks
    DROP CONSTRAINT IF EXISTS chk_t_inventory_stocks_status;

UPDATE t_inventory_stocks
SET inventory_status = CASE inventory_status
    WHEN '在庫あり' THEN 'available'
    WHEN '在庫注意' THEN 'low'
    WHEN '在庫なし' THEN 'out_of_stock'
    WHEN '削除' THEN 'deleted'
    ELSE inventory_status
END
WHERE inventory_status IN ('在庫あり', '在庫注意', '在庫なし', '削除');

ALTER TABLE t_inventory_stocks
    ALTER COLUMN inventory_status SET DEFAULT 'available';

ALTER TABLE t_inventory_stocks
    ADD CONSTRAINT chk_t_inventory_stocks_status
        CHECK (inventory_status IN ('available', 'low', 'out_of_stock', 'deleted'));

ALTER TABLE t_inventory_stocks
    DROP CONSTRAINT IF EXISTS chk_t_inventory_stocks_minimum_quantity;

ALTER TABLE t_inventory_stocks
    DROP COLUMN IF EXISTS minimum_quantity;
