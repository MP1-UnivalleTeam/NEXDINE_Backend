package com.restaurant.app.repository;

import com.restaurant.app.model.Sucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SucursalRepository extends JpaRepository<Sucursal, Long> {
    List<Sucursal> findByRestauranteId(Long restauranteId);
}
