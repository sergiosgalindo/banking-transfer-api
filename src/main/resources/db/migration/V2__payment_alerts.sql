CREATE TABLE payment_alert (
    id UUID PRIMARY KEY,
    order_id UUID,
    webhook_id VARCHAR(200),
    reason VARCHAR(80) NOT NULL,
    expected_amount NUMERIC(12, 2),
    received_amount NUMERIC(12, 2),
    tracking_key VARCHAR(200),
    detail VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_payment_alert_created_at ON payment_alert (created_at);
