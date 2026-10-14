package com.restaurant.app.repository;

import com.restaurant.app.model.RestauranteSuperadmin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestauranteSuperadminRepository extends JpaRepository<RestauranteSuperadmin, RestauranteSuperadmin.RestauranteSuperadminId> {

    List<RestauranteSuperadmin> findByUsuarioId(UUID usuarioId);

    Optional<RestauranteSuperadmin> findFirstByUsuarioId(UUID usuarioId);
}
