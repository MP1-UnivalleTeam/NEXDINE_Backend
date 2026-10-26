package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pedidos")
@Data
@EqualsAndHashCode(callSuper = true)
public class Pedido extends EntidadUuid {

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String telefono;

    @Column(name = "tipo_servicio", nullable = false, length = 20)
    private String tipoServicio;

    @Column(length = 200)
    private String direccion;

    @Column(length = 200)
    private String referencia;

    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @Column(name = "numero_seguimiento", unique = true, length = 20)
    private String numeroSeguimiento;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal cargos = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal propina = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "cupon_codigo", length = 50)
    private String cuponCodigo;

    @Column(name = "propina_tipo", length = 20)
    private String propinaTipo;

    @Column(name = "propina_valor", precision = 10, scale = 2)
    private BigDecimal propinaValor;

    @Column(name = "pago_metodo", length = 20)
    private String pagoMetodo;

    @Column(name = "pago_importe_recibido", precision = 10, scale = 2)
    private BigDecimal pagoImporteRecibido;

    @Column(name = "pago_cambio", precision = 10, scale = 2)
    private BigDecimal pagoCambio;

    @Column(name = "pago_estado", length = 20)
    private String pagoEstado;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @PrePersist
    void asignarFechaCreacion() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
        if (numeroSeguimiento == null) {
            numeroSeguimiento = "NX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }
}
