package com.restaurant.app.repository;

import com.restaurant.app.model.Restaurante;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RestauranteRepository extends JpaRepository<Restaurante, Long> {

    Optional<Restaurante> findBySlug(String slug);

    Optional<Restaurante> findBySlugAndActivoTrue(String slug);

    boolean existsBySlug(String slug);
}