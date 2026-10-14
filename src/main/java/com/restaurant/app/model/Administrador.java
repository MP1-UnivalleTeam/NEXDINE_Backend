package com.restaurant.app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "administradores")
@Data
@EqualsAndHashCode(callSuper = true)
public class Administrador extends Empleado {

    private String permisos;

    public void agregarEmpleado() {
    }

    public void eliminarEmpleado() {
    }

    public void editarEmpleado() {
    }

    public void generarReporte() {
    }

    public void exportarReporte() {
    }

    public void configurarHorarios() {
    }
}
