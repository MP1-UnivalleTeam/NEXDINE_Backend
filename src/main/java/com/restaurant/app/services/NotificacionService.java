package com.restaurant.app.services;

import com.restaurant.app.model.Notificacion;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.NotificacionRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.util.CatalogoPedidos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final PedidoEventosService eventosService;
    private final UsuarioContextService usuarioContextService;
    private final SucursalRepository sucursalRepository;

    public NotificacionService(NotificacionRepository notificacionRepository,
                               PedidoEventosService eventosService,
                               UsuarioContextService usuarioContextService,
                               SucursalRepository sucursalRepository) {
        this.notificacionRepository = notificacionRepository;
        this.eventosService = eventosService;
        this.usuarioContextService = usuarioContextService;
        this.sucursalRepository = sucursalRepository;
    }

    @Transactional
    public Notificacion registrarPagoExitoso(Pedido pedido, BigDecimal montoPagado) {
        if (notificacionRepository.existsByPedidoIdAndTipo(
                pedido.getId(), CatalogoPedidos.NOTIFICACION_PAGO_EXITOSO)) {
            return notificacionRepository.findFirstByPedidoIdAndTipo(
                            pedido.getId(), CatalogoPedidos.NOTIFICACION_PAGO_EXITOSO)
                    .orElse(null);
        }

        Notificacion notificacion = new Notificacion();
        notificacion.setPedidoId(pedido.getId());
        notificacion.setSucursalId(pedido.getSucursalId());
        notificacion.setTipo(CatalogoPedidos.NOTIFICACION_PAGO_EXITOSO);
        notificacion.setDestinatarioTipo(CatalogoPedidos.DESTINATARIO_PERSONAL);
        notificacion.setTitulo("Pago confirmado");
        notificacion.setMensaje("El pedido " + pedido.getNumeroSeguimiento()
                + " fue pagado por " + montoPagado.toPlainString()
                + ". Resultado: EXITOSO.");
        return notificacionRepository.save(notificacion);
    }

    public void publicarPagoExitoso(Pedido pedido, BigDecimal montoPagado) {
        if (pedido.getSucursalId() == null) {
            return;
        }
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("tipo", CatalogoPedidos.NOTIFICACION_PAGO_EXITOSO);
        evento.put("pedidoId", pedido.getId());
        evento.put("numeroSeguimiento", pedido.getNumeroSeguimiento());
        evento.put("montoPagado", montoPagado);
        evento.put("resultado", "EXITOSO");
        eventosService.publicarNotificacion(pedido.getSucursalId(), evento);
    }

    @Transactional(readOnly = true)
    public List<Notificacion> listar(Usuario usuario) {
        if (usuario == null) {
            throw new IllegalStateException("Usuario no autenticado");
        }
        if ("CLIENTE".equals(usuario.getRol())) {
            return notificacionRepository
                    .findByDestinatarioIdOrderByFechaCreacionDesc(usuario.getId());
        }

        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);
        List<UUID> sucursalIds;
        if (contexto.getSucursalId() != null) {
            sucursalIds = List.of(contexto.getSucursalId());
        } else {
            sucursalIds = sucursalRepository.findByRestauranteId(contexto.getRestauranteId())
                    .stream().map(Sucursal::getId).toList();
        }
        if (sucursalIds.isEmpty()) {
            return List.of();
        }
        return notificacionRepository.findBySucursalIdInOrderByFechaCreacionDesc(sucursalIds);
    }
}
