package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "sucursales")
@Data
public class Sucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    @Column(nullable = false)
    private String nombre;

    private String direccion;
    private String telefono;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;
}
