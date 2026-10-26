package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "items_pedido")
@Data
@EqualsAndHashCode(callSuper = true)
public class ItemPedido extends EntidadUuid {

    @Column(name = "pedido_id", nullable = false)
    private UUID pedidoId;

    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    @Column(name = "producto_nombre", nullable = false, length = 150)
    private String productoNombre;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "adicional_opciones", precision = 10, scale = 2)
    private BigDecimal adicionalOpciones = BigDecimal.ZERO;

    @Column(name = "opcion_ids", columnDefinition = "TEXT")
    private String opcionIds;

    @Column(name = "opcion_nombres", columnDefinition = "TEXT")
    private String opcionNombres;

    @Column(columnDefinition = "TEXT")
    private String observaciones;
}
