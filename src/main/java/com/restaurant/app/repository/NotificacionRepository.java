package com.restaurant.app.repository;

import com.restaurant.app.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

    boolean existsByPedidoIdAndTipo(UUID pedidoId, String tipo);

    Optional<Notificacion> findFirstByPedidoIdAndTipo(UUID pedidoId, String tipo);

    List<Notificacion> findBySucursalIdInOrderByFechaCreacionDesc(Collection<UUID> sucursalIds);

    List<Notificacion> findByDestinatarioIdOrderByFechaCreacionDesc(UUID destinatarioId);
}
