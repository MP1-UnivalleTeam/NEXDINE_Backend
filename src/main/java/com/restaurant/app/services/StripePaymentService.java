package com.restaurant.app.services;

import com.stripe.StripeClient;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.StripeException;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.restaurant.app.model.PagoStripe;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.PagoStripeRepository;
import com.restaurant.app.repository.PedidoRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.util.CatalogoPedidos;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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
public class StripePaymentService {

    private static final Set<String> ACTIVE_INTENT_STATUSES = Set.of(
            "requires_payment_method", "requires_confirmation", "requires_action",
            "processing", "requires_capture");
    private static final Set<String> STAFF_ROLES = Set.of(
            "MESERO", "COCINERO", "ADMINISTRADOR", "SUPERADMIN");

    private final ObjectProvider<StripeClient> stripeClientProvider;
    private final PagoStripeRepository pagoStripeRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final MesaService mesaService;
    private final UsuarioContextService usuarioContextService;
    private final SucursalRepository sucursalRepository;
    private final NotificacionService notificacionService;
    private final String webhookSecret;
    private final String currency;
    private final boolean testMode;
    private final BigDecimal depositPercent;

    public StripePaymentService(ObjectProvider<StripeClient> stripeClientProvider,
                                PagoStripeRepository pagoStripeRepository,
                                PedidoRepository pedidoRepository,
                                PedidoService pedidoService,
                                MesaService mesaService,
                                UsuarioContextService usuarioContextService,
                                SucursalRepository sucursalRepository,
                                NotificacionService notificacionService,
                                @Value("${stripe.webhook-secret:}") String webhookSecret,
                                @Value("${stripe.currency:cop}") String currency,
                                @Value("${stripe.test-mode:true}") boolean testMode,
                                @Value("${stripe.deposit-percent:30}") BigDecimal depositPercent) {
        this.stripeClientProvider = stripeClientProvider;
        this.pagoStripeRepository = pagoStripeRepository;
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
        this.mesaService = mesaService;
        this.usuarioContextService = usuarioContextService;
        this.sucursalRepository = sucursalRepository;
        this.notificacionService = notificacionService;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.currency = currency == null ? "" : currency.trim().toLowerCase(Locale.ROOT);
        this.testMode = testMode;
        if (depositPercent == null || depositPercent.signum() <= 0
                || depositPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("STRIPE_DEPOSIT_PERCENT debe estar entre 0 y 100.");
        }
        if (!this.currency.matches("[a-z]{3}")) {
            throw new IllegalArgumentException("STRIPE_CURRENCY debe ser un código ISO de tres letras.");
        }
        this.depositPercent = depositPercent;
    }

    @Transactional
    public Map<String, Object> crearPaymentIntent(Usuario usuario, UUID pedidoId,
                                                  boolean anticipo) throws StripeException {
        StripeClient stripe = clienteStripe();
        if (webhookSecret.isBlank()) {
            throw new StripeNoConfiguradoException(
                    "Configura STRIPE_WEBHOOK_SECRET antes de habilitar pagos con Stripe.");
        }
        Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId)
                .map(p -> cargarPedidoAutorizado(usuario, p))
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        if (!CatalogoPedidos.ESTADO_CONFIRMADO.equals(pedido.getEstado())) {
            throw new IllegalArgumentException("Solo se puede cobrar un pedido confirmado.");
        }
        if (CatalogoPedidos.PAGO_PAGADO.equals(pedido.getPagoEstado())) {
            throw new IllegalArgumentException("El pedido ya tiene un pago confirmado.");
        }
        if ("PARCIAL".equals(pedido.getPagoEstado())
                && !CatalogoPedidos.METODO_TARJETA.equals(pedido.getPagoMetodo())) {
            throw new IllegalArgumentException(
                    "El pedido tiene un pago parcial registrado por otro método de pago.");
        }
        if (anticipo && !CatalogoPedidos.TIPO_DOMICILIO.equals(pedido.getTipoServicio())) {
            throw new IllegalArgumentException("El anticipo solo está disponible para pedidos a domicilio.");
        }

        BigDecimal total = pedidoService.obtenerTotalCalculado(pedidoId)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal pagado = pagoStripeRepository.sumarPagosExitosos(pedidoId);
        if (pagado == null) {
            pagado = BigDecimal.ZERO;
        }
        if (anticipo && pagado.signum() > 0) {
            throw new IllegalArgumentException("El anticipo ya fue pagado.");
        }
        BigDecimal importe = anticipo
                ? total.multiply(depositPercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : total.subtract(pagado).max(BigDecimal.ZERO);
        if (importe.signum() <= 0) {
            throw new IllegalArgumentException("El pedido ya está pagado.");
        }

        List<PagoStripe> activos = pagoStripeRepository
                .findByPedidoIdAndEstadoInOrderByFechaCreacionDesc(
                        pedidoId, ACTIVE_INTENT_STATUSES);
        if (!activos.isEmpty()) {
            PagoStripe existente = activos.get(0);
            if (existente.isAnticipo() != anticipo
                    || existente.getImporte().compareTo(importe) != 0) {
                throw new IllegalArgumentException(
                        "Ya existe un intento de pago pendiente para este pedido.");
            }
            PaymentIntent intent = stripe.v1().paymentIntents().retrieve(
                    existente.getPaymentIntentId());
            return respuestaIntent(intent, existente);
        }

        long amount = convertirAMinorUnits(importe);
        long attempt = pagoStripeRepository.countByPedidoId(pedidoId) + 1;
        String idempotencyKey = "pedido-" + pedidoId + "-stripe-" + attempt;
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency)
                .addAllowedPaymentMethodType(
                        PaymentIntentCreateParams.AllowedPaymentMethodType.CARD)
                .setDescription("Pedido NEXDINE " + pedido.getNumeroSeguimiento())
                .putMetadata("pedidoId", pedidoId.toString())
                .putMetadata("numeroSeguimiento", pedido.getNumeroSeguimiento())
                .putMetadata("anticipo", Boolean.toString(anticipo))
                .build();

        PaymentIntent intent = stripe.v1().paymentIntents().create(
                params,
                RequestOptions.builder().setIdempotencyKey(idempotencyKey).build());

        PagoStripe pago = new PagoStripe();
        pago.setPedidoId(pedidoId);
        pago.setPaymentIntentId(intent.getId());
        pago.setImporte(importe);
        pago.setMoneda(currency);
        pago.setEstado(intent.getStatus());
        pago.setAnticipo(anticipo);
        pagoStripeRepository.save(pago);

        pedido.setPagoMetodo(CatalogoPedidos.METODO_TARJETA);
        pedido.setPagoEstado(CatalogoPedidos.PAGO_PENDIENTE);
        pedido.setPagoAnticipo(anticipo);
        pedido.setPagoImporteRecibido(null);
        pedido.setPagoCambio(null);
        pedido.setPagoReferencia(intent.getId());
        pedido.setPagoFechaActualizacion(LocalDateTime.now());
        pedidoRepository.save(pedido);
        return respuestaIntent(intent, pago);
    }

    @Transactional
    public Map<String, Object> cancelarPaymentIntent(Usuario usuario, UUID pedidoId)
            throws StripeException {
        StripeClient stripe = clienteStripe();
        Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId)
                .map(p -> cargarPedidoAutorizado(usuario, p))
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        if (CatalogoPedidos.PAGO_PAGADO.equals(pedido.getPagoEstado())) {
            throw new IllegalStateException("El pedido ya tiene un pago confirmado.");
        }

        List<PagoStripe> activos = pagoStripeRepository
                .findByPedidoIdAndEstadoInOrderByFechaCreacionDesc(
                        pedidoId, ACTIVE_INTENT_STATUSES);
        if (activos.isEmpty()) {
            throw new IllegalArgumentException("No hay un pago de Stripe pendiente para cancelar.");
        }

        PagoStripe pago = pagoStripeRepository
                .findByPaymentIntentIdForUpdate(activos.get(0).getPaymentIntentId())
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró el intento de pago asociado al pedido."));
        PaymentIntent intent = stripe.v1().paymentIntents().retrieve(pago.getPaymentIntentId());
        if ("succeeded".equals(intent.getStatus())) {
            throw new IllegalStateException("El pago ya fue procesado y no se puede cancelar.");
        }
        if (!"canceled".equals(intent.getStatus())) {
            intent = stripe.v1().paymentIntents().cancel(intent.getId());
        }
        if (!"canceled".equals(intent.getStatus())) {
            throw new IllegalStateException("Stripe no confirmó la cancelación del pago.");
        }

        pago.setEstado("CANCELADO");
        pago.setFechaActualizacion(LocalDateTime.now());
        pagoStripeRepository.save(pago);

        BigDecimal pagado = pagoStripeRepository.sumarPagosExitosos(pedidoId);
        if (pagado == null) {
            pagado = BigDecimal.ZERO;
        }
        pedido.setPagoEstado(pagado.signum() > 0 ? "PARCIAL" : "CANCELADO");
        pedido.setPagoImporteRecibido(pagado);
        pedido.setPagoFechaActualizacion(LocalDateTime.now());
        pedidoRepository.save(pedido);
        publicarEstadoPagoDespuesDelCommit(pedido, pagado);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("pedidoId", pedidoId);
        respuesta.put("paymentIntentId", pago.getPaymentIntentId());
        respuesta.put("pagoEstado", pedido.getPagoEstado());
        respuesta.put("estadoIntento", pago.getEstado());
        respuesta.put("pagoImporteRecibido", pagado);
        return respuesta;
    }

    @Transactional
    public void procesarWebhook(Event event) {
        String eventType = event.getType();
        if (!Set.of("payment_intent.succeeded", "payment_intent.payment_failed",
                "payment_intent.canceled", "payment_intent.processing").contains(eventType)) {
            return;
        }
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = deserializer.getObject().orElseGet(() -> {
            try {
                return deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                throw new IllegalArgumentException(
                        "No fue posible leer el PaymentIntent del evento de Stripe.", e);
            }
        });
        if (!(stripeObject instanceof PaymentIntent intent)) {
            throw new IllegalArgumentException("El evento de Stripe no contiene un PaymentIntent.");
        }
        if (testMode == Boolean.TRUE.equals(intent.getLivemode())) {
            throw new IllegalArgumentException("El evento de Stripe no corresponde al modo configurado.");
        }

        PagoStripe pago = pagoStripeRepository.findByPaymentIntentIdForUpdate(intent.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El PaymentIntent no pertenece a un pedido de NEXDINE."));
        if (!currency.equalsIgnoreCase(intent.getCurrency())
                || intent.getAmount() == null
                || intent.getAmount() != convertirAMinorUnits(pago.getImporte())) {
            throw new IllegalArgumentException("El importe o la moneda del PaymentIntent no coinciden.");
        }
        String estadoNuevo = switch (eventType) {
            case "payment_intent.succeeded" -> "PAGADO";
            case "payment_intent.canceled" -> "CANCELADO";
            default -> intent.getStatus();
        };
        if (estadoNuevo == null || estadoNuevo.isBlank()) {
            throw new IllegalArgumentException("Stripe envió un PaymentIntent sin estado.");
        }
        if (estadoNuevo.equals(pago.getEstado())) {
            return;
        }
        if ("PAGADO".equals(pago.getEstado())) {
            return;
        }

        pago.setEstado(estadoNuevo);
        pago.setFechaActualizacion(LocalDateTime.now());
        pagoStripeRepository.save(pago);

        Pedido pedido = pedidoRepository.findById(pago.getPedidoId())
                .orElseThrow(() -> new IllegalStateException("El pedido del pago de Stripe no existe."));
        BigDecimal pagado = pagoStripeRepository.sumarPagosExitosos(pedido.getId());
        if (pagado == null) {
            pagado = BigDecimal.ZERO;
        }
        String estadoPagoAnterior = pedido.getPagoEstado();
        BigDecimal total = pedidoService.obtenerTotalCalculado(pedido.getId());
        pedido.setPagoMetodo(CatalogoPedidos.METODO_TARJETA);
        pedido.setPagoAnticipo(pago.isAnticipo());
        pedido.setPagoReferencia(intent.getId());
        pedido.setPagoImporteRecibido(pagado);
        pedido.setPagoCambio(BigDecimal.ZERO);
        pedido.setPagoFechaActualizacion(LocalDateTime.now());
        pedido.setPagoEstado(pagado.signum() == 0
                ? CatalogoPedidos.PAGO_PENDIENTE
                : pagado.compareTo(total) >= 0 ? CatalogoPedidos.PAGO_PAGADO : "PARCIAL");
        pedidoRepository.save(pedido);

        if (CatalogoPedidos.PAGO_PAGADO.equals(pedido.getPagoEstado())
                && !CatalogoPedidos.PAGO_PAGADO.equals(estadoPagoAnterior)) {
            notificacionService.registrarPagoExitoso(pedido, pagado);
            if (pedido.getMesaId() != null) {
                mesaService.liberarMesaSiCorresponde(pedido.getMesaId());
            }
        }
        publicarEstadoPagoDespuesDelCommit(pedido, pagado);
    }

    private Map<String, Object> respuestaIntent(PaymentIntent intent, PagoStripe pago) {
        if (intent.getClientSecret() == null || intent.getClientSecret().isBlank()) {
            throw new StripeNoConfiguradoException(
                    "Stripe no devolvió un client_secret para continuar el pago.");
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("paymentIntentId", intent.getId());
        respuesta.put("clientSecret", intent.getClientSecret());
        respuesta.put("importe", pago.getImporte());
        respuesta.put("moneda", pago.getMoneda());
        respuesta.put("estado", intent.getStatus());
        respuesta.put("anticipo", pago.isAnticipo());
        return respuesta;
    }

    /**
     * RF032: reembolsa a la tarjeta el monto indicado de un pago de Stripe ya
     * cobrado. Devuelve el identificador del reembolso en Stripe.
     */
    public String reembolsarATarjeta(UUID pedidoId, BigDecimal monto) throws StripeException {
        PagoStripe pago = pagoStripeRepository
                .findByPedidoIdAndEstadoInOrderByFechaCreacionDesc(pedidoId, List.of("PAGADO"))
                .stream()
                .filter(p -> p.getImporte() != null && p.getImporte().compareTo(monto) >= 0)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "El pedido no tiene un pago con tarjeta por ese monto para reembolsar."));
        com.stripe.param.RefundCreateParams params = com.stripe.param.RefundCreateParams.builder()
                .setPaymentIntent(pago.getPaymentIntentId())
                .setAmount(convertirAMinorUnits(monto))
                .build();
        RequestOptions opciones = RequestOptions.builder()
                .setIdempotencyKey("reembolso-" + pedidoId + "-" + convertirAMinorUnits(monto)
                        + "-" + pago.getPaymentIntentId())
                .build();
        return clienteStripe().refunds().create(params, opciones).getId();
    }

    private StripeClient clienteStripe() {
        StripeClient stripe = stripeClientProvider.getIfAvailable();
        if (stripe == null) {
            throw new StripeNoConfiguradoException(
                    "Stripe no está configurado. Define STRIPE_ENABLED y STRIPE_SECRET_KEY.");
        }
        return stripe;
    }

    private Pedido cargarPedidoAutorizado(Usuario usuario, Pedido pedido) {
        if (usuario == null) {
            return pedido;
        }
        if (usuario.getRol() == null || !STAFF_ROLES.contains(usuario.getRol())) {
            throw new IllegalStateException("No tiene permisos para iniciar este pago.");
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

    private void publicarEstadoPagoDespuesDelCommit(Pedido pedido, BigDecimal pagado) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificacionService.publicarEstadoPago(pedido, pagado);
                }
            });
        } else {
            notificacionService.publicarEstadoPago(pedido, pagado);
        }
    }

    static long convertirAMinorUnits(BigDecimal importe) {
        if (importe == null || importe.signum() <= 0) {
            throw new IllegalArgumentException("El importe debe ser mayor que cero.");
        }
        long amount = importe.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
        if (amount > 99_999_999L) {
            throw new IllegalArgumentException("El importe supera el límite admitido por Stripe.");
        }
        return amount;
    }

    public static class StripeNoConfiguradoException extends RuntimeException {
        public StripeNoConfiguradoException(String message) {
            super(message);
        }
    }
}
