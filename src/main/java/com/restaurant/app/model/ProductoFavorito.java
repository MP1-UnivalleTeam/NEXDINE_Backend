package com.restaurant.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Entity
@Table(name = "productos_favoritos", uniqueConstraints = {
        @UniqueConstraint(name = "uk_producto_favorito_cliente_producto",
                columnNames = {"cliente_id", "producto_id"})
})
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductoFavorito extends EntidadUuid {

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "producto_id", nullable = false)
    private UUID productoId;
}
