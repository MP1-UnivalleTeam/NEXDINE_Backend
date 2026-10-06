package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mapeo de la tabla mesas.
 * Una mesa pertenece a una sucursal y se identifica en el QR
 * mediante un codigo opaco (no su id numerico).
 */
@Entity
@Table(name = "mesas")
@Data
@NoArgsConstructor
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(nullable = false, length = 50)
    private String nombre;

    /**
     * Codigo opaco que viaja dentro del QR.
     * Ejemplo: mesa-7f3a9c2b
     */
    @Column(nullable = false, length = 20, unique = true)
    private String codigo;

    @Column(nullable = false)
    private boolean activa = true;
}