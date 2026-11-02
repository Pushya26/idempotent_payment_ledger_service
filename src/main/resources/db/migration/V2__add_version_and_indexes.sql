-- Optimistic locking support
ALTER TABLE payment_intents ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

-- Performance indexes for listing and filtering
CREATE INDEX idx_payment_intents_status ON payment_intents(status);
CREATE INDEX idx_payment_intents_created_at ON payment_intents(created_at DESC);
