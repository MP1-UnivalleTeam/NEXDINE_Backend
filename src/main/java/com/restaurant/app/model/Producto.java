package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.lang.NonNull;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "productos")
@Data
@EqualsAndHashCode(callSuper = true)
public class Producto extends EntidadUuid {

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(columnDefinition = "TEXT")
    private String imagen;

    @Column(name = "imagen_public_id", columnDefinition = "TEXT")
    private String imagenPublicId;

    @Column(unique = true, length = 40)
    private String sku;

    @Column(name = "nombre_normalizado", columnDefinition = "TEXT")
    private String nombreNormalizado;

    @Column(nullable = false)
    private boolean disponible = true;

    @Column(name = "restaurante_id", nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private UUID restauranteId;

    @Column(name = "categoria_id")
    private UUID categoriaId;

    @Transient
    private Boolean disponibleEnMiSucursal;
}
