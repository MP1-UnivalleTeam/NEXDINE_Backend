package com.restaurant.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** Recordatorio automático configurado por un administrador (RF027). */
@Entity
@Table(name = "recordatorios")
@Data
@EqualsAndHashCode(callSuper = true)
public class Recordatorio extends EntidadUuid {

    @Column(name = "restaurante_id", nullable = false)
    private UUID restauranteId;

    @Column(name = "sucursal_id")
    private UUID sucursalId;

    @Column(name = "creado_por")
    private UUID creadoPor;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(length = 500)
    private String mensaje;

    @Column(nullable = false, length = 20)
    private String frecuencia;

    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    /** Días de la semana separados por coma, 0 = domingo … 6 = sábado. */
    @Column(name = "dias_semana", length = 20)
    private String diasSemana;

    @Column(name = "dia_mes")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer diaMes;

    /** Canales separados por coma: APP, CORREO. */
    @Column(nullable = false, length = 30)
    private String canales = "APP";

    @Column(length = 500)
    private String destinatarios;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "ultima_ejecucion")
    private LocalDateTime ultimaEjecucion;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @PrePersist
    void asignarFechas() {
        LocalDateTime ahora = LocalDateTime.now();
        if (creadoEn == null) {
            creadoEn = ahora;
        }
        actualizadoEn = ahora;
    }

    @PreUpdate
    void marcarActualizacion() {
        actualizadoEn = LocalDateTime.now();
    }
}
