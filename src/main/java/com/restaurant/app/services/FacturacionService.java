package com.restaurant.app.services;

import com.restaurant.app.model.ConfiguracionFacturacion;
import com.restaurant.app.model.Factura;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.ConfiguracionFacturacionRepository;
import com.restaurant.app.repository.FacturaRepository;
import com.restaurant.app.repository.PedidoRepository;
import com.restaurant.app.repository.SucursalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RF030: facturación de pedidos listos o entregados.
 *
 * El servidor guarda los datos del emisor, asigna el consecutivo oficial y
 * envía el PDF por correo. La validación ante la DIAN (CUFE) requiere un
 * proveedor tecnológico autorizado; mientras no exista, el CUFE queda vacío y
 * el PDF lo indica.
 */
@Service
public class FacturacionService {

    private static final Set<String> ROLES_FACTURACION = Set.of("MESERO", "ADMINISTRADOR", "SUPERADMIN");
    private static final Set<String> ROLES_ADMIN = Set.of("ADMINISTRADOR", "SUPERADMIN");
    private static final Set<String> ESTADOS_FACTURABLES = Set.of("LISTO", "ENTREGADO");
    private static final Set<String> TIPOS_DOCUMENTO = Set.of("CC", "NIT", "CE", "PP", "CF");

    private final ConfiguracionFacturacionRepository configuracionRepository;
    private final FacturaRepository facturaRepository;
    private final PedidoRepository pedidoRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioContextService usuarioContextService;
    private final PedidoService pedidoService;
    private final CorreoService correoService;

    public FacturacionService(ConfiguracionFacturacionRepository configuracionRepository,
                              FacturaRepository facturaRepository,
                              PedidoRepository pedidoRepository,
                              SucursalRepository sucursalRepository,
                              UsuarioContextService usuarioContextService,
                              PedidoService pedidoService,
                              CorreoService correoService) {
        this.configuracionRepository = configuracionRepository;
        this.facturaRepository = facturaRepository;
        this.pedidoRepository = pedidoRepository;
        this.sucursalRepository = sucursalRepository;
        this.usuarioContextService = usuarioContextService;
        this.pedidoService = pedidoService;
        this.correoService = correoService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerEmisor(Usuario actor) {
        exigirRol(actor, ROLES_FACTURACION);
        UUID restauranteId = usuarioContextService.obtenerContexto(actor).getRestauranteId();
        return configuracionRepository.findByRestauranteId(restauranteId)
                .map(this::aMapa)
                .orElseGet(() -> {
                    Map<String, Object> vacio = new LinkedHashMap<>();
                    vacio.put("configurado", false);
                    return vacio;
                });
    }

    @Transactional
    public Map<String, Object> guardarEmisor(Usuario actor, Map<String, Object> datos) {
        exigirRol(actor, ROLES_ADMIN);
        UUID restauranteId = usuarioContextService.obtenerContexto(actor).getRestauranteId();
        ConfiguracionFacturacion config = configuracionRepository.findByRestauranteId(restauranteId)
                .orElseGet(() -> {
                    ConfiguracionFacturacion nueva = new ConfiguracionFacturacion();
                    nueva.setRestauranteId(restauranteId);
                    return nueva;
                });
        config.setRazonSocial(obligatorio(datos.get("razonSocial"), "La razón social", 150));
        config.setNit(obligatorio(datos.get("nit"), "El NIT", 30));
        config.setRegimen(opcional(datos.get("regimen"), 80));
        config.setDireccion(opcional(datos.get("direccion"), 200));
        config.setCiudad(opcional(datos.get("ciudad"), 80));
        config.setTelefono(opcional(datos.get("telefono"), 30));
        config.setCorreo(CorreoService.normalizarCorreo(opcional(datos.get("correo"), 150)));
        config.setResolucion(opcional(datos.get("resolucion"), 150));
        String prefijo = opcional(datos.get("prefijo"), 10);
        if (prefijo != null) {
            if (!prefijo.matches("[A-Za-z0-9]{1,10}")) {
                throw new IllegalArgumentException("El prefijo solo admite letras y números.");
            }
            config.setPrefijo(prefijo.toUpperCase());
        }
        return aMapa(configuracionRepository.save(config));
    }

    /**
     * Emite la factura del pedido o devuelve la ya emitida (una por pedido),
     * asignando el siguiente consecutivo del restaurante.
     */
    @Transactional
    public Map<String, Object> emitir(Usuario actor, UUID pedidoId, Map<String, Object> cliente) {
        exigirRol(actor, ROLES_FACTURACION);
        Pedido pedido = pedidoEnContexto(actor, pedidoId);
        if (!ESTADOS_FACTURABLES.contains(pedido.getEstado())) {
            throw new IllegalArgumentException("Solo se facturan pedidos listos o entregados.");
        }
        Factura existente = facturaRepository.findByPedidoId(pedidoId).orElse(null);
        if (existente != null) {
            return respuesta(existente, true);
        }
        UUID restauranteId = restauranteDelPedido(pedido);
        ConfiguracionFacturacion config = configuracionRepository.findByRestauranteIdForUpdate(restauranteId)
                .orElseThrow(() -> new IllegalStateException(
                        "Configura primero los datos del emisor (razón social y NIT)."));

        Map<String, Object> datosCliente = cliente == null ? Map.of() : cliente;
        String tipoDocumento = opcional(datosCliente.get("tipoDocumento"), 5);
        if (tipoDocumento != null && !TIPOS_DOCUMENTO.contains(tipoDocumento)) {
            throw new IllegalArgumentException("El tipo de documento no es válido.");
        }

        config.setConsecutivo(config.getConsecutivo() + 1);
        configuracionRepository.save(config);

        Factura factura = new Factura();
        factura.setPedidoId(pedidoId);
        factura.setRestauranteId(restauranteId);
        factura.setNumero(config.getPrefijo() + "-" + config.getConsecutivo());
        factura.setFechaEmision(LocalDateTime.now());
        factura.setClienteNombre(opcional(datosCliente.get("nombre"), 150));
        factura.setClienteTipoDocumento(tipoDocumento);
        factura.setClienteDocumento(opcional(datosCliente.get("documento"), 30));
        factura.setClienteCorreo(CorreoService.normalizarCorreo(opcional(datosCliente.get("correo"), 150)));
        factura.setTotal(pedidoService.obtenerTotalCalculado(pedidoId));
        factura.setEmitidaPor(actor.getId());
        return respuesta(facturaRepository.save(factura), false);
    }

    @Transactional
    public Map<String, Object> enviar(Usuario actor, UUID pedidoId, String numero, String correo,
                                      MultipartFile archivo) {
        exigirRol(actor, ROLES_FACTURACION);
        pedidoEnContexto(actor, pedidoId);
        Factura factura = facturaRepository.findByPedidoId(pedidoId)
                .filter(f -> f.getNumero().equals(numero))
                .orElseThrow(() -> new IllegalArgumentException(
                        "La factura indicada no corresponde al pedido."));
        String destino = CorreoService.normalizarCorreo(correo);
        if (destino == null) {
            throw new IllegalArgumentException("Debe indicar el correo de destino.");
        }
        CorreoService.Adjunto adjunto = CorreoService.adjuntoPdf(archivo, "factura-" + numero + ".pdf");
        String emisor = configuracionRepository.findByRestauranteId(factura.getRestauranteId())
                .map(ConfiguracionFacturacion::getRazonSocial).orElse("NexDine");
        correoService.enviar(destino,
                "Factura electrónica " + numero + " - " + emisor,
                "Hola,\n\nAdjuntamos la factura " + numero + " de tu compra en " + emisor
                        + ".\n\nGracias por tu visita.\n\nEste es un mensaje automático de NexDine.",
                adjunto);
        factura.setCorreoEnviadoA(destino);
        factura.setFechaEnvio(LocalDateTime.now());
        facturaRepository.save(factura);
        return Map.of("enviado", true, "correo", destino);
    }

    private Map<String, Object> respuesta(Factura factura, boolean yaExistia) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("numero", factura.getNumero());
        datos.put("fecha", factura.getFechaEmision());
        datos.put("cufe", factura.getCufe());
        datos.put("total", factura.getTotal() != null ? factura.getTotal() : BigDecimal.ZERO);
        datos.put("yaEmitida", yaExistia);
        return datos;
    }

    private Map<String, Object> aMapa(ConfiguracionFacturacion c) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("configurado", true);
        datos.put("razonSocial", c.getRazonSocial());
        datos.put("nit", c.getNit());
        datos.put("regimen", c.getRegimen() != null ? c.getRegimen() : "");
        datos.put("direccion", c.getDireccion() != null ? c.getDireccion() : "");
        datos.put("ciudad", c.getCiudad() != null ? c.getCiudad() : "");
        datos.put("telefono", c.getTelefono() != null ? c.getTelefono() : "");
        datos.put("correo", c.getCorreo() != null ? c.getCorreo() : "");
        datos.put("prefijo", c.getPrefijo());
        datos.put("resolucion", c.getResolucion() != null ? c.getResolucion() : "");
        datos.put("consecutivo", c.getConsecutivo());
        return datos;
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

    private UUID restauranteDelPedido(Pedido pedido) {
        return sucursalRepository.findById(pedido.getSucursalId())
                .map(Sucursal::getRestauranteId)
                .orElseThrow(() -> new IllegalStateException("La sucursal del pedido no existe."));
    }

    static void exigirRol(Usuario actor, Set<String> roles) {
        if (actor == null || actor.getRol() == null || !roles.contains(actor.getRol())) {
            throw new IllegalStateException("No tiene permisos para esta acción.");
        }
    }

    static String obligatorio(Object valor, String campo, int max) {
        String texto = opcional(valor, max);
        if (texto == null) {
            throw new IllegalArgumentException(campo + " es obligatorio.");
        }
        return texto;
    }

    static String opcional(Object valor, int max) {
        if (valor == null) {
            return null;
        }
        String texto = String.valueOf(valor).trim();
        if (texto.isEmpty()) {
            return null;
        }
        if (texto.length() > max) {
            throw new IllegalArgumentException("Un campo supera el máximo de " + max + " caracteres.");
        }
        return texto;
    }
}
