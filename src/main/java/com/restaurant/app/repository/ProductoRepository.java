package com.restaurant.app.repository;

import com.restaurant.app.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    @Query("SELECT p FROM Producto p WHERE p.disponible = true")
    List<Producto> findDisponibles();

    @Query("SELECT p FROM Producto p WHERE p.disponible = true AND p.categoriaId = :categoriaId")
    List<Producto> findDisponiblesByCategoria(@Param("categoriaId") Long categoriaId);

    @Query("SELECT p FROM Producto p WHERE p.disponible = true AND p.restauranteId = :restauranteId")
    List<Producto> findDisponiblesByRestaurante(@Param("restauranteId") Long restauranteId);

    List<Producto> findByRestauranteId(Long restauranteId);
}
