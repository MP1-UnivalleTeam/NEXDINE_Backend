package com.restaurant.app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "cocineros")
@Data
@EqualsAndHashCode(callSuper = true)
public class Cocinero extends Empleado {

    private String areaCocina;

    public void verPedidosPendientes() {
        // TODO: Implementar visualización de pendientes
    }

    public void cambiarEstadoPedido() {
        // TODO: Implementar cambio de estado de pedido
    }
}
