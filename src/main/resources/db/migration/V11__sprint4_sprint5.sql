-- Sprint 4 y 5: cancelación con motivo, historial del cliente, notificaciones
-- por correo, facturación, reembolsos, reportes, recordatorios, perfil y
-- horarios de servicio.

-- ---------------------------------------------------------------- Pedidos
-- RF010: pedidos asociados al cliente que inició sesión.
-- RF016: correo opcional para avisar al cliente los cambios de estado.
-- RF033: motivo obligatorio al anular.
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS cliente_id UUID REFERENCES usuarios (id) ON DELETE SET NULL;
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS correo VARCHAR(150);
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS motivo_cancelacion VARCHAR(300);
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS fecha_cancelacion TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_pedidos_cliente_fecha
    ON pedidos (cliente_id, fecha_creacion DESC) WHERE cliente_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pedidos_sucursal_fecha
    ON pedidos (sucursal_id, fecha_creacion);

ALTER TABLE historial_pedidos ADD COLUMN IF NOT EXISTS motivo VARCHAR(300);

-- ---------------------------------------------------------------- Facturación (RF030)
CREATE TABLE IF NOT EXISTS configuracion_facturacion (
    id                  UUID PRIMARY KEY,
    restaurante_id      UUID         NOT NULL UNIQUE REFERENCES restaurantes (id) ON DELETE CASCADE,
    razon_social        VARCHAR(150) NOT NULL,
    nit                 VARCHAR(30)  NOT NULL,
    regimen             VARCHAR(80),
    direccion           VARCHAR(200),
    ciudad              VARCHAR(80),
    telefono            VARCHAR(30),
    correo              VARCHAR(150),
    prefijo             VARCHAR(10)  NOT NULL DEFAULT 'FE',
    resolucion          VARCHAR(150),
    consecutivo         BIGINT       NOT NULL DEFAULT 0 CHECK (consecutivo >= 0),
    fecha_actualizacion TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS facturas (
    id                    UUID PRIMARY KEY,
    pedido_id             UUID          NOT NULL UNIQUE REFERENCES pedidos (id),
    restaurante_id        UUID          NOT NULL REFERENCES restaurantes (id),
    numero                VARCHAR(30)   NOT NULL,
    fecha_emision         TIMESTAMP     NOT NULL,
    cufe                  VARCHAR(120),
    cliente_nombre        VARCHAR(150),
    cliente_tipo_documento VARCHAR(5),
    cliente_documento     VARCHAR(30),
    cliente_correo        VARCHAR(150),
    total                 NUMERIC(12, 2) NOT NULL,
    emitida_por           UUID,
    correo_enviado_a      VARCHAR(150),
    fecha_envio           TIMESTAMP,
    CONSTRAINT ux_facturas_restaurante_numero UNIQUE (restaurante_id, numero)
);

-- ---------------------------------------------------------------- Reembolsos (RF032)
CREATE TABLE IF NOT EXISTS reembolsos (
    id                  UUID PRIMARY KEY,
    pedido_id           UUID           NOT NULL REFERENCES pedidos (id),
    sucursal_id         UUID           REFERENCES sucursales (id) ON DELETE SET NULL,
    monto               NUMERIC(12, 2) NOT NULL CHECK (monto > 0),
    metodo              VARCHAR(20)    NOT NULL CHECK (metodo IN ('NEQUI', 'TARJETA')),
    destino             VARCHAR(60),
    estado              VARCHAR(20)    NOT NULL CHECK (estado IN ('EN_PROCESO', 'COMPLETADO', 'RECHAZADO')),
    motivo              VARCHAR(300),
    correo              VARCHAR(150),
    referencia_externa  VARCHAR(100),
    creado_por          UUID,
    creado_por_nombre   VARCHAR(150),
    fecha_creacion      TIMESTAMP      NOT NULL,
    fecha_actualizacion TIMESTAMP      NOT NULL,
    fecha_envio_correo  TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_reembolsos_pedido ON reembolsos (pedido_id);
CREATE INDEX IF NOT EXISTS idx_reembolsos_sucursal_fecha ON reembolsos (sucursal_id, fecha_creacion DESC);

-- ---------------------------------------------------------------- Reportes (RF017, RF042)
CREATE TABLE IF NOT EXISTS resumen_ventas_diarias (
    id                  UUID PRIMARY KEY,
    fecha               DATE           NOT NULL,
    sucursal_id         UUID           NOT NULL REFERENCES sucursales (id) ON DELETE CASCADE,
    total_ventas        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    numero_pedidos      INTEGER        NOT NULL DEFAULT 0,
    pedidos_cancelados  INTEGER        NOT NULL DEFAULT 0,
    propinas            NUMERIC(14, 2) NOT NULL DEFAULT 0,
    impuestos           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    descuentos          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    fecha_actualizacion TIMESTAMP      NOT NULL,
    CONSTRAINT ux_resumen_ventas_fecha_sucursal UNIQUE (fecha, sucursal_id)
);

-- ---------------------------------------------------------------- Recordatorios (RF027)
CREATE TABLE IF NOT EXISTS recordatorios (
    id               UUID PRIMARY KEY,
    restaurante_id   UUID         NOT NULL REFERENCES restaurantes (id) ON DELETE CASCADE,
    sucursal_id      UUID         REFERENCES sucursales (id) ON DELETE CASCADE,
    creado_por       UUID,
    tipo             VARCHAR(30)  NOT NULL,
    titulo           VARCHAR(120) NOT NULL,
    mensaje          VARCHAR(500),
    frecuencia       VARCHAR(20)  NOT NULL CHECK (frecuencia IN ('UNA_VEZ', 'DIARIA', 'SEMANAL', 'MENSUAL')),
    fecha            DATE,
    hora             TIME         NOT NULL,
    dias_semana      VARCHAR(20),
    dia_mes          SMALLINT     CHECK (dia_mes BETWEEN 1 AND 31),
    canales          VARCHAR(30)  NOT NULL DEFAULT 'APP',
    destinatarios    VARCHAR(500),
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    ultima_ejecucion TIMESTAMP,
    creado_en        TIMESTAMP    NOT NULL,
    actualizado_en   TIMESTAMP    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_recordatorios_restaurante ON recordatorios (restaurante_id);

-- ---------------------------------------------------------------- Perfil (RF029)
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS avatar_public_id VARCHAR(255);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS color_acento VARCHAR(7);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS tema VARCHAR(10);
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_tema CHECK (tema IS NULL OR tema IN ('CLARO', 'OSCURO', 'SISTEMA'));
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_color_acento CHECK (color_acento IS NULL OR color_acento ~ '^#[0-9A-Fa-f]{6}$');

-- ---------------------------------------------------------------- Horarios de servicio (RF044)
CREATE TABLE IF NOT EXISTS horarios_sucursal (
    id            UUID PRIMARY KEY,
    sucursal_id   UUID     NOT NULL REFERENCES sucursales (id) ON DELETE CASCADE,
    dia_semana    SMALLINT NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    hora_apertura TIME     NOT NULL,
    hora_cierre   TIME     NOT NULL,
    activo        BOOLEAN  NOT NULL DEFAULT TRUE,
    CONSTRAINT ux_horarios_sucursal_dia UNIQUE (sucursal_id, dia_semana),
    CONSTRAINT ck_horarios_sucursal_rango CHECK (hora_apertura <> hora_cierre)
);

-- ---------------------------------------------------------------- Seguridad
-- Igual que el resto del esquema: solo el backend (rol postgres) accede.
-- La API pública de Supabase (anon / authenticated) no ve estas tablas.
DO $$
DECLARE
    tabla TEXT;
BEGIN
    FOREACH tabla IN ARRAY ARRAY['configuracion_facturacion', 'facturas', 'reembolsos',
                                 'resumen_ventas_diarias', 'recordatorios', 'horarios_sucursal']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tabla);
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
            EXECUTE format('REVOKE ALL ON TABLE %I FROM anon', tabla);
        END IF;
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
            EXECUTE format('REVOKE ALL ON TABLE %I FROM authenticated', tabla);
        END IF;
    END LOOP;
END
$$;
