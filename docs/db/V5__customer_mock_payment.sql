-- Apply once after V4. Existing operator orders do not require payment.
ALTER TABLE charge_order
    ADD COLUMN owner_username VARCHAR(50) NULL,
    ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN payment_method VARCHAR(20) NULL,
    ADD COLUMN payment_no VARCHAR(50) NULL,
    ADD COLUMN paid_at DATETIME(6) NULL,
    ADD INDEX idx_order_owner (owner_username, archived, id),
    ADD UNIQUE KEY uk_order_payment_no (payment_no);
