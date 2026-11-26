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

/** Datos del emisor y numeración de facturas de un restaurante (RF030). */
@Entity
@Table(name = "configuracion_facturacion")
@Data
@EqualsAndHashCode(callSuper = true)
public class ConfiguracionFacturacion extends EntidadUuid {

    @Column(name = "restaurante_id", nullable = false, unique = true)
    private UUID restauranteId;

    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;

    @Column(nullable = false, length = 30)
    private String nit;

    @Column(length = 80)
    private String regimen;

    @Column(length = 200)
    private String direccion;

    @Column(length = 80)
    private String ciudad;

    @Column(length = 30)
    private String telefono;

    @Column(length = 150)
    private String correo;

    @Column(nullable = false, length = 10)
    private String prefijo = "FE";

    @Column(length = 150)
    private String resolucion;

    @Column(nullable = false)
    private long consecutivo = 0;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    @PreUpdate
    void marcarActualizacion() {
        fechaActualizacion = LocalDateTime.now();
    }
}
