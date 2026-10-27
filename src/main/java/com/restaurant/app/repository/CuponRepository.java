package com.restaurant.app.repository;

import com.restaurant.app.model.Cupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CuponRepository extends JpaRepository<Cupon, UUID> {

    Optional<Cupon> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, UUID id);

    List<Cupon> findAllByOrderByFechaCreacionDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Cupon> findWithLockByCodigoIgnoreCase(String codigo);
}
