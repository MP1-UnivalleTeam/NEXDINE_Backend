package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "restaurantes")
@Data
public class Restaurante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    /**
     * Identificador URL-safe del restaurante para el enlace público /menu/{slug}.
     * Se genera una sola vez a partir del nombre y NO cambia aunque el nombre
     * se modifique, para no romper enlaces ya compartidos.
     */
    @Column(unique = true, length = 100)
    private String slug;

    private String descripcion;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;
}
