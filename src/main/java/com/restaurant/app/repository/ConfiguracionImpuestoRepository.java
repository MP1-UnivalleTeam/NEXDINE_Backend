package com.restaurant.app.repository;

import com.restaurant.app.model.ConfiguracionImpuesto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConfiguracionImpuestoRepository extends JpaRepository<ConfiguracionImpuesto, UUID> {

    Optional<ConfiguracionImpuesto> findByRestauranteId(UUID restauranteId);
}
