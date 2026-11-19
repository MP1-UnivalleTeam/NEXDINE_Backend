ALTER TABLE pedidos
    ADD COLUMN IF NOT EXISTS base_gravable NUMERIC(10, 2) NOT NULL DEFAULT 0;

UPDATE pedidos
SET base_gravable = GREATEST(subtotal - descuento, 0)
WHERE base_gravable = 0 AND subtotal > 0;

CREATE TABLE IF NOT EXISTS configuracion_impuestos (
    id UUID PRIMARY KEY,
    restaurante_id UUID NOT NULL REFERENCES restaurantes (id),
    porcentaje NUMERIC(5, 2) NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL,
    fecha_actualizacion TIMESTAMP NOT NULL,
    modificado_por_usuario_id UUID,
    modificado_por_nombre VARCHAR(150),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_configuracion_impuestos_restaurante UNIQUE (restaurante_id),
    CONSTRAINT ck_configuracion_impuestos_porcentaje
        CHECK (porcentaje >= 0 AND porcentaje <= 100)
);
