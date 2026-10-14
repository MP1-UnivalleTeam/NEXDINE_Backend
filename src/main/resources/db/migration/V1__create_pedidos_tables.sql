CREATE TABLE IF NOT EXISTS pedidos (
    id UUID PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    telefono VARCHAR(20) NOT NULL,
    tipo_servicio VARCHAR(20) NOT NULL,
    direccion VARCHAR(200),
    referencia VARCHAR(200),
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    numero_seguimiento VARCHAR(20) UNIQUE,
    fecha_creacion TIMESTAMP,
    subtotal NUMERIC(10,2) DEFAULT 0,
    descuento NUMERIC(10,2) DEFAULT 0,
    cargos NUMERIC(10,2) DEFAULT 0,
    propina NUMERIC(10,2) DEFAULT 0,
    total NUMERIC(10,2) DEFAULT 0,
    cupon_codigo VARCHAR(50),
    propina_tipo VARCHAR(20),
    propina_valor NUMERIC(10,2),
    pago_metodo VARCHAR(20),
    pago_importe_recibido NUMERIC(10,2),
    pago_cambio NUMERIC(10,2),
    pago_estado VARCHAR(20),
    observaciones TEXT
);

CREATE TABLE IF NOT EXISTS items_pedido (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
    producto_id UUID NOT NULL,
    producto_nombre VARCHAR(150) NOT NULL,
    cantidad INTEGER NOT NULL,
    precio_unitario NUMERIC(10,2) NOT NULL,
    adicional_opciones NUMERIC(10,2) DEFAULT 0,
    opcion_ids TEXT,
    opcion_nombres TEXT,
    observaciones TEXT
);

CREATE INDEX IF NOT EXISTS idx_items_pedido_pedido_id ON items_pedido(pedido_id);
CREATE INDEX IF NOT EXISTS idx_pedidos_numero_seguimiento ON pedidos(numero_seguimiento);
CREATE INDEX IF NOT EXISTS idx_pedidos_estado ON pedidos(estado);
