package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "producto_sucursal")
@Data
@NoArgsConstructor
@EqualsAndHashCode
@IdClass(ProductoSucursal.ProductoSucursalId.class)
public class ProductoSucursal {

    @Id
    @Column(name = "producto_id", nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private UUID productoId;

    @Id
    @Column(name = "sucursal_id", nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private UUID sucursalId;

    @Column(name = "restaurante_id", nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private UUID restauranteId;

    @Column(nullable = false)
    private boolean disponible = true;

    public ProductoSucursal(UUID productoId, UUID sucursalId, UUID restauranteId) {
        this.productoId = productoId;
        this.sucursalId = sucursalId;
        this.restauranteId = restauranteId;
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class ProductoSucursalId implements Serializable {
        private UUID productoId;
        private UUID sucursalId;
    }
}
