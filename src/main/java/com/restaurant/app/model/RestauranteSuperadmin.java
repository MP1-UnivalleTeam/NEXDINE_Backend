package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "restaurante_superadmins")
@Data
@NoArgsConstructor
@IdClass(RestauranteSuperadmin.RestauranteSuperadminId.class)
public class RestauranteSuperadmin {

    @Id
    @Column(name = "restaurante_id", nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private UUID restauranteId;

    @Id
    @Column(name = "usuario_id", nullable = false)
    @Getter(onMethod_ = @__(@NonNull))
    private UUID usuarioId;

    @Data
    @NoArgsConstructor
    public static class RestauranteSuperadminId implements Serializable {
        private UUID restauranteId;
        private UUID usuarioId;
    }
}
