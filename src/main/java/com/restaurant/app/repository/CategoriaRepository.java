package com.restaurant.app.repository;

import com.restaurant.app.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByRestauranteId(Long restauranteId);

    List<Categoria> findByRestauranteIdAndActivaTrue(Long restauranteId);

    boolean existsByRestauranteIdAndNombreIgnoreCase(Long restauranteId, String nombre);
}
