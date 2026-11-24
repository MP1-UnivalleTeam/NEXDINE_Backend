-- Garantiza que una notificación de pago exitoso no se duplique por concurrencia.
-- Se deduplican los registros previos conservando el más antiguo de cada
-- combinación (pedido_id, tipo) antes de crear el índice único.
DELETE FROM notificaciones
WHERE id IN (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY pedido_id, tipo
                   ORDER BY fecha_creacion, id
               ) AS fila
        FROM notificaciones
    ) duplicadas
    WHERE duplicadas.fila > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_notificaciones_pedido_tipo
    ON notificaciones (pedido_id, tipo);
