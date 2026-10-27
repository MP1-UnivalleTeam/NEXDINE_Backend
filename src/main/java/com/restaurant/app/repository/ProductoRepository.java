package com.restaurant.app.repository;

import com.restaurant.app.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductoRepository extends JpaRepository<Producto, UUID> {

    @Query("SELECT p FROM Producto p WHERE p.disponible = true")
    List<Producto> findDisponibles();

    @Query("SELECT p FROM Producto p WHERE p.disponible = true AND p.categoriaId = :categoriaId")
    List<Producto> findDisponiblesByCategoria(@Param("categoriaId") UUID categoriaId);

    @Query("SELECT p FROM Producto p WHERE p.disponible = true AND p.restauranteId = :restauranteId")
    List<Producto> findDisponiblesByRestaurante(@Param("restauranteId") UUID restauranteId);

    List<Producto> findByRestauranteId(UUID restauranteId);

    List<Producto> findByRestauranteIdAndDisponibleTrue(UUID restauranteId);

    List<Producto> findByRestauranteIdAndCategoriaIdAndDisponibleTrue(UUID restauranteId, UUID categoriaId);

    long countByImagenPublicId(String imagenPublicId);

    long countByImagenPublicIdAndIdNot(String imagenPublicId, UUID productoId);


    Optional<Producto> findBySku(String sku);

    boolean existsBySku(String sku);

    long countBySkuIsNull();


    boolean existsByRestauranteIdAndNombreNormalizado(UUID restauranteId, String nombreNormalizado);

    boolean existsByRestauranteIdAndNombreNormalizadoAndIdNot(
            UUID restauranteId, String nombreNormalizado, UUID productoId);
}
