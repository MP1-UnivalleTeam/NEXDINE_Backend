package com.restaurant.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "configuracion_impuestos",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_configuracion_impuestos_restaurante",
                columnNames = "restaurante_id"))
@Data
@EqualsAndHashCode(callSuper = true)
public class ConfiguracionImpuesto extends EntidadUuid {

    @Column(name = "restaurante_id", nullable = false)
    private UUID restauranteId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Column(name = "modificado_por_usuario_id")
    private UUID modificadoPorUsuarioId;

    @Column(name = "modificado_por_nombre", length = 150)
    private String modificadoPorNombre;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    void asignarFechas() {
        LocalDateTime ahora = LocalDateTime.now();
        if (fechaCreacion == null) {
            fechaCreacion = ahora;
        }
        fechaActualizacion = ahora;
    }

    @PreUpdate
    void actualizarFecha() {
        fechaActualizacion = LocalDateTime.now();
    }
}
