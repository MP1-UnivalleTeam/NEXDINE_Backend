package com.restaurant.app.repository;

import com.restaurant.app.model.ProductoOpcionGrupo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductoOpcionGrupoRepository extends JpaRepository<ProductoOpcionGrupo, UUID> {

    List<ProductoOpcionGrupo> findByProductoIdOrderByOrdenAscNombreAsc(UUID productoId);

    List<ProductoOpcionGrupo> findByProductoIdIn(List<UUID> productoIds);

    boolean existsByProductoId(UUID productoId);
}
