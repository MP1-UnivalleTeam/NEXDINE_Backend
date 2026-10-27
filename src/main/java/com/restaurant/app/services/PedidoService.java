package com.restaurant.app.services;

import com.restaurant.app.dto.PedidoManualRequest;
import com.restaurant.app.model.ItemPedido;
import com.restaurant.app.model.Mesa;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.ItemPedidoRepository;
import com.restaurant.app.repository.MesaRepository;
import com.restaurant.app.repository.PedidoRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;
import com.restaurant.app.repository.SucursalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PedidoService {

    private static final Set<String> ESTADOS_FINALES = Set.of("ENTREGADO", "CANCELADO");
    private static final Set<String> ESTADOS_VALIDOS = Set.of(
            "PENDIENTE", "CONFIRMADO", "EN_PREPARACION", "LISTO", "ENTREGADO", "CANCELADO");

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
    private final BigDecimal tasaImpuesto;

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
                         @Value("${app.pedidos.tasa-impuesto:0}") BigDecimal tasaImpuesto) {
        if (tasaImpuesto.compareTo(BigDecimal.ZERO) < 0
                || tasaImpuesto.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("La tasa de impuesto debe estar entre 0 y 100.");
        }
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
        this.tasaImpuesto = tasaImpuesto;
    }

    @Transactional
    public Pedido crearPedidoInvitado(String nombre, String telefono, String tipoServicio,
                                      String direccion, String referencia) {
        return crearPedidoInvitado(nombre, telefono, tipoServicio, direccion, referencia, null);
    }

    @Transactional
    public Pedido crearPedidoInvitado(String nombre, String telefono, String tipoServicio,
                                      String direccion, String referencia, UUID sucursalId) {
        Pedido pedido = new Pedido();
        pedido.setNombre(nombre);
        pedido.setTelefono(telefono);
        pedido.setTipoServicio(tipoServicio);
        pedido.setDireccion(direccion);
        pedido.setReferencia(referencia);
        pedido.setEstado("PENDIENTE");
        if (sucursalId != null) {
            Sucursal sucursal = sucursalRepository.findById(sucursalId)
                    .filter(Sucursal::isActiva)
                    .orElseThrow(() -> new IllegalArgumentException("La sucursal no existe o está inactiva."));
            pedido.setSucursalId(sucursal.getId());
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
            item.setObservaciones(linea.observaciones());
            itemPedidoRepository.save(item);
        }

        recalcularTotales(guardado);
        Pedido pedidoGuardado = pedidoRepository.save(guardado);
        publicarPedido(pedidoGuardado);
        return pedidoGuardado;
    }

    @Transactional
    public ItemPedido agregarItem(UUID pedidoId, UUID productoId, int cantidad,
                                  List<UUID> opcionIds, String observaciones) {
        if (cantidad < 1) {
            throw new IllegalArgumentException("La cantidad debe ser al menos 1.");
        }
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

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
        item.setObservaciones(observaciones);

        ItemPedido guardado = itemPedidoRepository.save(item);
        recalcularTotales(pedido);
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

        if ("CONFIRMADO".equals(pedido.getEstado())) {
            throw new IllegalArgumentException("El pedido ya fue confirmado.");
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

        BigDecimal subtotal = calcularSubtotal(items);
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

        recalcularTotales(pedido);
        pedido.setEstado("CONFIRMADO");
        Pedido guardado = pedidoRepository.save(pedido);

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
        if (estado == null || !ESTADOS_VALIDOS.contains(estado.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("El estado del pedido no es válido.");
        }

        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        if (pedido.getSucursalId() == null) {
            throw new IllegalStateException("El pedido no está asociado a una sucursal.");
        }
        exigirAccesoSucursal(contexto, pedido.getSucursalId());
        pedido.setEstado(estado.trim().toUpperCase(Locale.ROOT));
        Pedido guardado = pedidoRepository.save(pedido);
        publicarPedido(guardado);
        return guardado;
    }

    public List<UUID> sucursalesParaEventos(Usuario usuario) {
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);
        return sucursalesPermitidas(contexto);
    }

    private Map<String, Object> construirResumen(Pedido pedido, List<ItemPedido> items) {
        List<Map<String, Object>> itemsResponse = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedido item : items) {
            BigDecimal precio = ceroSiNulo(item.getPrecioUnitario());
            BigDecimal adicional = ceroSiNulo(item.getAdicionalOpciones());
            BigDecimal importe = precio.add(adicional)
                    .multiply(BigDecimal.valueOf(item.getCantidad()));
            subtotal = subtotal.add(importe);

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

        BigDecimal descuento = ceroSiNulo(pedido.getDescuento())
                .max(BigDecimal.ZERO).min(subtotal);
        BigDecimal base = subtotal.subtract(descuento).max(BigDecimal.ZERO);
        BigDecimal tasaAplicada = pedido.getTasaImpuesto() == null
                ? tasaImpuesto : pedido.getTasaImpuesto();
        BigDecimal impuestos = calcularImpuestos(base, tasaAplicada);
        BigDecimal propina = calcularPropina(pedido, subtotal);
        BigDecimal cargos = ceroSiNulo(pedido.getCargos());
        BigDecimal total = base.add(impuestos).add(propina).add(cargos);

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
        resumen.put("subtotal", subtotal);
        resumen.put("descuento", descuento);
        resumen.put("impuestos", impuestos);
        resumen.put("tasaImpuesto", tasaAplicada);
        resumen.put("cargos", cargos);
        resumen.put("propina", propina);
        resumen.put("propinaTipo", pedido.getPropinaTipo());
        resumen.put("propinaPorcentaje", "PORCENTAJE".equalsIgnoreCase(pedido.getPropinaTipo())
                ? pedido.getPropinaValor() : null);
        resumen.put("total", total);
        resumen.put("cuponCodigo", pedido.getCuponCodigo() != null ? pedido.getCuponCodigo() : "");
        resumen.put("pagoMetodo", pedido.getPagoMetodo() != null ? pedido.getPagoMetodo() : "");
        resumen.put("pagoEstado", pedido.getPagoEstado() != null ? pedido.getPagoEstado() : "");
        return resumen;
    }

    private void recalcularTotales(Pedido pedido) {
        BigDecimal subtotal = calcularSubtotal(itemPedidoRepository.findByPedidoId(pedido.getId()));
        BigDecimal descuento = ceroSiNulo(pedido.getDescuento())
                .max(BigDecimal.ZERO).min(subtotal);
        BigDecimal base = subtotal.subtract(descuento).max(BigDecimal.ZERO);
        BigDecimal impuestos = calcularImpuestos(base, tasaImpuesto);
        BigDecimal propina = calcularPropina(pedido, subtotal);
        pedido.setSubtotal(subtotal);
        pedido.setDescuento(descuento);
        pedido.setImpuestos(impuestos);
        pedido.setTasaImpuesto(tasaImpuesto);
        pedido.setPropina(propina);
        pedido.setTotal(base.add(impuestos)
                .add(propina)
                .add(ceroSiNulo(pedido.getCargos())));
    }

    private BigDecimal calcularSubtotal(List<ItemPedido> items) {
        return items.stream()
                .map(item -> ceroSiNulo(item.getPrecioUnitario())
                        .add(ceroSiNulo(item.getAdicionalOpciones()))
                        .multiply(BigDecimal.valueOf(item.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void aplicarPropina(Pedido pedido, BigDecimal propinaValor,
                                BigDecimal propinaPorcentaje, BigDecimal subtotal) {
        if (propinaPorcentaje != null) {
            if (propinaPorcentaje.signum() < 0
                    || propinaPorcentaje.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("El porcentaje de propina debe estar entre 0 y 100.");
            }
            BigDecimal porcentaje = propinaPorcentaje.setScale(2, RoundingMode.HALF_UP);
            pedido.setPropinaTipo(porcentaje.signum() == 0 ? "NINGUNA" : "PORCENTAJE");
            pedido.setPropinaValor(porcentaje);
            pedido.setPropina(calcularPorcentaje(subtotal, porcentaje));
            return;
        }

        if (propinaValor != null) {
            if (propinaValor.signum() < 0) {
                throw new IllegalArgumentException("La propina no puede ser negativa.");
            }
            BigDecimal monto = propinaValor.setScale(2, RoundingMode.HALF_UP);
            pedido.setPropinaTipo(monto.signum() == 0 ? "NINGUNA" : "PERSONALIZADA");
            pedido.setPropinaValor(monto);
            pedido.setPropina(monto);
            return;
        }

        pedido.setPropinaTipo("NINGUNA");
        pedido.setPropinaValor(BigDecimal.ZERO);
        pedido.setPropina(BigDecimal.ZERO);
    }

    private BigDecimal calcularPropina(Pedido pedido, BigDecimal subtotal) {
        if ("PORCENTAJE".equalsIgnoreCase(pedido.getPropinaTipo())
                && pedido.getPropinaValor() != null) {
            return calcularPorcentaje(subtotal, pedido.getPropinaValor());
        }
        return ceroSiNulo(pedido.getPropina());
    }

    private BigDecimal calcularPorcentaje(BigDecimal base, BigDecimal porcentaje) {
        return base.multiply(porcentaje)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcularImpuestos(BigDecimal base, BigDecimal tasa) {
        return base.multiply(tasa)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal ceroSiNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
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
