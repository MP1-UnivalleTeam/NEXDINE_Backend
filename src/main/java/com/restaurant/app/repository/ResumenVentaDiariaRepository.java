package com.restaurant.app.repository;

import com.restaurant.app.model.ResumenVentaDiaria;
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

public interface ResumenVentaDiariaRepository extends JpaRepository<ResumenVentaDiaria, UUID> {

    Optional<ResumenVentaDiaria> findByFechaAndSucursalId(LocalDate fecha, UUID sucursalId);

    List<ResumenVentaDiaria> findBySucursalIdInAndFechaBetweenOrderByFechaAsc(
            Collection<UUID> sucursalIds, LocalDate desde, LocalDate hasta);
}
