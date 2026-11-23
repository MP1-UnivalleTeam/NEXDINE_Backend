CREATE TABLE IF NOT EXISTS pagos_stripe (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL REFERENCES pedidos (id),
    payment_intent_id VARCHAR(100) NOT NULL,
    importe NUMERIC(12, 2) NOT NULL,
    moneda VARCHAR(3) NOT NULL,
    estado VARCHAR(30) NOT NULL,
    anticipo BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion TIMESTAMP NOT NULL,
    fecha_actualizacion TIMESTAMP NOT NULL,
    CONSTRAINT uk_pagos_stripe_payment_intent UNIQUE (payment_intent_id)
);

CREATE INDEX IF NOT EXISTS idx_pagos_stripe_pedido_estado
    ON pagos_stripe (pedido_id, estado, fecha_creacion DESC);
