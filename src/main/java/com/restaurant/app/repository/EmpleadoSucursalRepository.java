package com.restaurant.app.repository;

import com.restaurant.app.model.EmpleadoSucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmpleadoSucursalRepository extends JpaRepository<EmpleadoSucursal, EmpleadoSucursal.EmpleadoSucursalId> {

    List<EmpleadoSucursal> findByEmpleadoId(UUID empleadoId);

    Optional<EmpleadoSucursal> findByEmpleadoIdAndSucursalId(UUID empleadoId, UUID sucursalId);

    List<EmpleadoSucursal> findBySucursalId(UUID sucursalId);
}
