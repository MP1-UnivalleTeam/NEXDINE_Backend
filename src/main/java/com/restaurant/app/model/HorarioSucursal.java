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

/** Horario de atención de una sucursal para un día de la semana (RF044). */
@Entity
@Table(name = "horarios_sucursal")
@Data
@EqualsAndHashCode(callSuper = true)
public class HorarioSucursal extends EntidadUuid {

    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;

    /** 1 = lunes … 7 = domingo (ISO-8601, igual que java.time.DayOfWeek). */
    @Column(name = "dia_semana", nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int diaSemana;

    @Column(name = "hora_apertura", nullable = false)
    private LocalTime horaApertura;

    @Column(name = "hora_cierre", nullable = false)
    private LocalTime horaCierre;

    @Column(nullable = false)
    private boolean activo = true;
}
