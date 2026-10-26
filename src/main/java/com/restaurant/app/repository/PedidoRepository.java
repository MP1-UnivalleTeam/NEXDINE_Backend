package com.restaurant.app.repository;

import com.restaurant.app.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {
    Optional<Pedido> findByNumeroSeguimiento(String numeroSeguimiento);
}
