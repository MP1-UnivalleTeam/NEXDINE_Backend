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

    private String contraseña;
    private String permisos;

    public void agregarEmpleado() {
        // TODO: Implementar adición de empleado
    }

    public void eliminarEmpleado() {
        // TODO: Implementar eliminación de empleado
    }

    public void editarEmpleado() {
        // TODO: Implementar edición de empleado
    }

    public void generarReporte() {
        // TODO: Implementar generación de reportes
    }

    public void exportarReporte() {
        // TODO: Implementar exportación de reportes
    }

    public void configurarHorarios() {
        // TODO: Implementar configuración de horarios
    }
}
