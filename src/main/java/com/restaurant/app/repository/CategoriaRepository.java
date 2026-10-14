package com.restaurant.app.repository;

import com.restaurant.app.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {
    List<Categoria> findByRestauranteId(UUID restauranteId);

    List<Categoria> findByRestauranteIdAndActivaTrue(UUID restauranteId);

    boolean existsByRestauranteIdAndNombreIgnoreCase(UUID restauranteId, String nombre);

    List<Categoria> findByIdIn(Collection<UUID> ids);
}
