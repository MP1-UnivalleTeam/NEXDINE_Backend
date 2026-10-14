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
    private String turno;

    public void consultarPedidos() {
    }

    public void actualizarEstadoPedido() {
    }

    public void modificarPedido() {
    }
}
