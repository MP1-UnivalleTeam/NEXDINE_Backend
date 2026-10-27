package com.restaurant.app.repository;

import com.restaurant.app.model.Cupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface CuponRepository extends JpaRepository<Cupon, UUID> {

    Optional<Cupon> findByCodigoIgnoreCase(String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Cupon> findWithLockByCodigoIgnoreCase(String codigo);
}
