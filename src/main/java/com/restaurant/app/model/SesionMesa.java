package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "sesiones_mesa")
@Data
public class SesionMesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String idMesa;
    private String token;
    private LocalDateTime fechaCreacion;
    private LocalDateTime ultimaActividad;
    private boolean activa = true;

    public SesionMesa() {
        this.fechaCreacion = LocalDateTime.now();
        this.ultimaActividad = LocalDateTime.now();
    }
}
