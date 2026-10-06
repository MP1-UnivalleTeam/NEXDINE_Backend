package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Mapeo de la tabla restaurante_superadmins.
 * Restaurante <-> Usuario (SUPERADMIN)
 *
 * Solo se utiliza para CONSULTA (lectura). No se crean asociaciones automáticamente.
 */
@Entity
@Table(name = "restaurante_superadmins")
@Data
@NoArgsConstructor
@IdClass(RestauranteSuperadmin.RestauranteSuperadminId.class)
public class RestauranteSuperadmin {

    @Id
    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    @Id
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Data
    @NoArgsConstructor
    public static class RestauranteSuperadminId implements Serializable {
        private Long restauranteId;
        private Long usuarioId;
    }
}