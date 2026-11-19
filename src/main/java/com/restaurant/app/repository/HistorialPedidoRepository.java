package com.restaurant.app.repository;

import com.restaurant.app.model.HistorialPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistorialPedidoRepository extends JpaRepository<HistorialPedido, UUID> {

    List<HistorialPedido> findByPedidoIdOrderByFechaCambioDesc(UUID pedidoId);
}
