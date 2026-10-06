package com.restaurant.app.repository;

import com.restaurant.app.model.ProductoSucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductoSucursalRepository extends JpaRepository<ProductoSucursal, ProductoSucursal.ProductoSucursalId> {
    List<ProductoSucursal> findByProductoId(Long productoId);
    List<ProductoSucursal> findBySucursalId(Long sucursalId);
    Optional<ProductoSucursal> findByProductoIdAndSucursalId(Long productoId, Long sucursalId);
}
