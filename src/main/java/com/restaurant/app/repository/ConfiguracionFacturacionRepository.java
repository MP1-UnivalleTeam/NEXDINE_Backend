package com.restaurant.app.repository;

import com.restaurant.app.model.ConfiguracionFacturacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfiguracionFacturacionRepository extends JpaRepository<ConfiguracionFacturacion, UUID> {

    Optional<ConfiguracionFacturacion> findByRestauranteId(UUID restauranteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConfiguracionFacturacion c where c.restauranteId = :restauranteId")
    Optional<ConfiguracionFacturacion> findByRestauranteIdForUpdate(@Param("restauranteId") UUID restauranteId);
}
