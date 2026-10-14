package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import java.util.UUID;

@Entity
@Table(name = "mesas")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Mesa extends EntidadUuid {

    @Column(name = "sucursal_id", nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private UUID sucursalId;

    @Column(nullable = false, length = 50)
    @Getter(onMethod_ = @__(@NonNull))
    private String nombre;

    @Column(nullable = false, length = 20, unique = true)
    @Getter(onMethod_ = @__(@NonNull))
    private String codigo;

    @Column(nullable = false)
    private boolean activa = true;
}
