package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notificaciones",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notificaciones_pedido_tipo",
                columnNames = {"pedido_id", "tipo"}))
@Data
@EqualsAndHashCode(callSuper = true)
public class Notificacion extends EntidadUuid {

    @Column(name = "pedido_id", nullable = false)
    private UUID pedidoId;

    @Column(name = "sucursal_id")
    private UUID sucursalId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "destinatario_tipo", length = 20)
    private String destinatarioTipo;

    @Column(name = "destinatario_id")
    private UUID destinatarioId;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String mensaje;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(nullable = false)
    private boolean leida = false;

    @PrePersist
    void asignarFechaCreacion() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }
}
