package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "empleado_sucursal")
@Data
@IdClass(EmpleadoSucursal.EmpleadoSucursalId.class)
public class EmpleadoSucursal {

    @Id
    @Column(name = "empleado_id", nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private UUID empleadoId;

    @Id
    @Column(name = "sucursal_id", nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private UUID sucursalId;

    public EmpleadoSucursal() {
    }

    public EmpleadoSucursal(UUID empleadoId, UUID sucursalId) {
        this.empleadoId = empleadoId;
        this.sucursalId = sucursalId;
    }

    @Data
    @NoArgsConstructor
    public static class EmpleadoSucursalId implements Serializable {
        private UUID empleadoId;
        private UUID sucursalId;
    }
}
