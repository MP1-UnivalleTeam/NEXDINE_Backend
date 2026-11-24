package com.restaurant.app.repository;

import com.restaurant.app.model.PagoStripe;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagoStripeRepository extends JpaRepository<PagoStripe, UUID> {

    Optional<PagoStripe> findByPaymentIntentId(String paymentIntentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PagoStripe p where p.paymentIntentId = :paymentIntentId")
    Optional<PagoStripe> findByPaymentIntentIdForUpdate(
            @Param("paymentIntentId") String paymentIntentId);

    List<PagoStripe> findByPedidoIdAndEstadoInOrderByFechaCreacionDesc(
            UUID pedidoId, Collection<String> estados);

    long countByPedidoId(UUID pedidoId);

    @Query("select coalesce(sum(p.importe), 0) from PagoStripe p "
            + "where p.pedidoId = :pedidoId and p.estado = 'PAGADO'")
    BigDecimal sumarPagosExitosos(@Param("pedidoId") UUID pedidoId);
}
