package com.restaurant.app.repository;

import com.restaurant.app.model.Restaurante;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;

public interface RestauranteRepository extends JpaRepository<Restaurante, UUID> {

    Optional<Restaurante> findBySlug(String slug);

    Optional<Restaurante> findBySlugAndActivoTrue(String slug);

    boolean existsBySlug(String slug);


    Optional<Restaurante> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    long countByCodigoIsNull();

    long countByInicialesIsNull();

    @Query(value = """
            INSERT INTO producto_sku_consecutivos (restaurante_id, ultimo_numero, actualizado_en)
            VALUES (CAST(:restauranteId AS uuid), 1, CURRENT_TIMESTAMP)
            ON CONFLICT (restaurante_id) DO UPDATE
                SET ultimo_numero = producto_sku_consecutivos.ultimo_numero + 1,
                    actualizado_en = CURRENT_TIMESTAMP
            RETURNING ultimo_numero
            """, nativeQuery = true)
    Long siguienteConsecutivoProducto(@Param("restauranteId") UUID restauranteId);

    @Query(value = "SELECT nextval('restaurante_codigo_seq')", nativeQuery = true)
    Long siguienteNumeroCodigoRestaurante();
}
