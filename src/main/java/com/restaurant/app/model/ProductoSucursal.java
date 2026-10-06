package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Entity
@Table(name = "producto_sucursal")
@Data
@NoArgsConstructor
@IdClass(ProductoSucursal.ProductoSucursalId.class)
public class ProductoSucursal {

    @Id
    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Id
    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(nullable = false)
    private boolean disponible = true;

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class ProductoSucursalId implements Serializable {
        private Long productoId;
        private Long sucursalId;
    }
}
