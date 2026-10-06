package com.restaurant.app.repository;

import com.restaurant.app.model.EmpleadoSucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmpleadoSucursalRepository extends JpaRepository<EmpleadoSucursal, EmpleadoSucursal.EmpleadoSucursalId> {

    List<EmpleadoSucursal> findByEmpleadoId(Long empleadoId);

    Optional<EmpleadoSucursal> findByEmpleadoIdAndSucursalId(Long empleadoId, Long sucursalId);

    List<EmpleadoSucursal> findBySucursalId(Long sucursalId);
}