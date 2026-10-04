package com.restaurant.app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "empleados")
@Data
@EqualsAndHashCode(callSuper = true)
public class Empleado extends Usuario {

    private String idEmpleado;
    private Double salario;
    private String estado;
    private String turno;

    public void consultarPedidos() {
        // TODO: Implementar consulta de pedidos
    }

    public void actualizarEstadoPedido() {
        // TODO: Implementar actualización de estado
    }

    public void modificarPedido() {
        // TODO: Implementar modificación de pedido
    }
}