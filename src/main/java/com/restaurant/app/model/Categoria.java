package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.lang.NonNull;
import java.util.UUID;

@Entity
@Table(name = "categorias")
@Data
@EqualsAndHashCode(callSuper = true)
public class Categoria extends EntidadUuid {

    @Column(name = "restaurante_id", nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private UUID restauranteId;

    @Column(nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private String nombre;

    private String descripcion;

    @Column(nullable = false)
    private boolean activa = true;
}
