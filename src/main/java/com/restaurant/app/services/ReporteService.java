package com.restaurant.app.services;

import com.restaurant.app.model.ItemPedido;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.ResumenVentaDiaria;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.ItemPedidoRepository;
import com.restaurant.app.repository.PedidoRepository;
import com.restaurant.app.repository.ResumenVentaDiariaRepository;
import com.restaurant.app.repository.SucursalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RF017, RF021, RF028, RF042: reportes de ventas.
 *
 * Una venta es un pedido confirmado que no fue cancelado (CONFIRMADO,
 * EN_PREPARACION, LISTO o ENTREGADO). Los pedidos pendientes no cuentan
 * porque el cliente aún no los ha confirmado.
 *
 * El administrador solo ve su sucursal; el superadmin ve todas las sucursales
 * de su restaurante o la que elija.
 */
@Service
public class ReporteService {

    static final Set<String> ESTADOS_VENTA = Set.of("CONFIRMADO", "EN_PREPARACION", "LISTO", "ENTREGADO");
    private static final Set<String> PERIODOS = Set.of("DIARIO", "SEMANAL", "QUINCENAL", "MENSUAL");
    private static final Set<String> ROLES = Set.of("ADMINISTRADOR", "SUPERADMIN");
    private static final int MAX_DIAS = 93;
    private static final int MAX_PEDIDOS_DETALLE = 500;

    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final SucursalRepository sucursalRepository;
    private final ResumenVentaDiariaRepository resumenRepository;
    private final UsuarioContextService usuarioContextService;

    public ReporteService(PedidoRepository pedidoRepository,
                          ItemPedidoRepository itemPedidoRepository,
                          SucursalRepository sucursalRepository,
                          ResumenVentaDiariaRepository resumenRepository,
                          UsuarioContextService usuarioContextService) {
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.sucursalRepository = sucursalRepository;
        this.resumenRepository = resumenRepository;
        this.usuarioContextService = usuarioContextService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reporteVentas(Usuario actor, String periodo, LocalDate desde,
                                             LocalDate hasta, UUID sucursalId) {
        String periodoNormalizado = periodo == null ? "" : periodo.trim().toUpperCase();
        if (!PERIODOS.contains(periodoNormalizado)) {
            throw new IllegalArgumentException("El periodo debe ser DIARIO, SEMANAL, QUINCENAL o MENSUAL.");
        }
        validarRango(desde, hasta);
        List<UUID> sucursales = sucursalesDelReporte(actor, sucursalId);

        List<Pedido> pedidos = sucursales.isEmpty() ? List.of()
                : pedidoRepository.findBySucursalIdInAndFechaCreacionBetween(
                sucursales, desde.atStartOfDay(), hasta.atTime(LocalTime.MAX));
        List<Pedido> ventas = pedidos.stream().filter(p -> ESTADOS_VENTA.contains(p.getEstado())).toList();
        Totales totales = Totales.de(pedidos);

        Map<String, Object> reporte = new LinkedHashMap<>();
        reporte.put("periodo", periodoNormalizado);
        reporte.put("desde", desde);
        reporte.put("hasta", hasta);
        reporte.put("sucursalId", sucursalId);
        reporte.put("totalVentas", totales.ventas());
        reporte.put("numeroPedidos", totales.pedidos());
        reporte.put("ticketPromedio", totales.pedidos() == 0 ? BigDecimal.ZERO
                : totales.ventas().divide(BigDecimal.valueOf(totales.pedidos()), 0, RoundingMode.HALF_UP));
        reporte.put("propinas", totales.propinas());
        reporte.put("impuestos", totales.impuestos());
        reporte.put("descuentos", totales.descuentos());
        reporte.put("pedidosCancelados", totales.cancelados());
        reporte.put("serie", "DIARIO".equals(periodoNormalizado)
                ? seriePorHora(ventas, desde) : seriePorDia(ventas, desde, hasta));
        reporte.put("porServicio", agrupar(ventas, Pedido::getTipoServicio, "tipoServicio"));
        reporte.put("porMetodoPago", agrupar(ventas,
                p -> p.getPagoMetodo() == null || p.getPagoMetodo().isBlank() ? "SIN_DEFINIR" : p.getPagoMetodo(),
                "metodo"));
        reporte.put("topProductos", topProductos(ventas));
        reporte.put("pedidos", pedidos.stream()
                .filter(p -> !"PENDIENTE".equals(p.getEstado()))
                .sorted(Comparator.comparing(Pedido::getFechaCreacion,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_PEDIDOS_DETALLE)
                .map(p -> {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("numeroSeguimiento", p.getNumeroSeguimiento());
                    fila.put("fecha", p.getFechaCreacion());
                    fila.put("tipoServicio", p.getTipoServicio());
                    fila.put("metodoPago", p.getPagoMetodo() == null ? "" : p.getPagoMetodo());
                    fila.put("estado", p.getEstado());
                    fila.put("total", cero(p.getTotal()));
                    return fila;
                }).toList());
        return reporte;
    }

    /** RF017 / RF042: registro diario guardado automáticamente en la base de datos. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> registroDiario(Usuario actor, LocalDate desde, LocalDate hasta, UUID sucursalId) {
        validarRango(desde, hasta);
        List<UUID> sucursales = sucursalesDelReporte(actor, sucursalId);
        if (sucursales.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, List<ResumenVentaDiaria>> porFecha = resumenRepository
                .findBySucursalIdInAndFechaBetweenOrderByFechaAsc(sucursales, desde, hasta).stream()
                .collect(Collectors.groupingBy(ResumenVentaDiaria::getFecha, TreeMap::new, Collectors.toList()));
        List<Map<String, Object>> filas = new ArrayList<>();
        porFecha.forEach((fecha, resumenes) -> {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("fecha", fecha);
            fila.put("totalVentas", resumenes.stream().map(ResumenVentaDiaria::getTotalVentas).reduce(BigDecimal.ZERO, BigDecimal::add));
            fila.put("numeroPedidos", resumenes.stream().mapToInt(ResumenVentaDiaria::getNumeroPedidos).sum());
            fila.put("pedidosCancelados", resumenes.stream().mapToInt(ResumenVentaDiaria::getPedidosCancelados).sum());
            fila.put("propinas", resumenes.stream().map(ResumenVentaDiaria::getPropinas).reduce(BigDecimal.ZERO, BigDecimal::add));
            fila.put("actualizado", resumenes.stream().map(ResumenVentaDiaria::getFechaActualizacion)
                    .max(Comparator.naturalOrder()).orElse(null));
            filas.add(fila);
        });
        return filas;
    }

    /** Recalcula y guarda el resumen del día para cada sucursal (lo usa el programador). */
    @Transactional
    public int registrarDia(LocalDate fecha) {
        int registrados = 0;
        for (Sucursal sucursal : sucursalRepository.findAll()) {
            List<Pedido> pedidos = pedidoRepository.findBySucursalIdInAndFechaCreacionBetween(
                    List.of(sucursal.getId()), fecha.atStartOfDay(), fecha.atTime(LocalTime.MAX));
            ResumenVentaDiaria resumen = resumenRepository.findByFechaAndSucursalId(fecha, sucursal.getId())
                    .orElse(null);
            if (pedidos.isEmpty() && resumen == null) {
                continue;
            }
            if (resumen == null) {
                resumen = new ResumenVentaDiaria();
                resumen.setFecha(fecha);
                resumen.setSucursalId(sucursal.getId());
            }
            Totales totales = Totales.de(pedidos);
            resumen.setTotalVentas(totales.ventas());
            resumen.setNumeroPedidos(totales.pedidos());
            resumen.setPedidosCancelados(totales.cancelados());
            resumen.setPropinas(totales.propinas());
            resumen.setImpuestos(totales.impuestos());
            resumen.setDescuentos(totales.descuentos());
            resumenRepository.save(resumen);
            registrados++;
        }
        return registrados;
    }

    private List<UUID> sucursalesDelReporte(Usuario actor, UUID sucursalId) {
        FacturacionService.exigirRol(actor, ROLES);
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(actor);
        if (contexto.getSucursalId() != null) {
            // Administrador: siempre su propia sucursal.
            return List.of(contexto.getSucursalId());
        }
        List<UUID> delRestaurante = sucursalRepository.findByRestauranteId(contexto.getRestauranteId()).stream()
                .map(Sucursal::getId).toList();
        if (sucursalId != null) {
            if (!delRestaurante.contains(sucursalId)) {
                throw new IllegalStateException("La sucursal no pertenece a su restaurante.");
            }
            return List.of(sucursalId);
        }
        return delRestaurante;
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Debe indicar las fechas desde y hasta.");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > MAX_DIAS) {
            throw new IllegalArgumentException("El rango máximo de un reporte es de " + MAX_DIAS + " días.");
        }
    }

    private List<Map<String, Object>> seriePorDia(List<Pedido> ventas, LocalDate desde, LocalDate hasta) {
        Map<LocalDate, List<Pedido>> porDia = ventas.stream()
                .filter(p -> p.getFechaCreacion() != null)
                .collect(Collectors.groupingBy(p -> p.getFechaCreacion().toLocalDate()));
        List<Map<String, Object>> serie = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            List<Pedido> delDia = porDia.getOrDefault(dia, List.of());
            Map<String, Object> punto = new LinkedHashMap<>();
            punto.put("fecha", dia);
            punto.put("total", sumar(delDia));
            punto.put("pedidos", delDia.size());
            serie.add(punto);
        }
        return serie;
    }

    private List<Map<String, Object>> seriePorHora(List<Pedido> ventas, LocalDate dia) {
        Map<Integer, List<Pedido>> porHora = ventas.stream()
                .filter(p -> p.getFechaCreacion() != null)
                .collect(Collectors.groupingBy(p -> p.getFechaCreacion().getHour()));
        int primera = porHora.keySet().stream().min(Integer::compare).orElse(11);
        int ultima = porHora.keySet().stream().max(Integer::compare).orElse(22);
        List<Map<String, Object>> serie = new ArrayList<>();
        for (int hora = Math.min(primera, 11); hora <= Math.max(ultima, 22); hora++) {
            List<Pedido> deLaHora = porHora.getOrDefault(hora, List.of());
            Map<String, Object> punto = new LinkedHashMap<>();
            punto.put("fecha", dia);
            punto.put("hora", hora);
            punto.put("total", sumar(deLaHora));
            punto.put("pedidos", deLaHora.size());
            serie.add(punto);
        }
        return serie;
    }

    private List<Map<String, Object>> agrupar(List<Pedido> ventas,
                                              java.util.function.Function<Pedido, String> clave,
                                              String nombreClave) {
        Map<String, List<Pedido>> grupos = ventas.stream()
                .collect(Collectors.groupingBy(p -> clave.apply(p) == null ? "SIN_DEFINIR" : clave.apply(p)));
        return grupos.entrySet().stream()
                .map(e -> {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put(nombreClave, e.getKey());
                    fila.put("total", sumar(e.getValue()));
                    fila.put("pedidos", e.getValue().size());
                    return fila;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("total")).compareTo((BigDecimal) a.get("total")))
                .toList();
    }

    private List<Map<String, Object>> topProductos(List<Pedido> ventas) {
        if (ventas.isEmpty()) {
            return List.of();
        }
        List<ItemPedido> items = itemPedidoRepository.findByPedidoIdIn(ventas.stream().map(Pedido::getId).toList());
        Map<String, BigDecimal[]> acumulado = new LinkedHashMap<>();
        for (ItemPedido item : items) {
            BigDecimal unitario = cero(item.getPrecioUnitario()).add(cero(item.getAdicionalOpciones()));
            BigDecimal[] valores = acumulado.computeIfAbsent(
                    item.getProductoNombre() == null ? "Producto" : item.getProductoNombre(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            valores[0] = valores[0].add(BigDecimal.valueOf(item.getCantidad()));
            valores[1] = valores[1].add(unitario.multiply(BigDecimal.valueOf(item.getCantidad())));
        }
        return acumulado.entrySet().stream()
                .sorted((a, b) -> b.getValue()[1].compareTo(a.getValue()[1]))
                .limit(10)
                .map(e -> {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("nombre", e.getKey());
                    fila.put("cantidad", e.getValue()[0].intValue());
                    fila.put("total", e.getValue()[1].setScale(2, RoundingMode.HALF_UP));
                    return fila;
                }).toList();
    }

    private static BigDecimal sumar(List<Pedido> pedidos) {
        return pedidos.stream().map(p -> cero(p.getTotal())).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    record Totales(BigDecimal ventas, int pedidos, int cancelados, BigDecimal propinas,
                   BigDecimal impuestos, BigDecimal descuentos) {

        static Totales de(List<Pedido> pedidos) {
            List<Pedido> ventas = pedidos.stream().filter(p -> ESTADOS_VENTA.contains(p.getEstado())).toList();
            int cancelados = (int) pedidos.stream().filter(p -> "CANCELADO".equals(p.getEstado())).count();
            return new Totales(
                    sumar(ventas),
                    ventas.size(),
                    cancelados,
                    ventas.stream().map(p -> cero(p.getPropina())).reduce(BigDecimal.ZERO, BigDecimal::add),
                    ventas.stream().map(p -> cero(p.getImpuestos())).reduce(BigDecimal.ZERO, BigDecimal::add),
                    ventas.stream().map(p -> cero(p.getDescuento())).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }
}
