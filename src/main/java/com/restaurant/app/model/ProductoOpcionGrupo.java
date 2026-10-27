package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Entity
@Table(name = "producto_opcion_grupo")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductoOpcionGrupo extends EntidadUuid {

    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "permite_multiple", nullable = false)
    private boolean permiteMultiple;

    @Column(nullable = false)
    private boolean obligatorio;

    @Column(nullable = false)
    private int orden;
}
