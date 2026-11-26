-- 1. Cifrar con BCrypt las contraseñas que siguen en texto plano.
--    UserService ya acepta hashes $2a$ (BCryptPasswordEncoder), así que los
--    usuarios inician sesión con la misma contraseña de siempre.
UPDATE usuarios
SET "contraseña" = extensions.crypt("contraseña", extensions.gen_salt('bf', 10))
WHERE "contraseña" IS NOT NULL
  AND "contraseña" !~ '^\$2[aby]?\$\d{2}\$.{53}$';

-- 2. Un correo no puede pertenecer a dos cuentas (sin distinguir mayúsculas).
CREATE UNIQUE INDEX IF NOT EXISTS ux_usuarios_correo
    ON usuarios (lower(correo))
    WHERE correo IS NOT NULL AND correo <> '';

-- 3. Solo valores que el backend reconoce (ver CatalogoPedidos).
ALTER TABLE usuarios
    ADD CONSTRAINT ck_usuarios_rol
    CHECK (rol IS NULL OR rol IN ('SUPERADMIN', 'ADMINISTRADOR', 'MESERO', 'COCINERO', 'CLIENTE'));

ALTER TABLE pedidos
    ADD CONSTRAINT ck_pedidos_estado
    CHECK (estado IN ('PENDIENTE', 'CONFIRMADO', 'EN_PREPARACION', 'LISTO', 'ENTREGADO', 'CANCELADO'));

ALTER TABLE pedidos
    ADD CONSTRAINT ck_pedidos_tipo_servicio
    CHECK (tipo_servicio IN ('MESA', 'DOMICILIO', 'PARA_LLEVAR'));

ALTER TABLE pedidos
    ADD CONSTRAINT ck_pedidos_pago_estado
    CHECK (pago_estado IS NULL OR pago_estado IN ('PENDIENTE', 'PARCIAL', 'PAGADO', 'CANCELADO'));

-- PagoService guarda '' al quitar el método de pago.
ALTER TABLE pedidos
    ADD CONSTRAINT ck_pedidos_pago_metodo
    CHECK (pago_metodo IS NULL OR pago_metodo IN ('', 'CONTRA_ENTREGA', 'EFECTIVO', 'TARJETA', 'TRANSFERENCIA', 'ONLINE'));

ALTER TABLE pedidos
    ADD CONSTRAINT ck_pedidos_montos_no_negativos
    CHECK (COALESCE(subtotal, 0) >= 0 AND COALESCE(descuento, 0) >= 0 AND COALESCE(cargos, 0) >= 0
       AND COALESCE(propina, 0) >= 0 AND COALESCE(total, 0) >= 0 AND impuestos >= 0);

ALTER TABLE items_pedido
    ADD CONSTRAINT ck_items_pedido_cantidad CHECK (cantidad > 0);

ALTER TABLE mesas
    ADD CONSTRAINT ck_mesas_estado CHECK (estado IN ('LIBRE', 'OCUPADA'));
