package com.restaurant.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** Reembolso de un pedido pagado a Nequi o a la tarjeta (RF032). */
@Entity
@Table(name = "reembolsos")
@Data
@EqualsAndHashCode(callSuper = true)
public class Reembolso extends EntidadUuid {

    @Column(name = "pedido_id", nullable = false)
    private UUID pedidoId;

    @Column(name = "sucursal_id")
    private UUID sucursalId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 20)
    private String metodo;

    @Column(length = 60)
    private String destino;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(length = 300)
    private String motivo;

    @Column(length = 150)
    private String correo;

    @Column(name = "referencia_externa", length = 100)
    private String referenciaExterna;

    @Column(name = "creado_por")
    private UUID creadoPor;

    @Column(name = "creado_por_nombre", length = 150)
    private String creadoPorNombre;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Column(name = "fecha_envio_correo")
    private LocalDateTime fechaEnvioCorreo;

    @PrePersist
    void asignarFechas() {
        LocalDateTime ahora = LocalDateTime.now();
        if (fechaCreacion == null) {
            fechaCreacion = ahora;
        }
        fechaActualizacion = ahora;
    }

    @PreUpdate
    void marcarActualizacion() {
        fechaActualizacion = LocalDateTime.now();
    }
}
