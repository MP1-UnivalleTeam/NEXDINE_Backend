package com.restaurant.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pagos_stripe", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pagos_stripe_payment_intent", columnNames = "payment_intent_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class PagoStripe extends EntidadUuid {

    @Column(name = "pedido_id", nullable = false)
    private UUID pedidoId;

    @Column(name = "payment_intent_id", nullable = false, length = 100)
    private String paymentIntentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column(nullable = false, length = 30)
    private String estado;

    @Column(nullable = false)
    private boolean anticipo;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    void asignarFechas() {
        LocalDateTime ahora = LocalDateTime.now();
        if (fechaCreacion == null) {
            fechaCreacion = ahora;
        }
        if (fechaActualizacion == null) {
            fechaActualizacion = ahora;
        }
    }
}
