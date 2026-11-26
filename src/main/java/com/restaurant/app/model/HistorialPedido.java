package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "historial_pedidos")
@Data
@EqualsAndHashCode(callSuper = true)
public class HistorialPedido extends EntidadUuid {

    @Column(name = "pedido_id", nullable = false)
    private UUID pedidoId;

    @Column(name = "estado_anterior", length = 20)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private String estadoNuevo;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nombre", length = 150)
    private String usuarioNombre;

    @Column(name = "motivo", length = 300)
    private String motivo;

    @Column(name = "fecha_cambio", nullable = false)
    private LocalDateTime fechaCambio;

    @PrePersist
    void asignarFechaCambio() {
        if (fechaCambio == null) {
            fechaCambio = LocalDateTime.now();
        }
    }
}
