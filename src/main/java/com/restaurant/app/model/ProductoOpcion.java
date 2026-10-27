package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "producto_opcion")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductoOpcion extends EntidadUuid {

    @Column(name = "grupo_id", nullable = false)
    private UUID grupoId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "precio_adicional", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioAdicional = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean disponible = true;

    @Column(nullable = false)
    private int orden;
}
