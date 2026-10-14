package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Entity
@Table(name = "sesiones_mesa")
@Data
@EqualsAndHashCode(callSuper = true)
public class SesionMesa extends EntidadUuid {

    private String idMesa;
    private String token;
    private LocalDateTime fechaCreacion;
    private LocalDateTime ultimaActividad;
    private boolean activa = true;

    @Column(name = "tipo_servicio", length = 20)
    private String tipoServicio;

    public SesionMesa() {
        this.fechaCreacion = LocalDateTime.now();
        this.ultimaActividad = LocalDateTime.now();
    }
}
