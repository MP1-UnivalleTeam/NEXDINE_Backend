package com.restaurant.app.services;

import com.restaurant.app.dto.PedidoManualRequest;
import com.restaurant.app.dto.PedidoServicioRequest;
import com.restaurant.app.model.HistorialPedido;
import com.restaurant.app.model.ItemPedido;
import com.restaurant.app.model.Mesa;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.HistorialPedidoRepository;
import com.restaurant.app.repository.ItemPedidoRepository;
import com.restaurant.app.repository.MesaRepository;
import com.restaurant.app.repository.PedidoRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.util.CatalogoPedidos;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PedidoService {

    private static final int MAX_OBSERVACION_ITEM = 500;
    private static final Set<String> ESTADOS_FINALES = CatalogoPedidos.ESTADOS_FINALES;
    private static final Set<String> ESTADOS_VALIDOS = CatalogoPedidos.ESTADOS_PEDIDO;

    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final ProductoRepository productoRepository;
    private final ProductoSucursalRepository productoSucursalRepository;
    private final MesaRepository mesaRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioContextService usuarioContextService;
    private final PedidoEventosService eventosService;
    private final ProductoOpcionService productoOpcionService;
    private final CuponService cuponService;
    private final HistorialPedidoRepository historialPedidoRepository;
    private final MesaService mesaService;
    private final ConfiguracionImpuestoService configuracionImpuestoService;
    private final CalculadoraPedidoService calculadoraPedidoService;

    public PedidoService(PedidoRepository pedidoRepository,
                         ItemPedidoRepository itemPedidoRepository,
                         ProductoRepository productoRepository,
                         ProductoSucursalRepository productoSucursalRepository,
                         MesaRepository mesaRepository,
                         SucursalRepository sucursalRepository,
                         UsuarioContextService usuarioContextService,
                         PedidoEventosService eventosService,
                         ProductoOpcionService productoOpcionService,
                         CuponService cuponService,
                         HistorialPedidoRepository historialPedidoRepository,
                         MesaService mesaService,
                         ConfiguracionImpuestoService configuracionImpuestoService,
                         CalculadoraPedidoService calculadoraPedidoService) {
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.productoRepository = productoRepository;
        this.productoSucursalRepository = productoSucursalRepository;
        this.mesaRepository = mesaRepository;
        this.sucursalRepository = sucursalRepository;
        this.usuarioContextService = usuarioContextService;
        this.eventosService = eventosService;
        this.productoOpcionService = productoOpcionService;
        this.cuponService = cuponService;
        this.historialPedidoRepository = historialPedidoRepository;
        this.mesaService = mesaService;
        this.configuracionImpuestoService = configuracionImpuestoService;
        this.calculadoraPedidoService = calculadoraPedidoService;
    }

    @Transactional
    public Pedido crearPedidoInvitado(String nombre, String telefono, String tipoServicio,
                                      String direccion, String referencia) {
        return crearPedidoInvitado(nombre, telefono, tipoServicio, direccion, referencia, null);
    }

    @Transactional
    public Pedido crearPedidoInvitado(String nombre, String telefono, String tipoServicio,
                                      String direccion, String referencia, UUID sucursalId) {
        return crearPedidoInvitado(nombre, telefono, tipoServicio, direccion, referencia,
                sucursalId, null);
    }

    @Transactional
    public Pedido crearPedidoInvitado(String nombre, String telefono, String tipoServicio,
                                      String direccion, String referencia, UUID sucursalId,
                                      UUID mesaId) {
        if (tipoServicio == null || tipoServicio.isBlank()) {
            throw new IllegalArgumentException("El tipo de servicio es obligatorio.");
        }
        Pedido pedido = new Pedido();
        pedido.setNombre(nombre);
        pedido.setTelefono(telefono);
        pedido.setTipoServicio(tipoServicio);
        pedido.setDireccion(direccion);
        pedido.setReferencia(referencia);
        pedido.setEstado("PENDIENTE");
        pedido.setPagoEstado(CatalogoPedidos.PAGO_PENDIENTE);

        if (mesaId != null) {
            Mesa mesa = mesaRepository.findById(mesaId)
                    .orElseThrow(() -> new IllegalArgumentException("La mesa no existe."));
            if (!mesa.isActiva()) {
                throw new IllegalArgumentException("La mesa está inactiva.");
            }
            if (sucursalId != null && !sucursalId.equals(mesa.getSucursalId())) {
                throw new IllegalArgumentException(
                        "La mesa no pertenece a la sucursal indicada.");
            }
            pedido.setMesaId(mesa.getId());
            pedido.setSucursalId(mesa.getSucursalId());
            pedido.setTipoServicio(CatalogoPedidos.TIPO_MESA);
            pedido.setTasaImpuesto(tasaActualDeSucursal(mesa.getSucursalId()));
            Pedido guardado = pedidoRepository.save(pedido);
            mesaService.ocuparMesa(mesa.getId());
            return guardado;
        }

        if (sucursalId != null) {
            Sucursal sucursal = sucursalRepository.findById(sucursalId)
                    .filter(Sucursal::isActiva)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La sucursal no existe o está inactiva."));
            pedido.setSucursalId(sucursal.getId());
            pedido.setTasaImpuesto(
                    configuracionImpuestoService.obtenerTasaVigente(
                            sucursal.getRestauranteId()));
        }
        return pedidoRepository.save(pedido);
    }

    @Transactional
    public Pedido crearPedidoManual(Usuario usuario, PedidoManualRequest solicitud) {
        exigirMeseroOAdmin(usuario);
        if (solicitud == null || solicitud.mesaId() == null
                || solicitud.items() == null || solicitud.items().isEmpty()) {
            throw new IllegalArgumentException("Debe indicar una mesa y al menos un producto.");
        }

        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);
        Mesa mesa = mesaRepository.findById(solicitud.mesaId())
                .orElseThrow(() -> new IllegalArgumentException("La mesa no existe."));
        exigirAccesoSucursal(contexto, mesa.getSucursalId());
        if (!mesa.isActiva()) {
            throw new IllegalArgumentException("No se pueden registrar pedidos en una mesa inactiva.");
        }
        Sucursal sucursal = sucursalRepository.findById(mesa.getSucursalId())
                .orElseThrow(() -> new IllegalStateException("La sucursal de la mesa no existe."));

        String nombre = solicitud.nombre() == null || solicitud.nombre().isBlank()
                ? "Cliente de mesa" : solicitud.nombre().trim();
        String telefono = solicitud.telefono() == null || solicitud.telefono().isBlank()
                ? "N/A" : solicitud.telefono().trim();
        if (nombre.length() > 100 || telefono.length() > 20) {
            throw new IllegalArgumentException("El nombre admite hasta 100 caracteres y el teléfono hasta 20.");
        }

        Pedido pedido = new Pedido();
        pedido.setNombre(nombre);
        pedido.setTelefono(telefono);
        pedido.setTipoServicio("MESA");
        pedido.setEstado("PENDIENTE");
        pedido.setPagoEstado(CatalogoPedidos.PAGO_PENDIENTE);
        pedido.setMesaId(mesa.getId());
        pedido.setSucursalId(mesa.getSucursalId());
        pedido.setObservaciones(solicitud.observaciones());
        Pedido guardado = pedidoRepository.save(pedido);

        for (PedidoManualRequest.Item linea : solicitud.items()) {
            if (linea == null || linea.productoId() == null || linea.cantidad() < 1) {
                throw new IllegalArgumentException("Cada producto debe tener un identificador y cantidad válida.");
            }
            Producto producto = productoRepository.findById(linea.productoId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado."));
            if (!producto.isDisponible()
                    || !producto.getRestauranteId().equals(sucursal.getRestauranteId())
                    || !productoSucursalRepository.existsByProductoIdAndSucursalIdAndDisponibleTrue(
                    producto.getId(), mesa.getSucursalId())) {
                throw new IllegalArgumentException(
                        "El producto no está disponible en la sucursal de la mesa.");
            }

            ItemPedido item = new ItemPedido();
            item.setPedidoId(guardado.getId());
            item.setProductoId(producto.getId());
            item.setProductoNombre(producto.getNombre());
            item.setCantidad(linea.cantidad());
            item.setPrecioUnitario(producto.getPrecio());
            item.setAdicionalOpciones(BigDecimal.ZERO);
            item.setObservaciones(normalizarObservacion(linea.observaciones()));
            itemPedidoRepository.save(item);
        }

        recalcularTotales(guardado, tasaActual(guardado));
        mesaService.ocuparMesa(mesa.getId());
        Pedido pedidoGuardado = pedidoRepository.save(guardado);
        publicarPedido(pedidoGuardado);
        return pedidoGuardado;
    }

    @Transactional
    public Pedido crearPedidoParaLlevar(PedidoServicioRequest solicitud) {
        return crearPedidoDeServicio(solicitud, CatalogoPedidos.TIPO_PARA_LLEVAR, false);
    }

    @Transactional
    public Pedido crearPedidoDomicilio(PedidoServicioRequest solicitud) {
        return crearPedidoDeServicio(solicitud, CatalogoPedidos.TIPO_DOMICILIO, true);
    }

    private Pedido crearPedidoDeServicio(PedidoServicioRequest solicitud, String tipoServicio,
                                         boolean exigirDireccion) {
        if (solicitud == null || solicitud.sucursalId() == null
                || solicitud.items() == null || solicitud.items().isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe indicar la sucursal y al menos un producto.");
        }
        String nombre = solicitud.nombre() == null || solicitud.nombre().isBlank()
                ? "" : solicitud.nombre().trim();
        String telefono = solicitud.telefono() == null || solicitud.telefono().isBlank()
                ? "" : solicitud.telefono().trim();
        if (nombre.isEmpty() || telefono.isEmpty()) {
            throw new IllegalArgumentException("El nombre y el teléfono son obligatorios.");
        }
        if (nombre.length() > 100 || telefono.length() > 20) {
            throw new IllegalArgumentException(
                    "El nombre admite hasta 100 caracteres y el teléfono hasta 20.");
        }
        if (exigirDireccion && (solicitud.direccion() == null
                || solicitud.direccion().isBlank())) {
            throw new IllegalArgumentException("La dirección es obligatoria para domicilio.");
        }

        Sucursal sucursal = sucursalRepository.findById(solicitud.sucursalId())
                .filter(Sucursal::isActiva)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La sucursal no existe o está inactiva."));

        Pedido pedido = new Pedido();
        pedido.setNombre(nombre);
        pedido.setTelefono(telefono);
        pedido.setTipoServicio(tipoServicio);
        pedido.setDireccion(solicitud.direccion());
        pedido.setReferencia(solicitud.referencia());
        pedido.setObservaciones(solicitud.observaciones());
        pedido.setEstado("PENDIENTE");
        pedido.setPagoEstado(CatalogoPedidos.PAGO_PENDIENTE);
        pedido.setSucursalId(sucursal.getId());
        Pedido guardado = pedidoRepository.save(pedido);

        for (PedidoServicioRequest.Item linea : solicitud.items()) {
            if (linea == null || linea.productoId() == null || linea.cantidad() < 1) {
                throw new IllegalArgumentException(
                        "Cada producto debe tener un identificador y cantidad válida.");
            }
            ItemPedido item = construirItem(guardado.getId(), linea.productoId(),
                    linea.cantidad(), linea.opcionIds(), linea.observaciones(),
                    sucursal.getRestauranteId(), sucursal.getId());
            itemPedidoRepository.save(item);
        }

        recalcularTotales(guardado, tasaActual(guardado));
        Pedido pedidoGuardado = pedidoRepository.save(guardado);
        publicarPedido(pedidoGuardado);
        return pedidoGuardado;
    }

    private ItemPedido construirItem(UUID pedidoId, UUID productoId, int cantidad,
                                     List<UUID> opcionIds, String observaciones,
                                     UUID restauranteId, UUID sucursalId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado."));
        if (!producto.isDisponible()
                || !producto.getRestauranteId().equals(restauranteId)
                || !productoSucursalRepository.existsByProductoIdAndSucursalIdAndDisponibleTrue(
                productoId, sucursalId)) {
            throw new IllegalArgumentException(
                    "El producto no está disponible en la sucursal del pedido.");
        }
        ProductoOpcionService.SeleccionOpciones seleccion =
                productoOpcionService.validarSeleccion(productoId, opcionIds);

        ItemPedido item = new ItemPedido();
        item.setPedidoId(pedidoId);
        item.setProductoId(productoId);
        item.setProductoNombre(producto.getNombre());
        item.setCantidad(cantidad);
        item.setPrecioUnitario(producto.getPrecio());
        item.setAdicionalOpciones(seleccion.adicional());
        item.setOpcionIds(seleccion.ids().isEmpty() ? null
                : String.join(",", seleccion.ids().stream().map(String::valueOf).toList()));
        item.setOpcionNombres(seleccion.nombres());
        item.setOpcionesDetalle(seleccion.detalle());
        item.setObservaciones(normalizarObservacion(observaciones));
        return item;
    }

    @Transactional
    public ItemPedido agregarItem(UUID pedidoId, UUID productoId, int cantidad,
                                  List<UUID> opcionIds, String observaciones) {
        if (cantidad < 1) {
            throw new IllegalArgumentException("La cantidad debe ser al menos 1.");
        }
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
        if (!"PENDIENTE".equalsIgnoreCase(pedido.getEstado())) {
            throw new IllegalArgumentException("No se pueden agregar productos a un pedido que ya fue confirmado.");
        }

        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
        if (!producto.isDisponible()) {
            throw new IllegalArgumentException("El producto no está disponible.");
        }
        if (pedido.getSucursalId() != null) {
            Sucursal sucursal = sucursalRepository.findById(pedido.getSucursalId())
                    .orElseThrow(() -> new IllegalStateException("La sucursal del pedido no existe."));
            if (!producto.getRestauranteId().equals(sucursal.getRestauranteId())
                    || !productoSucursalRepository.existsByProductoIdAndSucursalIdAndDisponibleTrue(
                    productoId, pedido.getSucursalId())) {
                throw new IllegalArgumentException("El producto no está disponible en la sucursal del pedido.");
            }
        }

        ProductoOpcionService.SeleccionOpciones seleccion =
                productoOpcionService.validarSeleccion(productoId, opcionIds);

        ItemPedido item = new ItemPedido();
        item.setPedidoId(pedidoId);
        item.setProductoId(productoId);
        item.setProductoNombre(producto.getNombre());
        item.setCantidad(cantidad);
        item.setPrecioUnitario(producto.getPrecio());
        item.setAdicionalOpciones(seleccion.adicional());
        item.setOpcionIds(seleccion.ids().isEmpty() ? null
                : String.join(",", seleccion.ids().stream().map(String::valueOf).toList()));
        item.setOpcionNombres(seleccion.nombres());
        item.setOpcionesDetalle(seleccion.detalle());
        item.setObservaciones(normalizarObservacion(observaciones));

        ItemPedido guardado = itemPedidoRepository.save(item);
        recalcularTotales(pedido, tasaDelPedido(pedido));
        pedidoRepository.save(pedido);
        publicarPedido(pedido);
        return guardado;
    }

    @Transactional
    public Pedido confirmarPedido(UUID pedidoId, String propinaTipo, BigDecimal propinaValor,
                                  String cuponCodigo) {
        return confirmarPedido(pedidoId, propinaTipo, propinaValor, null, cuponCodigo);
    }

    @Transactional
    public Pedido confirmarPedido(UUID pedidoId, String propinaTipo, BigDecimal propinaValor,
                                  BigDecimal propinaPorcentaje, String cuponCodigo) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

        if (!"PENDIENTE".equals(pedido.getEstado())) {
            throw new IllegalArgumentException(
                    "El pedido no está pendiente y no puede confirmarse.");
        }
        if (pedido.getSucursalId() == null) {
            throw new IllegalArgumentException("Debe seleccionar una sucursal antes de confirmar el pedido.");
        }

        Sucursal sucursal = sucursalRepository.findById(pedido.getSucursalId())
                .filter(Sucursal::isActiva)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La sucursal del pedido no existe o está inactiva."));

        List<ItemPedido> items = itemPedidoRepository.findByPedidoId(pedidoId);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("El pedido debe tener al menos un producto.");
        }
        for (ItemPedido item : items) {
            Producto producto = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new IllegalArgumentException("Uno de los productos ya no existe."));
            if (!producto.isDisponible()
                    || !producto.getRestauranteId().equals(sucursal.getRestauranteId())
                    || !productoSucursalRepository.existsByProductoIdAndSucursalIdAndDisponibleTrue(
                    producto.getId(), sucursal.getId())) {
                throw new IllegalArgumentException("El producto " + producto.getNombre()
                        + " ya no está disponible en la sucursal seleccionada.");
            }
        }

        BigDecimal subtotal = calculadoraPedidoService.calcularSubtotal(items);
        aplicarPropina(pedido, propinaValor, propinaPorcentaje, subtotal);

        CuponService.CuponAplicado cuponAplicado = null;
        if (cuponCodigo != null && !cuponCodigo.isBlank()) {
            cuponAplicado = cuponService.aplicar(cuponCodigo, subtotal);
            pedido.setCuponCodigo(cuponAplicado.codigo());
            pedido.setDescuento(cuponAplicado.descuento());
        } else {
            pedido.setCuponCodigo(null);
            pedido.setDescuento(BigDecimal.ZERO);
        }

        recalcularTotales(pedido, tasaDelPedido(pedido));
        String estadoAnterior = pedido.getEstado();
        pedido.setEstado("CONFIRMADO");
        Pedido guardado = pedidoRepository.save(pedido);
        registrarHistorial(guardado, estadoAnterior, "CONFIRMADO", null);
        if (guardado.getMesaId() != null) {
            mesaService.ocuparMesa(guardado.getMesaId());
        }

        if (cuponAplicado != null) {
            cuponService.registrarUso(cuponAplicado.codigo());
        }

        publicarPedido(guardado);
        return guardado;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerResumen(UUID pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
        return construirResumen(pedido, itemPedidoRepository.findByPedidoId(pedidoId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerResumen(Usuario usuario, UUID pedidoId) {
        exigirPersonal(usuario);
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
        if (pedido.getSucursalId() == null) {
            throw new IllegalStateException("El pedido no está asociado a una sucursal autorizada.");
        }
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);
        exigirAccesoSucursal(contexto, pedido.getSucursalId());
        return construirResumen(pedido, itemPedidoRepository.findByPedidoId(pedidoId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarPedidosActivos(Usuario usuario) {
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);
        List<UUID> sucursalIds = sucursalesPermitidas(contexto);
        if (sucursalIds.isEmpty()) {
            return List.of();
        }
        return pedidoRepository.findBySucursalIdInAndEstadoNotInOrderByFechaCreacionDesc(
                        sucursalIds, ESTADOS_FINALES).stream()
                .map(pedido -> construirResumen(
                        pedido, itemPedidoRepository.findByPedidoId(pedido.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarPedidosDeMesa(Usuario usuario, UUID mesaId) {
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);
        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa no existe."));
        exigirAccesoSucursal(contexto, mesa.getSucursalId());
        return pedidoRepository.findByMesaIdAndEstadoNotInOrderByFechaCreacionDesc(
                        mesaId, ESTADOS_FINALES).stream()
                .map(pedido -> construirResumen(
                        pedido, itemPedidoRepository.findByPedidoId(pedido.getId())))
                .toList();
    }

    @Transactional
    public Pedido actualizarEstado(Usuario usuario, UUID pedidoId, String estado) {
        exigirPersonal(usuario);
        String nuevoEstado = normalizarEstado(estado);
        if (nuevoEstado == null) {
            throw new IllegalArgumentException("El estado del pedido no es válido.");
        }

        Pedido pedido = cargarPedidoEnContexto(usuario, pedidoId);
        return cambiarEstado(usuario, pedido, nuevoEstado);
    }

    @Transactional
    public Pedido actualizarEstadoCocina(Usuario usuario, UUID pedidoId, String estado) {
        if (usuario == null || usuario.getRol() == null
                || !Set.of("COCINERO", "ADMINISTRADOR", "SUPERADMIN")
                .contains(usuario.getRol())) {
            throw new IllegalStateException(
                    "Se requiere una sesión de cocina autorizada para actualizar el pedido.");
        }
        String nuevoEstado = normalizarEstado(estado);
        if (nuevoEstado == null) {
            throw new IllegalArgumentException("El estado del pedido no es válido.");
        }
        Pedido pedido = cargarPedidoEnContexto(usuario, pedidoId);
        if (!esTransicionCocina(pedido.getEstado(), nuevoEstado)) {
            throw new IllegalArgumentException(
                    "La transición de cocina no es válida: "
                            + pedido.getEstado() + " -> " + nuevoEstado);
        }
        return cambiarEstado(usuario, pedido, nuevoEstado);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerPedidoServicio(Usuario usuario, UUID pedidoId,
                                                     String tipoServicio) {
        exigirPersonal(usuario);
        Pedido pedido = cargarPedidoEnContexto(usuario, pedidoId);
        if (tipoServicio != null && !tipoServicio.equals(pedido.getTipoServicio())) {
            throw new IllegalArgumentException(
                    "El pedido no corresponde al tipo de servicio solicitado.");
        }
        return construirResumen(pedido, itemPedidoRepository.findByPedidoId(pedidoId));
    }

    @Transactional
    public Pedido cambiarEstadoServicio(Usuario usuario, UUID pedidoId,
                                        String tipoServicio, String estado) {
        exigirPersonal(usuario);
        Pedido pedido = cargarPedidoEnContexto(usuario, pedidoId);
        if (tipoServicio != null && !tipoServicio.equals(pedido.getTipoServicio())) {
            throw new IllegalArgumentException(
                    "El pedido no corresponde al tipo de servicio solicitado.");
        }
        String nuevoEstado = normalizarEstado(estado);
        if (nuevoEstado == null) {
            throw new IllegalArgumentException("El estado del pedido no es válido.");
        }
        return cambiarEstado(usuario, pedido, nuevoEstado);
    }

    private Pedido cargarPedidoEnContexto(Usuario usuario, UUID pedidoId) {
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        if (pedido.getSucursalId() == null) {
            throw new IllegalStateException("El pedido no está asociado a una sucursal.");
        }
        exigirAccesoSucursal(contexto, pedido.getSucursalId());
        return pedido;
    }

    private Pedido cambiarEstado(Usuario usuario, Pedido pedido, String nuevoEstado) {
        String estadoActual = pedido.getEstado();
        if (ESTADOS_FINALES.contains(estadoActual)) {
            throw new IllegalArgumentException(
                    "El pedido ya está " + estadoActual + " y no puede actualizarse.");
        }
        if (!CatalogoPedidos.TRANSICIONES_PEDIDO
                .getOrDefault(estadoActual, Set.of()).contains(nuevoEstado)) {
            throw new IllegalArgumentException(
                    "La transición de estado no es válida: " + estadoActual + " -> " + nuevoEstado);
        }
        if (usuario != null && "COCINERO".equals(usuario.getRol())
                && !esTransicionCocina(estadoActual, nuevoEstado)) {
            throw new IllegalStateException(
                    "El rol de cocina solo puede avanzar pedidos a preparación o listos.");
        }

        pedido.setEstado(nuevoEstado);
        Pedido guardado = pedidoRepository.save(pedido);
        registrarHistorial(guardado, estadoActual, nuevoEstado, usuario);
        if (pedido.getMesaId() != null && ESTADOS_FINALES.contains(nuevoEstado)) {
            mesaService.liberarMesaSiCorresponde(pedido.getMesaId());
        }
        publicarPedido(guardado);
        return guardado;
    }

    private boolean esTransicionCocina(String estadoActual, String nuevoEstado) {
        return ("CONFIRMADO".equals(estadoActual) && "EN_PREPARACION".equals(nuevoEstado))
                || ("EN_PREPARACION".equals(estadoActual) && "LISTO".equals(nuevoEstado));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarPedidosCocina(Usuario usuario,
                                                         Collection<String> estados,
                                                         UUID sucursalIdSolicitada) {
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);
        List<UUID> sucursalesPermitidas = sucursalesPermitidas(contexto);
        if (sucursalIdSolicitada != null) {
            exigirAccesoSucursal(contexto, sucursalIdSolicitada);
            sucursalesPermitidas = List.of(sucursalIdSolicitada);
        }
        if (sucursalesPermitidas.isEmpty()) {
            return List.of();
        }
        Specification<Pedido> especificacion = construirEspecificacion(
                null, estados, null, null, null, null, sucursalesPermitidas);
        List<Pedido> pedidos = pedidoRepository.findAll(
                especificacion, Sort.by(Sort.Direction.ASC, "fechaCreacion"));
        return construirResumenes(pedidos);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarPedidos(Usuario usuario, String tipoServicio,
                                                  Collection<String> estados, String pagoEstado,
                                                  LocalDate fechaDesde, LocalDate fechaHasta,
                                                  String cliente, UUID sucursalIdSolicitada) {
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);
        List<UUID> sucursalesPermitidas = sucursalesPermitidas(contexto);
        if (sucursalIdSolicitada != null) {
            exigirAccesoSucursal(contexto, sucursalIdSolicitada);
            sucursalesPermitidas = List.of(sucursalIdSolicitada);
        }
        if (sucursalesPermitidas.isEmpty()) {
            return List.of();
        }

        Specification<Pedido> especificacion = construirEspecificacion(
                tipoServicio, estados, pagoEstado, fechaDesde, fechaHasta, cliente,
                sucursalesPermitidas);
        List<Pedido> pedidos = pedidoRepository.findAll(
                especificacion, Sort.by(Sort.Direction.DESC, "fechaCreacion"));
        return construirResumenes(pedidos);
    }

    @Transactional(readOnly = true)
    public BigDecimal obtenerTotalCalculado(UUID pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        BigDecimal total = (BigDecimal) construirResumen(
                pedido, itemPedidoRepository.findByPedidoId(pedidoId)).get("total");
        return total == null ? BigDecimal.ZERO : total;
    }

    private Specification<Pedido> construirEspecificacion(
            String tipoServicio, Collection<String> estados, String pagoEstado,
            LocalDate fechaDesde, LocalDate fechaHasta, String cliente,
            List<UUID> sucursalesPermitidas) {
        return (Root<Pedido> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (tipoServicio != null && !tipoServicio.isBlank()) {
                predicados.add(cb.equal(root.get("tipoServicio"), tipoServicio));
            }
            if (estados != null && !estados.isEmpty()) {
                predicados.add(root.get("estado").in(estados));
            }
            if (pagoEstado != null && !pagoEstado.isBlank()) {
                if (CatalogoPedidos.PAGO_PENDIENTE.equals(pagoEstado)) {
                    predicados.add(cb.or(
                            cb.isNull(root.get("pagoEstado")),
                            cb.equal(root.get("pagoEstado"), pagoEstado)));
                } else {
                    predicados.add(cb.equal(root.get("pagoEstado"), pagoEstado));
                }
            }
            if (fechaDesde != null) {
                predicados.add(cb.greaterThanOrEqualTo(
                        root.<LocalDateTime>get("fechaCreacion"), fechaDesde.atStartOfDay()));
            }
            if (fechaHasta != null) {
                predicados.add(cb.lessThanOrEqualTo(
                        root.<LocalDateTime>get("fechaCreacion"),
                        fechaHasta.atTime(LocalTime.MAX)));
            }
            if (cliente != null && !cliente.isBlank()) {
                predicados.add(cb.like(
                        cb.lower(root.get("nombre")),
                        "%" + cliente.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            predicados.add(root.get("sucursalId").in(sucursalesPermitidas));
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    private List<Map<String, Object>> construirResumenes(List<Pedido> pedidos) {
        if (pedidos.isEmpty()) {
            return List.of();
        }
        List<UUID> pedidoIds = pedidos.stream().map(Pedido::getId).toList();
        Map<UUID, List<ItemPedido>> itemsPorPedido = itemPedidoRepository
                .findByPedidoIdIn(pedidoIds).stream()
                .collect(Collectors.groupingBy(ItemPedido::getPedidoId));
        return pedidos.stream()
                .map(pedido -> construirResumen(
                        pedido, itemsPorPedido.getOrDefault(pedido.getId(), List.of())))
                .toList();
    }

    private String normalizarEstado(String estado) {
        if (estado == null) {
            return null;
        }
        String normalizado = estado.trim().toUpperCase(Locale.ROOT);
        return ESTADOS_VALIDOS.contains(normalizado) ? normalizado : null;
    }

    private void registrarHistorial(Pedido pedido, String estadoAnterior, String estadoNuevo,
                                    Usuario usuario) {
        HistorialPedido historial = new HistorialPedido();
        historial.setPedidoId(pedido.getId());
        historial.setEstadoAnterior(estadoAnterior);
        historial.setEstadoNuevo(estadoNuevo);
        if (usuario != null) {
            historial.setUsuarioId(usuario.getId());
            historial.setUsuarioNombre(usuario.getNombre());
        }
        historialPedidoRepository.save(historial);
    }

    public List<UUID> sucursalesParaEventos(Usuario usuario) {
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);
        return sucursalesPermitidas(contexto);
    }

    private Map<String, Object> construirResumen(Pedido pedido, List<ItemPedido> items) {
        List<Map<String, Object>> itemsResponse = new ArrayList<>();
        for (ItemPedido item : items) {
            BigDecimal precio = calculadoraPedidoService.normalizarMoneda(item.getPrecioUnitario());
            BigDecimal adicional = calculadoraPedidoService.normalizarMoneda(
                    item.getAdicionalOpciones());
            BigDecimal importe = precio.add(adicional)
                    .multiply(BigDecimal.valueOf(item.getCantidad()))
                    .setScale(CalculadoraPedidoService.ESCALA_MONETARIA,
                            CalculadoraPedidoService.REDONDEO);

            List<Map<String, Object>> detalle = productoOpcionService
                    .leerDetalle(item.getOpcionesDetalle());
            List<String> personalizaciones = detalle.stream()
                    .map(opcion -> String.valueOf(opcion.get("nombre")))
                    .filter(nombre -> !nombre.isBlank())
                    .toList();
            if (personalizaciones.isEmpty() && item.getOpcionNombres() != null
                    && !item.getOpcionNombres().isBlank()) {
                personalizaciones = List.of(item.getOpcionNombres().split(",\\s*"));
            }

            Map<String, Object> itemResponse = new LinkedHashMap<>();
            itemResponse.put("id", item.getId());
            itemResponse.put("productoId", item.getProductoId());
            itemResponse.put("nombre", item.getProductoNombre());
            itemResponse.put("cantidad", item.getCantidad());
            itemResponse.put("precioUnitario", precio);
            itemResponse.put("adicionalOpciones", adicional);
            itemResponse.put("importe", importe);
            itemResponse.put("personalizaciones", personalizaciones);
            itemResponse.put("opciones", detalle);
            itemResponse.put("observaciones", item.getObservaciones() == null
                    ? "" : item.getObservaciones());
            itemsResponse.add(itemResponse);
        }

        CalculadoraPedidoService.Desglose calculado = calculadoraPedidoService.calcular(
                items, pedido.getDescuento(), pedido.getTasaImpuesto(),
                pedido.getPropinaTipo(), pedido.getPropinaValor(),
                pedido.getPropina(), pedido.getCargos());
        CalculadoraPedidoService.Desglose desglose = "PENDIENTE".equalsIgnoreCase(pedido.getEstado())
                ? calculado
                : desgloseConsolidado(pedido, calculado);

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("id", pedido.getId());
        resumen.put("numeroSeguimiento", pedido.getNumeroSeguimiento());
        resumen.put("estado", pedido.getEstado());
        resumen.put("tipoServicio", pedido.getTipoServicio());
        resumen.put("mesaId", pedido.getMesaId());
        resumen.put("sucursalId", pedido.getSucursalId());
        resumen.put("nombre", pedido.getNombre());
        resumen.put("telefono", pedido.getTelefono());
        resumen.put("direccion", pedido.getDireccion() != null ? pedido.getDireccion() : "");
        resumen.put("referencia", pedido.getReferencia() != null ? pedido.getReferencia() : "");
        resumen.put("items", itemsResponse);
        resumen.put("subtotal", desglose.subtotal());
        resumen.put("descuento", desglose.descuento());
        resumen.put("baseGravable", desglose.baseGravable());
        resumen.put("impuestos", desglose.impuestos());
        resumen.put("tasaImpuesto", desglose.tasaImpuesto());
        resumen.put("cargos", desglose.cargos());
        resumen.put("propina", desglose.propina());
        resumen.put("propinaTipo", pedido.getPropinaTipo());
        resumen.put("propinaPorcentaje", "PORCENTAJE".equalsIgnoreCase(pedido.getPropinaTipo())
                ? pedido.getPropinaValor() : null);
        resumen.put("total", desglose.total());
        resumen.put("cuponCodigo", pedido.getCuponCodigo() != null ? pedido.getCuponCodigo() : "");
        resumen.put("pagoMetodo", pedido.getPagoMetodo() != null ? pedido.getPagoMetodo() : "");
        resumen.put("pagoEstado", pedido.getPagoEstado() != null ? pedido.getPagoEstado() : "");
        resumen.put("pagoImporteRecibido",
                calculadoraPedidoService.normalizarMoneda(pedido.getPagoImporteRecibido()));
        resumen.put("pagoCambio",
                calculadoraPedidoService.normalizarMoneda(pedido.getPagoCambio()));
        resumen.put("fechaCreacion", pedido.getFechaCreacion());
        resumen.put("observaciones", pedido.getObservaciones() != null ? pedido.getObservaciones() : "");
        return resumen;
    }

    private CalculadoraPedidoService.Desglose desgloseConsolidado(
            Pedido pedido, CalculadoraPedidoService.Desglose calculado) {
        BigDecimal subtotal = pedido.getSubtotal() != null
                && pedido.getSubtotal().signum() > 0
                ? calculadoraPedidoService.normalizarMoneda(pedido.getSubtotal())
                : calculado.subtotal();
        BigDecimal descuento = pedido.getDescuento() != null
                ? calculadoraPedidoService.normalizarMoneda(pedido.getDescuento())
                .max(BigDecimal.ZERO)
                .min(subtotal)
                : calculado.descuento();
        BigDecimal base = pedido.getBaseGravable() != null
                && pedido.getBaseGravable().signum() >= 0
                && pedido.getBaseGravable().compareTo(BigDecimal.ZERO) > 0
                ? calculadoraPedidoService.normalizarMoneda(pedido.getBaseGravable())
                : subtotal.subtract(descuento).max(BigDecimal.ZERO);
        BigDecimal tasa = pedido.getTasaImpuesto() != null
                ? calculadoraPedidoService.normalizarTasa(pedido.getTasaImpuesto())
                : calculado.tasaImpuesto();
        BigDecimal impuestos = pedido.getImpuestos() != null
                ? calculadoraPedidoService.normalizarMoneda(pedido.getImpuestos())
                : calculado.impuestos();
        BigDecimal propina = pedido.getPropina() != null
                ? calculadoraPedidoService.normalizarMoneda(pedido.getPropina())
                : calculado.propina();
        BigDecimal cargos = pedido.getCargos() != null
                ? calculadoraPedidoService.normalizarMoneda(pedido.getCargos())
                : calculado.cargos();
        BigDecimal total = pedido.getTotal() != null
                && pedido.getTotal().signum() > 0
                ? calculadoraPedidoService.normalizarMoneda(pedido.getTotal())
                : base.add(impuestos).add(propina).add(cargos);
        return new CalculadoraPedidoService.Desglose(subtotal, descuento, base,
                tasa, impuestos, propina, cargos, total);
    }

    private void recalcularTotales(Pedido pedido, BigDecimal tasa) {
        CalculadoraPedidoService.Desglose desglose = calculadoraPedidoService.calcular(
                itemPedidoRepository.findByPedidoId(pedido.getId()),
                pedido.getDescuento(), tasa, pedido.getPropinaTipo(),
                pedido.getPropinaValor(), pedido.getPropina(), pedido.getCargos());
        pedido.setSubtotal(desglose.subtotal());
        pedido.setDescuento(desglose.descuento());
        pedido.setBaseGravable(desglose.baseGravable());
        pedido.setImpuestos(desglose.impuestos());
        pedido.setTasaImpuesto(desglose.tasaImpuesto());
        pedido.setPropina(desglose.propina());
        pedido.setCargos(desglose.cargos());
        pedido.setTotal(desglose.total());
    }

    /**
     * La tasa de un pedido ya creado es la que quedó grabada en el pedido
     * (snapshot histórico). Solo se consulta la configuración vigente cuando
     * el pedido todavía no tiene una tasa asignada.
     */
    private BigDecimal tasaDelPedido(Pedido pedido) {
        if (pedido.getTasaImpuesto() != null) {
            return pedido.getTasaImpuesto();
        }
        return tasaActual(pedido);
    }

    private BigDecimal tasaActual(Pedido pedido) {
        if (pedido.getSucursalId() == null) {
            return BigDecimal.ZERO;
        }
        return tasaActualDeSucursal(pedido.getSucursalId());
    }

    private BigDecimal tasaActualDeSucursal(UUID sucursalId) {
        Sucursal sucursal = sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new IllegalStateException(
                        "La sucursal del pedido no existe."));
        return configuracionImpuestoService.obtenerTasaVigente(
                sucursal.getRestauranteId());
    }

    private String normalizarObservacion(String observacion) {
        if (observacion == null || observacion.isBlank()) {
            return null;
        }
        String normalizada = observacion.trim();
        if (normalizada.length() > MAX_OBSERVACION_ITEM) {
            throw new IllegalArgumentException(
                    "La observación de cada producto admite hasta " + MAX_OBSERVACION_ITEM + " caracteres.");
        }
        return normalizada;
    }

    private void aplicarPropina(Pedido pedido, BigDecimal propinaValor,
                                BigDecimal propinaPorcentaje, BigDecimal subtotal) {
        if (propinaPorcentaje != null) {
            BigDecimal porcentaje = calculadoraPedidoService.validarPorcentaje(propinaPorcentaje);
            pedido.setPropinaTipo(porcentaje.signum() == 0 ? "NINGUNA" : "PORCENTAJE");
            pedido.setPropinaValor(porcentaje);
            pedido.setPropina(calculadoraPedidoService.calcularPorcentaje(subtotal, porcentaje));
            return;
        }

        if (propinaValor != null) {
            if (propinaValor.signum() < 0) {
                throw new IllegalArgumentException("La propina no puede ser negativa.");
            }
            BigDecimal monto = calculadoraPedidoService.normalizarMoneda(propinaValor);
            pedido.setPropinaTipo(monto.signum() == 0 ? "NINGUNA" : "PERSONALIZADA");
            pedido.setPropinaValor(monto);
            pedido.setPropina(monto);
            return;
        }

        pedido.setPropinaTipo("NINGUNA");
        pedido.setPropinaValor(BigDecimal.ZERO);
        pedido.setPropina(BigDecimal.ZERO);
    }

    private List<UUID> sucursalesPermitidas(UsuarioContextService.UsuarioContext contexto) {
        if (contexto.getSucursalId() != null) {
            return List.of(contexto.getSucursalId());
        }
        return sucursalRepository.findByRestauranteId(contexto.getRestauranteId()).stream()
                .map(Sucursal::getId)
                .toList();
    }

    private void exigirAccesoSucursal(UsuarioContextService.UsuarioContext contexto, UUID sucursalId) {
        Sucursal sucursal = sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new IllegalStateException("La sucursal del recurso no existe."));
        if (!sucursal.getRestauranteId().equals(contexto.getRestauranteId())
                || (contexto.getSucursalId() != null
                && !contexto.getSucursalId().equals(sucursalId))) {
            throw new IllegalStateException("No tiene permisos sobre este pedido o mesa.");
        }
    }

    private void exigirPersonal(Usuario usuario) {
        if (usuario == null || usuario.getRol() == null
                || !Set.of("MESERO", "COCINERO", "ADMINISTRADOR", "SUPERADMIN").contains(usuario.getRol())) {
            throw new IllegalStateException("Se requiere una sesión de personal autorizado.");
        }
    }

    private void exigirMeseroOAdmin(Usuario usuario) {
        if (usuario == null || usuario.getRol() == null
                || !Set.of("MESERO", "ADMINISTRADOR", "SUPERADMIN")
                .contains(usuario.getRol())) {
            throw new IllegalStateException("Se requiere una sesión de mesero o administrador.");
        }
    }

    private void publicarPedido(Pedido pedido) {
        if (pedido.getSucursalId() == null) {
            return;
        }
        Map<String, Object> evento = construirResumen(
                pedido, itemPedidoRepository.findByPedidoId(pedido.getId()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventosService.publicar(pedido.getSucursalId(), evento);
                }
            });
        } else {
            eventosService.publicar(pedido.getSucursalId(), evento);
        }
    }
}
