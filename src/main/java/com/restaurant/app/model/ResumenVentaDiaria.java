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

/** Totales de ventas y pedidos de un día en una sucursal (RF017, RF042). */
@Entity
@Table(name = "resumen_ventas_diarias")
@Data
@EqualsAndHashCode(callSuper = true)
public class ResumenVentaDiaria extends EntidadUuid {

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    @Column(name = "total_ventas", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalVentas = BigDecimal.ZERO;

    @Column(name = "numero_pedidos", nullable = false)
    private int numeroPedidos;

    @Column(name = "pedidos_cancelados", nullable = false)
    private int pedidosCancelados;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal propinas = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal impuestos = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal descuentos = BigDecimal.ZERO;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    @PreUpdate
    void marcarActualizacion() {
        fechaActualizacion = LocalDateTime.now();
    }
}
