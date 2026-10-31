UPDATE t_inventory_stocks AS stock
SET inventory_status = CASE
    WHEN stock.quantity = 0 THEN 'out_of_stock'
    WHEN stock.quantity <= COALESCE(item.min_quantity, 0) THEN 'low'
    ELSE 'available'
END
FROM m_inventory_items AS item
WHERE stock.inventory_item_id = item.id
  AND stock.inventory_status <> 'deleted'
  AND stock.inventory_status IS DISTINCT FROM CASE
      WHEN stock.quantity = 0 THEN 'out_of_stock'
      WHEN stock.quantity <= COALESCE(item.min_quantity, 0) THEN 'low'
      ELSE 'available'
  END;
