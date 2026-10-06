package com.restaurant.app.repository;

import com.restaurant.app.model.AdministradorSucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AdministradorSucursalRepository extends JpaRepository<AdministradorSucursal, AdministradorSucursal.AdministradorSucursalId> {
    List<AdministradorSucursal> findByAdministradorId(Long administradorId);
    Optional<AdministradorSucursal> findByAdministradorIdAndSucursalId(Long administradorId, Long sucursalId);
    List<AdministradorSucursal> findBySucursalId(Long sucursalId);
}
