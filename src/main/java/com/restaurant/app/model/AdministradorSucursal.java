package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "administrador_sucursal")
@Data
@IdClass(AdministradorSucursal.AdministradorSucursalId.class)
public class AdministradorSucursal {

    @Id
    @Column(name = "administrador_id", nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private UUID administradorId;

    @Id
    @Column(name = "sucursal_id", nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private UUID sucursalId;

    public AdministradorSucursal() {
    }

    public AdministradorSucursal(UUID administradorId, UUID sucursalId) {
        this.administradorId = administradorId;
        this.sucursalId = sucursalId;
    }

    @Data
    @NoArgsConstructor
    public static class AdministradorSucursalId implements Serializable {
        private UUID administradorId;
        private UUID sucursalId;
    }
}
