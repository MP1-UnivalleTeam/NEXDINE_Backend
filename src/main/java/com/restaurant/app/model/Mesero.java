package com.restaurant.app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "meseros")
@Data
@EqualsAndHashCode(callSuper = true)
public class Mesero extends Empleado {

    private String mesasAsignadas;

    public void seleccionarMesa() {
        // TODO: Implementar selección de mesa
    }

    public void registrarPedidoManual() {
        // TODO: Implementar registro manual
    }

    public void consultarEstadoMesa() {
        // TODO: Implementar consulta de estado de mesa
    }
}
