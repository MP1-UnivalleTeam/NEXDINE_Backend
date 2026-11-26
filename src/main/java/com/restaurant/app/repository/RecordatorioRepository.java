package com.restaurant.app.repository;

import com.restaurant.app.model.Recordatorio;
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

public interface RecordatorioRepository extends JpaRepository<Recordatorio, UUID> {

    List<Recordatorio> findByRestauranteIdOrderByCreadoEnAsc(UUID restauranteId);

    List<Recordatorio> findByActivoTrue();

    List<Recordatorio> findByRestauranteIdAndUltimaEjecucionAfter(UUID restauranteId, LocalDateTime desde);
}
