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
    }

    public void registrarPedidoManual() {
    }

    public void consultarEstadoMesa() {
    }
}
