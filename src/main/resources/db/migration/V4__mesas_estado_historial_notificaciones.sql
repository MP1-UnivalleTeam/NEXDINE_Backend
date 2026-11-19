ALTER TABLE mesas
    ADD COLUMN IF NOT EXISTS estado VARCHAR(20) NOT NULL DEFAULT 'LIBRE';

CREATE TABLE IF NOT EXISTS historial_pedidos (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL REFERENCES pedidos (id),
    estado_anterior VARCHAR(20),
    estado_nuevo VARCHAR(20) NOT NULL,
    usuario_id UUID,
    usuario_nombre VARCHAR(150),
    fecha_cambio TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_historial_pedidos_pedido
    ON historial_pedidos (pedido_id, fecha_cambio DESC);

CREATE TABLE IF NOT EXISTS notificaciones (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL REFERENCES pedidos (id),
    sucursal_id UUID,
    tipo VARCHAR(40) NOT NULL,
    destinatario_tipo VARCHAR(20),
    destinatario_id UUID,
    titulo VARCHAR(150) NOT NULL,
    mensaje TEXT,
    fecha_creacion TIMESTAMP,
    leida BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_notificaciones_sucursal
    ON notificaciones (sucursal_id, fecha_creacion DESC);

CREATE INDEX IF NOT EXISTS idx_notificaciones_destinatario
    ON notificaciones (destinatario_id, fecha_creacion DESC);

CREATE INDEX IF NOT EXISTS idx_notificaciones_pedido_tipo
    ON notificaciones (pedido_id, tipo);
