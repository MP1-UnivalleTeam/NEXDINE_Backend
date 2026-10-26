ALTER TABLE pedidos
    ADD COLUMN IF NOT EXISTS impuestos NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tasa_impuesto NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS mesa_id UUID,
    ADD COLUMN IF NOT EXISTS sucursal_id UUID;

CREATE INDEX IF NOT EXISTS idx_pedidos_sucursal_estado_fecha
    ON pedidos (sucursal_id, estado, fecha_creacion DESC);

CREATE INDEX IF NOT EXISTS idx_pedidos_mesa_estado_fecha
    ON pedidos (mesa_id, estado, fecha_creacion DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_pedidos_mesa' AND conrelid = 'pedidos'::regclass
    ) THEN
        ALTER TABLE pedidos
            ADD CONSTRAINT fk_pedidos_mesa FOREIGN KEY (mesa_id) REFERENCES mesas (id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_pedidos_sucursal' AND conrelid = 'pedidos'::regclass
    ) THEN
        ALTER TABLE pedidos
            ADD CONSTRAINT fk_pedidos_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursales (id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS productos_favoritos (
    id UUID PRIMARY KEY,
    cliente_id UUID NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    producto_id UUID NOT NULL REFERENCES productos (id) ON DELETE CASCADE,
    CONSTRAINT uk_producto_favorito_cliente_producto UNIQUE (cliente_id, producto_id)
);

CREATE INDEX IF NOT EXISTS idx_productos_favoritos_cliente
    ON productos_favoritos (cliente_id);
