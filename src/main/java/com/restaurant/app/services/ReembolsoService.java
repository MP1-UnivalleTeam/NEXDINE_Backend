package com.restaurant.app.services;

import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Reembolso;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.PedidoRepository;
import com.restaurant.app.repository.ReembolsoRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.util.CatalogoPedidos;
import com.stripe.exception.StripeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RF032: reembolsos de pedidos pagados.
 *
 * - TARJETA: si el pago se cobró con Stripe, el reembolso se solicita a
 *   Stripe automáticamente; si no, queda en proceso para hacerlo a mano.
 * - NEQUI: se registra en proceso y el administrador lo marca como
 *   completado cuando haga la transferencia.
 * En ambos casos el administrador puede enviar al cliente un comprobante en
 * PDF que informa que el reembolso está en proceso.
 */
@Service
public class ReembolsoService {

    private static final Logger logger = LoggerFactory.getLogger(ReembolsoService.class);
    private static final Set<String> ROLES_ADMIN = Set.of("ADMINISTRADOR", "SUPERADMIN");
    private static final Set<String> METODOS = Set.of("NEQUI", "TARJETA");
    private static final Set<String> ESTADOS_FINALES = Set.of("COMPLETADO", "RECHAZADO");

    private final ReembolsoRepository reembolsoRepository;
    private final PedidoRepository pedidoRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioContextService usuarioContextService;
    private final StripePaymentService stripePaymentService;
    private final CorreoService correoService;

    public ReembolsoService(ReembolsoRepository reembolsoRepository,
                            PedidoRepository pedidoRepository,
                            SucursalRepository sucursalRepository,
                            UsuarioContextService usuarioContextService,
                            StripePaymentService stripePaymentService,
                            CorreoService correoService) {
        this.reembolsoRepository = reembolsoRepository;
        this.pedidoRepository = pedidoRepository;
        this.sucursalRepository = sucursalRepository;
        this.usuarioContextService = usuarioContextService;
        this.stripePaymentService = stripePaymentService;
        this.correoService = correoService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(Usuario actor) {
        FacturacionService.exigirRol(actor, ROLES_ADMIN);
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(actor);
        List<UUID> sucursales = contexto.getSucursalId() != null
                ? List.of(contexto.getSucursalId())
                : sucursalRepository.findByRestauranteId(contexto.getRestauranteId()).stream()
                .map(Sucursal::getId).toList();
        if (sucursales.isEmpty()) {
            return List.of();
        }
        return reembolsoRepository.findBySucursalIdInOrderByFechaCreacionDesc(sucursales).stream()
                .map(this::aMapa).toList();
    }

    /** Pedidos cancelados con pago registrado a los que aún les falta reembolso. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendientes(Usuario actor) {
        FacturacionService.exigirRol(actor, ROLES_ADMIN);
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(actor);
        List<UUID> sucursales = contexto.getSucursalId() != null
                ? List.of(contexto.getSucursalId())
                : sucursalRepository.findByRestauranteId(contexto.getRestauranteId()).stream()
                .map(Sucursal::getId).toList();
        if (sucursales.isEmpty()) {
            return List.of();
        }
        return pedidoRepository.findBySucursalIdInAndEstadoAndPagoEstadoInOrderByFechaCreacionDesc(
                        sucursales, CatalogoPedidos.ESTADO_CANCELADO,
                        List.of(CatalogoPedidos.PAGO_PAGADO, CatalogoPedidos.PAGO_PARCIAL)).stream()
                .map(pedido -> {
                    BigDecimal pagado = montoPagado(pedido);
                    BigDecimal reembolsado = montoYaReembolsado(pedido.getId());
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("pedidoId", pedido.getId());
                    fila.put("numeroSeguimiento", pedido.getNumeroSeguimiento());
                    fila.put("cliente", pedido.getNombre());
                    fila.put("telefono", pedido.getTelefono());
                    fila.put("correo", pedido.getCorreo() != null ? pedido.getCorreo() : "");
                    fila.put("pagoMetodo", pedido.getPagoMetodo() != null ? pedido.getPagoMetodo() : "");
                    fila.put("motivoCancelacion", pedido.getMotivoCancelacion() != null ? pedido.getMotivoCancelacion() : "");
                    fila.put("fechaCancelacion", pedido.getFechaCancelacion());
                    fila.put("pagado", pagado);
                    fila.put("reembolsado", reembolsado);
                    fila.put("pendiente", pagado.subtract(reembolsado).max(BigDecimal.ZERO));
                    return fila;
                })
                .filter(fila -> ((BigDecimal) fila.get("pendiente")).signum() > 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarDePedido(Usuario actor, UUID pedidoId) {
        FacturacionService.exigirRol(actor, ROLES_ADMIN);
        pedidoEnContexto(actor, pedidoId);
        return reembolsoRepository.findByPedidoIdOrderByFechaCreacionDesc(pedidoId).stream()
                .map(this::aMapa).toList();
    }

    @Transactional
    public Map<String, Object> crear(Usuario actor, UUID pedidoId, Map<String, Object> datos) {
        FacturacionService.exigirRol(actor, ROLES_ADMIN);
        Pedido pedido = pedidoEnContexto(actor, pedidoId);
        boolean pagado = CatalogoPedidos.PAGO_PAGADO.equals(pedido.getPagoEstado())
                || CatalogoPedidos.PAGO_PARCIAL.equals(pedido.getPagoEstado());
        if (!pagado) {
            throw new IllegalArgumentException("Solo se reembolsan pedidos con un pago registrado.");
        }
        if (!CatalogoPedidos.ESTADO_CANCELADO.equals(pedido.getEstado())) {
            throw new IllegalArgumentException("Cancela el pedido (con su motivo) antes de reembolsarlo.");
        }

        String metodo = FacturacionService.obligatorio(datos.get("metodo"), "El método", 20).toUpperCase();
        if (!METODOS.contains(metodo)) {
            throw new IllegalArgumentException("El reembolso solo puede ir a Nequi o a la tarjeta.");
        }
        BigDecimal monto = leerMonto(datos.get("monto"));
        BigDecimal disponible = montoPagado(pedido).subtract(montoYaReembolsado(pedidoId));
        if (monto.compareTo(disponible) > 0) {
            throw new IllegalArgumentException("El monto supera lo pagado y aún no reembolsado ("
                    + disponible.setScale(0, RoundingMode.HALF_UP).toPlainString() + ").");
        }
        String destino = FacturacionService.opcional(datos.get("destino"), 60);
        if ("NEQUI".equals(metodo) && (destino == null || !destino.replaceAll("\\s", "").matches("3\\d{9}"))) {
            throw new IllegalArgumentException("Indica el número de celular Nequi (10 dígitos, empieza por 3).");
        }

        Reembolso reembolso = new Reembolso();
        reembolso.setPedidoId(pedidoId);
        reembolso.setSucursalId(pedido.getSucursalId());
        reembolso.setMonto(monto);
        reembolso.setMetodo(metodo);
        reembolso.setDestino("NEQUI".equals(metodo) ? destino.replaceAll("\\s", "") : destino);
        reembolso.setEstado("EN_PROCESO");
        reembolso.setMotivo(FacturacionService.opcional(datos.get("motivo"), 300) != null
                ? FacturacionService.opcional(datos.get("motivo"), 300) : pedido.getMotivoCancelacion());
        String correo = CorreoService.normalizarCorreo(FacturacionService.opcional(datos.get("correo"), 150));
        reembolso.setCorreo(correo != null ? correo : pedido.getCorreo());
        reembolso.setCreadoPor(actor.getId());
        reembolso.setCreadoPorNombre(actor.getNombre());

        if ("TARJETA".equals(metodo) && CatalogoPedidos.METODO_TARJETA.equals(pedido.getPagoMetodo())) {
            try {
                reembolso.setReferenciaExterna(stripePaymentService.reembolsarATarjeta(pedidoId, monto));
            } catch (StripeException e) {
                logger.error("Stripe rechazó el reembolso. requestId={}", e.getRequestId());
                throw new IllegalStateException("Stripe no pudo procesar el reembolso. Inténtalo nuevamente.");
            }
        }
        return aMapa(reembolsoRepository.save(reembolso));
    }

    @Transactional
    public Map<String, Object> actualizarEstado(Usuario actor, UUID reembolsoId, String estado, String referencia) {
        FacturacionService.exigirRol(actor, ROLES_ADMIN);
        Reembolso reembolso = cargarEnContexto(actor, reembolsoId);
        String nuevo = estado == null ? "" : estado.trim().toUpperCase();
        if (!ESTADOS_FINALES.contains(nuevo)) {
            throw new IllegalArgumentException("El estado debe ser COMPLETADO o RECHAZADO.");
        }
        if (ESTADOS_FINALES.contains(reembolso.getEstado())) {
            throw new IllegalArgumentException("El reembolso ya está " + reembolso.getEstado() + ".");
        }
        reembolso.setEstado(nuevo);
        String ref = FacturacionService.opcional(referencia, 100);
        if (ref != null) {
            reembolso.setReferenciaExterna(ref);
        }
        return aMapa(reembolsoRepository.save(reembolso));
    }

    @Transactional
    public Map<String, Object> enviarComprobante(Usuario actor, UUID reembolsoId, String correo, MultipartFile archivo) {
        FacturacionService.exigirRol(actor, ROLES_ADMIN);
        Reembolso reembolso = cargarEnContexto(actor, reembolsoId);
        String destino = CorreoService.normalizarCorreo(correo);
        if (destino == null) {
            throw new IllegalArgumentException("Debe indicar el correo del cliente.");
        }
        Pedido pedido = pedidoRepository.findById(reembolso.getPedidoId()).orElseThrow();
        String numero = pedido.getNumeroSeguimiento() != null ? pedido.getNumeroSeguimiento() : "";
        CorreoService.Adjunto adjunto = CorreoService.adjuntoPdf(archivo, "reembolso-" + numero + ".pdf");
        correoService.enviar(destino,
                "Tu reembolso del pedido " + numero + " está en proceso",
                "Hola " + (pedido.getNombre() != null ? pedido.getNombre() : "") + ",\n\n"
                        + "Tu reembolso por " + reembolso.getMonto().setScale(0, RoundingMode.HALF_UP).toPlainString()
                        + " a " + ("NEQUI".equals(reembolso.getMetodo()) ? "Nequi" : "tu tarjeta")
                        + " está en proceso. Adjuntamos el comprobante.\n\n"
                        + "Este es un mensaje automático de NexDine.",
                adjunto);
        reembolso.setCorreo(destino);
        reembolso.setFechaEnvioCorreo(LocalDateTime.now());
        return aMapa(reembolsoRepository.save(reembolso));
    }

    private BigDecimal montoPagado(Pedido pedido) {
        if (pedido.getPagoImporteRecibido() != null && pedido.getPagoImporteRecibido().signum() > 0) {
            BigDecimal recibido = pedido.getPagoImporteRecibido().subtract(
                    pedido.getPagoCambio() != null ? pedido.getPagoCambio() : BigDecimal.ZERO);
            return recibido.signum() > 0 ? recibido : BigDecimal.ZERO;
        }
        return pedido.getTotal() != null ? pedido.getTotal() : BigDecimal.ZERO;
    }

    private BigDecimal montoYaReembolsado(UUID pedidoId) {
        return reembolsoRepository.findByPedidoIdOrderByFechaCreacionDesc(pedidoId).stream()
                .filter(r -> !"RECHAZADO".equals(r.getEstado()))
                .map(Reembolso::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal leerMonto(Object valor) {
        try {
            BigDecimal monto = new BigDecimal(String.valueOf(valor).trim()).setScale(2, RoundingMode.HALF_UP);
            if (monto.signum() <= 0) {
                throw new IllegalArgumentException("El monto debe ser mayor que cero.");
            }
            return monto;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El monto no es válido.");
        }
    }

    private Reembolso cargarEnContexto(Usuario actor, UUID reembolsoId) {
        Reembolso reembolso = reembolsoRepository.findById(reembolsoId)
                .orElseThrow(() -> new IllegalArgumentException("Reembolso no encontrado."));
        pedidoEnContexto(actor, reembolso.getPedidoId());
        return reembolso;
    }

    private Pedido pedidoEnContexto(Usuario actor, UUID pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        if (pedido.getSucursalId() == null) {
            throw new IllegalStateException("El pedido no está asociado a una sucursal.");
        }
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(actor);
        Sucursal sucursal = sucursalRepository.findById(pedido.getSucursalId())
                .orElseThrow(() -> new IllegalStateException("La sucursal del pedido no existe."));
        if (!sucursal.getRestauranteId().equals(contexto.getRestauranteId())
                || (contexto.getSucursalId() != null && !contexto.getSucursalId().equals(sucursal.getId()))) {
            throw new IllegalStateException("No tiene permisos sobre este pedido.");
        }
        return pedido;
    }

    private Map<String, Object> aMapa(Reembolso r) {
        Pedido pedido = pedidoRepository.findById(r.getPedidoId()).orElse(null);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", r.getId());
        datos.put("pedidoId", r.getPedidoId());
        datos.put("numeroSeguimiento", pedido != null ? pedido.getNumeroSeguimiento() : "");
        datos.put("cliente", pedido != null ? pedido.getNombre() : "");
        datos.put("totalPedido", pedido != null ? pedido.getTotal() : null);
        datos.put("monto", r.getMonto());
        datos.put("metodo", r.getMetodo());
        datos.put("destino", r.getDestino() != null ? r.getDestino() : "");
        datos.put("estado", r.getEstado());
        datos.put("motivo", r.getMotivo() != null ? r.getMotivo() : "");
        datos.put("correo", r.getCorreo() != null ? r.getCorreo() : "");
        datos.put("referenciaExterna", r.getReferenciaExterna() != null ? r.getReferenciaExterna() : "");
        datos.put("creadoPorNombre", r.getCreadoPorNombre() != null ? r.getCreadoPorNombre() : "");
        datos.put("fechaCreacion", r.getFechaCreacion());
        datos.put("fechaActualizacion", r.getFechaActualizacion());
        datos.put("fechaEnvioCorreo", r.getFechaEnvioCorreo());
        return datos;
    }
}
