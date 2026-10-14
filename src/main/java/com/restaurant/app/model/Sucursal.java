package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.lang.NonNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sucursales")
@Data
@EqualsAndHashCode(callSuper = true)
public class Sucursal extends EntidadUuid {

    @Column(name = "restaurante_id", nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private UUID restauranteId;

    @Column(nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private String nombre;

    private String direccion;
    private String telefono;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;
}
