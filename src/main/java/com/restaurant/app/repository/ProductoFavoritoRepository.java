package com.restaurant.app.repository;

import com.restaurant.app.model.ProductoFavorito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductoFavoritoRepository extends JpaRepository<ProductoFavorito, UUID> {

    List<ProductoFavorito> findByClienteId(UUID clienteId);

    Optional<ProductoFavorito> findByClienteIdAndProductoId(UUID clienteId, UUID productoId);
}
