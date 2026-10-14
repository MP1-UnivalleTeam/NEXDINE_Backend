package com.restaurant.app.repository;

import com.restaurant.app.model.Sucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface SucursalRepository extends JpaRepository<Sucursal, UUID> {
    List<Sucursal> findByRestauranteId(UUID restauranteId);

    List<Sucursal> findByRestauranteIdAndActivaTrue(UUID restauranteId);

    boolean existsByRestauranteIdAndNombreIgnoreCase(UUID restauranteId, String nombre);
}
