package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "productos")
@Data
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(columnDefinition = "TEXT")
    private String imagen;

    @Column(nullable = false)
    private boolean disponible = true;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    @Column(name = "categoria_id")
    private Long categoriaId;

    /**
     * Disponibilidad del producto en la sucursal del usuario autenticado.
     * Solo para presentación: NO se persiste en la tabla productos.
     * Se calcula desde producto_sucursal (RF007).
     *
     * null = el usuario no tiene contexto de sucursal (SUPERADMIN)
     */
    @Transient
    private Boolean disponibleEnMiSucursal;
}
