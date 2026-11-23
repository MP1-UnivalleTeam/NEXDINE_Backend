ALTER TABLE pedidos
    ADD COLUMN IF NOT EXISTS fecha_vencimiento TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_pedidos_fecha_vencimiento
    ON pedidos (fecha_vencimiento) WHERE fecha_vencimiento IS NOT NULL;
