-- Apply once to the existing charge_platform database before deploying the order archive changes.
-- Existing orders are treated as test orders because the current simulator creates all orders.
ALTER TABLE charge_order
    ADD COLUMN test_order TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN archived TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN archived_at DATETIME NULL;

CREATE INDEX idx_charge_order_status_archived ON charge_order (status, archived);
CREATE INDEX idx_charge_order_test_archived ON charge_order (test_order, archived);
