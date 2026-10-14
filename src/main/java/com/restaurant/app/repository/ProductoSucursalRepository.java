package com.restaurant.app.repository;

import com.restaurant.app.model.ProductoSucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductoSucursalRepository extends JpaRepository<ProductoSucursal, ProductoSucursal.ProductoSucursalId> {
    @NonNull
    List<ProductoSucursal> findByProductoId(UUID productoId);
    List<ProductoSucursal> findBySucursalId(UUID sucursalId);
    Optional<ProductoSucursal> findByProductoIdAndSucursalId(UUID productoId, UUID sucursalId);

    List<ProductoSucursal> findByProductoIdIn(Collection<UUID> productoIds);

    void deleteByProductoId(UUID productoId);
    void deleteBySucursalId(UUID sucursalId);
}
