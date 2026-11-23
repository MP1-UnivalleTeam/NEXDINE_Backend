package com.restaurant.app.repository;

import com.restaurant.app.model.Pedido;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, UUID>, JpaSpecificationExecutor<Pedido> {

    boolean existsByCuponCodigoIgnoreCase(String cuponCodigo);
    Optional<Pedido> findByNumeroSeguimiento(String numeroSeguimiento);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :pedidoId")
    Optional<Pedido> findByIdForUpdate(@Param("pedidoId") UUID pedidoId);

    List<Pedido> findBySucursalIdInAndEstadoNotInOrderByFechaCreacionDesc(
            Collection<UUID> sucursalIds, Collection<String> estadosFinales);

    List<Pedido> findByMesaIdAndEstadoNotInOrderByFechaCreacionDesc(
            UUID mesaId, Collection<String> estadosFinales);

    List<Pedido> findByMesaId(UUID mesaId);

    List<Pedido> findByTipoServicioAndEstadoAndFechaVencimientoBefore(
            String tipoServicio, String estado, java.time.LocalDateTime fechaVencimiento);
}
