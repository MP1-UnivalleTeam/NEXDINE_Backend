package com.restaurant.app.repository;

import com.restaurant.app.model.SesionMesa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SesionMesaRepository extends JpaRepository<SesionMesa, Long> {

    Optional<SesionMesa> findByToken(String token);

    Optional<SesionMesa> findByIdMesaAndActiva(String idMesa, boolean activa);
}
