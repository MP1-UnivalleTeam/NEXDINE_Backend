package com.restaurant.app.repository;

import com.restaurant.app.model.Mesa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MesaRepository extends JpaRepository<Mesa, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mesa m where m.id = :id")
    Optional<Mesa> findByIdForUpdate(@Param("id") UUID id);

    Optional<Mesa> findByCodigo(String codigo);

    List<Mesa> findBySucursalId(UUID sucursalId);

    List<Mesa> findBySucursalIdAndActivaTrue(UUID sucursalId);

    boolean existsByCodigo(String codigo);

    boolean existsBySucursalIdAndNombreIgnoreCase(UUID sucursalId, String nombre);
}
