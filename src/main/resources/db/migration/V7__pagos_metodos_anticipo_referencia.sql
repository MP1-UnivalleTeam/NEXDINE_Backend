ALTER TABLE pedidos
    ADD COLUMN IF NOT EXISTS pago_referencia VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pago_anticipo BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS pago_fecha_actualizacion TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_pedidos_pago_estado
    ON pedidos (pago_estado, fecha_creacion DESC);
