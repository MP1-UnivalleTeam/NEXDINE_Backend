package com.restaurant.app.services;

import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.PedidoRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.util.CatalogoPedidos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PagoService {

    private static final Set<String> ROLES_PERSONAL = Set.of(
            "MESERO", "COCINERO", "ADMINISTRADOR", "SUPERADMIN");
    private static final Set<String> ROLES_CONFIRMACION = Set.of(
            "MESERO", "ADMINISTRADOR", "SUPERADMIN");

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final MesaService mesaService;
    private final UsuarioContextService usuarioContextService;
    private final NotificacionService notificacionService;
    private final SucursalRepository sucursalRepository;

    public PagoService(PedidoRepository pedidoRepository,
                       PedidoService pedidoService,
                       MesaService mesaService,
                       UsuarioContextService usuarioContextService,
                       NotificacionService notificacionService,
                       SucursalRepository sucursalRepository) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
        this.mesaService = mesaService;
        this.usuarioContextService = usuarioContextService;
        this.notificacionService = notificacionService;
        this.sucursalRepository = sucursalRepository;
    }

    @Transactional
    public Pedido solicitarMetodoPago(Usuario usuario, UUID pedidoId, String metodo) {
        Pedido pedido = cargarPedidoAutorizado(usuario, pedidoId);
        String normalizado = metodo == null ? ""
                : metodo.trim().toUpperCase(Locale.ROOT)
                .replace(' ', '_').replace('-', '_');
        if (!CatalogoPedidos.METODOS_PAGO.contains(normalizado)) {
            throw new IllegalArgumentException("El método de pago no es válido.");
        }
        if (CatalogoPedidos.PAGO_PAGADO.equals(pedido.getPagoEstado())) {
            throw new IllegalArgumentException("El pedido ya tiene un pago confirmado.");
        }
        if (CatalogoPedidos.ESTADO_CANCELADO.equals(pedido.getEstado())) {
            throw new IllegalArgumentException(
                    "No se puede seleccionar un método de pago para un pedido cancelado.");
        }
        if (CatalogoPedidos.METODO_CONTRA_ENTREGA.equals(normalizado)
                && !CatalogoPedidos.TIPO_DOMICILIO.equals(pedido.getTipoServicio())) {
            throw new IllegalArgumentException(
                    "El pago contra entrega solo está disponible para pedidos a domicilio.");
        }
        if (normalizado.equals(pedido.getPagoMetodo())) {
            return pedido;
        }

        pedido.setPagoMetodo(normalizado);
        pedido.setPagoEstado(CatalogoPedidos.PAGO_PENDIENTE);
        pedido.setPagoImporteRecibido(null);
        pedido.setPagoCambio(null);
        return pedidoRepository.save(pedido);
    }

    @Transactional
    public Pedido registrarMontoEfectivo(Usuario usuario, UUID pedidoId,
                                         BigDecimal montoRecibido) {
        Pedido pedido = cargarPedidoAutorizado(usuario, pedidoId);
        if (montoRecibido == null || montoRecibido.signum() <= 0) {
            throw new IllegalArgumentException("El monto recibido debe ser un valor positivo.");
        }
        if (CatalogoPedidos.PAGO_PAGADO.equals(pedido.getPagoEstado())) {
            throw new IllegalArgumentException("El pedido ya tiene un pago confirmado.");
        }
        if (CatalogoPedidos.ESTADO_CANCELADO.equals(pedido.getEstado())) {
            throw new IllegalArgumentException(
                    "No se puede registrar un monto para un pedido cancelado.");
        }

        BigDecimal total = pedidoService.obtenerTotalCalculado(pedidoId);
        if (montoRecibido.compareTo(total) < 0) {
            throw new IllegalArgumentException(
                    "El monto recibido es insuficiente. El total del pedido es "
                            + total.toPlainString() + ".");
        }

        String metodo = pedido.getPagoMetodo() == null
                ? CatalogoPedidos.METODO_EFECTIVO : pedido.getPagoMetodo();
        if (!CatalogoPedidos.METODO_EFECTIVO.equals(metodo)
                && !CatalogoPedidos.METODO_CONTRA_ENTREGA.equals(metodo)) {
            throw new IllegalArgumentException(
                    "El método de pago seleccionado no permite registrar monto en efectivo.");
        }

        BigDecimal recibido = montoRecibido.setScale(2, RoundingMode.HALF_UP);
        pedido.setPagoMetodo(metodo);
        pedido.setPagoImporteRecibido(recibido);
        pedido.setPagoCambio(recibido.subtract(total).setScale(2, RoundingMode.HALF_UP));
        pedido.setPagoEstado(CatalogoPedidos.PAGO_PENDIENTE);
        return pedidoRepository.save(pedido);
    }

    @Transactional
    public Pedido registrarPagoEnLinea(Usuario usuario, UUID pedidoId,
                                       BigDecimal montoPagado,
                                       String referencia,
                                       boolean anticipo) {
        Pedido pedido = cargarPedidoAutorizado(usuario, pedidoId);
        if (montoPagado == null || montoPagado.signum() <= 0) {
            throw new IllegalArgumentException("El monto pagado debe ser mayor que cero.");
        }
        if (referencia == null || referencia.isBlank()) {
            throw new IllegalArgumentException("La referencia del pago en línea es obligatoria.");
        }
        if (CatalogoPedidos.PAGO_PAGADO.equals(pedido.getPagoEstado())) {
            throw new IllegalArgumentException("El pedido ya tiene un pago confirmado.");
        }
        if (CatalogoPedidos.ESTADO_CANCELADO.equals(pedido.getEstado())) {
            throw new IllegalArgumentException(
                    "No se puede registrar un pago para un pedido cancelado.");
        }

        String referenciaNormalizada = referencia.trim();
        if (referenciaNormalizada.length() > 100) {
            throw new IllegalArgumentException("La referencia admite hasta 100 caracteres.");
        }

        BigDecimal total = pedidoService.obtenerTotalCalculado(pedidoId);
        BigDecimal pagado = montoPagado.setScale(2, RoundingMode.HALF_UP);
        pedido.setPagoMetodo(CatalogoPedidos.METODO_ONLINE);
        pedido.setPagoImporteRecibido(pagado);
        pedido.setPagoCambio(BigDecimal.ZERO);
        pedido.setPagoReferencia(referenciaNormalizada);
        pedido.setPagoAnticipo(anticipo);
        pedido.setPagoFechaActualizacion(LocalDateTime.now());
        pedido.setPagoEstado(anticipo && pagado.compareTo(total) < 0
                ? "PARCIAL" : (pagado.compareTo(total) >= 0
                ? CatalogoPedidos.PAGO_PAGADO : "PENDIENTE"));
        Pedido guardado = pedidoRepository.save(pedido);

        if (CatalogoPedidos.PAGO_PAGADO.equals(guardado.getPagoEstado())) {
            notificacionService.registrarPagoExitoso(guardado, pagado);
            publicarNotificacionDespuesDelCommit(guardado, pagado);
        }
        return guardado;
    }

    @Transactional
    public Pedido confirmarPago(Usuario usuario, UUID pedidoId) {
        if (usuario == null || usuario.getRol() == null
                || !ROLES_CONFIRMACION.contains(usuario.getRol())) {
            throw new IllegalStateException(
                    "Se requiere personal autorizado para confirmar el pago.");
        }
        Pedido pedido = cargarPedidoAutorizado(usuario, pedidoId);
        if (CatalogoPedidos.PAGO_PAGADO.equals(pedido.getPagoEstado())) {
            throw new IllegalArgumentException("El pago ya fue confirmado.");
        }
        if (CatalogoPedidos.ESTADO_CANCELADO.equals(pedido.getEstado())) {
            throw new IllegalArgumentException(
                    "No se puede confirmar el pago de un pedido cancelado.");
        }

        pedido.setPagoEstado(CatalogoPedidos.PAGO_PAGADO);
        Pedido guardado = pedidoRepository.save(pedido);

        if (pedido.getMesaId() != null) {
            mesaService.liberarMesaSiCorresponde(pedido.getMesaId());
        }

        BigDecimal monto = pedido.getPagoImporteRecibido() != null
                ? pedido.getPagoImporteRecibido()
                : (pedido.getTotal() != null ? pedido.getTotal() : BigDecimal.ZERO);
        notificacionService.registrarPagoExitoso(guardado, monto);
        publicarNotificacionDespuesDelCommit(guardado, monto);
        return guardado;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerPago(Usuario usuario, UUID pedidoId) {
        Pedido pedido = cargarPedidoAutorizado(usuario, pedidoId);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("pedidoId", pedido.getId());
        respuesta.put("numeroSeguimiento", pedido.getNumeroSeguimiento());
        respuesta.put("estado", pedido.getEstado());
        respuesta.put("tipoServicio", pedido.getTipoServicio());
        respuesta.put("pagoMetodo", pedido.getPagoMetodo() == null ? "" : pedido.getPagoMetodo());
        respuesta.put("pagoEstado", pedido.getPagoEstado() == null ? "" : pedido.getPagoEstado());
        respuesta.put("pagoImporteRecibido", pedido.getPagoImporteRecibido() == null
                ? BigDecimal.ZERO : pedido.getPagoImporteRecibido());
        respuesta.put("pagoCambio", pedido.getPagoCambio() == null
                ? BigDecimal.ZERO : pedido.getPagoCambio());
        respuesta.put("pagoReferencia", pedido.getPagoReferencia() == null
                ? "" : pedido.getPagoReferencia());
        respuesta.put("pagoAnticipo", pedido.isPagoAnticipo());
        respuesta.put("pagoFechaActualizacion", pedido.getPagoFechaActualizacion());
        respuesta.put("total", pedidoService.obtenerTotalCalculado(pedidoId));
        return respuesta;
    }

    private Pedido cargarPedidoAutorizado(Usuario usuario, UUID pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        if (usuario == null) {
            return pedido;
        }
        if (usuario.getRol() == null || !ROLES_PERSONAL.contains(usuario.getRol())) {
            throw new IllegalStateException("Se requiere una sesión de personal autorizado.");
        }
        if (pedido.getSucursalId() == null) {
            throw new IllegalStateException("El pedido no está asociado a una sucursal.");
        }

        Sucursal sucursal = sucursalRepository.findById(pedido.getSucursalId())
                .orElseThrow(() -> new IllegalStateException("La sucursal del pedido no existe."));
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);
        if (!sucursal.getRestauranteId().equals(contexto.getRestauranteId())
                || (contexto.getSucursalId() != null
                && !contexto.getSucursalId().equals(sucursal.getId()))) {
            throw new IllegalStateException("No tiene permisos sobre este pedido.");
        }
        return pedido;
    }

    private void publicarNotificacionDespuesDelCommit(Pedido pedido, BigDecimal monto) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificacionService.publicarPagoExitoso(pedido, monto);
                }
            });
        } else {
            notificacionService.publicarPagoExitoso(pedido, monto);
        }
    }
}
