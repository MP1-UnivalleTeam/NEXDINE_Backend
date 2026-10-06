package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Mapeo de la tabla empleado_sucursal.
 * Empleado <-> Sucursal
 */
@Entity
@Table(name = "empleado_sucursal")
@Data
@IdClass(EmpleadoSucursal.EmpleadoSucursalId.class)
public class EmpleadoSucursal {

    @Id
    @Column(name = "empleado_id", nullable = false)
    private Long empleadoId;

    @Id
    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    public EmpleadoSucursal() {
    }

    public EmpleadoSucursal(Long empleadoId, Long sucursalId) {
        this.empleadoId = empleadoId;
        this.sucursalId = sucursalId;
    }

    @Data
    @NoArgsConstructor
    public static class EmpleadoSucursalId implements Serializable {
        private Long empleadoId;
        private Long sucursalId;
    }
}