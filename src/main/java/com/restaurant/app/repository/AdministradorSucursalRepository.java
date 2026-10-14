package com.restaurant.app.repository;

import com.restaurant.app.model.AdministradorSucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdministradorSucursalRepository extends JpaRepository<AdministradorSucursal, AdministradorSucursal.AdministradorSucursalId> {
    List<AdministradorSucursal> findByAdministradorId(UUID administradorId);
    Optional<AdministradorSucursal> findByAdministradorIdAndSucursalId(UUID administradorId, UUID sucursalId);
    List<AdministradorSucursal> findBySucursalId(UUID sucursalId);
}
