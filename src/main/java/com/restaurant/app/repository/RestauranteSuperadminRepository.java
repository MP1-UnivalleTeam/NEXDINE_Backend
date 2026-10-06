package com.restaurant.app.repository;

import com.restaurant.app.model.RestauranteSuperadmin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RestauranteSuperadminRepository extends JpaRepository<RestauranteSuperadmin, RestauranteSuperadmin.RestauranteSuperadminId> {

    List<RestauranteSuperadmin> findByUsuarioId(Long usuarioId);

    Optional<RestauranteSuperadmin> findFirstByUsuarioId(Long usuarioId);
}