package com.restaurant.app.repository;

import com.restaurant.app.model.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MesaRepository extends JpaRepository<Mesa, UUID> {

    Optional<Mesa> findByCodigo(String codigo);

    List<Mesa> findBySucursalId(UUID sucursalId);

    List<Mesa> findBySucursalIdAndActivaTrue(UUID sucursalId);

    boolean existsByCodigo(String codigo);

    boolean existsBySucursalIdAndNombreIgnoreCase(UUID sucursalId, String nombre);
}
