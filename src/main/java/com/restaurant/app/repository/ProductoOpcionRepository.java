package com.restaurant.app.repository;

import com.restaurant.app.model.ProductoOpcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductoOpcionRepository extends JpaRepository<ProductoOpcion, UUID> {

    List<ProductoOpcion> findByGrupoIdInOrderByOrdenAscNombreAsc(Collection<UUID> grupoIds);

    List<ProductoOpcion> findByIdIn(Collection<UUID> ids);
}
