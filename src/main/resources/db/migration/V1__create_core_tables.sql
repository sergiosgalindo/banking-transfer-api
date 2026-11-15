CREATE TABLE event (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    venue VARCHAR(200) NOT NULL,
    starts_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    category VARCHAR(80) NOT NULL,
    description VARCHAR(2000) NOT NULL
);

CREATE TABLE ticket_type (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES event (id),
    name VARCHAR(120) NOT NULL,
    regular_price NUMERIC(12, 2) NOT NULL,
    presale_price NUMERIC(12, 2),
    presale_ends_at TIMESTAMP(6) WITH TIME ZONE,
    capacity INTEGER NOT NULL,
    sold INTEGER NOT NULL,
    reserved INTEGER NOT NULL,
    CONSTRAINT ticket_type_capacity_ok CHECK (
        capacity >= 0
        AND sold >= 0
        AND reserved >= 0
        AND sold + reserved <= capacity
    )
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    ref_number INTEGER NOT NULL,
    event_id UUID NOT NULL REFERENCES event (id),
    ticket_type_id UUID NOT NULL REFERENCES ticket_type (id),
    quantity INTEGER NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    customer_phone VARCHAR(32) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(40) NOT NULL,
    codi_qr_id VARCHAR(120),
    payment_link_id VARCHAR(120),
    payment_link_url VARCHAR(1000),
    tracking_key VARCHAR(120),
    expires_at TIMESTAMP(6) WITH TIME ZONE,
    access_code VARCHAR(120),
    checked_in_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    paid_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT orders_ref_number_unique UNIQUE (ref_number),
    CONSTRAINT orders_ref_number_digits CHECK (ref_number >= 0 AND ref_number <= 9999999),
    CONSTRAINT orders_quantity_positive CHECK (quantity >= 1)
);

CREATE TABLE processed_webhook (
    webhook_id VARCHAR(200) PRIMARY KEY,
    transaction_id VARCHAR(200) NOT NULL
);

CREATE INDEX idx_event_starts_at ON event (starts_at);
CREATE INDEX idx_ticket_type_event_id ON ticket_type (event_id);
CREATE INDEX idx_orders_event_id ON orders (event_id);
