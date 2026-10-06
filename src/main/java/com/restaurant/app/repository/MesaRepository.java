package com.restaurant.app.repository;

import com.restaurant.app.model.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MesaRepository extends JpaRepository<Mesa, Long> {

    Optional<Mesa> findByCodigo(String codigo);

    List<Mesa> findBySucursalId(Long sucursalId);

    List<Mesa> findBySucursalIdAndActivaTrue(Long sucursalId);

    boolean existsByCodigo(String codigo);

    boolean existsBySucursalIdAndNombreIgnoreCase(Long sucursalId, String nombre);
}