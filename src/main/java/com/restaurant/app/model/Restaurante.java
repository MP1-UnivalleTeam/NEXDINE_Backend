package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Entity
@Table(name = "restaurantes")
@Data
@EqualsAndHashCode(callSuper = true)
public class Restaurante extends EntidadUuid {

    @Column(nullable = false)
    private String nombre;

    @Column(unique = true, length = 100)
    private String slug;

    @Column(unique = true, length = 20)
    private String codigo;

    @Column(length = 10)
    private String iniciales;

    private String descripcion;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;
}
