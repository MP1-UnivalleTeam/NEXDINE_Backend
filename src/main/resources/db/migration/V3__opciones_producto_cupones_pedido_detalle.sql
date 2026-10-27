ALTER TABLE items_pedido
    ADD COLUMN IF NOT EXISTS opciones_detalle TEXT;

CREATE TABLE IF NOT EXISTS producto_opcion_grupo (
    id UUID PRIMARY KEY,
    producto_id UUID NOT NULL REFERENCES productos (id) ON DELETE CASCADE,
    nombre VARCHAR(100) NOT NULL,
    permite_multiple BOOLEAN NOT NULL DEFAULT FALSE,
    obligatorio BOOLEAN NOT NULL DEFAULT FALSE,
    orden INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_producto_opcion_grupo_producto
    ON producto_opcion_grupo (producto_id, orden);

CREATE TABLE IF NOT EXISTS producto_opcion (
    id UUID PRIMARY KEY,
    grupo_id UUID NOT NULL REFERENCES producto_opcion_grupo (id) ON DELETE CASCADE,
    nombre VARCHAR(100) NOT NULL,
    precio_adicional NUMERIC(10, 2) NOT NULL DEFAULT 0,
    disponible BOOLEAN NOT NULL DEFAULT TRUE,
    orden INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_producto_opcion_grupo
    ON producto_opcion (grupo_id, orden);

CREATE TABLE IF NOT EXISTS cupones (
    id UUID PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    valor NUMERIC(10, 2) NOT NULL,
    monto_minimo NUMERIC(10, 2) NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_inicio TIMESTAMP,
    fecha_fin TIMESTAMP,
    uso_maximo INTEGER,
    usos INTEGER NOT NULL DEFAULT 0,
    fecha_creacion TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cupones_codigo
    ON cupones (codigo);
