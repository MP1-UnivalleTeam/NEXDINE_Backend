package com.restaurant.app.repository;

import com.restaurant.app.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    boolean existsByCuponCodigoIgnoreCase(String cuponCodigo);
    Optional<Pedido> findByNumeroSeguimiento(String numeroSeguimiento);

    List<Pedido> findBySucursalIdInAndEstadoNotInOrderByFechaCreacionDesc(
            Collection<UUID> sucursalIds, Collection<String> estadosFinales);

    List<Pedido> findByMesaIdAndEstadoNotInOrderByFechaCreacionDesc(
            UUID mesaId, Collection<String> estadosFinales);
}
