package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Entity
@Table(name = "administrador_sucursal")
@Data
@NoArgsConstructor
@IdClass(AdministradorSucursal.AdministradorSucursalId.class)
public class AdministradorSucursal {

    @Id
    @Column(name = "administrador_id", nullable = false)
    private Long administradorId;

    @Id
    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Data
    @NoArgsConstructor
    public static class AdministradorSucursalId implements Serializable {
        private Long administradorId;
        private Long sucursalId;
    }
}
